/*
 * Copyright 2014 the original author or authors.
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

package com.matthewmitchell.peercoin_android_wallet.data;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.matthewmitchell.peercoinj.core.Address;
import com.matthewmitchell.peercoinj.core.AddressFormatException;
import com.matthewmitchell.peercoinj.core.Coin;
import com.matthewmitchell.peercoinj.core.Monetary;
import com.matthewmitchell.peercoinj.core.NetworkParameters;
import com.matthewmitchell.peercoinj.core.ScriptException;
import com.matthewmitchell.peercoinj.core.Transaction;
import com.matthewmitchell.peercoinj.core.Wallet.SendRequest;
import com.matthewmitchell.peercoinj.core.WrongNetworkException;
import com.matthewmitchell.peercoinj.protocols.payments.PaymentProtocol;
import com.matthewmitchell.peercoinj.protocols.payments.PaymentProtocolException;
import com.matthewmitchell.peercoinj.script.Script;
import com.matthewmitchell.peercoinj.script.ScriptBuilder;
import com.matthewmitchell.peercoinj.shapeshift.ShapeShift;
import com.matthewmitchell.peercoinj.shapeshift.ShapeShiftCoin;
import com.matthewmitchell.peercoinj.shapeshift.ShapeShiftMonetary;
import com.matthewmitchell.peercoinj.uri.PeercoinURI;
import com.matthewmitchell.peercoinj.uri.PeercoinURIParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.io.BaseEncoding;

import com.matthewmitchell.peercoin_android_wallet.Constants;
import com.matthewmitchell.peercoin_android_wallet.util.Bluetooth;
import com.matthewmitchell.peercoin_android_wallet.util.GenericUtils;
import com.matthewmitchell.peercoinj.params.Networks;

/**
 * @author Andreas Schildbach
 */
public final class PaymentIntent implements Parcelable {

    public enum Standard {
        BIP21, BIP70
    }

    public final static class Output implements Parcelable {

        public final Monetary amount;
        public final Script script;

        private enum OutputType {
            COIN, SHAPESHIFT, NO_AMOUNT
        }

        public Output(final Monetary amount, final Script script) {
            this.amount = amount;
            this.script = script;
        }

        public static Output valueOf(final PaymentProtocol.Output output) throws PaymentProtocolException.InvalidOutputs {
            try
            {
                final Script script = new Script(output.scriptData);
                return new PaymentIntent.Output(output.amount, script);
            }
            catch (final ScriptException x)
            {
                throw new PaymentProtocolException.InvalidOutputs("unparseable script in output: " + Arrays.toString(output.scriptData));
            }
        }

        public boolean hasAmount()
        {
            return amount != null && amount.signum() != 0;
        }

        @Override
        public String toString()
        {
            final StringBuilder builder = new StringBuilder();

            builder.append(getClass().getSimpleName());
            builder.append('[');
            builder.append(hasAmount() ? amount.toPlainString() : "null");
            builder.append(',');
            if (script.isSentToAddress() || script.isPayToScriptHash())
                builder.append(script.getToAddress(Constants.NETWORK_PARAMETERS));
            else if (script.isSentToRawPubKey())
                for (final byte b : script.getPubKey())
                    builder.append(String.format("%02x", b));
            else if (script.isSentToMultiSig())
                builder.append("multisig");
            else
                builder.append("unknown");
            builder.append(']');

            return builder.toString();
        }

        @Override
        public int describeContents()
        {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {

            OutputType type;

            if (amount instanceof Coin)
                type = OutputType.COIN;
            else if (amount instanceof ShapeShiftMonetary)
                type = OutputType.SHAPESHIFT;
            else
                type = OutputType.NO_AMOUNT;

            dest.writeInt(type.ordinal());

            if (type == OutputType.SHAPESHIFT) {
                dest.writeLong(amount.getValue());
                dest.writeInt(amount.smallestUnitExponent());
            }else if (type == OutputType.COIN) 
                dest.writeLong(amount.getValue());

            final byte[] program = script.getProgram();
            dest.writeInt(program.length);
            dest.writeByteArray(program);

        }

        public static final Parcelable.Creator<Output> CREATOR = new Parcelable.Creator<Output>()
        {
            @Override
            public Output createFromParcel(final Parcel in)
            {
                return new Output(in);
            }

            @Override
            public Output[] newArray(final int size)
            {
                return new Output[size];
            }
        };

        private Output(final Parcel in) {

            OutputType type = OutputType.values()[in.readInt()];

            if (type == OutputType.COIN)
                amount = Coin.valueOf(in.readLong());
            else if (type == OutputType.SHAPESHIFT)
                amount = new ShapeShiftMonetary(in.readLong(), in.readInt());
            else
                amount = null;

            final int programLength = in.readInt();
            final byte[] program = new byte[programLength];
            in.readByteArray(program);
            script = new Script(program);

        }

    }

