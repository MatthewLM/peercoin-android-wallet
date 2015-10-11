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

package com.matthewmitchell.peercoin_android_wallet.ui.send;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.matthewmitchell.peercoinj.protocols.payments.Protos.Payment;
import com.matthewmitchell.peercoinj.core.Address;
import com.matthewmitchell.peercoinj.core.AddressFormatException;
import com.matthewmitchell.peercoinj.core.Coin;
import com.matthewmitchell.peercoinj.core.Monetary;
import com.matthewmitchell.peercoinj.core.NetworkParameters;
import com.matthewmitchell.peercoinj.core.InsufficientMoneyException;
import com.matthewmitchell.peercoinj.core.Sha256Hash;
import com.matthewmitchell.peercoinj.core.Transaction;
import com.matthewmitchell.peercoinj.core.TransactionConfidence;
import com.matthewmitchell.peercoinj.core.TransactionConfidence.ConfidenceType;
import com.matthewmitchell.peercoinj.core.VerificationException;
import com.matthewmitchell.peercoinj.core.VersionedChecksummedBytes;
import com.matthewmitchell.peercoinj.core.Wallet;
import com.matthewmitchell.peercoinj.core.Wallet.BalanceType;
import com.matthewmitchell.peercoinj.core.Wallet.CouldNotAdjustDownwards;
import com.matthewmitchell.peercoinj.core.Wallet.SendRequest;
import com.matthewmitchell.peercoinj.core.Wallet.TooSmallOutput;
import com.matthewmitchell.peercoinj.protocols.payments.PaymentProtocol;
import com.matthewmitchell.peercoinj.wallet.KeyChain.KeyPurpose;
import com.matthewmitchell.peercoinj.shapeshift.AsyncHttpClient;
import com.matthewmitchell.peercoinj.shapeshift.ShapeShift;
import com.matthewmitchell.peercoinj.shapeshift.ShapeShiftCoin;
import com.matthewmitchell.peercoinj.shapeshift.ShapeShiftComm;
import com.matthewmitchell.peercoinj.shapeshift.ShapeShiftMonetary;
import com.matthewmitchell.peercoinj.utils.MonetaryFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import com.matthewmitchell.peercoin_android_wallet.AddressBookProvider;
import com.matthewmitchell.peercoin_android_wallet.Configuration;
import com.matthewmitchell.peercoin_android_wallet.Constants;
import com.matthewmitchell.peercoin_android_wallet.ExchangeRatesProvider;
import com.matthewmitchell.peercoin_android_wallet.ExchangeRatesProvider.WalletExchangeRate;
import com.matthewmitchell.peercoin_android_wallet.WalletApplication;
import com.matthewmitchell.peercoin_android_wallet.data.PaymentIntent;
import com.matthewmitchell.peercoin_android_wallet.data.PaymentIntent.Standard;
import com.matthewmitchell.peercoin_android_wallet.integration.android.PeercoinIntegration;
import com.matthewmitchell.peercoin_android_wallet.offline.DirectPaymentTask;
import com.matthewmitchell.peercoin_android_wallet.ui.AbstractBindServiceActivity;
import com.matthewmitchell.peercoin_android_wallet.ui.AddressAndLabel;
import com.matthewmitchell.peercoin_android_wallet.ui.CurrencyAmountView;
import com.matthewmitchell.peercoin_android_wallet.ui.CurrencyCalculatorLink;
import com.matthewmitchell.peercoin_android_wallet.ui.DialogBuilder;
import com.matthewmitchell.peercoin_android_wallet.ui.EditAddressBookEntryFragment;
import com.matthewmitchell.peercoin_android_wallet.ui.ExchangeRateLoader;
import com.matthewmitchell.peercoin_android_wallet.ui.InputParser.BinaryInputParser;
import com.matthewmitchell.peercoin_android_wallet.ui.InputParser.StreamInputParser;
import com.matthewmitchell.peercoin_android_wallet.ui.InputParser.StringInputParser;
import com.matthewmitchell.peercoin_android_wallet.ui.ProgressDialogFragment;
import com.matthewmitchell.peercoin_android_wallet.ui.ScanActivity;
import com.matthewmitchell.peercoin_android_wallet.ui.TransactionsListAdapter;
import com.matthewmitchell.peercoin_android_wallet.util.Bluetooth;
import com.matthewmitchell.peercoin_android_wallet.util.Nfc;
import com.matthewmitchell.peercoin_android_wallet.util.WalletUtils;
import com.matthewmitchell.peercoin_android_wallet.R;
import java.util.List;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends Fragment
{
    private AbstractBindServiceActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private ContentResolver contentResolver;
    private LoaderManager loaderManager;
    private FragmentManager fragmentManager;
    @CheckForNull
    private BluetoothAdapter bluetoothAdapter;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private TextView payeeNameView;
    private TextView payeeVerifiedByView;
    private AutoCompleteTextView receivingAddressView;
    private ReceivingAddressViewAdapter receivingAddressViewAdapter;
    private View receivingStaticView;
    private TextView receivingStaticAddressView;
    private TextView receivingStaticLabelView;
    private CurrencyCalculatorLink amountCalculatorLink;
    private CheckBox directPaymentEnableView;

    private TextView hintView;
    private TextView shapeShiftHintView;
    private TextView shapeShiftEstView;
    private TextView directPaymentMessageView;
    private ListView sentTransactionView;
    private TransactionsListAdapter sentTransactionListAdapter;
    private View privateKeyPasswordViewGroup;
    private EditText privateKeyPasswordView;
    private View privateKeyBadPasswordView;
    private Button viewGo;
    private Button viewCancel;

    private CurrencyAmountView localAmountView;
    private CurrencyAmountView ppcAmountView;

    private State state = State.INPUT;

    private PaymentIntent paymentIntent = null;
    private AddressAndLabel validatedAddress = null;

    private Transaction sentTransaction = null;
    private Boolean directPaymentAck = null;

    private Transaction dryrunTransaction;
    private Exception dryrunException;

    // SHAPESHIFT

    private enum ShapeShiftStatus {
        NONE, FUTURE_UPDATE, UPDATING, OUTSIDE_LIMITS, PARSE_ERROR, CONNECTION_ERROR, OTHER_ERROR, TOO_SMALL
    }

    private LinearLayout shapeShiftTitles;
    private LinearLayout shapeShiftAmounts;
    private Spinner destCoinSpinner;
    private TextView shapeShiftForeignTitle;
    private CurrencyAmountView shapeShiftForeignAmountView;
    private CurrencyAmountView shapeShiftRateView;

    private ArrayAdapter destCoinSpinnerAdapter;

    private ShapeShiftCoin usingShapeShiftCoin;
    private boolean isExactForeignAmount;
    private Address depositAddress = null;
    private Address unusedSendAmountAddress = null;
    private ShapeShiftStatus shapeShiftStatus = ShapeShiftStatus.NONE;
    private String shapeShiftStatusText = "";
    private Coin limitMin;
    private Coin limitMax;
    private ShapeShiftComm activeShapeShiftComm = null;
    private Handler updateDelayHandler = new Handler(Looper.getMainLooper());
    private long lastSendAmountUpdate = 0;
    private long secondsToUpdate;
    private long futureUpdateTime;

    private final long SHAPESHIFT_ERROR_DELAY = 20000;
    private final long SHAPESHIFT_LIMIT_DELAY = 30000;
    private final long SHAPESHIFT_SHIFT_DELAY = 60 * 1000;
    private final long SHAPESHIFT_MIN_SEND_AMOUNT_DELAY = 5000;
    private final long SHAPESHIFT_SEND_AMOUNT_GAP = 5 * 60 * 1000;

    // END SHAPESHIFT

    private static final int ID_RATE_LOADER = 0;
    private static final int ID_RECEIVING_ADDRESS_LOADER = 1;

    private static final int REQUEST_CODE_SCAN = 0;
    private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST = 1;
    private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT = 2;

    private static final Logger log = LoggerFactory.getLogger(SendCoinsFragment.class);

    private enum State
    {
        INPUT, FINALISE_SHAPESHIFT, DECRYPTING, SIGNING, SENDING, SENT, FAILED
    }

    private final class ReceivingAddressListener implements OnFocusChangeListener, TextWatcher
    {
        @Override
        public void onFocusChange(final View v, final boolean hasFocus)
        {
            if (!hasFocus)
            {
                validateReceivingAddress(false);
                updateView();
            }
        }

        @Override
        public void afterTextChanged(final Editable s)
        {
            if (s.length() > 0)
                validateReceivingAddress(true);
            else
                updateView();
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
        {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
        {
        }
    }

    private final ReceivingAddressListener receivingAddressListener = new ReceivingAddressListener();

    private final class ReceivingAddressActionMode implements ActionMode.Callback
    {
        private final Address address;

        public ReceivingAddressActionMode(final Address address)
        {
            this.address = address;
        }

        @Override
        public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
        {
            final MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.send_coins_address_context, menu);

            return true;
        }

        @Override
        public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
        {
            menu.findItem(R.id.send_coins_address_context_clear).setVisible(paymentIntent.mayEditAddress());

            return true;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
        {
            switch (item.getItemId())
            {
                case R.id.send_coins_address_context_edit_address:
                    handleEditAddress();

                    mode.finish();
                    return true;

                case R.id.send_coins_address_context_clear:
                    handleClear();

                    mode.finish();
                    return true;
            }

            return false;
        }

        @Override
        public void onDestroyActionMode(final ActionMode mode)
        {
            if (receivingStaticView.hasFocus())
                requestFocusFirst();
        }

        private void handleEditAddress()
        {
            EditAddressBookEntryFragment.edit(fragmentManager, address.toString(), receivingStaticLabelView.getText().toString());
        }

        private void handleClear()
        {
            // switch from static to input
            validatedAddress = null;
            receivingAddressView.setText(null);
            receivingStaticAddressView.setText(null);

            updateView();

            requestFocusFirst();
        }
    }

    private final CurrencyAmountView.Listener amountsListener = new CurrencyAmountView.Listener()
    {
        @Override
        public void changed() {

            updateShapeShift(false);

            updateView();
            handler.post(dryrunRunnable);

        }

        @Override
        public void focusChanged(final boolean hasFocus)
        {
        }
    };

    private final TextWatcher privateKeyPasswordListener = new TextWatcher()
    {
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
        {
            privateKeyBadPasswordView.setVisibility(View.INVISIBLE);
            updateView();
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
        {
        }

        @Override
        public void afterTextChanged(final Editable s)
        {
        }
    };

    private final ContentObserver contentObserver = new ContentObserver(handler)
    {
        @Override
        public void onChange(final boolean selfChange)
        {
            updateView();
        }
    };

    private final TransactionConfidence.Listener sentTransactionConfidenceListener = new TransactionConfidence.Listener()
    {
        @Override
        public void onConfidenceChanged(final Transaction tx, final TransactionConfidence.Listener.ChangeReason reason)
        {
            activity.runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if (!isResumed())
                                return;

                            sentTransactionListAdapter.notifyDataSetChanged();

                            final TransactionConfidence confidence = sentTransaction.getConfidence();
                            final ConfidenceType confidenceType = confidence.getConfidenceType();
                            final int numBroadcastPeers = confidence.numBroadcastPeers();

                            if (state == State.SENDING)
                            {
                                if (confidenceType == ConfidenceType.DEAD)
                                    setState(State.FAILED);
                                else if (numBroadcastPeers > 1 || confidenceType == ConfidenceType.BUILDING)
                                    setState(State.SENT);
                            }

                            if (reason == ChangeReason.SEEN_PEERS && confidenceType == ConfidenceType.PENDING)
                            {
                                // play sound effect
                                final int soundResId = getResources().getIdentifier("send_coins_broadcast_" + numBroadcastPeers, "raw",
                                        activity.getPackageName());
                                if (soundResId > 0)
                                    RingtoneManager.getRingtone(activity, Uri.parse("android.resource://" + activity.getPackageName() + "/" + soundResId))
                                        .play();
                            }
                        }
                    });
        }
    };

    private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
    {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
        {
            return new ExchangeRateLoader(activity, config);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
        {
            if (data != null && data.getCount() > 0)
            {
                data.moveToFirst();
                final WalletExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);

                if (state == State.INPUT)
                    amountCalculatorLink.setExchangeRate(exchangeRate.rate);
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader)
        {
        }
    };

    private final LoaderCallbacks<Cursor> receivingAddressLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
    {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
        {
            final String constraint = args != null ? args.getString("constraint") : null;
            return new CursorLoader(activity, AddressBookProvider.contentUri(activity.getPackageName()), null, AddressBookProvider.SELECTION_QUERY,
                    new String[] { constraint != null ? constraint : "" }, null);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> cursor, final Cursor data)
        {
            receivingAddressViewAdapter.swapCursor(data);
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> cursor)
        {
            receivingAddressViewAdapter.swapCursor(null);
        }
    };

    private final class ReceivingAddressViewAdapter extends CursorAdapter implements FilterQueryProvider
    {
        public ReceivingAddressViewAdapter(final Context context)
        {
            super(context, null, false);
            setFilterQueryProvider(this);
        }

        @Override
        public View newView(final Context context, final Cursor cursor, final ViewGroup parent)
        {
            final LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(R.layout.address_book_row, parent, false);
        }

        @Override
        public void bindView(final View view, final Context context, final Cursor cursor)
        {
            final String label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
            final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));

            final ViewGroup viewGroup = (ViewGroup) view;
            final TextView labelView = (TextView) viewGroup.findViewById(R.id.address_book_row_label);
            labelView.setText(label);
            final TextView addressView = (TextView) viewGroup.findViewById(R.id.address_book_row_address);
            addressView.setText(WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
        }

        @Override
        public CharSequence convertToString(final Cursor cursor)
        {
            return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
        }

        @Override
        public Cursor runQuery(final CharSequence constraint)
        {
            final Bundle args = new Bundle();
            if (constraint != null)
                args.putString("constraint", constraint.toString());
            loaderManager.restartLoader(ID_RECEIVING_ADDRESS_LOADER, args, receivingAddressLoaderCallbacks);
            return getCursor();
        }
    }

    private final DialogInterface.OnClickListener activityDismissListener = new DialogInterface.OnClickListener()
    {
        @Override
        public void onClick(final DialogInterface dialog, final int which)
        {
            activity.finish();
        }
    };

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);

        // Now we have the ability to runAfterLoad. Do all the things that need initialisation.

        activity.runAfterLoad(new Runnable() {

            @Override
            public void run() {

                config = application.getConfiguration();
                wallet = application.getWallet();

                for (CurrencyAmountView v : new CurrencyAmountView []{ppcAmountView, shapeShiftRateView}) {
                    v.setCurrencySymbol(config.getFormat().code());
                    v.setInputFormat(config.getMaxPrecisionFormat());
                    v.setHintFormat(config.getFormat());
                }

                localAmountView.setInputFormat(Constants.LOCAL_FORMAT);
                localAmountView.setHintFormat(Constants.LOCAL_FORMAT);

                amountCalculatorLink.setExchangeDirection(config.getLastExchangeDirection());

                sentTransactionListAdapter = new TransactionsListAdapter(activity, wallet, application.maxConnectedPeers(), false);
                sentTransactionView.setAdapter(sentTransactionListAdapter);

                if (savedInstanceState == null) {

                    final Intent intent = activity.getIntent();
                    final String action = intent.getAction();
                    final Uri intentUri = intent.getData();
                    final String scheme = intentUri != null ? intentUri.getScheme() : null;
                    final String mimeType = intent.getType();

                    if ((Intent.ACTION_VIEW.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) && intentUri != null
                            && ("ppcoin".equals(scheme) || ShapeShift.getCoin(scheme) != null)) {
                        initStateFromPeercoinUri(intentUri);
                    }else if ((NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) && PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(mimeType)) {
                        final NdefMessage ndefMessage = (NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
                        final byte[] ndefMessagePayload = Nfc.extractMimePayload(PaymentProtocol.MIMETYPE_PAYMENTREQUEST, ndefMessage);
                        initStateFromPaymentRequest(mimeType, ndefMessagePayload);
                    }else if ((Intent.ACTION_VIEW.equals(action)) && PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(mimeType)) {
                        final byte[] paymentRequest = PeercoinIntegration.paymentRequestFromIntent(intent);

                        if (intentUri != null)
                            initStateFromIntentUri(mimeType, intentUri);
                        else if (paymentRequest != null)
                            initStateFromPaymentRequest(mimeType, paymentRequest);
                        else
                            throw new IllegalArgumentException();
                    }else if (intent.hasExtra(SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT)) {
                        initStateFromIntentExtras(intent.getExtras());
                    }else{
                        updateStateFrom(PaymentIntent.blank());
                    }
                }else
                    restoreInstanceState(savedInstanceState); // May need wallet

            }

        });

    }

    @Override
    public void onAttach(final Activity activity)
    {
        super.onAttach(activity);

        this.activity = (AbstractBindServiceActivity) activity;
        this.application = (WalletApplication) activity.getApplication();
        this.contentResolver = activity.getContentResolver();
        this.loaderManager = getLoaderManager();
        this.fragmentManager = getFragmentManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
    {
        final View view = inflater.inflate(R.layout.send_coins_fragment, container);

        payeeNameView = (TextView) view.findViewById(R.id.send_coins_payee_name);
        payeeVerifiedByView = (TextView) view.findViewById(R.id.send_coins_payee_verified_by);

        ppcAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_ppc);
        localAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_local);
        amountCalculatorLink = new CurrencyCalculatorLink(ppcAmountView, localAmountView);

        shapeShiftTitles = (LinearLayout) view.findViewById(R.id.send_coins_shapeshift_titles);
        shapeShiftAmounts = (LinearLayout) view.findViewById(R.id.send_coins_shapeshift_amounts);
        destCoinSpinner = (Spinner) view.findViewById(R.id.send_coins_shapeshift_dest_coin_spinner);
        shapeShiftForeignTitle = (TextView) view.findViewById(R.id.send_coins_fragment_shapeshift_foreign_label);
        shapeShiftForeignAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_shapeshift_foreign);
        shapeShiftRateView = (CurrencyAmountView) view.findViewById(R.id.send_coins_shapeshift_rate);

        destCoinSpinnerAdapter = new ArrayAdapter<NetworkParameters>(getActivity(), android.R.layout.simple_spinner_item);
        destCoinSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        destCoinSpinner.setAdapter(destCoinSpinnerAdapter);

        destCoinSpinner.setOnItemSelectedListener(new OnItemSelectedListener () {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                NetworkParameters network = (NetworkParameters) parent.getItemAtPosition(position);

                if (network.isShapeShift()) {
                    // Only set shapeshift again if network changed
                    if (network != usingShapeShiftCoin)
                        setShapeShift((ShapeShiftCoin) network, isExactForeignAmount, null);
                }else
                    usingShapeShiftCoin = null;

                updateView();

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }

        });

        shapeShiftForeignAmountView.setListener(new CurrencyAmountView.Listener() {

            @Override
            public void changed() {

                updateShapeShift(true);

            }

            @Override
            public void focusChanged(boolean hasFocus) {

            }

        });
        receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
        receivingAddressViewAdapter = new ReceivingAddressViewAdapter(activity);
        receivingAddressView.setAdapter(receivingAddressViewAdapter);
        receivingAddressView.setOnFocusChangeListener(receivingAddressListener);
        receivingAddressView.addTextChangedListener(receivingAddressListener);

        receivingStaticView = view.findViewById(R.id.send_coins_receiving_static);
        receivingStaticAddressView = (TextView) view.findViewById(R.id.send_coins_receiving_static_address);
        receivingStaticLabelView = (TextView) view.findViewById(R.id.send_coins_receiving_static_label);

        receivingStaticView.setOnFocusChangeListener(new OnFocusChangeListener()
                {
                    private ActionMode actionMode;

                    @Override
                    public void onFocusChange(final View v, final boolean hasFocus)
                    {
                        if (hasFocus)
                        {
                            final Address address = paymentIntent.hasAddress() ? paymentIntent.getAddress()
                                : (validatedAddress != null ? validatedAddress.address : null);
                            if (address != null)
                                actionMode = activity.startActionMode(new ReceivingAddressActionMode(address));
                        }
                        else
                        {
                            actionMode.finish();
                        }
                    }
                });

        directPaymentEnableView = (CheckBox) view.findViewById(R.id.send_coins_direct_payment_enable);
        directPaymentEnableView.setOnCheckedChangeListener(new OnCheckedChangeListener()
                {
                    @Override
                    public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked)
                    {
                        if (paymentIntent.isBluetoothPaymentUrl() && isChecked && !bluetoothAdapter.isEnabled())
                        {
                            // ask for permission to enable bluetooth
                            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT);
                        }
                    }
                });

        hintView = (TextView) view.findViewById(R.id.send_coins_hint);
        shapeShiftHintView = (TextView) view.findViewById(R.id.send_coins_shapeshift_hint);
        shapeShiftEstView = (TextView) view.findViewById(R.id.send_coins_shapeshift_est); 
        shapeShiftEstView.setText(R.string.send_coins_fragment_hint_shapeshift_estimated);

        directPaymentMessageView = (TextView) view.findViewById(R.id.send_coins_direct_payment_message);

        sentTransactionView = (ListView) view.findViewById(R.id.send_coins_sent_transaction);

        privateKeyPasswordViewGroup = view.findViewById(R.id.send_coins_private_key_password_group);
        privateKeyPasswordView = (EditText) view.findViewById(R.id.send_coins_private_key_password);
        privateKeyBadPasswordView = view.findViewById(R.id.send_coins_private_key_bad_password);

        viewGo = (Button) view.findViewById(R.id.send_coins_go);
        viewGo.setOnClickListener(new OnClickListener()
                {
                    @Override
                    public void onClick(final View v)
                    {
                        validateReceivingAddress(false);

                        if (everythingValid())
                            handleGo();
                        else
                            requestFocusFirst();

                        updateView();
                    }
                });

        viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
        viewCancel.setOnClickListener(new OnClickListener()
                {
                    @Override
                    public void onClick(final View v)
                    {
                        handleCancel();
                    }
                });

        return view;
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();

        config.setLastExchangeDirection(amountCalculatorLink.getExchangeDirection());
    }

    @Override
    public void onResume()
    {
        super.onResume();

        contentResolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true, contentObserver);

        amountCalculatorLink.setListener(amountsListener);
        privateKeyPasswordView.addTextChangedListener(privateKeyPasswordListener);

        activity.runAfterLoad(new Runnable() {

            @Override
            public void run() {

                loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
                loaderManager.initLoader(ID_RECEIVING_ADDRESS_LOADER, null, receivingAddressLoaderCallbacks);

                updateView();
                handler.post(dryrunRunnable);

            }

        });

    }

    @Override
    public void onPause()
    {
        loaderManager.destroyLoader(ID_RECEIVING_ADDRESS_LOADER);
        loaderManager.destroyLoader(ID_RATE_LOADER);

        privateKeyPasswordView.removeTextChangedListener(privateKeyPasswordListener);
        amountCalculatorLink.setListener(null);

        contentResolver.unregisterContentObserver(contentObserver);

        super.onPause();
    }

    @Override
    public void onDetach() {
        handler.removeCallbacksAndMessages(null);
        updateDelayHandler.removeCallbacksAndMessages(null);
        super.onDetach();
    }

    @Override
    public void onDestroy()
    {
        backgroundThread.getLooper().quit();

        if (sentTransaction != null)
            sentTransaction.getConfidence().removeEventListener(sentTransactionConfidenceListener);

        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState)
    {
        super.onSaveInstanceState(outState);

        saveInstanceState(outState);
    }

    private void saveInstanceState(final Bundle outState) {

        outState.putSerializable("state", state);

        outState.putParcelable("payment_intent", paymentIntent);
        if (validatedAddress != null)
            outState.putParcelable("validated_address", validatedAddress);

        if (sentTransaction != null)
            outState.putSerializable("sent_transaction_hash", sentTransaction.getHash());
        if (directPaymentAck != null)
            outState.putBoolean("direct_payment_ack", directPaymentAck);

        if (usingShapeShiftCoin != null) {
            outState.putString("shapeshift_coin", usingShapeShiftCoin.getId());
            outState.putBoolean("exact_foreign_amount", isExactForeignAmount);
            outState.putSerializable("shapeshift_status", shapeShiftStatus);
            outState.putSerializable("shapeshift_foreign_amount", shapeShiftForeignAmountView.getAmount());
            outState.putLong("shapeshift_last_update", lastSendAmountUpdate);
            outState.putLong("shapeshift_update_time", futureUpdateTime);
        }

    }

    private void restoreInstanceState(final Bundle savedInstanceState) {

        state = (State) savedInstanceState.getSerializable("state");

        paymentIntent = (PaymentIntent) savedInstanceState.getParcelable("payment_intent");
        validatedAddress = savedInstanceState.getParcelable("validated_address");

        if (paymentIntent.networks != null)
            destCoinSpinnerAdapter.addAll(paymentIntent.networks);
        else if (validatedAddress != null)
            destCoinSpinnerAdapter.addAll(validatedAddress.address.getParameters());

        if (savedInstanceState.containsKey("sent_transaction_hash")) {
            sentTransaction = wallet.getTransaction((Sha256Hash) savedInstanceState.getSerializable("sent_transaction_hash"));
            sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);
        }

        if (savedInstanceState.containsKey("direct_payment_ack"))
            directPaymentAck = savedInstanceState.getBoolean("direct_payment_ack");

        if (savedInstanceState.containsKey("shapeshift_coin")) {

            shapeShiftStatus = (ShapeShiftStatus) savedInstanceState.getSerializable("shapeshift_status");
            lastSendAmountUpdate = savedInstanceState.getLong("shapeshift_last_update");
            isExactForeignAmount = savedInstanceState.getBoolean("exact_foreign_amount");
            futureUpdateTime = savedInstanceState.getLong("shapeshift_update_time");

            setShapeShiftNoUpdate(ShapeShift.getCoin(savedInstanceState.getString("shapeshift_coin")),
                    (Monetary) savedInstanceState.getSerializable("shapeshift_foreign_amount"));

            // As the amounts get reset after this function run it in a Handler

            handler.post(new Runnable() {

                @Override
                public void run() {
                    if (shapeShiftStatus == ShapeShiftStatus.FUTURE_UPDATE)
                        futureUpdate(futureUpdateTime - System.currentTimeMillis());
                    else
                        updateShapeShift(isExactForeignAmount);
                }

            });

        }

    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
    {
        handler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        onActivityResultResumed(requestCode, resultCode, intent);
                    }
                });
    }

    private void onActivityResultResumed(final int requestCode, final int resultCode, final Intent intent)
    {
        if (requestCode == REQUEST_CODE_SCAN)
        {
            if (resultCode == Activity.RESULT_OK)
            {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

                new StringInputParser(input)
                {
                    @Override
                    protected void handlePaymentIntent(final PaymentIntent paymentIntent)
                    {
                        updateStateFrom(paymentIntent);
                    }

                    @Override
                    protected void handleDirectTransaction(final Transaction transaction) throws VerificationException
                    {
                        cannotClassify(input);
                    }

                    @Override
                    protected void error(final int messageResId, final Object... messageArgs)
                    {
                        dialog(activity, null, R.string.button_scan, messageResId, messageArgs);
                    }
                }.parse();
            }
        }
        else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST)
        {
            if (paymentIntent.isBluetoothPaymentRequestUrl() && usingShapeShiftCoin == null)
                requestPaymentRequest();
        }
        else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT)
        {
            if (paymentIntent.isBluetoothPaymentUrl() && usingShapeShiftCoin == null)
                directPaymentEnableView.setChecked(resultCode == Activity.RESULT_OK);
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
    {
        inflater.inflate(R.menu.send_coins_fragment_options, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu)
    {
        final MenuItem scanAction = menu.findItem(R.id.send_coins_options_scan);
        final PackageManager pm = activity.getPackageManager();
        scanAction.setVisible(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));
        scanAction.setEnabled(state == State.INPUT);

        final MenuItem emptyAction = menu.findItem(R.id.send_coins_options_empty);
        emptyAction.setEnabled(state == State.INPUT);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.send_coins_options_scan:
                handleScan();
                return true;

            case R.id.send_coins_options_empty:
                handleEmpty();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private Address getAddress() {
        if (paymentIntent.hasAddress())
            return paymentIntent.getAddress();
        if (validatedAddress != null)
            return validatedAddress.address;
        return null;
    }

    private boolean maybeUpdateShapeShift() {

        if (usingShapeShiftCoin == null)
            return true;

        if (activeShapeShiftComm != null && activeShapeShiftComm.shouldStop()) {
            activeShapeShiftComm = null;
            updateShapeShift(isExactForeignAmount);
            return true;
        }

        return false;

    }

    private void futureUpdate(long delay) {

        activeShapeShiftComm = null;
        if (shapeShiftStatus == ShapeShiftStatus.NONE)
            shapeShiftStatus = ShapeShiftStatus.FUTURE_UPDATE;

        updateDelayHandler.removeCallbacksAndMessages(null);

        futureUpdateTime = System.currentTimeMillis() + delay;

        final Runnable timerRunnable = new Runnable() {

            @Override
            public void run() {

                if (state != State.INPUT)
                    return;

                long now = System.currentTimeMillis();
                long remaining = futureUpdateTime - now;

                if (remaining <= 0) {
                    updateShapeShift(isExactForeignAmount);
                    return;
                }

                // Split minutes, seconds and milliseconds

                long milipart = remaining % 1000;
                remaining /= 1000;
                secondsToUpdate = remaining;
                long secondPart = remaining % 60;
                remaining /= 60;
                long minutePart = remaining;

                // Update UI
                updateView();

                // Get time delay of the next update

                long updateDelay = milipart + 1; // Add one to absolutely ensurely it falls to the next time period

                if (minutePart == 1)
                    // Get to seconds
                    updateDelay += secondPart * 1000;
                else if (minutePart > 1)
                    // Get to the next half minute to round to the nearest minute
                    updateDelay += (secondPart >= 30 ? secondPart - 30 : secondPart + 30) * 1000;

                updateDelayHandler.postDelayed(this, updateDelay);

            }


        };

        timerRunnable.run();

    }

    private void handleShapeShiftError(final int networkCode, final String text) {
        if (networkCode == AsyncHttpClient.CONNECTION_ERROR)
            shapeShiftStatus = ShapeShiftStatus.CONNECTION_ERROR;
        else if (networkCode == AsyncHttpClient.PARSE_ERROR)
            shapeShiftStatus = ShapeShiftStatus.PARSE_ERROR;
        else {
            shapeShiftStatus = ShapeShiftStatus.OTHER_ERROR;
            shapeShiftStatusText = text;
        }
    }

    class ShapeShiftCallbacks extends ShapeShiftComm.Callbacks {

        @Override
        public void networkError(final int networkCode, final String text) {

            SendCoinsFragment.this.activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    if (maybeUpdateShapeShift())
                        return;

                    handler.post(dryrunRunnable);
                    handleShapeShiftError(networkCode, text);
                    futureUpdate(SHAPESHIFT_ERROR_DELAY);

                }

            });

        }

    }

    private void updateShapeShift(boolean isExactForeignAmountLocal) {
        // Make the necessary shapeshift calls

        if (usingShapeShiftCoin == null || state != State.INPUT)
            return;

        updateDelayHandler.removeCallbacksAndMessages(null);
        isExactForeignAmount = isExactForeignAmountLocal;

        if (activeShapeShiftComm != null) {
            activeShapeShiftComm.stop();
            return;
        }

        final ShapeShiftComm shapeShiftComm = new ShapeShiftComm();
        activeShapeShiftComm = shapeShiftComm;
        depositAddress = null;

        if (isExactForeignAmount) {

            final ShapeShiftMonetary amount = (ShapeShiftMonetary) shapeShiftForeignAmountView.getAmount();

            if (amount == null) {
                amountCalculatorLink.setPPCAmount(null);
                shapeShiftRateView.setAmount(Coin.ZERO, false);
                activeShapeShiftComm = null;
                shapeShiftStatus = ShapeShiftStatus.NONE;
                updateView();
                return;
            }

            long timeRemaining = SHAPESHIFT_MIN_SEND_AMOUNT_DELAY - (System.currentTimeMillis() - lastSendAmountUpdate);

            if (timeRemaining > 0) {
                // Do not update now
                futureUpdate(timeRemaining);
                return;
            }

            shapeShiftComm.setCallbacks(new ShapeShiftCallbacks() {

                @Override
                public void sendAmountResponse(final Address deposit, final Coin amount, final long expiry, final Coin rate) {

                    SendCoinsFragment.this.activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            unusedSendAmountAddress = deposit;

                            if (maybeUpdateShapeShift())
                                return;

                            if (amount.isZero()) {
                                shapeShiftStatus = ShapeShiftStatus.TOO_SMALL;
                                futureUpdate(SHAPESHIFT_LIMIT_DELAY);
                                return;
                            }

                            amountCalculatorLink.setPPCAmount(amount);
                            shapeShiftRateView.setAmount(rate, false);
                            depositAddress = deposit;
                            lastSendAmountUpdate = System.currentTimeMillis();

                            handler.post(dryrunRunnable);

                            long delay = expiry - System.currentTimeMillis() - SHAPESHIFT_SEND_AMOUNT_GAP;
                            shapeShiftStatus = ShapeShiftStatus.NONE;
                            futureUpdate(Math.max(delay, SHAPESHIFT_MIN_SEND_AMOUNT_DELAY));

                        }

                    });

                }

                @Override
                public void cancelPendingResponse() {

                    SendCoinsFragment.this.activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            unusedSendAmountAddress = null;

                            if (maybeUpdateShapeShift())
                                return;

                            Address refund = wallet.currentAddress(KeyPurpose.REFUND);
                            shapeShiftComm.sendAmount(usingShapeShiftCoin, getAddress(), amount, refund);

                        }

                    });

                }

            });

            shapeShiftStatus = ShapeShiftStatus.UPDATING;
            updateView();

            // Make sure to cancel the old transaction first if needed
            if (unusedSendAmountAddress != null)
                shapeShiftComm.cancelPending(unusedSendAmountAddress);
            else {
                Address refund = wallet.currentAddress(KeyPurpose.REFUND);
                shapeShiftComm.sendAmount(usingShapeShiftCoin, getAddress(), amount, refund);
            }

        }else{

            final Coin amount = amountCalculatorLink.getAmount();

            if (amount == null) {
                shapeShiftForeignAmountView.setAmount(null, false);
                shapeShiftRateView.setAmount(Coin.ZERO, false);
                activeShapeShiftComm = null;
                shapeShiftStatus = ShapeShiftStatus.NONE;
                updateView();
                return;
            }

            shapeShiftComm.setCallbacks(new ShapeShiftCallbacks() {

                @Override
                public void marketInfoResponse(final ShapeShiftMonetary rate, final ShapeShiftMonetary fee, final Coin max, final Coin min) {

                    SendCoinsFragment.this.activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            if (maybeUpdateShapeShift())
                                return;

                            if ((amount.isGreaterThan(min) || amount.equals(min))
                                    && (amount.isLessThan(max) || amount.equals(max))) {

                                try {

                                    ShapeShiftMonetary expectedAmount = new ShapeShiftMonetary(amount, rate, usingShapeShiftCoin.exponent);
                                    expectedAmount.subEqual(fee);

                                    if (expectedAmount.getValue() < 0)
                                        expectedAmount = new ShapeShiftMonetary(0, usingShapeShiftCoin.exponent);

                                    shapeShiftForeignAmountView.setAmount(expectedAmount, false);
                                    shapeShiftRateView.setAmount(rate.toCoinRate(), false);
                                    shapeShiftStatus = ShapeShiftStatus.NONE;
                                    futureUpdate(SHAPESHIFT_SHIFT_DELAY);

                                } catch (ArithmeticException x) {
                                    shapeShiftStatus = ShapeShiftStatus.PARSE_ERROR;
                                    futureUpdate(SHAPESHIFT_ERROR_DELAY);
                                }

                            }else{
                                shapeShiftStatus = ShapeShiftStatus.OUTSIDE_LIMITS;
                                limitMin = min;
                                limitMax = max;
                                futureUpdate(SHAPESHIFT_LIMIT_DELAY);
                            }

                        }

                    });

                }

            });	

            shapeShiftStatus = ShapeShiftStatus.UPDATING;
            updateView();
            shapeShiftComm.marketInfo(usingShapeShiftCoin);

        }

    }

    private void setShapeShiftNoUpdate(ShapeShiftCoin coin, Monetary foreignAmount) {

        usingShapeShiftCoin = coin;

        shapeShiftForeignAmountView.setCurrencySymbol(coin.coinCode);
        shapeShiftForeignAmountView.setInputFormat(coin.format);
        shapeShiftForeignAmountView.setHintAndFormat(coin.format, new ShapeShiftMonetary(0, coin.exponent));

        if (foreignAmount != null)
            shapeShiftForeignAmountView.setAmount(foreignAmount, false);

        shapeShiftForeignTitle.setText(coin.coinCode + " " + getString(R.string.send_coins_fragment_shapeshift_foreign_label));

    }

    private void setShapeShift(ShapeShiftCoin coin, boolean isExactForeignAmountLocal, Monetary foreignAmount) {

        setShapeShiftNoUpdate(coin, foreignAmount);
        updateShapeShift(isExactForeignAmountLocal);

    }

    private void validateReceivingAddress(boolean updateShapeshift) {

        try {

            final String addressStr = receivingAddressView.getText().toString().trim();
            if (!addressStr.isEmpty()){

                List<NetworkParameters> networks = Address.getParametersFromAddress(addressStr);

                if (networks == null)
                    return;

                destCoinSpinnerAdapter.addAll(networks);

                if (updateShapeshift) {
                    if (networks.get(0).isShapeShift())
                        setShapeShift((ShapeShiftCoin) networks.get(0), isExactForeignAmount, null);
                    else
                        usingShapeShiftCoin = null;	
                }

                final String label = AddressBookProvider.resolveLabel(activity, addressStr);
                validatedAddress = new AddressAndLabel(networks, addressStr, label);
                receivingAddressView.setText(null);

            }
        } catch (final AddressFormatException x) {
            // swallow
        }

    }

    private void handleCancel()
    {
        if (state == State.INPUT)
            activity.setResult(Activity.RESULT_CANCELED);

        activity.finish();
    }

    private boolean isOutputsValid()
    {
        if (paymentIntent.hasOutputs())
            return true;

        if (validatedAddress != null)
            return true;

        return false;
    }

    private boolean isAmountValid()
    {
        return dryrunTransaction != null && dryrunException == null;
    }

    private boolean isPasswordValid()
    {
        if (!wallet.isEncrypted())
            return true;

        return !privateKeyPasswordView.getText().toString().trim().isEmpty();
    }

    private boolean isShapeShiftValid() {
        return usingShapeShiftCoin == null || (
                (!isExactForeignAmount || depositAddress != null)
                && (shapeShiftStatus == ShapeShiftStatus.NONE || shapeShiftStatus == ShapeShiftStatus.FUTURE_UPDATE)
                );

    }

    private boolean everythingValid() {
        return state == State.INPUT && isOutputsValid() && isAmountValid() && isPasswordValid() && isShapeShiftValid();
    }

    private void requestFocusFirst() {

        if (!isOutputsValid())
            receivingAddressView.requestFocus();
        else if (!isAmountValid() && (usingShapeShiftCoin == null || !isExactForeignAmount))
            amountCalculatorLink.requestFocus();
        else if (everythingValid())
            viewGo.requestFocus();
        else if (usingShapeShiftCoin == null)
            log.warn("unclear focus");

    }

    private void handleGo() {

        privateKeyBadPasswordView.setVisibility(View.INVISIBLE);

        if (usingShapeShiftCoin != null && !isExactForeignAmount && depositAddress == null) {
            // We need to finally get the deposit address to shift to.

            setState(State.FINALISE_SHAPESHIFT);

            final ShapeShiftComm shapeShiftComm = new ShapeShiftComm();

            shapeShiftComm.setCallbacks(new ShapeShiftComm.Callbacks() {

                @Override
                public void shiftResponse(final Address deposit) {

                    SendCoinsFragment.this.activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            depositAddress = deposit;
                            handleGo();

                        }

                    });

                }

                @Override
                public void networkError(final int networkCode, final String text) {

                    SendCoinsFragment.this.activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            handleShapeShiftError(networkCode, text);
                            setState(State.INPUT);
                            futureUpdate(SHAPESHIFT_ERROR_DELAY);

                        }

                    });

                }

            });

            Address refund = wallet.currentAddress(KeyPurpose.REFUND);
            shapeShiftComm.shift(usingShapeShiftCoin, getAddress(), refund);

            return;

        }

        if (wallet.isEncrypted()) {

            new DeriveKeyTask(backgroundHandler) {

                @Override
                protected void onSuccess(@Nonnull KeyParameter encryptionKey) {
                    signAndSendPayment(encryptionKey);
                }

            }.deriveKey(wallet.getKeyCrypter(), privateKeyPasswordView.getText().toString().trim());

            setState(State.DECRYPTING);

        } else
            signAndSendPayment(null);

    }

    private void signAndSendPayment(final KeyParameter encryptionKey) {

        setState(State.SIGNING);
        // Ensure the address we want is used
        Address addressReplace = null;

        if (usingShapeShiftCoin != null)
            addressReplace = depositAddress;
        else if (validatedAddress != null)
            addressReplace = validatedAddress.address;

        // final payment intent
        final PaymentIntent finalPaymentIntent = paymentIntent.mergeWithEditedValues(amountCalculatorLink.getAmount(), addressReplace);
        final Coin finalAmount = finalPaymentIntent.getAmount();

        // prepare send request
        final SendRequest sendRequest = finalPaymentIntent.toSendRequest();
        sendRequest.emptyWallet = paymentIntent.mayEditAmount() && finalAmount.equals(wallet.getBalance(BalanceType.ESTMINUSFEE));
        sendRequest.feePerKb = SendRequest.DEFAULT_FEE_PER_KB;
        sendRequest.memo = validatedAddress == null ? paymentIntent.memo : validatedAddress.label;
        sendRequest.aesKey = encryptionKey;

        new SendCoinsOfflineTask(wallet, backgroundHandler)
        {
            @Override
            protected void onSuccess(final Transaction transaction)
            {
                sentTransaction = transaction;

                setState(State.SENDING);

                sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

                final Address refundAddress = paymentIntent.standard == Standard.BIP70 ? wallet.freshAddress(KeyPurpose.REFUND) : null;
                final Payment payment = PaymentProtocol.createPaymentMessage(Arrays.asList(new Transaction[] { sentTransaction }), finalAmount,
                        refundAddress, null, paymentIntent.payeeData);

                if (directPaymentEnableView.isChecked())
                    directPay(payment);

                application.broadcastTransaction(sentTransaction);

                final ComponentName callingActivity = activity.getCallingActivity();
                if (callingActivity != null)
                {
                    log.info("returning result to calling activity: {}", callingActivity.flattenToString());

                    final Intent result = new Intent();
                    PeercoinIntegration.transactionHashToResult(result, sentTransaction.getHashAsString());
                    if (paymentIntent.standard == Standard.BIP70)
                        PeercoinIntegration.paymentToResult(result, payment.toByteArray());
                    activity.setResult(Activity.RESULT_OK, result);
                }
            }

            private void directPay(final Payment payment)
            {
                final DirectPaymentTask.ResultCallback callback = new DirectPaymentTask.ResultCallback()
                {
                    @Override
                    public void onResult(final boolean ack)
                    {
                        directPaymentAck = ack;

                        if (state == State.SENDING)
                            setState(State.SENT);

                        // If we sent to a sendAmount deposit address, we don't need to cancel
                        if (usingShapeShiftCoin != null && isExactForeignAmount)
                            unusedSendAmountAddress = null;

                        updateView();
                    }

                    @Override
                    public void onFail(final int messageResId, final Object... messageArgs)
                    {
                        final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_direct_payment_failed_title);
                        dialog.setMessage(paymentIntent.paymentUrl + "\n" + getString(messageResId, messageArgs) + "\n\n"
                                + getString(R.string.send_coins_fragment_direct_payment_failed_msg));
                        dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(final DialogInterface dialog, final int which)
                                    {
                                        directPay(payment);
                                    }
                                });
                        dialog.setNegativeButton(R.string.button_dismiss, null);
                        dialog.show();
                    }
                };

                if (paymentIntent.isHttpPaymentUrl())
                {
                    new DirectPaymentTask.HttpPaymentTask(backgroundHandler, callback, paymentIntent.paymentUrl, application.httpUserAgent())
                        .send(payment);
                }
                else if (paymentIntent.isBluetoothPaymentUrl() && bluetoothAdapter != null && bluetoothAdapter.isEnabled())
                {
                    new DirectPaymentTask.BluetoothPaymentTask(backgroundHandler, callback, bluetoothAdapter,
                            Bluetooth.getBluetoothMac(paymentIntent.paymentUrl)).send(payment);
                }
            }

            @Override
            protected void onInsufficientMoney(@Nonnull final Coin missing) {

                returnToInputAndUpdate();

                final Coin estimated = wallet.getBalance(BalanceType.ESTIMATED);
                final Coin available = wallet.getBalance(BalanceType.AVAILABLE);
                final Coin pending = estimated.subtract(available);

                final MonetaryFormat ppcFormat = config.getFormat();

                final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_insufficient_money_title);
                final StringBuilder msg = new StringBuilder();
                msg.append(getString(R.string.send_coins_fragment_insufficient_money_msg1, ppcFormat.format(missing)));
                msg.append("\n\n");
                if (pending.signum() > 0)
                    msg.append(getString(R.string.send_coins_fragment_pending, ppcFormat.format(pending))).append("\n\n");
                msg.append(getString(R.string.send_coins_fragment_insufficient_money_msg2));
                dialog.setMessage(msg);
                dialog.setPositiveButton(R.string.send_coins_options_empty, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which)
                            {
                                handleEmpty();
                            }
                        });
                dialog.setNegativeButton(R.string.button_cancel, null);
                dialog.show();
            }

            @Override
            protected void onInvalidKey() {

                returnToInputAndUpdate();

                privateKeyBadPasswordView.setVisibility(View.VISIBLE);
                privateKeyPasswordView.requestFocus();

            }

            @Override
            protected void onEmptyWalletFailed() {

                returnToInputAndUpdate();

                final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_empty_wallet_failed_title);
                dialog.setMessage(R.string.send_coins_fragment_hint_empty_wallet_failed);
                dialog.setNeutralButton(R.string.button_dismiss, null);
                dialog.show();

            }

            @Override
            protected void onFailure(@Nonnull Exception exception)
            {
                setState(State.FAILED);

                final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_error_msg);
                dialog.setMessage(exception.toString());
                dialog.setNeutralButton(R.string.button_dismiss, null);
                dialog.show();
            }
        }.sendCoinsOffline(sendRequest); // send asynchronously
    }

    private void handleScan()
    {
        startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
    }

    private void handleEmpty() {

        final Coin available = wallet.getBalance(BalanceType.ESTMINUSFEE);
        amountCalculatorLink.setPPCAmount(available);

        updateShapeShift(false);

        updateView();
        handler.post(dryrunRunnable);

    }

    private Runnable dryrunRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (state == State.INPUT)
                executeDryrun();

            updateView();
        }

        private void executeDryrun()
        {
            dryrunTransaction = null;
            dryrunException = null;

            final Coin amount = amountCalculatorLink.getAmount();
            if (amount != null)
            {
                try
                {
                    final Address dummy = wallet.currentReceiveAddress(); // won't be used, tx is never committed
                    final SendRequest sendRequest = paymentIntent.mergeWithEditedValues(amount, dummy).toSendRequest();
                    sendRequest.signInputs = false;
                    sendRequest.emptyWallet = paymentIntent.mayEditAmount() && amount.equals(wallet.getBalance(BalanceType.ESTMINUSFEE));
                    sendRequest.feePerKb = SendRequest.DEFAULT_FEE_PER_KB;
                    wallet.completeTx(sendRequest);
                    dryrunTransaction = sendRequest.tx;
                }
                catch (final Exception x)
                {
                    dryrunException = x;
                }
            }
        }
    };

    private void returnToInputAndUpdate() {

        setState(State.INPUT);
        updateShapeShift(isExactForeignAmount);

    }

    private void setState(final State state) {

        this.state = state;

        if (state != State.INPUT && activeShapeShiftComm != null)
            activeShapeShiftComm.stop();

        activity.invalidateOptionsMenu();
        updateView();

    }

    private void updateView() {

        if (getView() == null)
            return;

        if (paymentIntent != null) {

            final MonetaryFormat nbtFormat = config.getFormat();

            getView().setVisibility(View.VISIBLE);

            if (paymentIntent.hasPayee()) {

                payeeNameView.setVisibility(View.VISIBLE);
                payeeNameView.setText(paymentIntent.payeeName);

                payeeVerifiedByView.setVisibility(View.VISIBLE);
                final String verifiedBy = paymentIntent.payeeVerifiedBy != null ? paymentIntent.payeeVerifiedBy
                    : getString(R.string.send_coins_fragment_payee_verified_by_unknown);
                payeeVerifiedByView.setText(Constants.CHAR_CHECKMARK
                        + String.format(getString(R.string.send_coins_fragment_payee_verified_by), verifiedBy));

            } else {
                payeeNameView.setVisibility(View.GONE);
                payeeVerifiedByView.setVisibility(View.GONE);
            }

            if (paymentIntent.hasOutputs()) {

                receivingAddressView.setVisibility(View.GONE);
                receivingStaticView.setVisibility(View.VISIBLE);

                receivingStaticLabelView.setText(paymentIntent.memo);

                if (paymentIntent.hasAddress())
                    receivingStaticAddressView.setText(WalletUtils.formatAddress(paymentIntent.getAddress(), Constants.ADDRESS_FORMAT_GROUP_SIZE,
                                Constants.ADDRESS_FORMAT_LINE_SIZE));
                else
                    receivingStaticAddressView.setText(R.string.send_coins_fragment_receiving_address_complex);

            } else if (validatedAddress != null) {

                receivingAddressView.setVisibility(View.GONE);
                receivingStaticView.setVisibility(View.VISIBLE);
                receivingStaticAddressView.setText(WalletUtils.formatAddress(validatedAddress.address, Constants.ADDRESS_FORMAT_GROUP_SIZE,
                            Constants.ADDRESS_FORMAT_LINE_SIZE));
                final String addressBookLabel = AddressBookProvider.resolveLabel(activity, validatedAddress.address.toString());
                final String staticLabel;
                if (addressBookLabel != null)
                    staticLabel = addressBookLabel;
                else if (validatedAddress.label != null)
                    staticLabel = validatedAddress.label;
                else
                    staticLabel = getString(R.string.address_unlabeled);
                receivingStaticLabelView.setText(staticLabel);
                receivingStaticLabelView.setTextColor(getResources().getColor(
                            validatedAddress.label != null ? R.color.fg_significant : R.color.fg_insignificant));

            } else {
                receivingStaticView.setVisibility(View.GONE);
                receivingAddressView.setVisibility(View.VISIBLE);
            }

            int shapeShiftVisibility = (usingShapeShiftCoin != null) ? View.VISIBLE : View.GONE;
            shapeShiftTitles.setVisibility(shapeShiftVisibility);
            shapeShiftAmounts.setVisibility(shapeShiftVisibility);

            receivingAddressView.setEnabled(state == State.INPUT);
            receivingStaticView.setEnabled(state == State.INPUT);

            amountCalculatorLink.setEnabled(state == State.INPUT && paymentIntent.mayEditAmount());
            shapeShiftForeignAmountView.setEnabled(state == State.INPUT);
            destCoinSpinner.setEnabled(state == State.INPUT);

            final boolean directPaymentVisible;
            if (paymentIntent.hasPaymentUrl() && usingShapeShiftCoin == null) {
                if (paymentIntent.isBluetoothPaymentUrl())
                    directPaymentVisible = bluetoothAdapter != null;
                else
                    directPaymentVisible = !Constants.BUG_OPENSSL_HEARTBLEED;
            } else
                directPaymentVisible = false;

            directPaymentEnableView.setVisibility(directPaymentVisible ? View.VISIBLE : View.GONE);
            directPaymentEnableView.setEnabled(state == State.INPUT);

            // Set errors

            hintView.setVisibility(View.GONE);
            shapeShiftHintView.setVisibility(View.GONE);
            shapeShiftEstView.setVisibility(View.GONE);

            if (state == State.INPUT) {

                if (paymentIntent.mayEditAddress() && validatedAddress == null && !receivingAddressView.getText().toString().trim().isEmpty()) {

                    hintView.setTextColor(getResources().getColor(R.color.fg_error));
                    hintView.setVisibility(View.VISIBLE);
                    hintView.setText(R.string.send_coins_fragment_receiving_address_error);

                } else if (dryrunException != null) {

                    hintView.setTextColor(getResources().getColor(R.color.fg_error));
                    hintView.setVisibility(View.VISIBLE);
                    if (dryrunException instanceof TooSmallOutput)
                        hintView.setText(getString(R.string.send_coins_fragment_hint_too_small_output));
                    else if (dryrunException instanceof InsufficientMoneyException)
                        hintView.setText(getString(R.string.send_coins_fragment_hint_insufficient_money,
                                    nbtFormat.format(((InsufficientMoneyException) dryrunException).missing)));
                    else if (dryrunException instanceof CouldNotAdjustDownwards)
                        hintView.setText(getString(R.string.send_coins_fragment_hint_empty_wallet_failed));
                    else
                        hintView.setText(dryrunException.toString());

                } else if (dryrunTransaction != null) {

                    final Coin previewFee = dryrunTransaction.getFee();
                    if (previewFee != null) {
                        hintView.setText(getString(R.string.send_coins_fragment_hint_fee, nbtFormat.format(previewFee)));
                        hintView.setTextColor(getResources().getColor(R.color.fg_insignificant));
                        hintView.setVisibility(View.VISIBLE);
                    }

                }

                if (usingShapeShiftCoin != null) {

                    if (shapeShiftStatus != ShapeShiftStatus.NONE) {

                        shapeShiftHintView.setTextColor(getResources().getColor(R.color.fg_error));

                        if (shapeShiftStatus == ShapeShiftStatus.OTHER_ERROR)
                            shapeShiftHintView.setText(shapeShiftStatusText);
                        else if (shapeShiftStatus == ShapeShiftStatus.CONNECTION_ERROR)
                            shapeShiftHintView.setText(R.string.send_coins_fragment_hint_shapeshift_connection_error);
                        else if (shapeShiftStatus == ShapeShiftStatus.OUTSIDE_LIMITS)
                            shapeShiftHintView.setText(getString(R.string.send_coins_fragment_hint_shapeshift_outside_limits, limitMin.toFriendlyString(), limitMax.toFriendlyString()));
                        else if (shapeShiftStatus == ShapeShiftStatus.TOO_SMALL)
                            shapeShiftHintView.setText(R.string.send_coins_fragment_hint_shapeshift_too_small);
                        else if (shapeShiftStatus == ShapeShiftStatus.PARSE_ERROR)
                            shapeShiftHintView.setText(R.string.send_coins_fragment_hint_shapeshift_parse_error);
                        else {

                            if (!isExactForeignAmount)
                                shapeShiftEstView.setVisibility(View.VISIBLE);

                            shapeShiftHintView.setTextColor(getResources().getColor(R.color.fg_significant));

                            if (shapeShiftStatus == ShapeShiftStatus.UPDATING)
                                shapeShiftHintView.setText(R.string.send_coins_fragment_hint_shapeshift_updating);
                            else if (shapeShiftStatus == ShapeShiftStatus.FUTURE_UPDATE) {

                                String timeToWait;

                                if (secondsToUpdate >= 60) {

                                    long minutes = secondsToUpdate / 60 + secondsToUpdate % 60 / 30;
                                    timeToWait = String.format("%d minute%s", minutes, minutes == 1 ? "" : "s");

                                }else
                                    timeToWait = String.format("%d second%s", secondsToUpdate, secondsToUpdate == 1 ? "" : "s");

                                shapeShiftHintView.setText(getString(
                                            R.string.send_coins_fragment_hint_shapeshift_future_update, timeToWait
                                            ));

                            }

                        }

                        shapeShiftHintView.setVisibility(View.VISIBLE);

                    }
                }
            }

            if (sentTransaction != null) {
                sentTransactionView.setVisibility(View.VISIBLE);
                sentTransactionListAdapter.setFormat(nbtFormat);
                sentTransactionListAdapter.replace(sentTransaction);
            } else {
                sentTransactionView.setVisibility(View.GONE);
                sentTransactionListAdapter.clear();
            }

            if (directPaymentAck != null) {
                directPaymentMessageView.setVisibility(View.VISIBLE);
                directPaymentMessageView.setText(directPaymentAck ? R.string.send_coins_fragment_direct_payment_ack
                        : R.string.send_coins_fragment_direct_payment_nack);
            } else
                directPaymentMessageView.setVisibility(View.GONE);

            viewCancel.setEnabled(state != State.DECRYPTING && state != State.SIGNING && state != State.FINALISE_SHAPESHIFT);
            viewGo.setEnabled(everythingValid());

            if (state == State.INPUT) {
                viewCancel.setText(R.string.button_cancel);
                viewGo.setText(R.string.send_coins_fragment_button_send);
            } else if (state == State.DECRYPTING) {
                viewCancel.setText(R.string.button_cancel);
                viewGo.setText(R.string.send_coins_fragment_state_decrypting);
            }else if (state == State.FINALISE_SHAPESHIFT) {
                viewCancel.setText(R.string.button_cancel);
                viewGo.setText(R.string.send_coins_fragment_state_finalise_shapeshift);
            }else if (state == State.SIGNING) {
                viewCancel.setText(R.string.button_cancel);
                viewGo.setText(R.string.send_coins_preparation_msg);
            } else if (state == State.SENDING) {
                viewCancel.setText(R.string.send_coins_fragment_button_back);
                viewGo.setText(R.string.send_coins_sending_msg);
            } else if (state == State.SENT) {
                viewCancel.setText(R.string.send_coins_fragment_button_back);
                viewGo.setText(R.string.send_coins_sent_msg);
            } else if (state == State.FAILED) {
                viewCancel.setText(R.string.send_coins_fragment_button_back);
                viewGo.setText(R.string.send_coins_failed_msg);
            }

            final boolean privateKeyPasswordViewVisible = (state == State.INPUT || state == State.FINALISE_SHAPESHIFT || state == State.DECRYPTING) && wallet.isEncrypted();
            privateKeyPasswordViewGroup.setVisibility(privateKeyPasswordViewVisible ? View.VISIBLE : View.GONE);
            privateKeyPasswordView.setEnabled(state == State.INPUT);

            // focus linking
            final int activeAmountViewId = amountCalculatorLink.activeTextView().getId();
            receivingAddressView.setNextFocusDownId(activeAmountViewId);
            receivingAddressView.setNextFocusForwardId(activeAmountViewId);
            receivingStaticView.setNextFocusDownId(activeAmountViewId);
            amountCalculatorLink.setNextFocusId(privateKeyPasswordViewVisible ? R.id.send_coins_private_key_password : R.id.send_coins_go);
            privateKeyPasswordView.setNextFocusUpId(activeAmountViewId);
            privateKeyPasswordView.setNextFocusDownId(R.id.send_coins_go);
            viewGo.setNextFocusUpId(privateKeyPasswordViewVisible ? R.id.send_coins_private_key_password : activeAmountViewId);

        } else
            getView().setVisibility(View.GONE);

    }

    private void initStateFromIntentExtras(@Nonnull final Bundle extras)
    {
        final PaymentIntent paymentIntent = extras.getParcelable(SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT);

        updateStateFrom(paymentIntent);
    }

    private void initStateFromPeercoinUri(@Nonnull final Uri peercoinUri)
    {
        final String input = peercoinUri.toString();

        new StringInputParser(input)
        {
            @Override
            protected void handlePaymentIntent(@Nonnull final PaymentIntent paymentIntent)
            {
                updateStateFrom(paymentIntent);
            }

            @Override
            protected void handlePrivateKey(@Nonnull final VersionedChecksummedBytes key)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void handleDirectTransaction(@Nonnull final Transaction transaction) throws VerificationException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void error(final int messageResId, final Object... messageArgs)
            {
                dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
            }
        }.parse();
    }

    private void initStateFromPaymentRequest(@Nonnull final String mimeType, @Nonnull final byte[] input)
    {
        new BinaryInputParser(mimeType, input)
        {
            @Override
            protected void handlePaymentIntent(final PaymentIntent paymentIntent)
            {
                updateStateFrom(paymentIntent);
            }

            @Override
            protected void error(final int messageResId, final Object... messageArgs)
            {
                dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
            }
        }.parse();
    }

    private void initStateFromIntentUri(@Nonnull final String mimeType, @Nonnull final Uri peercoinUri)
    {
        try
        {
            final InputStream is = contentResolver.openInputStream(peercoinUri);

            new StreamInputParser(mimeType, is)
            {
                @Override
                protected void handlePaymentIntent(final PaymentIntent paymentIntent)
                {
                    updateStateFrom(paymentIntent);
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs)
                {
                    dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
                }
            }.parse();
        }
        catch (final FileNotFoundException x)
        {
            throw new RuntimeException(x);
        }
    }

    private void updateStateFrom(final @Nonnull PaymentIntent paymentIntent) {

        log.info("got {}", paymentIntent);

        this.paymentIntent = paymentIntent;

        validatedAddress = null;
        directPaymentAck = null;

        // delay these actions until fragment is resumed
        handler.post(new Runnable() {

            @Override
            public void run() {
                if (state == State.INPUT) {
                    receivingAddressView.setText(null);

                    if (paymentIntent.networks != null) {
                        destCoinSpinnerAdapter.clear();
                        destCoinSpinnerAdapter.addAll(paymentIntent.networks);
                    }

                    if (paymentIntent.networks != null && paymentIntent.networks.get(0).isShapeShift()) {

                        Monetary amount = paymentIntent.getShapeShiftAmount();
                        setShapeShift((ShapeShiftCoin) paymentIntent.networks.get(0), amount != null, amount);
                        amountCalculatorLink.setPPCAmount(Coin.ZERO);

                    }else{

                        usingShapeShiftCoin = null;
                        amountCalculatorLink.setPPCAmount(paymentIntent.getAmount());

                        if (paymentIntent.isBluetoothPaymentUrl())
                            directPaymentEnableView.setChecked(bluetoothAdapter != null && bluetoothAdapter.isEnabled());
                        else if (paymentIntent.isHttpPaymentUrl())
                            directPaymentEnableView.setChecked(!Constants.BUG_OPENSSL_HEARTBLEED);

                    }

                    requestFocusFirst();
                    updateView();
                    handler.post(dryrunRunnable);
                }

                if (paymentIntent.hasPaymentRequestUrl())
                {
                    if (paymentIntent.isBluetoothPaymentRequestUrl() && !Constants.BUG_OPENSSL_HEARTBLEED)
                    {
                        if (bluetoothAdapter.isEnabled())
                            requestPaymentRequest();
                        else
                            // ask for permission to enable bluetooth
                            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                                    REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST);
                    }
                    else if (paymentIntent.isHttpPaymentRequestUrl())
                    {
                        requestPaymentRequest();
                    }
                }
            }
        });
    }

    private void requestPaymentRequest()
    {
        final String host;
        if (!Bluetooth.isBluetoothUrl(paymentIntent.paymentRequestUrl))
            host = Uri.parse(paymentIntent.paymentRequestUrl).getHost();
        else
            host = Bluetooth.decompressMac(Bluetooth.getBluetoothMac(paymentIntent.paymentRequestUrl));

        ProgressDialogFragment.showProgress(fragmentManager, getString(R.string.send_coins_fragment_request_payment_request_progress, host));

        final RequestPaymentRequestTask.ResultCallback callback = new RequestPaymentRequestTask.ResultCallback()
        {
            @Override
            public void onPaymentIntent(final PaymentIntent paymentIntent)
            {
                ProgressDialogFragment.dismissProgress(fragmentManager);

                if (SendCoinsFragment.this.paymentIntent.isExtendedBy(paymentIntent))
                {
                    // success
                    updateStateFrom(paymentIntent);
                    updateView();
                    handler.post(dryrunRunnable);
                }
                else
                {
                    final StringBuilder reasons = new StringBuilder();
                    if (!SendCoinsFragment.this.paymentIntent.equalsAddress(paymentIntent))
                        reasons.append("address");
                    if (!SendCoinsFragment.this.paymentIntent.equalsAmount(paymentIntent))
                        reasons.append(reasons.length() == 0 ? "" : ", ").append("amount");
                    if (reasons.length() == 0)
                        reasons.append("unknown");

                    final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_request_payment_request_failed_title);
                    dialog.setMessage(getString(R.string.send_coins_fragment_request_payment_request_wrong_signature) + "\n\n" + reasons);
                    dialog.singleDismissButton(null);
                    dialog.show();

                    log.info("BIP72 trust check failed: {}", reasons);
                }
            }

            @Override
            public void onFail(final int messageResId, final Object... messageArgs)
            {
                ProgressDialogFragment.dismissProgress(fragmentManager);

                final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_fragment_request_payment_request_failed_title);
                dialog.setMessage(getString(messageResId, messageArgs));
                dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which)
                            {
                                requestPaymentRequest();
                            }
                        });
                dialog.setNegativeButton(R.string.button_dismiss, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which)
                            {
                                if (!paymentIntent.hasOutputs())
                                    handleCancel();
                            }
                        });
                dialog.show();
            }
        };

        if (!Bluetooth.isBluetoothUrl(paymentIntent.paymentRequestUrl))
            new RequestPaymentRequestTask.HttpRequestTask(backgroundHandler, callback, application.httpUserAgent())
                .requestPaymentRequest(paymentIntent.paymentRequestUrl);
        else
            new RequestPaymentRequestTask.BluetoothRequestTask(backgroundHandler, callback, bluetoothAdapter)
                .requestPaymentRequest(paymentIntent.paymentRequestUrl);
    }
}
