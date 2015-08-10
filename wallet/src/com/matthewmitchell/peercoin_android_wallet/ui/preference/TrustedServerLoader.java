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

import android.content.AsyncTaskLoader;
import android.content.Context;

/**
 *
 * @author Matthew Mitchell
 */
public class TrustedServerLoader extends AsyncTaskLoader<TrustedServerList> {

    private final Context context;
    private TrustedServerList cache = null;

    public TrustedServerLoader(final Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public TrustedServerList loadInBackground() {
        return TrustedServerList.getInstance(context);
    }

    @Override
    public void deliverResult(TrustedServerList data) {

        if (isReset())
            return;

        cache = data;

        if (isStarted())
            super.deliverResult(data);

    }

    @Override 
    protected void onStartLoading() {

        super.onStartLoading();

        if (cache != null)
            deliverResult(cache);
        else
            // Must call this, as android will not load it itself for some reason
            forceLoad();

    }

}
