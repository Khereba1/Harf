package com.khereba1.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Editor switches the user can turn on or off (kept on the phone, observable by Compose). */
object Prefs {
    private var sp: SharedPreferences? = null

    /** Smart guides: layers snap to the canvas center and edges and to the centers and edges of other layers. */
    var snapOn: Boolean by mutableStateOf(true)
        private set

    var gridOn: Boolean by mutableStateOf(false)
        private set

    var gridSnap: Boolean by mutableStateOf(false)
        private set

    /** Grid cells across the width of the canvas. */
    var gridDivs: Int by mutableIntStateOf(8)
        private set

    /** Resize with "tap twice, drag on the second tap" instead of pinching or dragging corners. */
    var dragResize: Boolean by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val s = context.applicationContext.getSharedPreferences("harf_editor", Context.MODE_PRIVATE)
        sp = s
        snapOn = s.getBoolean("snap", true)
        gridOn = s.getBoolean("grid", false)
        gridSnap = s.getBoolean("gridSnap", false)
        gridDivs = s.getInt("gridDivs", 8).coerceIn(2, 40)
        dragResize = s.getBoolean("dragResize", false)
    }

    fun updateSnap(v: Boolean) {
        snapOn = v
        sp?.edit()?.putBoolean("snap", v)?.apply()
    }

    fun updateGrid(v: Boolean) {
        gridOn = v
        sp?.edit()?.putBoolean("grid", v)?.apply()
    }

    fun updateGridSnap(v: Boolean) {
        gridSnap = v
        sp?.edit()?.putBoolean("gridSnap", v)?.apply()
    }

    fun updateGridDivs(v: Int) {
        gridDivs = v.coerceIn(2, 40)
        sp?.edit()?.putInt("gridDivs", gridDivs)?.apply()
    }

    fun updateDragResize(v: Boolean) {
        dragResize = v
        sp?.edit()?.putBoolean("dragResize", v)?.apply()
    }

    /** Distance between grid lines in document pixels. */
    fun gridStep(docW: Int): Float = docW / gridDivs.toFloat()
}
