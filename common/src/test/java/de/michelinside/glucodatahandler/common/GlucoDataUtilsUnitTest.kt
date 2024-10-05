package de.michelinside.glucodatahandler.common

import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import org.junit.Assert.assertTrue
import org.junit.Test


class GlucoDataUtilsUnitTest {
    @Test
    fun testMmolToMg() {
        assertTrue(GlucoDataUtils.mmolToMg(10F) == 180F)
        assertTrue(GlucoDataUtils.mmolToMg(-10F) == -180F)
        assertTrue(GlucoDataUtils.mmolToMg(0.06F) == 1F)
        assertTrue(GlucoDataUtils.mmolToMg(-0.06F) == -1F)
        assertTrue(GlucoDataUtils.mmolToMg(0.08F) == 1F)
        assertTrue(GlucoDataUtils.mmolToMg(-0.08F) == -1F)
        assertTrue(GlucoDataUtils.mmolToMg(0.09F) == 2F)
        assertTrue(GlucoDataUtils.mmolToMg(-0.09F) == -2F)
        assertTrue(GlucoDataUtils.mmolToMg(0.1F) == 2F)
        assertTrue(GlucoDataUtils.mmolToMg(-0.1F) == -2F)
    }

    @Test
    fun testMgToMmol() {
        assertTrue(GlucoDataUtils.mgToMmol(180F) == 10F)
        assertTrue(GlucoDataUtils.mgToMmol(-180F) == -10F)
        assertTrue(GlucoDataUtils.mgToMmol(1F) == 0.06F)
        assertTrue(GlucoDataUtils.mgToMmol(-1F) == -0.06F)
        assertTrue(GlucoDataUtils.mgToMmol(0.5F) == 0.03F)
        assertTrue(GlucoDataUtils.mgToMmol(-0.5F) == -0.03F)
        assertTrue(GlucoDataUtils.mgToMmol(1.5F) == 0.08F)
        assertTrue(GlucoDataUtils.mgToMmol(-1.5F) == -0.08F)
        assertTrue(GlucoDataUtils.mgToMmol(1.7F) == 0.09F)
        assertTrue(GlucoDataUtils.mgToMmol(-1.7F) == -0.09F)
        assertTrue(GlucoDataUtils.mgToMmol(1.8F) == 0.1F)
        assertTrue(GlucoDataUtils.mgToMmol(-1.8F) == -0.1F)
        assertTrue(GlucoDataUtils.mgToMmol(2F) == 0.1F)
        assertTrue(GlucoDataUtils.mgToMmol(-2F) == -0.1F)
    }


    @Test
    fun testMgToMmolToMg() {
        for (i in 0..300) {
            assertTrue(GlucoDataUtils.mgToMmol(GlucoDataUtils.mmolToMg(i.toFloat())) == i.toFloat())
            assertTrue(GlucoDataUtils.mgToMmol(GlucoDataUtils.mmolToMg(-i.toFloat())) == -i.toFloat())
        }

    }
}