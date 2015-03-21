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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.matthewmitchell.peercoinj.core.Address;
import com.matthewmitchell.peercoinj.core.Wallet;
import com.matthewmitchell.peercoinj.uri.PeercoinURI;
import com.matthewmitchell.peercoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.graphics.Bitmap;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.matthewmitchell.peercoin_android_wallet.Constants;
import com.matthewmitchell.peercoin_android_wallet.WalletApplication;
import com.matthewmitchell.peercoin_android_wallet.util.BitmapFragment;
import com.matthewmitchell.peercoin_android_wallet.util.Qr;
import com.matthewmitchell.peercoin_android_wallet.util.ThrottlingWalletChangeListener;
import com.matthewmitchell.peercoin_android_wallet.util.WalletUtils;
import com.matthewmitchell.peercoin_android_wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletAddressFragment extends Fragment implements NfcAdapter.CreateNdefMessageCallback
{
	private WalletActivity activity;
	private WalletApplication application;
	private LoaderManager loaderManager;
	@CheckForNull
	private NfcAdapter nfcAdapter;

	private ImageView currentAddressQrView;

	private Bitmap currentAddressQrBitmap = null;
	private Spanned currentAddressQrLabel = null;
	private final AtomicReference<String> currentAddressUriRef = new AtomicReference<String>();

	private static final int ID_ADDRESS_LOADER = 0;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (WalletActivity)activity;
		this.application = (WalletApplication) activity.getApplication();
		this.loaderManager = getLoaderManager();
		final NfcManager nfcManager = (NfcManager) activity.getSystemService(Context.NFC_SERVICE);
		this.nfcAdapter = nfcManager.getDefaultAdapter();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (nfcAdapter != null && nfcAdapter.isEnabled())
			nfcAdapter.setNdefPushMessageCallback(this, activity);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_address_fragment, container, false);
		currentAddressQrView = (ImageView) view.findViewById(R.id.peercoin_address_qr);

		currentAddressQrView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				handleShowQRCode();
			}
		});

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();
		
		activity.runAfterLoad(new Runnable() {

		    @Override
		    public void run() {
				loaderManager.initLoader(ID_ADDRESS_LOADER, null, addressLoaderCallbacks);
		    }
		    
		});

		updateView();
	}

	@Override
	public void onPause()
	{
		loaderManager.destroyLoader(ID_ADDRESS_LOADER);

		super.onPause();
	}

	private void updateView()
	{
		currentAddressQrView.setImageBitmap(currentAddressQrBitmap);
	}

	private void handleShowQRCode()
	{
		BitmapFragment.show(getFragmentManager(), currentAddressQrBitmap, currentAddressQrLabel);
	}

	public static class AddressData {
		public Address address;
		public Bitmap bitmap;
		public String addressStr;
		public Spanned label;
	}
	
	public static class CurrentAddressLoader extends AsyncTaskLoader<AddressData>
	{
		private LocalBroadcastManager broadcastManager;
		private final Wallet wallet;
		private final int size;

		private static final Logger log = LoggerFactory.getLogger(WalletBalanceLoader.class);

		public CurrentAddressLoader(final Context context, @Nonnull final Wallet wallet, final int size)
		{
			super(context);

			this.broadcastManager = LocalBroadcastManager.getInstance(context.getApplicationContext());
			this.wallet = wallet;
			this.size = size;
		}

		@Override
		protected void onStartLoading()
		{
			super.onStartLoading();

			wallet.addEventListener(walletChangeListener, Threading.SAME_THREAD);
			broadcastManager.registerReceiver(walletChangeReceiver, new IntentFilter(WalletApplication.ACTION_WALLET_CHANGED));

			safeForceLoad();
		}

		@Override
		protected void onStopLoading()
		{
			broadcastManager.unregisterReceiver(walletChangeReceiver);
			wallet.removeEventListener(walletChangeListener);
			walletChangeListener.removeCallbacks();

			super.onStopLoading();
		}

		@Override
		protected void onReset()
		{
			broadcastManager.unregisterReceiver(walletChangeReceiver);
			wallet.removeEventListener(walletChangeListener);
			walletChangeListener.removeCallbacks();

			super.onReset();
		}

		@Override
		public AddressData loadInBackground() {
			
			AddressData data = new AddressData();
			data.address = wallet.currentReceiveAddress();
			data.addressStr = PeercoinURI.convertToPeercoinURI(data.address, null, null, null);
			data.bitmap = Qr.bitmap(data.addressStr, size);
			data.label = WalletUtils.formatAddress(data.address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE);
			
			return data;
			
		}

		private final ThrottlingWalletChangeListener walletChangeListener = new ThrottlingWalletChangeListener()
		{
			@Override
			public void onThrottledWalletChanged()
			{
				safeForceLoad();
			}
		};

		private final BroadcastReceiver walletChangeReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(final Context context, final Intent intent)
			{
				safeForceLoad();
			}
		};

		private void safeForceLoad()
		{
			try
			{
				forceLoad();
			}
			catch (final RejectedExecutionException x)
			{
				log.info("rejected execution: " + CurrentAddressLoader.this.toString());
			}
		}
	}

	private final LoaderCallbacks<AddressData> addressLoaderCallbacks = new LoaderManager.LoaderCallbacks<AddressData>()
	{
		private Address lastAddress;

		@Override
		public Loader<AddressData> onCreateLoader(final int id, final Bundle args) {
			final int size = getResources().getDimensionPixelSize(R.dimen.bitmap_dialog_qr_size);
			return new CurrentAddressLoader(activity, application.getWallet(), size);
		}

		@Override
		public void onLoadFinished(final Loader<AddressData> loader, final AddressData currentAddress)
		{
			if (!currentAddress.equals(lastAddress))
			{
				lastAddress = currentAddress.address;
				currentAddressQrBitmap = currentAddress.bitmap;
				currentAddressQrLabel = currentAddress.label;
				currentAddressUriRef.set(currentAddress.addressStr);

				updateView();
			}
		}

		@Override
		public void onLoaderReset(final Loader<AddressData> loader)
		{
		}
	};

	@Override
	public NdefMessage createNdefMessage(final NfcEvent event)
	{
		final String uri = currentAddressUriRef.get();
		if (uri != null)
			return new NdefMessage(new NdefRecord[] { NdefRecord.createUri(uri) });
		else
			return null;
	}
}
