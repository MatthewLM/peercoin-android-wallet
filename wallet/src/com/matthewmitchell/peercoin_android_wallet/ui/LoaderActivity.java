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

import com.matthewmitchell.peercoin_android_wallet.WalletApplication;
import com.matthewmitchell.peercoin_android_wallet.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;


/**
 * This class prevents the user interacting with the Activity before the wallet has had chance to load.
 */
public class LoaderActivity extends Activity {

	protected ProgressDialog progressDialog = null;
	WalletApplication application;
	
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
            
		progressDialog = ProgressDialog.show(this, getString(R.string.load_wallet_title), getString(R.string.load_wallet_message), true, false); 
		
		this.application = (WalletApplication) getApplication();
		
		// Register for load event
		
		this.runAfterLoad(new Runnable(){

			@Override
			public void run() {
				dismissDialog();
			}
			
		});
		
		super.onCreate(savedInstanceState);
        
	}
	
	public void dismissDialog() {
		
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
			progressDialog = null;
		}
		
	}
	
	@Override
	protected void onDestroy() {
		
		dismissDialog();
		super.onDestroy();
		
	}
	
	public void runAfterLoad(final Runnable callback) {
		
		final Activity activity = this;
		
		application.setOnLoadedCallback(new Runnable(){

			@Override
			public void run() {
				activity.runOnUiThread(callback);
			}
			
		});
		
	}
	
}
