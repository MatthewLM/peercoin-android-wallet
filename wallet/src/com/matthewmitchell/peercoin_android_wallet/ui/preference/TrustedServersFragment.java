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

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Loader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.mobeta.android.dslv.DragSortListView;

import com.matthewmitchell.peercoin_android_wallet.R;
import com.matthewmitchell.peercoin_android_wallet.ui.ConfirmationDialogFragment;
import com.matthewmitchell.peercoin_android_wallet.ui.HelpDialogFragment;
import com.matthewmitchell.peercoin_android_wallet.ui.preference.TrustedServer.ServerStatus;
import org.slf4j.LoggerFactory;


/**
 *
 * @author Matthew Mitchell
 */
public class TrustedServersFragment extends ListFragment
    implements TrustedServerDialogFragment.TrustedServerDialogCallback, ConfirmationDialogFragment.ConfirmationDialogCallbacks {

    private ArrayAdapter<TrustedServer> adapter;
    private TrustedServerList servers = null;

    private static final int SERVER_LIST_LOADER = 0;

    private DragSortListView.DropListener onDrop = new DragSortListView.DropListener() {
        @Override
        public void drop(int from, int to) {

            if (from != to) {
                servers.move(from, to);
                adapter.notifyDataSetChanged();
            }

        }
    };

    private Runnable datasetChangeRunnable = new Runnable() {
        @Override
        public void run() {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adapter.notifyDataSetChanged();
                }
            });

        }
    };

    public TrustedServersFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.trusted_servers_fragment, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        DragSortListView list = getListView();
        list.setDropListener(onDrop);

    }

    @Override
    public DragSortListView getListView() {
        return (DragSortListView) super.getListView();
    }

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TrustedServersFragment.class);

    private LoaderManager.LoaderCallbacks<TrustedServerList> serverListLoaderCallbacks = new LoaderManager.LoaderCallbacks<TrustedServerList> () {

        @Override
        public Loader onCreateLoader(int id, Bundle args) {
            return new TrustedServerLoader(TrustedServersFragment.this.getActivity());
        }

        @Override
        public void onLoadFinished(Loader loader, TrustedServerList data) {

            servers = data;
            servers.setOnChanged(datasetChangeRunnable);

            adapter = new ArrayAdapter<TrustedServer>(getActivity(), -1, data) {

                @Override
                public View getView(int position, View v, ViewGroup parent){

                    if (v == null)
                        v = LayoutInflater.from(getActivity()).inflate(R.layout.trusted_servers_list_item, parent, false);

                    final TrustedServer server = getItem(position);

                    final TextView name = (TextView) v.findViewById(R.id.trusted_server_name);
                    name.setText(server.name);

                    final TextView priority = (TextView) v.findViewById(R.id.trusted_server_priority);
                    priority.setText(String.valueOf(server.priority) + ".");

                    final View status = v.findViewById(R.id.trusted_server_status);

                    int colour;

                    if (server.status == ServerStatus.SERVER_SUCCEEDED)
                        colour = R.color.trusted_server_bg_success;
                    else if (server.status == ServerStatus.SERVER_FAILED)
                        colour = R.color.trusted_server_bg_fail;
                    else
                        colour= R.color.trusted_server_bg_no_try;

                    status.setBackgroundColor(getResources().getColor(colour));

                    int tf = servers.isLastServer(server) ? Typeface.BOLD : Typeface.NORMAL;
                    name.setTypeface(null, tf);
                    priority.setTypeface(null, tf);

                    return v;

                }

            };

            setListAdapter(adapter);
            getActivity().invalidateOptionsMenu();

        }

        @Override
        public void onLoaderReset(Loader loader) {
            setListAdapter(null);
        }

    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(SERVER_LIST_LOADER, null, serverListLoaderCallbacks);

    }

    @Override
    public void onDestroy () {

        if (servers != null)
            servers.setOnChanged(null);

        getLoaderManager().destroyLoader(SERVER_LIST_LOADER);
        super.onDestroy();

    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {

        inflater.inflate(R.menu.trusted_servers_fragment_options, menu);

        final MenuItem restoreDefaults = menu.findItem(R.id.trusted_servers_restore_defaults);
        final MenuItem newServer = menu.findItem(R.id.trusted_servers_new);

        restoreDefaults.setEnabled(servers != null);
        newServer.setEnabled(servers != null);

        super.onCreateOptionsMenu(menu, inflater);

    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        switch (item.getItemId()) {

            case R.id.trusted_servers_help:
                HelpDialogFragment.page(getFragmentManager(), R.string.help_trusted_servers);
                return true;

            case R.id.trusted_servers_restore_defaults:
                handleRestoreDefaults();
                return true;

            case R.id.trusted_servers_new:
                handleNew();
                return true;

        }

        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onListItemClick(final ListView l, final View v, final int position, final long id) {

        final TrustedServer server = servers.get(position);

        getActivity().startActionMode(new ActionMode.Callback() {

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {

                final MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.trusted_servers_context, menu);

                mode.setTitle(server.name);

                return true;

            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

                switch (item.getItemId()) {

                    case R.id.trusted_servers_context_edit:
                        handleEdit(server);
                        mode.finish();
                        return true;

                    case R.id.trusted_servers_context_remove:
                        handleDelete(server);
                        mode.finish();
                        return true;

                }

                return false;

            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {

            }

        });

    }

    private void handleRestoreDefaults() {

        ConfirmationDialogFragment.create(
                getString(R.string.trusted_servers_restore_defaults_title),
                getString(R.string.trusted_servers_restore_defaults_message),
                this,
                null
                );

    }

    private void showServerDialog(TrustedServer server) {

        TrustedServerDialogFragment dialog = TrustedServerDialogFragment.newInstance(server);
        dialog.setTargetFragment(this, 0);
        dialog.show(getFragmentManager(), "dialog");

    }

    private void handleNew() {
        showServerDialog(null);
    }

    private void handleEdit(TrustedServer server) {
        showServerDialog(server);
    }

    private void handleDelete(TrustedServer server) {

        ConfirmationDialogFragment.create(
                getString(R.string.trusted_servers_delete_server_title),
                getString(R.string.trusted_servers_delete_server_message, server.name),
                this,
                server
                );

    }

    @Override
    public void onServerSet(TrustedServer server, String name, String url, boolean equal) {

        if (server != null) {
            servers.editServer(server, name, url, equal);
            adapter.notifyDataSetChanged();
        } else
            servers.newServer(name, url, equal);

    }

    @Override
    public void onNegative(Parcelable foo) {}

    @Override
    public void onPositive(Parcelable server) {

        if (server != null) {
            servers.deleteServer((TrustedServer) server);
            adapter.notifyDataSetChanged();
        } else
            servers.restoreDefaults();

    }

}