    @CheckForNull
    public final Standard standard;

    @CheckForNull
    public final String payeeName;

    @CheckForNull
    public final String payeeVerifiedBy;

    @CheckForNull
    public final Output[] outputs;

    @CheckForNull
    public final String memo;

    @CheckForNull
    public final String paymentUrl;

    @CheckForNull
    public final byte[] payeeData;

    @CheckForNull
    public final String paymentRequestUrl;

    @CheckForNull
    public final byte[] paymentRequestHash;

    public final List<NetworkParameters> networks;

    private static final Logger log = LoggerFactory.getLogger(PaymentIntent.class);

    public PaymentIntent(@Nullable final Standard standard, @Nullable final String payeeName, @Nullable final String payeeVerifiedBy,
            @Nullable final Output[] outputs, @Nullable final String memo, @Nullable final String paymentUrl, @Nullable final byte[] payeeData,
            @Nullable final String paymentRequestUrl, @Nullable final byte[] paymentRequestHash, @Nullable final List<NetworkParameters> networks) {
        this.standard = standard;
        this.payeeName = payeeName;
        this.payeeVerifiedBy = payeeVerifiedBy;
        this.outputs = outputs;
        this.memo = memo;
        this.paymentUrl = paymentUrl;
        this.payeeData = payeeData;
        this.paymentRequestUrl = paymentRequestUrl;
        this.paymentRequestHash = paymentRequestHash;
        this.networks = networks;
    }

    private PaymentIntent(@Nonnull final Address address, @Nullable final String addressLabel) {
        this(null, null, null, buildSimplePayTo(Coin.ZERO, address), addressLabel, null, null, null, null, address.getParameters());
    }

    public static PaymentIntent blank() {
        return new PaymentIntent(null, null, null, null, null, null, null, null, null, null);
    }

    public static PaymentIntent fromAddress(@Nonnull final Address address, @Nullable final String addressLabel) {
        return new PaymentIntent(address, addressLabel);
    }

    public static PaymentIntent fromAddress(@Nonnull final String address, @Nullable final String addressLabel) throws WrongNetworkException, AddressFormatException {
        return new PaymentIntent(new Address(address), addressLabel);
    }

    public static PaymentIntent fromPeercoinUri(@Nonnull final PeercoinURI peercoinUri) {
        final Address address = peercoinUri.getAddress();
        final Output[] outputs = address != null ? buildSimplePayTo(peercoinUri.getAmount(), address) : null;
        final String bluetoothMac = (String) peercoinUri.getParameterByName(Bluetooth.MAC_URI_PARAM);
        final String paymentRequestHashStr = (String) peercoinUri.getParameterByName("h");
        final byte[] paymentRequestHash = paymentRequestHashStr != null ? base64UrlDecode(paymentRequestHashStr) : null;

        return new PaymentIntent(PaymentIntent.Standard.BIP21, null, null, outputs, peercoinUri.getLabel(), bluetoothMac != null ? "bt:"
        + bluetoothMac : null, null, peercoinUri.getPaymentRequestUrl(), paymentRequestHash, address.getParameters());
    }

    /**
     * Get a payment intent from a ShapeShift URI
     */
    public static PaymentIntent fromShapeShiftURI(String input) {
        // See if input is a URI for a shapeshift coin

        // Make sure spaces as like placed in Reddcoin are removed
        input = input.replace(" ", "");

        int i = input.indexOf(':');
        if (i != -1) {
            String prefix = input.substring(0, i);
            ShapeShiftCoin coin = ShapeShift.getCoin(prefix);
            if (coin != null) {
                try {
                    // Attempt to get address, label and amount for coin.
                    PeercoinURI uri = new PeercoinURI(coin, input, coin.getId());
                    return PaymentIntent.fromPeercoinUri(uri);
                } catch (PeercoinURIParseException ex) {
                }
            }
        }

        return null;

    }

    /**
     * Get a payment address from an address string
     */
    public static PaymentIntent fromAddress(String input) {

        try {
            // Else see if it is an address
            Address address = new Address(input);
            return PaymentIntent.fromAddress(address, null);
        } catch (AddressFormatException ex) {
            return null;
        }

     }

    private static final BaseEncoding BASE64URL = BaseEncoding.base64Url().omitPadding();

