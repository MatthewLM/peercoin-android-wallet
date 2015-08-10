/*
 * Copyright 2013-2014 the original author or authors.
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
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

import com.matthewmitchell.peercoinj.core.Address;
import com.matthewmitchell.peercoinj.core.AddressFormatException;
import com.matthewmitchell.peercoinj.core.NetworkParameters;
import com.matthewmitchell.peercoinj.core.WrongNetworkException;

/**
 * @author Andreas Schildbach
 */
public class AddressAndLabel implements Parcelable
{
	public final Address address;
	public final String label;

	public AddressAndLabel(@Nonnull final NetworkParameters addressParams, @Nonnull final String address, @Nullable final String label)
			throws WrongNetworkException, AddressFormatException {
		this.address = new Address(addressParams, address);
		this.label = label;
	}

	public AddressAndLabel(@Nonnull final List<NetworkParameters> addressParams, @Nonnull final String address, @Nullable final String label)
			throws WrongNetworkException, AddressFormatException {
		this.address = new Address(addressParams, address);
		this.label = label;
	}

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		
		List<NetworkParameters> networks = address.getParameters();
		
		dest.writeInt(networks.size());
		
		for (NetworkParameters network: networks)
			dest.writeSerializable(network);
		
		dest.writeByteArray(address.getHash160());
		dest.writeString(label);
	}

	public static final Parcelable.Creator<AddressAndLabel> CREATOR = new Parcelable.Creator<AddressAndLabel>()
	{
		@Override
		public AddressAndLabel createFromParcel(final Parcel in)
		{
			return new AddressAndLabel(in);
		}

		@Override
		public AddressAndLabel[] newArray(final int size)
		{
			return new AddressAndLabel[size];
		}
	};

	private AddressAndLabel(final Parcel in) {
		
		final int paramsSize = in.readInt();
		final List<NetworkParameters> addressParameters = new ArrayList<NetworkParameters>(paramsSize);
		
		for (int x = 0; x < paramsSize; x++)
			addressParameters.add((NetworkParameters) in.readSerializable());
		
		final byte[] addressHash = new byte[Address.LENGTH];
		in.readByteArray(addressHash);
		address = new Address(addressParameters, addressHash);

		label = in.readString();
	}

}

