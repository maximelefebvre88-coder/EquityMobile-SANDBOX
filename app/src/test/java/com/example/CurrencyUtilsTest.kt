package com.example

import com.example.domain.util.getFxMultiplier
import com.example.domain.util.getTickerCurrency
import com.example.domain.util.isCanadianTicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyUtilsTest {

    @Test
    fun testCanadianTickerDetection() {
        assertTrue(isCanadianTicker("AW.TO"))
        assertTrue(isCanadianTicker("aw.to"))
        assertTrue(isCanadianTicker("SHOP.TO"))
        assertTrue(isCanadianTicker("XIU.TO"))
        assertTrue(isCanadianTicker("HUT.V"))
        assertTrue(isCanadianTicker("TEST.VN"))
        assertTrue(isCanadianTicker("AC.CN"))
        assertTrue(isCanadianTicker("BTCX.B.NEO"))
        assertTrue(isCanadianTicker("ABX.TSX"))

        assertFalse(isCanadianTicker("AAPL"))
        assertFalse(isCanadianTicker("MSFT"))
        assertFalse(isCanadianTicker("GOOGL"))
        assertFalse(isCanadianTicker("CASH"))
    }

    @Test
    fun testTickerCurrency() {
        assertEquals("CAD", getTickerCurrency("AW.TO"))
        assertEquals("CAD", getTickerCurrency("shop.to"))
        assertEquals("USD", getTickerCurrency("AAPL"))
        assertEquals("USD", getTickerCurrency("MSFT"))
        assertEquals("", getTickerCurrency("CASH"))
        assertEquals("", getTickerCurrency(""))
    }

    @Test
    fun testFxMultiplier() {
        // Same currency
        assertEquals(1.0, getFxMultiplier("CAD", "CAD"), 0.0001)
        assertEquals(1.0, getFxMultiplier("USD", "USD"), 0.0001)

        // USD to CAD
        assertEquals(1.36, getFxMultiplier("USD", "CAD", 1.36), 0.0001)

        // CAD to USD
        assertEquals(1.0 / 1.36, getFxMultiplier("CAD", "USD", 1.36), 0.0001)

        // Empty / CASH
        assertEquals(1.0, getFxMultiplier("", "CAD"), 0.0001)
        assertEquals(1.0, getFxMultiplier("USD", ""), 0.0001)
    }
}
