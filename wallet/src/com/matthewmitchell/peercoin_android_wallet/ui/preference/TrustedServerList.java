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

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.matthewmitchell.peercoin_android_wallet.R;
import com.matthewmitchell.peercoin_android_wallet.ui.preference.TrustedServer.ServerStatus;
import com.matthewmitchell.peercoinj.store.ValidHashStore.TrustedServersInterface;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import org.slf4j.LoggerFactory;

/**
 * Provides a list of servers for validing block hashes from an SQLite database.
 * @author Matthew Mitchell
 */
public class TrustedServerList extends ArrayList<TrustedServer> implements TrustedServersInterface {

    /**
     * An executor to write changes to the database asynchronously.
     */
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private static final Random prng = new Random();

    private final Context context;
    private Runnable onChanged = null;

    /**
     * Information for the servers of the same priority.
     */
    private class PriorityBucket {

        public int offset;
        public int size;
        public int start = prng.nextInt(Integer.MAX_VALUE);
        public int cursor = 0;

        /**
         * Create a priority bucket referencing servers in the TrustedServerList
         * @param offset Offset in the list of the first server in the bucket.
         * @param size The number of servers in the bucket
         */
        public PriorityBucket(int offset, int size) {
            this.offset = offset;
            this.size = size;
        }

        /**
         * Get the next server in this bucket. If null is returned there are no more servers to obtain, and the next bucket should be used.
         * In this case the bucket is reset for the next time it is used.
         * @return The next server or null if there are no more servers in this bucket.
         */
        public TrustedServer getNext() {

            if (cursor == size) {
                cursor = 0;
                return null;
            }

            return TrustedServerList.this.get(offset + (start + cursor++) % size);

        }

        /**
         * Reset cursor at the next server, so getNext will return the server, but also continue though the other servers afterwards.
         */
        public void resetAt() {
            start = (start + cursor) % size;
            cursor = 0;
        }

    }

    private Object lastSuccessfulLock = new Object();

    private ArrayList<PriorityBucket> priorityInfo = new ArrayList<PriorityBucket>();
    private int currentPriorityBucket = 0;
    private TrustedServer lastServer = null;
    private TrustedServer lastSuccessfulServer = null;
    private boolean invalidated = false;

    private static TrustedServerList instance = null;

    private TrustedServerList(final Context context) {
        this.context = context;
    }

    /**
     * Gets a singleton instance of the trusted server list, loading it from the database if necessary.
     */
    synchronized public static TrustedServerList getInstance(final Context context) {

        if (instance != null)
            return instance;

        instance = new TrustedServerList(context);
        Cursor cursor = TrustedServersDatabaseHelper.getInstance(context).getServersCursor();

        while (cursor.moveToNext())
            instance.add(new TrustedServer(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3) != 0));

        cursor.close();
        instance.calculatePriority();

