/*
 * Copyright (C) 2015 NuBits Developers
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
package com.matthewmitchell.peercoin_android_wallet.ui.preference;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import com.matthewmitchell.peercoin_android_wallet.ui.DialogBuilder;
import com.matthewmitchell.peercoin_android_wallet.ui.EncryptKeysDialogFragment;
import com.matthewmitchell.peercoin_android_wallet.ui.ShowPasswordCheckListener;

import com.matthewmitchell.peercoin_android_wallet.R;
import java.net.URL;

/**
 *
 * @author Matthew Mitchell
 */
public class TrustedServerDialogFragment extends DialogFragment {

    private TrustedServer server;

    private EditText serverName, serverURL;
    private TextView urlError;
    private CheckBox priorityEqual;
    private Button positiveButton = null;

    final static private String OLD_SERVER = "old_server";

    public interface TrustedServerDialogCallback {
        public void onServerSet(TrustedServer server, String name, String url, boolean equal);
    } 

    public static TrustedServerDialogFragment newInstance(TrustedServer server) {

        TrustedServerDialogFragment instance = new TrustedServerDialogFragment();
        instance.server = server;
        return instance;

    }

    @Override
    public void onSaveInstanceState (Bundle outState) {

        super.onSaveInstanceState(outState);
        outState.putParcelable(OLD_SERVER, server);

    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {

        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.trusted_server_dialog, null);

        serverName = (EditText) view.findViewById(R.id.trusted_server_dialog_name);
        serverURL = (EditText) view.findViewById(R.id.trusted_server_dialog_url);
        urlError = (TextView) view.findViewById(R.id.trusted_server_dialog_url_error);
        priorityEqual = (CheckBox) view.findViewById(R.id.trusted_server_dialog_equal);

        if (server != null) {
            serverName.setText(server.name);
            serverURL.setText(server.url);
            priorityEqual.setChecked(server.equal);
        } else if (savedInstanceState != null)
            server = savedInstanceState.getParcelable(OLD_SERVER);

        final TextWatcher txtChange = new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validate();
            }

        };

        serverName.addTextChangedListener(txtChange);
        serverURL.addTextChangedListener(txtChange);

        serverURL.setOnFocusChangeListener(new OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {

                if (!hasFocus)
                    urlError.setVisibility((isURLValid() || serverURL.getText().length() == 0) ? View.GONE : View.VISIBLE);

            }

        });

        final DialogBuilder builder = new DialogBuilder(getActivity());
        builder.setTitle(server == null ? R.string.trusted_server_dialog_title_new : R.string.trusted_server_dialog_title_edit);
        builder.setView(view);
        builder.setPositiveButton(R.string.button_set, null);
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.setCancelable(false);

        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        // For new servers, show keyboard immediately
        if (server == null)
            dialog.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(final DialogInterface d) {

                positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                positiveButton.setTypeface(Typeface.DEFAULT_BOLD);

                positiveButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(final View v) {
                        ((TrustedServerDialogCallback) getTargetFragment()).onServerSet(
                        server, serverName.getText().toString(), serverURL.getText().toString(), priorityEqual.isChecked()
                        );
                        d.dismiss();
                    }

                });

                validate();

            }
        });

        return dialog;

    }

    private boolean isURLValid()  {

        String url = serverURL.getText().toString();
        return URLUtil.isNetworkUrl(url) && Patterns.WEB_URL.matcher(url).matches();

    }

    private void validate() {

        if (positiveButton != null)
            positiveButton.setEnabled(isURLValid() && serverName.getText().length() > 0);

    }

}
