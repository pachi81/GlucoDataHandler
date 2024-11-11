package de.michelinside.glucodatahandler.common

import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import org.junit.Assert.assertEquals
import org.junit.Test


class GlucoDataUtilsUnitTest {
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
    }


    @Test
    fun testMgToMmolToMg() {
        for (i in 1..300) {
            assertEquals(GlucoDataUtils.mgToMmol(GlucoDataUtils.mmolToMg(i.toFloat())), i.toFloat())
            assertEquals(GlucoDataUtils.mgToMmol(GlucoDataUtils.mmolToMg(-i.toFloat())), -i.toFloat())
        }
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
        assertEquals(GlucoDataUtils.getRateDegrees(0.5F), 23)
        assertEquals(GlucoDataUtils.getRateDegrees(-0.5F), -23)
        assertEquals(GlucoDataUtils.getRateDegrees(1.5F), 68)
        assertEquals(GlucoDataUtils.getRateDegrees(-1.5F), -68)
        assertEquals(GlucoDataUtils.getRateDegrees(1.2F), 54)
        assertEquals(GlucoDataUtils.getRateDegrees(-1.2F), -54)

    }
}