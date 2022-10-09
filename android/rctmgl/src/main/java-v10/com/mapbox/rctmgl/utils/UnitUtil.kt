package com.mapbox.rctmgl.utils

import android.icu.util.LocaleData
import android.icu.util.ULocale
import com.mapbox.navigation.base.formatter.UnitType
import java.util.*

class UnitUtil {
    companion object {
        fun getDistanceUnitTypeByLocaleData(locale: Locale): UnitType {
            val uLocale = ULocale.forLocale(locale)
            val measurementSystem = LocaleData.getMeasurementSystem(uLocale)
            if (measurementSystem == LocaleData.MeasurementSystem.SI) {
                return UnitType.METRIC
            }
            return UnitType.IMPERIAL
        }
    }
}