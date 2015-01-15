/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.matthewmitchell.peercoin_android_wallet.service;

import android.content.AsyncTaskLoader;
import android.content.Context;
import com.matthewmitchell.peercoin_android_wallet.Constants;
import com.matthewmitchell.peercoinj.core.PeerGroup;
import com.matthewmitchell.peercoinj.net.discovery.PeerDBDiscovery;
import java.io.File;


public class PeerDBDiscoveryLoader extends AsyncTaskLoader<PeerDBDiscovery>  {

	private final File file;
	private final PeerGroup peerGroup; 
	
	public PeerDBDiscoveryLoader(Context context, PeerGroup peerGroup) {
		super(context);
		this.file = new File(context.getDir("peers", Context.MODE_PRIVATE), Constants.Files.PEERS_FILENAME);
		this.peerGroup = peerGroup;
	}
	
	@Override
	protected void onStartLoading() {
		super.onStartLoading();
		forceLoad();
	}
	
	@Override
	protected void onStopLoading() {
		cancelLoad();
	}
	
	@Override
	public PeerDBDiscovery loadInBackground() {
		return new PeerDBDiscovery(Constants.NETWORK_PARAMETERS, file, peerGroup);
	}
	
}
