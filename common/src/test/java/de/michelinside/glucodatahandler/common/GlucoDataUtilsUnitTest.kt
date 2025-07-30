package de.michelinside.glucodatahandler.common

import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test


class GlucoDataUtilsUnitTest {

    @Test
    fun testIsMmolValue() {
        assertTrue(GlucoDataUtils.isMmolValue(0F))
        assertTrue(GlucoDataUtils.isMmolValue(0.03F))
        assertTrue(GlucoDataUtils.isMmolValue(19.1F))
        assertTrue(GlucoDataUtils.isMmolValue(18F))
        assertTrue(GlucoDataUtils.isMmolValue(3F))
        assertTrue(GlucoDataUtils.isMmolValue(Constants.GLUCOSE_MAX_VALUE.toFloat()/Constants.GLUCOSE_CONVERSION_FACTOR))

        assertTrue(GlucoDataUtils.isMmolValue(GlucoDataUtils.mgToMmol(Constants.GLUCOSE_MIN_VALUE.toFloat())))
        assertTrue(GlucoDataUtils.isMmolValue(GlucoDataUtils.mgToMmol(Constants.GLUCOSE_MAX_VALUE.toFloat())))

        assertFalse(GlucoDataUtils.isMmolValue(Constants.GLUCOSE_MIN_VALUE.toFloat()))
        assertFalse(GlucoDataUtils.isMmolValue(Constants.GLUCOSE_MAX_VALUE.toFloat()))
        assertFalse(GlucoDataUtils.isMmolValue((Constants.GLUCOSE_MIN_VALUE-1).toFloat()))
    }

    @Test
    fun testMmolToMg() {
        assertEquals(GlucoDataUtils.mmolToMg(10F), 180F)
        assertEquals(GlucoDataUtils.mmolToMg(-10F), -180F)
        assertEquals(GlucoDataUtils.mmolToMg(0.06F), 1F)
        assertEquals(GlucoDataUtils.mmolToMg(-0.06F), -1F)
        assertEquals(GlucoDataUtils.mmolToMg(0.08F), 1F)
        assertEquals(GlucoDataUtils.mmolToMg(-0.08F), -1F)
        assertEquals(GlucoDataUtils.mmolToMg(0.09F), 2F)
        assertEquals(GlucoDataUtils.mmolToMg(-0.09F), -2F)
        assertEquals(GlucoDataUtils.mmolToMg(0.1F), 2F)
        assertEquals(GlucoDataUtils.mmolToMg(-0.1F), -2F)
        assertEquals(GlucoDataUtils.mmolToMg((Constants.GLUCOSE_MIN_VALUE-1).toFloat()), Float.NaN)
        assertEquals(GlucoDataUtils.mmolToMg(Constants.GLUCOSE_MIN_VALUE.toFloat()), Float.NaN)
        assertEquals(GlucoDataUtils.mmolToMg(Float.NaN), Float.NaN)
    }

    @Test
    fun testMgToMmol() {
        assertEquals(GlucoDataUtils.mgToMmol(180F), 10F)
        assertEquals(GlucoDataUtils.mgToMmol(-180F), -10F)
        assertEquals(GlucoDataUtils.mgToMmol(1F), 0.06F)
        assertEquals(GlucoDataUtils.mgToMmol(-1F), -0.06F)
        assertEquals(GlucoDataUtils.mgToMmol(0.5F), 0.03F)
        assertEquals(GlucoDataUtils.mgToMmol(-0.5F), -0.03F)
        assertEquals(GlucoDataUtils.mgToMmol(1.5F), 0.08F)
        assertEquals(GlucoDataUtils.mgToMmol(-1.5F), -0.08F)
        assertEquals(GlucoDataUtils.mgToMmol(1.7F), 0.09F)
        assertEquals(GlucoDataUtils.mgToMmol(-1.7F), -0.09F)
        assertEquals(GlucoDataUtils.mgToMmol(1.8F), 0.1F)
        assertEquals(GlucoDataUtils.mgToMmol(-1.8F), -0.1F)
        assertEquals(GlucoDataUtils.mgToMmol(2F), 0.1F)
        assertEquals(GlucoDataUtils.mgToMmol(-2F), -0.1F)
        assertEquals(GlucoDataUtils.mgToMmol(Float.NaN), Float.NaN)
        assertEquals(GlucoDataUtils.mgToMmol((Constants.GLUCOSE_MAX_VALUE+1).toFloat()), Float.NaN)
    }

    @Test
    fun isValueValid() {
        assertTrue(GlucoDataUtils.isGlucoseValid(10F))
        assertTrue(GlucoDataUtils.isGlucoseValid(3.1F))
        assertTrue(GlucoDataUtils.isGlucoseValid(Constants.GLUCOSE_MIN_VALUE))
        assertTrue(GlucoDataUtils.isGlucoseValid(Constants.GLUCOSE_MAX_VALUE))
        assertFalse(GlucoDataUtils.isGlucoseValid(Constants.GLUCOSE_MIN_VALUE-1))
        assertFalse(GlucoDataUtils.isGlucoseValid(Constants.GLUCOSE_MAX_VALUE+1))

        assertTrue(GlucoDataUtils.isGlucoseValid(GlucoDataUtils.mgToMmol(Constants.GLUCOSE_MIN_VALUE.toFloat())))
        assertTrue(GlucoDataUtils.isGlucoseValid(GlucoDataUtils.mgToMmol(Constants.GLUCOSE_MAX_VALUE.toFloat())))
        assertFalse(GlucoDataUtils.isGlucoseValid(GlucoDataUtils.mgToMmol((Constants.GLUCOSE_MIN_VALUE-1).toFloat())))
        assertFalse(GlucoDataUtils.isGlucoseValid(GlucoDataUtils.mgToMmol((Constants.GLUCOSE_MAX_VALUE+1).toFloat())))
    }

    @Test
    fun testGetRateDegrees() {
        assertEquals(GlucoDataUtils.getRateDegrees(Float.NaN), 0)
        assertEquals(GlucoDataUtils.getRateDegrees(0F), 0)
        assertEquals(GlucoDataUtils.getRateDegrees(1F), 45)
        assertEquals(GlucoDataUtils.getRateDegrees(-1F), -45)
        assertEquals(GlucoDataUtils.getRateDegrees(2F), 90)
        assertEquals(GlucoDataUtils.getRateDegrees(-2F), -90)
        assertEquals(GlucoDataUtils.getRateDegrees(2.5F), 90)
        assertEquals(GlucoDataUtils.getRateDegrees(-2.5F), -90)
        assertEquals(GlucoDataUtils.getRateDegrees(0.5F), 20)
        assertEquals(GlucoDataUtils.getRateDegrees(-0.5F), -20)
        assertEquals(GlucoDataUtils.getRateDegrees(1.5F), 65)
        assertEquals(GlucoDataUtils.getRateDegrees(-1.5F), -65)
        assertEquals(GlucoDataUtils.getRateDegrees(1.2F), 50)
        assertEquals(GlucoDataUtils.getRateDegrees(-1.2F), -50)

    }
}