        return instance;

    }

    /**
     * Callback for when the list changes, when restoring defaults, adding a new server, or when a server's status changes.
     */
    public void setOnChanged(final Runnable onChanged) {
        this.onChanged = onChanged;
    }

    private void addPriorityInfo(final int end, final int count) {
        priorityInfo.add(new PriorityBucket(end - count, count));
    }

    private void calculatePriority() {

        int priority = 1, position = 0, priorityCount = 0;
        boolean first = true;
        priorityInfo.clear();

        for (TrustedServer server: this) {

            if (!server.equal && !first) {
                addPriorityInfo(position, priorityCount);
                priority++;
                priorityCount = 0;
            }

            server.priority = priority;
            server.order = position++;
            priorityCount++;
            first = false;

        }

        addPriorityInfo(position, priorityCount);
        currentPriorityBucket = 0;

    }

    private void addServer(TrustedServer server) {

        synchronized (this) {
            add(server);
            calculatePriority();
        }

    }

    /**
     * Handles moving servers from one position to the other.
     */
    public void move(final int from, final int to) {

        final int prevPriority = lastServer == null ? 0 : lastServer.priority;
        final TrustedServer server;

        synchronized (this) {

            server = remove(from);
            add(to, server);
            calculatePriority();

            // Invalidate if a server has moved to a priority number less than the server.
            if (lastServer != null && server.id != lastServer.id && server.priority < lastServer.priority)
                invalidated = true;

        }

        dbExecutor.submit(new Runnable() {

            @Override
            public void run() {
                TrustedServersDatabaseHelper.getInstance(context).updateOrder(server.id, from, to);
            }

        });

    }

    /**
     * Restores the list to the default server list.
     */
    public void restoreDefaults() {

        synchronized (this) {
            clear();
        }

        dbExecutor.execute(new Runnable() {

            @Override
            public void run() {

                TrustedServer[] servers = TrustedServersDatabaseHelper.getInstance(context).restoreDefaults();

                synchronized (this) {
                    for (TrustedServer server: servers)
                        addServer(server);
                    invalidated = true;
                }

                if (onChanged != null)
                    onChanged.run();

            }

        });

    }

    /**
     * Inserts a new server to the end of the list
     */
    public void newServer(final String name, final String url, final boolean equal) {

        dbExecutor.execute(new Runnable() {

            @Override
            public void run() {

                TrustedServer server = TrustedServersDatabaseHelper.getInstance(context).insertServer(name, url, equal);

                synchronized (this) {
                    addServer(server);
                }

                if (onChanged != null)
                    onChanged.run();

            }

        });

    }

    /**
     * Edits the information for a particular server.
     */
    public void editServer(TrustedServer server, final String name, final String url, final boolean equal) {

        final TrustedServer inlist;

        synchronized (this) {

            // Get the specific server object in the list that needs changing
            inlist = get(server.order);

            // Server is invalidated if URL of the server or any earlier priority server is changed.
            if (lastServer != null && (server.priority < lastServer.priority || server.id == lastServer.id) && !inlist.url.equals(url))
                invalidated = true;

            inlist.name = name;

            if (!inlist.url.equals(url)) {
                inlist.url = url;
                inlist.status = ServerStatus.SERVER_NOT_TRIED;
            }

            if (inlist.equal != equal) {
                inlist.equal = equal;
                calculatePriority();
            }

        }

        dbExecutor.submit(new Runnable() {

            @Override
            public void run() {
                TrustedServersDatabaseHelper.getInstance(context).updateServerDetails(inlist.id, name, url, equal);
            }

        });

    }

    /**
     * Deletes a server from the list
     */
    public void deleteServer(final TrustedServer server) {

        synchronized (this) {

            if (lastServer != null && lastServer.id == server.id)
                invalidated = true;

            remove(server.order);
            calculatePriority();

        }

        dbExecutor.submit(new Runnable() {

            @Override
            public void run() {
                TrustedServersDatabaseHelper.getInstance(context).deleteServer(server.order);
            }

        });

    }

    /**
     * Returns true if the server was the last server to be queried successfully.
     */
    public boolean isLastServer(TrustedServer server) {

        synchronized (lastSuccessfulLock) {
            if (lastSuccessfulServer == null)
                return false;

            return server.id == lastSuccessfulServer.id;
        }

    }

    @Override
    public URL getNext(boolean didFail) {

        synchronized (this) {

            invalidated = false;

            if (isEmpty())
                return null;

            if (!didFail) {

                // Go to first bucket and reset all the buckets, as we want to try the highest priority servers first.
                currentPriorityBucket = 0;
                for (PriorityBucket bucket : priorityInfo)
                    bucket.resetAt();

            }

            for (;;) {

                TrustedServer server = priorityInfo.get(currentPriorityBucket).getNext();

                if (server != null) {
                    try {
                        URL url = new URL(server.url);
                        lastServer = server;
                        return url;
                    } catch (MalformedURLException ex) {}
                } else if (++currentPriorityBucket == priorityInfo.size()) {

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(
                            new Runnable(){
                                @Override
                                public void run() {
                                    Toast.makeText(context, R.string.failed_all_servers, Toast.LENGTH_LONG).show();
                                }
                            }
                            );

                    currentPriorityBucket = 0;
                    lastServer = null;

                    return null;

                }

            }
        }

    }

    @Override
    public boolean invalidated() {
        return invalidated;
    }

    @Override
    public void markSuccess(boolean success) {

        if (lastServer != null) {
            if (success) {
                synchronized (lastSuccessfulLock) {
                    lastSuccessfulServer = lastServer;
                }
                lastServer.status = ServerStatus.SERVER_SUCCEEDED;
            } else {
                lastServer.status = ServerStatus.SERVER_FAILED;
                if (lastServer == lastSuccessfulServer)
                    synchronized (lastSuccessfulLock) {
                        lastSuccessfulServer = null;
                    }
            }
        }

        if (onChanged != null)
            onChanged.run();

    }

}

