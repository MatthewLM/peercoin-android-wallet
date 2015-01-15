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

import android.content.Context;
import com.matthewmitchell.peercoin_android_wallet.Constants;
import java.io.File;

import com.matthewmitchell.peercoinj.core.BlockChain;
import com.matthewmitchell.peercoinj.store.BlockStore;
import com.matthewmitchell.peercoinj.store.BlockStoreException;
import com.matthewmitchell.peercoinj.store.ValidHashStore;

public class BlockchainData {
	
	public BlockStore blockStore = null;
	public File blockChainFile = null;
	public BlockChain blockChain = null;
	public File validHashStoreFile = null;
	public ValidHashStore validHashStore = null;
	
        public BlockchainData(Context context) {
            
            blockChainFile = new File(context.getDir("blockstore", Context.MODE_PRIVATE), Constants.Files.BLOCKCHAIN_FILENAME);
            validHashStoreFile = new File(context.getDir("validhashes", Context.MODE_PRIVATE), Constants.Files.VALID_HASHES_FILENAME);
            
        }
        
	public void delete(boolean resetBlockchain) {
		
		 if (blockStore != null) {
	            try
	            {
	                blockStore.close();
	            }
	            catch (final BlockStoreException x)
	            {
	                throw new RuntimeException(x);
	            }
	        }

                if (validHashStore != null) validHashStore.close();
                
                if (resetBlockchain) {
                    if (validHashStoreFile != null) validHashStoreFile.delete();
                    if (blockChainFile != null) blockChainFile.delete();
                }
		
	}
	
}
