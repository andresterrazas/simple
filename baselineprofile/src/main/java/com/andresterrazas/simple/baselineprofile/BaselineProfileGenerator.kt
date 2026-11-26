package com.andresterrazas.simple.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genera el perfil de rendimiento de simple: registra qué código se usa al arrancar y al recorrer
 * el inicio y los menús, para que Android lo compile por adelantado al instalar.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PKG, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait(openIntent("home"))
        homeJourney()
        settingsJourney()
    }
}
