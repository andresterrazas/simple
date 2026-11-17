package com.andresterrazas.simple.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Home : Screen
    data class AppMenu(val pkg: String) : Screen
    data class IconPicker(val pkg: String) : Screen
    data object Settings : Screen
    data object Setup : Screen
    data object Order : Screen
    data object Clock : Screen
    data object Controls : Screen
    data object Wallpaper : Screen
    data object Dock : Screen
    data object Blur : Screen
    data object Upload : Screen
    data object Apps : Screen
    data object System : Screen
    data object Diagnostics : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    fun push(s: Screen) {
        if (stack.last() != s) stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun reset() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

class UiState {
    var expanded by mutableStateOf(false)
    var moving by mutableStateOf<String?>(null)
    var focusTick by mutableIntStateOf(0)
    var lastFocused: String? = null

    fun reset() {
        expanded = false
        moving = null
        lastFocused = null
        focusTick++
    }

    fun collapse() {
        expanded = false
        focusTick++
    }
}
