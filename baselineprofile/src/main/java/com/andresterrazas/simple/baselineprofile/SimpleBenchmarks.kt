package com.andresterrazas.simple.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mide el efecto del perfil: arranque en frío y fluidez de los recorridos, sin perfil y con perfil.
 */
@RunWith(AndroidJUnit4::class)
class SimpleBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test fun startupWithoutProfile() = startup(CompilationMode.None())
    @Test fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))
    @Test fun navigationWithoutProfile() = navigation(CompilationMode.None())
    @Test fun navigationWithProfile() = navigation(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 8,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait(openIntent("home"))
    }

    private fun navigation(mode: CompilationMode) = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 3,
        setupBlock = {
            pressHome()
            startActivityAndWait(openIntent("home"))
        },
    ) {
        homeJourney()
        settingsJourney()
    }
}
