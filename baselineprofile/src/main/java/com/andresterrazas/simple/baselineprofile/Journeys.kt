package com.andresterrazas.simple.baselineprofile

import android.content.Intent
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope

const val PKG = "com.andresterrazas.simple"
private const val ACTIVITY = "$PKG.MainActivity"

/** Intent para abrir simple, opcionalmente directo en una pantalla (ver MainActivity.openFromExtra). */
fun openIntent(screen: String = "home"): Intent =
    Intent(Intent.ACTION_MAIN)
        .setClassName(PKG, ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra("simple.open", screen)

/** Pulsa una tecla del control varias veces, a ritmo de persona, y espera a que la interfaz se calme. */
fun MacrobenchmarkScope.dpad(keyCode: Int, times: Int, pauseMs: Long = 220) {
    repeat(times) {
        device.pressKeyCode(keyCode)
        Thread.sleep(pauseMs)
    }
    device.waitForIdle()
}

/** Con simple ya abierto, lo lleva a otra pantalla sin relanzar el proceso. */
fun MacrobenchmarkScope.goTo(screen: String) {
    device.executeShellCommand("am start -n $PKG/$ACTIVITY --es simple.open $screen")
    Thread.sleep(600)
    device.waitForIdle()
}

/** Recorrido del inicio: moverse por el dock, abrir la cuadrícula completa y volver al encabezado. */
fun MacrobenchmarkScope.homeJourney() {
    dpad(KeyEvent.KEYCODE_DPAD_RIGHT, 6)
    dpad(KeyEvent.KEYCODE_DPAD_LEFT, 6)
    dpad(KeyEvent.KEYCODE_DPAD_DOWN, 4)
    dpad(KeyEvent.KEYCODE_DPAD_RIGHT, 3)
    dpad(KeyEvent.KEYCODE_DPAD_UP, 6)
    dpad(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
    dpad(KeyEvent.KEYCODE_DPAD_DOWN, 1)
}

/** Recorrido de los menús: cada pantalla de Configuración, bajando y subiendo por toda la lista. */
fun MacrobenchmarkScope.settingsJourney() {
    for (screen in listOf("settings", "header", "dock", "wallpaper", "recs", "apps", "updates")) {
        goTo(screen)
        dpad(KeyEvent.KEYCODE_DPAD_DOWN, 12)
        dpad(KeyEvent.KEYCODE_DPAD_UP, 12)
    }
    // Un desplegable: abrirlo, moverse y cerrarlo con Atrás.
    goTo("dock")
    dpad(KeyEvent.KEYCODE_DPAD_CENTER, 1)
    dpad(KeyEvent.KEYCODE_DPAD_DOWN, 2)
    dpad(KeyEvent.KEYCODE_BACK, 1)
    goTo("home")
}
