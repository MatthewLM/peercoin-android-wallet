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

import android.os.Parcel;
import android.os.Parcelable;

/**
 *
 * @author Matthew Mitchell
 */
public class TrustedServer implements Parcelable {

    public long id;
    public String name;
    public String url;
    public boolean equal;

    /**
     * The displayed priority number.
     */
    public int priority = 0;

    /**
     * The order of the servers starting from zero.
     */
    public int order;

    public enum ServerStatus {
        SERVER_NOT_TRIED,
        SERVER_SUCCEEDED,
        SERVER_FAILED,
    }

    /**
     * The status of the last attempt to access the server, or else SERVER_NOT_TRIED
     */
    public ServerStatus status = ServerStatus.SERVER_NOT_TRIED;

    /**
     * Store information for a particular server for obtaining valid block hashes.
     *
     * @param id The unique id for the server.
     * @param name The name shown to the user.
     * @param url The exact URL, including protocol, for obtaining the valid block hashes.
     * @param equal If true the server will have the same priority as the one above it.
     */
    public TrustedServer(long id, String name, String url, boolean equal) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.equal = equal;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeLong(id);
        dest.writeString(name);
        dest.writeString(url);
        dest.writeValue(new boolean[] {equal});
        dest.writeInt(priority);
        dest.writeInt(order);

    }

    public static final Parcelable.Creator<TrustedServer> CREATOR = new Parcelable.Creator<TrustedServer>() {

        public TrustedServer createFromParcel(Parcel in) {
            return new TrustedServer(in);
        }

        public TrustedServer[] newArray(int size) {
            return new TrustedServer[size];
        }

    };

    private TrustedServer(Parcel in) {

        id = in.readLong();
        name = in.readString();
        url = in.readString();
        equal = (Boolean) in.readValue(null);
        priority = in.readInt();
        order = in.readInt();

    }

}
