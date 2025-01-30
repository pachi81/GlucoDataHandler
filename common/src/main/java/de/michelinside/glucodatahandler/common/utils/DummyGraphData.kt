package de.michelinside.glucodatahandler.common.utils

import kotlin.random.Random

object DummyGraphData {
    fun create(hours: Int = 8, stepMinute: Int = 5, min: Int = 50, max: Int = 250): Map<Long, Int> {
        val data = mutableMapOf<Long, Int>()
        if(hours<=0)
            return data
        val startTime = System.currentTimeMillis() - hours * 60 * 60 * 1000
        val timeDiff = 60L * 1000L * stepMinute
        var lastValue = min
        var deltaFactor = 1
        for (i in startTime until System.currentTimeMillis() step timeDiff) {
            if(deltaFactor > 0 && lastValue >= max)
                deltaFactor = -1
            else if(deltaFactor < 0 && lastValue <= min)
                deltaFactor = 1
            lastValue += Random.nextInt(10) * deltaFactor
            data[i] = lastValue
        }
        return data
    }
}