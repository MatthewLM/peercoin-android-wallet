/*
 * Copyright 2011-2014 the original author or authors.
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

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.google.common.base.Charsets;
import com.matthewmitchell.peercoin_android_wallet.Constants;

import com.matthewmitchell.peercoin_android_wallet.WalletApplication;
import com.matthewmitchell.peercoin_android_wallet.R;
import com.matthewmitchell.peercoin_android_wallet.util.Crypto;
import com.matthewmitchell.peercoin_android_wallet.util.Io;
import com.matthewmitchell.peercoin_android_wallet.util.WalletUtils;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractWalletActivity extends LoaderActivity
{
	protected static final int DIALOG_RESTORE_WALLET = 0;
	private WalletApplication application;
	
	protected RestoreWalletTask restoreTask = null;

	protected static final Logger log = LoggerFactory.getLogger(AbstractWalletActivity.class);

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		application = (WalletApplication) getApplication();

		super.onCreate(savedInstanceState);
	}

	protected WalletApplication getWalletApplication()
	{
		return application;
	}

	protected final void toast(@Nonnull final String text, final Object... formatArgs)
	{
		toast(text, 0, Toast.LENGTH_SHORT, formatArgs);
	}

	protected final void longToast(@Nonnull final String text, final Object... formatArgs)
	{
		toast(text, 0, Toast.LENGTH_LONG, formatArgs);
	}

	protected final void toast(@Nonnull final String text, final int imageResId, final int duration, final Object... formatArgs)
	{
		final View view = getLayoutInflater().inflate(R.layout.transient_notification, null);
		TextView tv = (TextView) view.findViewById(R.id.transient_notification_text);
		tv.setText(String.format(text, formatArgs));
		tv.setCompoundDrawablesWithIntrinsicBounds(imageResId, 0, 0, 0);

		final Toast toast = new Toast(this);
		toast.setView(view);
		toast.setDuration(duration);
		toast.show();
	}

	protected final void toast(final int textResId, final Object... formatArgs)
	{
		toast(textResId, 0, Toast.LENGTH_SHORT, formatArgs);
	}

	protected final void longToast(final int textResId, final Object... formatArgs)
	{
		toast(textResId, 0, Toast.LENGTH_LONG, formatArgs);
	}

	protected final void toast(final int textResId, final int imageResId, final int duration, final Object... formatArgs)
	{
		final View view = getLayoutInflater().inflate(R.layout.transient_notification, null);
		TextView tv = (TextView) view.findViewById(R.id.transient_notification_text);
		tv.setText(getString(textResId, formatArgs));
		tv.setCompoundDrawablesWithIntrinsicBounds(imageResId, 0, 0, 0);

		final Toast toast = new Toast(this);
		toast.setView(view);
		toast.setDuration(duration);
		toast.show();
	}
	
	protected void restoreWalletFromEncrypted(@Nonnull final InputStream cipher, @Nonnull final String password) {
		restoreTask = new RestoreWalletTask();
		restoreTask.restoreWalletFromEncrypted(cipher, password, this);
	}
	
	protected void restoreWalletFromEncrypted(@Nonnull final File file, @Nonnull final String password) {
		restoreTask = new RestoreWalletTask();
		restoreTask.restoreWalletFromEncrypted(file, password, this);
	}

	protected void restoreWalletFromProtobuf(@Nonnull final File file) {
		restoreTask = new RestoreWalletTask();
		restoreTask.restoreWalletFromProtobuf(file, this);
	}

	protected void restorePrivateKeysFromBase58(@Nonnull final File file) {
		restoreTask = new RestoreWalletTask();
		restoreTask.restorePrivateKeysFromBase58(file, this);
	}
	
	@Override
	protected void onStop() {
		if (restoreTask != null) { 
			restoreTask.cancel(false);
			restoreTask = null;
		}
		super.onStop();
	}

}
