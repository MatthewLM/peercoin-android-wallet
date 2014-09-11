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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Currency;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.format.DateUtils;
import com.matthewmitchell.peercoin_android_wallet.util.GenericUtils;
import com.matthewmitchell.peercoin_android_wallet.util.Io;
import com.matthewmitchell.peercoinj.core.Utils;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesProvider extends ContentProvider
{
	public static class ExchangeRate
	{
		public ExchangeRate(@Nonnull final String currencyCode, @Nonnull final BigInteger rate, final String source)
		{
			this.currencyCode = currencyCode;
			this.rate = rate;
			this.source = source;
		}

		public final String currencyCode;
		public final BigInteger rate;
		public final String source;

		@Override
		public String toString()
		{
			return getClass().getSimpleName() + '[' + currencyCode + ':' + GenericUtils.formatDebugValue(rate) + ']';
		}
	}

	public static final String KEY_CURRENCY_CODE = "currency_code";
	private static final String KEY_RATE = "rate";
	private static final String KEY_SOURCE = "source";

	public static final String QUERY_PARAM_Q = "q";

	private Configuration config;
	private String userAgent;

	@CheckForNull
	private Map<String, ExchangeRate> exchangeRates = null;
	private long lastUpdated = 0;

	private static final URL BTCE_URL;
	private static final String BTCE_SOURCE = "btc-e.com";

	// https://bitmarket.eu/api/ticker

	static {
		try {
			BTCE_URL = new URL("https://btc-e.com/api/2/ppc_usd/ticker");
		} catch (final MalformedURLException x) {
			throw new RuntimeException(x); // cannot happen
		}
	}

	private static final long UPDATE_FREQ_MS = 10 * DateUtils.MINUTE_IN_MILLIS;

	private static final Logger log = LoggerFactory.getLogger(ExchangeRatesProvider.class);

	@Override
	public boolean onCreate()
	{
		final Context context = getContext();

		this.config = new Configuration(PreferenceManager.getDefaultSharedPreferences(context));

		this.userAgent = WalletApplication.httpUserAgent(WalletApplication.packageInfoFromContext(context).versionName);
		
		final ExchangeRate cachedExchangeRate = config.getCachedExchangeRate();
		if (cachedExchangeRate != null)
		{
			exchangeRates = new TreeMap<String, ExchangeRate>();
			exchangeRates.put(cachedExchangeRate.currencyCode, cachedExchangeRate);
		}

		return true;
	}

	public static Uri contentUri(@Nonnull final String packageName)
	{
		return Uri.parse("content://" + packageName + '.' + "exchange_rates");
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder)
	{
		final long now = System.currentTimeMillis();

		if (lastUpdated == 0 || now - lastUpdated > UPDATE_FREQ_MS)
		{
			Map<String, ExchangeRate> newExchangeRates = null;
			if (newExchangeRates == null)
				newExchangeRates = requestExchangeRates(userAgent);

			if (newExchangeRates != null)
			{
				exchangeRates = newExchangeRates;
				lastUpdated = now;

				final ExchangeRate exchangeRateToCache = bestExchangeRate(config.getExchangeCurrencyCode());
				if (exchangeRateToCache != null)
					config.setCachedExchangeRate(exchangeRateToCache);
			}
		}

		if (exchangeRates == null)
			return null;

		final MatrixCursor cursor = new MatrixCursor(new String[] { BaseColumns._ID, KEY_CURRENCY_CODE, KEY_RATE, KEY_SOURCE });

		if (selection == null)
		{
			for (final Map.Entry<String, ExchangeRate> entry : exchangeRates.entrySet())
			{
				final ExchangeRate rate = entry.getValue();
				cursor.newRow().add(rate.currencyCode.hashCode()).add(rate.currencyCode).add(rate.rate.longValue()).add(rate.source);
			}
		}
		else if (selection.equals(QUERY_PARAM_Q))
		{
			final String selectionArg = selectionArgs[0].toLowerCase(Locale.US);
			for (final Map.Entry<String, ExchangeRate> entry : exchangeRates.entrySet())
			{
				final ExchangeRate rate = entry.getValue();
				final String currencyCode = rate.currencyCode;
				final String currencySymbol = GenericUtils.currencySymbol(currencyCode);
				if (currencyCode.toLowerCase(Locale.US).contains(selectionArg) || currencySymbol.toLowerCase(Locale.US).contains(selectionArg))
					cursor.newRow().add(currencyCode.hashCode()).add(currencyCode).add(rate.rate.longValue()).add(rate.source);
			}
		}
		else if (selection.equals(KEY_CURRENCY_CODE))
		{
			final String selectionArg = selectionArgs[0];
			final ExchangeRate rate = bestExchangeRate(selectionArg);
			if (rate != null)
				cursor.newRow().add(rate.currencyCode.hashCode()).add(rate.currencyCode).add(rate.rate.longValue()).add(rate.source);
		}

		return cursor;
	}

	private ExchangeRate bestExchangeRate(final String currencyCode)
	{
		ExchangeRate rate = currencyCode != null ? exchangeRates.get(currencyCode) : null;
		if (rate != null)
			return rate;

		final String defaultCode = defaultCurrencyCode();
		rate = defaultCode != null ? exchangeRates.get(defaultCode) : null;

		if (rate != null)
			return rate;

		return exchangeRates.get(Constants.DEFAULT_EXCHANGE_CURRENCY);
	}

	private String defaultCurrencyCode()
	{
		try
		{
			return Currency.getInstance(Locale.getDefault()).getCurrencyCode();
		}
		catch (final IllegalArgumentException x)
		{
			return null;
		}
	}

	public static ExchangeRate getExchangeRate(@Nonnull final Cursor cursor)
	{
		final String currencyCode = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_CODE));
		final BigInteger rate = BigInteger.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE)));
		final String source = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_SOURCE));

		return new ExchangeRate(currencyCode, rate, source);
	}

	@Override
	public Uri insert(final Uri uri, final ContentValues values)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int delete(final Uri uri, final String selection, final String[] selectionArgs)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public String getType(final Uri uri)
	{
		throw new UnsupportedOperationException();
	}

	private static String getURLResult(URL url, final String userAgent) {

		HttpURLConnection connection = null;
		Reader reader = null;

		try {
			connection = (HttpURLConnection) url.openConnection();
			
			connection.setInstanceFollowRedirects(false);
			connection.setConnectTimeout(Constants.HTTP_TIMEOUT_MS);
			connection.setReadTimeout(Constants.HTTP_TIMEOUT_MS);
			connection.addRequestProperty("User-Agent", userAgent);
			connection.connect();

			final int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				
				InputStream is = new BufferedInputStream(connection.getInputStream(), 1024);

				reader = new InputStreamReader(is, Constants.UTF_8);
				final StringBuilder content = new StringBuilder();

				Io.copy(reader, content);	
				return content.toString();

			} else {
				log.warn("http status {} when fetching exchange rates from {}", responseCode, BTCE_URL);
			}
		} catch (final Exception x) {
			log.warn("problem fetching exchange rates from " + BTCE_URL, x);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (final IOException x) {
					// swallow
				}
			}

			if (connection != null)
				connection.disconnect();
		}

		return null;

	}

	private static Map<String, ExchangeRate> requestExchangeRates(final String userAgent) {

		final Map<String, ExchangeRate> rates = new TreeMap<String, ExchangeRate>();

		String usdPriceResult = getURLResult(BTCE_URL, userAgent);
		if (usdPriceResult == null)
			return null;
		
		BigInteger usdPrice;
		try {
			JSONObject jsonResult = new JSONObject(usdPriceResult);
			usdPrice = GenericUtils.parseCoin(jsonResult.getJSONObject("ticker").getString("last"), 0);
		}catch (JSONException e) {
			return null;
		}
			
		rates.put("USD", new ExchangeRate("USD", usdPrice, BTCE_SOURCE));

		// Now for USD conversions
		
		URL yahoo;

		try {
			yahoo = new URL(
				Uri.parse("https://query.yahooapis.com/v1/public/yql")
				.buildUpon()
				.appendQueryParameter("q", "select * from yahoo.finance.xchange where pair=\"usdeur, usdgbp, usdcny, usdjpy, usdsgd, usdhkd, usdcad, usdnzd, usdaud, usdclp, usddkk, usdsek, usdisk, usdchf, usdbrl, usdrub, usdpln, usdthb, usdkrw, usdtwd\"")
				.appendQueryParameter("format", "json")
				.appendQueryParameter("env", "store://datatables.org/alltableswithkeys")
				.build().toString()
			);	
		} catch (final MalformedURLException e) {
			return rates;
		}

		String yahooRes = getURLResult(yahoo, userAgent);
		if (yahooRes != null) {
			try {

				JSONObject head = new JSONObject(yahooRes);
				JSONArray convs = head.getJSONObject("query").getJSONObject("results").getJSONArray("rate");

				for (int x = 0; x < convs.length(); x++) {
					JSONObject conv = convs.getJSONObject(x);
					String currency = conv.getString("id").substring(3);
					String priceString = conv.getString("Rate");
					
					BigInteger price = (new BigDecimal(GenericUtils.parseCoin(priceString, 0).multiply(usdPrice))).movePointLeft(6).toBigInteger();
					rates.put(currency, new ExchangeRate(currency, price, BTCE_SOURCE));

				}

			} catch (Exception e) {
				log.warn("Got error reading yahoo exchange rates: {}", e);
			}
		}

		return rates;

	}
}
