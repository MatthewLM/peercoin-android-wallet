/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.matthewmitchell.peercoin_android_wallet.ui;

import com.matthewmitchell.peercoin_android_wallet.R;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import com.google.common.base.Charsets;
import com.matthewmitchell.peercoin_android_wallet.Constants;
import com.matthewmitchell.peercoin_android_wallet.WalletApplication;
import static com.matthewmitchell.peercoin_android_wallet.ui.AbstractWalletActivity.log;
import com.matthewmitchell.peercoin_android_wallet.util.Crypto;
import com.matthewmitchell.peercoin_android_wallet.util.Io;
import com.matthewmitchell.peercoin_android_wallet.util.WalletUtils;
import com.matthewmitchell.peercoinj.core.Wallet;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreWalletTask extends AsyncTask<Void, Void, IOException> {

	private enum WalletType {
		WALLET_TYPE_ENCRYPTED_FILE,
		WALLET_TYPE_ENCRYPTED_CIPHER,
		WALLET_TYPE_PROTOBUF,
		WALLET_TYPE_BASE58
	}
	
	AbstractWalletActivity activity;
	File file;
	String password;
	WalletType type;
	InputStream cipher;
	
	protected static final Logger log = LoggerFactory.getLogger(RestoreWalletTask.class);
	
	@Override
	protected IOException doInBackground(Void... v) {
			
		final Wallet wallet;

		if (type == WalletType.WALLET_TYPE_ENCRYPTED_FILE
			|| type == WalletType.WALLET_TYPE_ENCRYPTED_CIPHER) {

			try {

				InputStreamReader reader;
				
				if (type == WalletType.WALLET_TYPE_ENCRYPTED_FILE)
					reader = new InputStreamReader(new FileInputStream(file), Charsets.UTF_8);
				else
					reader = new InputStreamReader(cipher, Charsets.UTF_8);
				
				final BufferedReader cipherIn = new BufferedReader(reader);
				final StringBuilder cipherText = new StringBuilder();
				Io.copy(cipherIn, cipherText, Constants.BACKUP_MAX_CHARS);
				cipherIn.close();

				final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), password.toCharArray());
				final InputStream is = new ByteArrayInputStream(plainText);

				wallet = WalletUtils.restoreWalletFromProtobufOrBase58(is);

			}catch (final IOException x) {
				return x;
			}

		}else{

			FileInputStream is = null;

			try {

				is = new FileInputStream(file);
				
				if (type == WalletType.WALLET_TYPE_PROTOBUF)
					wallet = WalletUtils.restoreWalletFromProtobuf(is);
				else
					wallet = WalletUtils.restorePrivateKeysFromBase58(is);

			}catch (final IOException x) {
				return x;
			}finally{
				try{
					if (is != null)
						is.close();
				}catch (final IOException x2) {	
					// Swallow
				}
			}
			
		}

		log.info("successfully restored wallet: {}", file);

		activity.getWalletApplication().replaceWallet(wallet);

		return null;
	}
	
	@Override
	protected void onPostExecute(IOException exception) {
		
		activity.dismissDialog();
		
		if (exception == null) {
			
			// Success
			
			final DialogBuilder dialog = new DialogBuilder(activity);
			final StringBuilder message = new StringBuilder();
			message.append(activity.getString(R.string.restore_wallet_dialog_success));
			message.append("\n\n");
			message.append(activity.getString(R.string.restore_wallet_dialog_success_replay));
			dialog.setMessage(message);
			dialog.setNeutralButton(R.string.button_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int id) {
					activity.getWalletApplication().resetBlockchain();
					activity.finish();
				}
			});
			dialog.show();
			
		}else{
			
			final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.import_export_keys_dialog_failure_title);
			dialog.setMessage(activity.getString(R.string.import_keys_dialog_failure, exception.getMessage()));
			dialog.setPositiveButton(R.string.button_dismiss, null);
			dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int id) {
					activity.showDialog(activity.DIALOG_RESTORE_WALLET);
				}
			});
			dialog.show();

			log.info("problem restoring wallet", exception);
			
		}
		
	}
	
	@Override
	protected void onCancelled (IOException result) {
		activity.dismissDialog();
	}
	
	private void restoreWallet(final File file, final String password, AbstractWalletActivity activity, WalletType type) {
		this.activity = activity;
		this.file = file;
		this.password = password;
		this.type = type;
		activity.progressDialog = ProgressDialog.show(activity, "Restoring Wallet",  
				"Please wait whilst the backup is loaded and restored...", true, false); 
		execute();
	} 
	
	public void restoreWalletFromEncrypted(final File file, final String password, AbstractWalletActivity activity) {
		restoreWallet(file, password, activity, WalletType.WALLET_TYPE_ENCRYPTED_FILE);
	}
	
	public void restoreWalletFromEncrypted(final InputStream cipher, final String password, AbstractWalletActivity activity) {
		this.cipher = cipher;
		restoreWallet(null, password, activity, WalletType.WALLET_TYPE_ENCRYPTED_CIPHER);
	}
	
	public void restoreWalletFromProtobuf(final File file, AbstractWalletActivity activity) {
		restoreWallet(file, null, activity, WalletType.WALLET_TYPE_PROTOBUF);
	}
	
	public void restorePrivateKeysFromBase58(final File file, AbstractWalletActivity activity) {
		restoreWallet(file, null, activity, WalletType.WALLET_TYPE_BASE58);
	}
	
}