    private static byte[] base64UrlDecode(final String encoded)
    {
        try
        {
            return BASE64URL.decode(encoded);
        }
        catch (final IllegalArgumentException x)
        {
            log.info("cannot base64url-decode: " + encoded);
            return null;
        }
    }

    public PaymentIntent mergeWithEditedValues(@Nullable final Coin editedAmount, @Nullable final Address editedAddress) {

        final Output[] outputs;
        List<NetworkParameters> networks;

        if (hasOutputs() && editedAddress == null) {

            if (mayEditAmount()) {

                checkArgument(editedAmount != null);
                // put all coins on first output, skip the others
                outputs = new Output[] { new Output(editedAmount, this.outputs[0].script) };

            } else {
                // exact copy of outputs
                outputs = this.outputs;
            }
            networks = this.networks;
        } else {
            checkArgument(editedAmount != null);
            checkArgument(editedAddress != null);
            // custom output
            outputs = buildSimplePayTo(editedAmount, editedAddress);
            networks = editedAddress.getParameters();
        }

        return new PaymentIntent(standard, payeeName, payeeVerifiedBy, outputs, memo, null, payeeData, null, null, networks);

    }

    public SendRequest toSendRequest()
    {
        final Transaction transaction = new Transaction(Constants.NETWORK_PARAMETERS);
        for (final PaymentIntent.Output output : outputs)
            transaction.addOutput((Coin) output.amount, output.script);
        return SendRequest.forTx(transaction);
    }

    private static Output[] buildSimplePayTo(final Monetary amount, final Address address) {
        return new Output[] { new Output(amount, ScriptBuilder.createOutputScript(address)) };
    }

    public boolean hasPayee()
    {
        return payeeName != null;
    }

    public boolean hasOutputs()
    {
        return outputs != null && outputs.length > 0;
    }

    public boolean hasAddress()
    {
        if (outputs == null || outputs.length != 1)
            return false;

        final Script script = outputs[0].script;
        return script.isSentToAddress() || script.isPayToScriptHash() || script.isSentToRawPubKey();
    }

    public Address getAddress() {
        if (!hasAddress())
            throw new IllegalStateException();

        final Script script = outputs[0].script;
        return script.getToAddress(networks.get(0), true);
    }

    public boolean mayEditAddress()
    {
        return standard == null;
    }

    public boolean hasAmount()
    {
        if (hasOutputs())
            for (final Output output : outputs)
                if (output.hasAmount())
                    return true;

        return false;
    }

    /**
     * Gets the ShapeShift Monetary for the PaymentIntent
     */
    public Monetary getShapeShiftAmount() {

        if (!hasOutputs())
            return null;

        if (!outputs[0].hasAmount())
            return null;

        return outputs[0].amount;

    }

    public Coin getAmount() {

        Coin amount = Coin.ZERO;

        if (hasOutputs())
            for (final Output output : outputs)
                if (output.hasAmount())
                    amount = amount.add((Coin) output.amount);

        if (amount.signum() != 0)
            return amount;
        else
            return null;
    }

    public boolean mayEditAmount()
    {
        return !(standard == Standard.BIP70 && hasAmount());
    }

    public boolean hasPaymentUrl()
    {
        return paymentUrl != null;
    }

    public boolean isSupportedPaymentUrl()
    {
        return isHttpPaymentUrl() || isBluetoothPaymentUrl();
    }

    public boolean isHttpPaymentUrl()
    {
        return paymentUrl != null
            && (GenericUtils.startsWithIgnoreCase(paymentUrl, "http:") || GenericUtils.startsWithIgnoreCase(paymentUrl, "https:"));
    }

    public boolean isBluetoothPaymentUrl()
    {
        return Bluetooth.isBluetoothUrl(paymentUrl);
    }

    public boolean hasPaymentRequestUrl()
    {
        return paymentRequestUrl != null;
    }

    public boolean isSupportedPaymentRequestUrl()
    {
        return isHttpPaymentRequestUrl() || isBluetoothPaymentRequestUrl();
    }

    public boolean isHttpPaymentRequestUrl()
    {
        return paymentRequestUrl != null
            && (GenericUtils.startsWithIgnoreCase(paymentRequestUrl, "http:") || GenericUtils.startsWithIgnoreCase(paymentRequestUrl, "https:"));
    }

    public boolean isBluetoothPaymentRequestUrl()
    {
        return Bluetooth.isBluetoothUrl(paymentRequestUrl);
    }

