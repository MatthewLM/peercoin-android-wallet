/*
 * Copyright 2014 Matthew Mitchell
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.matthewmitchell.peercoin_android_wallet.ui;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.matthewmitchell.peercoin_android_wallet.Constants;
import com.matthewmitchell.peercoin_android_wallet.WalletApplication;
import com.matthewmitchell.peercoinj.core.BlockChain;
import com.matthewmitchell.peercoinj.core.Wallet;
import com.matthewmitchell.peercoinj.store.BlockStoreException;
import com.matthewmitchell.peercoinj.store.SPVBlockStore;
import com.matthewmitchell.peercoinj.store.ValidHashStore;

import android.content.AsyncTaskLoader;
import android.content.Context;

public class BlockchainLoader extends AsyncTaskLoader<BlockchainData> {
	
	WalletApplication application;
	Context context;
	BlockchainData bcd;
        
        boolean resetBlockchain;
	
	private static final Logger log = LoggerFactory.getLogger(BlockchainLoader.class);
	
	public BlockchainLoader(Context context, WalletApplication application) {
		super(context);
		this.context = context;
		this.application = application;
	}
	
	@Override
	protected void onStartLoading() {
		super.onStartLoading();
		bcd = new BlockchainData(context);
		forceLoad();
	}

	@Override
	public BlockchainData loadInBackground() {
		
		final Wallet wallet = application.getWallet();
		final boolean blockChainFileExists = bcd.blockChainFile.exists();

		if (!blockChainFileExists) {
			log.info("blockchain does not exist, resetting wallet");

			wallet.clearTransactions(0);
			wallet.setLastBlockSeenHeight(-1); // magic value
			wallet.setLastBlockSeenHash(null);
		}
		
		if(!isStarted())
			return null;

		try {
			bcd.blockStore = new SPVBlockStore(Constants.NETWORK_PARAMETERS, bcd.blockChainFile);
			bcd.blockStore.getChainHead(); // detect corruptions as early as possible
		}catch (final BlockStoreException x) {
			bcd.blockChainFile.delete();

			final String msg = "blockstore cannot be created";
			log.error(msg, x);
			throw new Error(msg, x);
		}
		
		if(!isStarted())
			return null;

		log.info("using " + bcd.blockStore.getClass().getName());
		
		try{
			bcd.validHashStore = new ValidHashStore(bcd.validHashStoreFile);
		}catch (IOException x){
			bcd.validHashStoreFile.delete();
			final String msg = "validhashstore cannot be created";
			log.error(msg, x);
			throw new Error(msg, x);
		}
		
		if(!isStarted())
			return null;

		try {
			bcd.blockChain = new BlockChain(Constants.NETWORK_PARAMETERS, wallet, bcd.blockStore, bcd.validHashStore);
		}catch (final BlockStoreException x) {
			throw new Error("blockchain cannot be created", x);
		}
		
		return bcd;
		
	}
        
        public void stopLoading(boolean resetBlockchain) {
		this.resetBlockchain = resetBlockchain;
		super.stopLoading();
        }
	
	@Override
	protected void onStopLoading() {
		cancelLoad();
	}
	
	@Override 
	public void onCanceled(BlockchainData data) {
		super.onCanceled(data);
		data.delete(resetBlockchain);
	}
	
}
