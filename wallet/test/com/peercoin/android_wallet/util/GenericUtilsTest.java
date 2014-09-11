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

package com.matthewmitchell.peercoin_android_wallet.util;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

import com.matthewmitchell.peercoin_android_wallet.util.GenericUtils;
import com.matthewmitchell.peercoinj.core.NetworkParameters;

/**
 * @author Andreas Schildbach
 */
public class GenericUtilsTest
{
	@Test
	public void formatValue() throws Exception
	{
		final BigInteger coin = new BigInteger("1000000");
		assertEquals("1.00", GenericUtils.formatValue(coin, 4, 0));
		assertEquals("1.00", GenericUtils.formatValue(coin, 6, 0));

		final BigInteger justNot = new BigInteger("999999");
		assertEquals("1.00", GenericUtils.formatValue(justNot, 4, 0));
		assertEquals("0.999999", GenericUtils.formatValue(justNot, 6, 0));

		final BigInteger slightlyMore = new BigInteger("1000001");
		assertEquals("1.00", GenericUtils.formatValue(slightlyMore, 4, 0));
		assertEquals("1.000001", GenericUtils.formatValue(slightlyMore, 6, 0));

		final BigInteger value = new BigInteger("11223344556677");
		assertEquals("11223344.5567", GenericUtils.formatValue(value, 4, 0));
		assertEquals("11223344.556677", GenericUtils.formatValue(value, 6, 0));

		assertEquals("2000000000.00", GenericUtils.formatValue(NetworkParameters.MAX_MONEY, 6, 0));
	}

	@Test
	public void formatMPPCValue() throws Exception
	{
		final BigInteger coin = new BigInteger("100000000");
		assertEquals("100000.00", GenericUtils.formatValue(coin, 2, 3));
		assertEquals("100000.00", GenericUtils.formatValue(coin, 3, 3));

		final BigInteger justNot = new BigInteger("99999999");
		assertEquals("100000.00", GenericUtils.formatValue(justNot, 2, 3));
		assertEquals("99999.999", GenericUtils.formatValue(justNot, 3, 3));

		final BigInteger slightlyMore = new BigInteger("100000010");
		assertEquals("100000.01", GenericUtils.formatValue(slightlyMore, 2, 3));
		assertEquals("100000.01", GenericUtils.formatValue(slightlyMore, 3, 3));

		final BigInteger value = new BigInteger("1122334455667788");
		assertEquals("1122334455667.79", GenericUtils.formatValue(value, 2, 3));
		assertEquals("1122334455667.788", GenericUtils.formatValue(value, 3, 3));

		assertEquals("2000000000000.00", GenericUtils.formatValue(NetworkParameters.MAX_MONEY, 3, 3));
	}

	@Test
	public void formatUPPCValue() throws Exception
	{
		final BigInteger coin = new BigInteger("100000000");
		assertEquals("100000000", GenericUtils.formatValue(coin, 0, 6));
		assertEquals("100000000", GenericUtils.formatValue(coin, 2, 6));

		final BigInteger justNot = new BigInteger("99999999");
		assertEquals("99999999", GenericUtils.formatValue(justNot, 0, 6));
		assertEquals("99999999", GenericUtils.formatValue(justNot, 2, 6));

		final BigInteger slightlyMore = new BigInteger("100000001");
		assertEquals("100000001", GenericUtils.formatValue(slightlyMore, 0, 6));
		assertEquals("100000001", GenericUtils.formatValue(slightlyMore, 2, 6));

		final BigInteger value = new BigInteger("1122334455667788");
		assertEquals("1122334455667788", GenericUtils.formatValue(value, 0, 6));
		assertEquals("1122334455667788", GenericUtils.formatValue(value, 2, 6));

		assertEquals("2000000000000000", GenericUtils.formatValue(NetworkParameters.MAX_MONEY, 2, 6));
	}
}