    /**
     * Check if given payment intent is only extending on <i>this</i> one, that is it does not alter any of the fields.
     * Address and amount fields must be equal, respectively (non-existence included).
     * 
     * Alternatively, a BIP21+BIP72 request can provide a hash of the BIP70 request.
     * 
     * @param other
     *            payment intent that is checked if it extends this one
     * @return true if it extends
     */
    public boolean isExtendedBy(final PaymentIntent other)
    {
        // shortcut via hash
        if (standard == Standard.BIP21 && other.standard == Standard.BIP70)
            if (paymentRequestHash != null && Arrays.equals(paymentRequestHash, other.paymentRequestHash))
                return true;

        // TODO memo
        return equalsAmount(other) && equalsAddress(other);
    }

    public boolean equalsAmount(final PaymentIntent other)
    {
        final boolean hasAmount = hasAmount();
        if (hasAmount != other.hasAmount())
            return false;
        if (hasAmount && !getAmount().equals(other.getAmount()))
            return false;
        return true;
    }

    public boolean equalsAddress(final PaymentIntent other)
    {
        final boolean hasAddress = hasAddress();
        if (hasAddress != other.hasAddress())
            return false;
        if (hasAddress && !getAddress().equals(other.getAddress()))
            return false;
        return true;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();

        builder.append(getClass().getSimpleName());
        builder.append('[');
        builder.append(standard);
        builder.append(',');
        if (hasPayee())
        {
            builder.append(payeeName);
            if (payeeVerifiedBy != null)
                builder.append("/").append(payeeVerifiedBy);
            builder.append(',');
        }
        builder.append(hasOutputs() ? Arrays.toString(outputs) : "null");
        builder.append(',');
        builder.append(paymentUrl);
        if (payeeData != null)
        {
            builder.append(',');
            builder.append(Arrays.toString(payeeData));
        }
        if (paymentRequestUrl != null)
        {
            builder.append(",paymentRequestUrl=");
            builder.append(paymentRequestUrl);
        }
        if (paymentRequestHash != null)
        {
            builder.append(",paymentRequestHash=");
            builder.append(BaseEncoding.base16().lowerCase().encode(paymentRequestHash));
        }
        if (networks != null) {
            builder.append(",networks=[");
            for (NetworkParameters network: networks) {
                builder.append(network.getId());
                builder.append(",");
            }
            builder.append("]");
        }
        builder.append(']');

        return builder.toString();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {

        dest.writeSerializable(standard);
        dest.writeString(payeeName);
        dest.writeString(payeeVerifiedBy);

        if (outputs != null) {
            dest.writeInt(outputs.length);
            dest.writeTypedArray(outputs, 0);
        } else
            dest.writeInt(0);

        dest.writeString(memo);
        dest.writeString(paymentUrl);

        if (payeeData != null) {
            dest.writeInt(payeeData.length);
            dest.writeByteArray(payeeData);
        } else
            dest.writeInt(0);

        dest.writeString(paymentRequestUrl);

        if (paymentRequestHash != null) {
            dest.writeInt(paymentRequestHash.length);
            dest.writeByteArray(paymentRequestHash);
        } else
            dest.writeInt(0);

        if (networks != null) {
            dest.writeInt(networks.size());
            for (NetworkParameters network: networks)
                dest.writeString(network.getId());
        }else
            dest.writeInt(0);

    }

    public static final Parcelable.Creator<PaymentIntent> CREATOR = new Parcelable.Creator<PaymentIntent>() {

        @Override
        public PaymentIntent createFromParcel(final Parcel in) {
            return new PaymentIntent(in);
        }

        @Override
        public PaymentIntent[] newArray(final int size) {
            return new PaymentIntent[size];
        }

    };

    private PaymentIntent(final Parcel in) {

        standard = (Standard) in.readSerializable();
        payeeName = in.readString();
        payeeVerifiedBy = in.readString();

        final int outputsLength = in.readInt();
        if (outputsLength > 0) {
            outputs = new Output[outputsLength];
            in.readTypedArray(outputs, Output.CREATOR);
        } else
            outputs = null;

        memo = in.readString();
        paymentUrl = in.readString();

        final int payeeDataLength = in.readInt();
        if (payeeDataLength > 0) {
            payeeData = new byte[payeeDataLength];
            in.readByteArray(payeeData);
        } else
            payeeData = null;

        paymentRequestUrl = in.readString();

        final int paymentRequestHashLength = in.readInt();
        if (paymentRequestHashLength > 0) {
            paymentRequestHash = new byte[paymentRequestHashLength];
            in.readByteArray(paymentRequestHash);
        } else
            paymentRequestHash = null;

        int networkNum = in.readInt();

        if (networkNum > 0) {
            networks = new ArrayList<NetworkParameters>(networkNum);
            for (int x = 0; x < networkNum; x++)
                networks.add(Networks.get(in.readString()));
        }else
            networks = null;

    }

}

