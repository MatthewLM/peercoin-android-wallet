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

package com.matthewmitchell.peercoin_android_wallet;

import java.io.File;

import com.matthewmitchell.peercoinj.core.NetworkParameters;
import com.matthewmitchell.peercoinj.params.MainNetParams;
import com.matthewmitchell.peercoinj.utils.MonetaryFormat;

import android.os.Build;
import android.os.Environment;
import android.text.format.DateUtils;

import com.matthewmitchell.peercoin_android_wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class Constants
{
    public static final boolean TEST = R.class.getPackage().getName().contains("_test");

    public static final NetworkParameters NETWORK_PARAMETERS = MainNetParams.get();

    public final static class Files
    {

        /** Old filename of the wallet. */
        public static final String WALLET_FILENAME_PROTOBUF_OLD = "wallet-protobuf";

        /** Filename of the wallet. */
        public static final String WALLET_FILENAME_PROTOBUF = "peercoin-wallet-protobuf";

        /** Filename of the automatic key backup (old format, can only be read). */
        public static final String WALLET_KEY_BACKUP_BASE58 = "peercoin-key-backup-base58";

        /** Filename of the automatic wallet backup. */
        public static final String WALLET_KEY_BACKUP_PROTOBUF = "peercoin-key-backup-protobuf";

        /** Manual backups go here. */
        public static final File EXTERNAL_WALLET_BACKUP_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        /** Filename of the manual key backup (old format, can only be read). */
        public static final String EXTERNAL_WALLET_KEY_BACKUP = "peercoin-wallet-keys";

        /** Filename of the manual wallet backup. */
        public static final String EXTERNAL_WALLET_BACKUP = "peercoin-wallet-backup";

        /** Filename of exported transactions. */
        public static final String TX_EXPORT_NAME = "peercoin-transactions";

        /** Filename of the block store for storing the chain. */
        public static final String BLOCKCHAIN_FILENAME = "blockchain";

        public static final String VALID_HASHES_FILENAME = "validhashes";
        public static final String PEERS_FILENAME = "peers";
    }

    /** Maximum size of backups. Files larger will be rejected. */
    public static final long BACKUP_MAX_CHARS = 10000000;

    public static final String EXPLORE_BASE_URL = "https://abe.peercoinexplorer.net/";

    /** URL to fetch version alerts from. */
    public static final String VERSION_URL = "";

    /** MIME type used for transmitting single transactions. */
    public static final String MIMETYPE_TRANSACTION = "application/x-ppctx";

    /** MIME type used for transmitting wallet backups. */
    public static final String MIMETYPE_WALLET_BACKUP = "application/x-peercoin-wallet-backup";

    /** MIME type used for transaction export. */
    public static final String MIMETYPE_TX_EXPORT = "text/csv";

    /** Number of confirmations until a transaction is fully confirmed. */
    public static final int MAX_NUM_CONFIRMATIONS = 7;

    /** User-agent to use for network access. */
    public static final String USER_AGENT = "Peercoin Wallet";

    /** Default currency to use if all default mechanisms fail. */
    public static final String DEFAULT_EXCHANGE_CURRENCY = "USD";

    /** Donation address for tip/donate action. */
    public static final String DONATION_ADDRESS = "PNY8FPHRY8NM8VG77TXZ4R3WY7QJ25ATG6";

    /** Recipient e-mail address for reports. */
    public static final String REPORT_EMAIL = "matthewmitchell@thelibertyportal.com";

    /** Subject line for manually reported issues. */
    public static final String REPORT_SUBJECT_ISSUE = "Reported issue";

    /** Subject line for crash reports. */
    public static final String REPORT_SUBJECT_CRASH = "Crash report";

    public static final char CHAR_HAIR_SPACE = '\u200a';
    public static final char CHAR_THIN_SPACE = '\u2009';
    public static final char CHAR_ALMOST_EQUAL_TO = '\u2248';
    public static final char CHAR_CHECKMARK = '\u2713';
    public static final char CURRENCY_PLUS_SIGN = '\uff0b';
    public static final char CURRENCY_MINUS_SIGN = '\uff0d';
    public static final String PREFIX_ALMOST_EQUAL_TO = Character.toString(CHAR_ALMOST_EQUAL_TO) + CHAR_THIN_SPACE;
    public static final int ADDRESS_FORMAT_GROUP_SIZE = 4;
    public static final int ADDRESS_FORMAT_LINE_SIZE = 12;

    public static final MonetaryFormat LOCAL_FORMAT = new MonetaryFormat().noCode().minDecimals(2).optionalDecimals();

    public static final String SOURCE_URL = "https://github.com/MatthewLM/peercoin-android-wallet";
    public static final String BINARY_URL = "https://github.com/MatthewLM/peercoin-android-wallet/releases";
    public static final String MARKET_APP_URL = "market://details?id=%s";
    public static final String WEBMARKET_APP_URL = "https://play.google.com/store/apps/details?id=%s";

    public static final int HTTP_TIMEOUT_MS = 15 * (int) DateUtils.SECOND_IN_MILLIS;
    public static final int PEER_TIMEOUT_MS = 8 * (int) DateUtils.SECOND_IN_MILLIS;

    public static final long LAST_USAGE_THRESHOLD_JUST_MS = DateUtils.HOUR_IN_MILLIS;
    public static final long LAST_USAGE_THRESHOLD_RECENTLY_MS = 2 * DateUtils.DAY_IN_MILLIS;

    public static final int SDK_JELLY_BEAN = 16;
    public static final int SDK_JELLY_BEAN_MR2 = 18;

    public static final int SDK_DEPRECATED_BELOW = Build.VERSION_CODES.ICE_CREAM_SANDWICH;

    public static final boolean BUG_OPENSSL_HEARTBLEED = Build.VERSION.SDK_INT == Constants.SDK_JELLY_BEAN
        && Build.VERSION.RELEASE.startsWith("4.1.1");

    public static final int MEMORY_CLASS_LOWEND = 48;

}
