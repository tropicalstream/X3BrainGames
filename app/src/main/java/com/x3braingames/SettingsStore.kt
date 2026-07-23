package com.x3braingames

import android.content.Context
import android.os.Build

/**
 * SBS autodetect + best records + the per-move autosave slots. Saves are
 * written after EVERY move (and on pause), so an accidental exit — temple
 * bump, launcher kill, battery — never loses a board.
 */
class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("x3braingames", Context.MODE_PRIVATE)

    private val deviceText = listOf(
        Build.MODEL, Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.PRODUCT
    ).joinToString(" ").lowercase()

    // Guide gotcha #24: X3 Pro reports MODEL=ARGF20; detect by brand instead.
    val isRayNeoX3 =
        "rayneo" in deviceText || "leiniao" in deviceText || "ffalcon" in deviceText ||
            ("x3" in deviceText && ("tcl" in deviceText || "falcon" in deviceText))

    val sbs get() = isRayNeoX3

    // ---- live game saves (whole board per move) ----
    var save2048: String
        get() = p.getString("s2048", "") ?: ""
        set(v) { p.edit().putString("s2048", v).apply() }
    var saveSudoku: String
        get() = p.getString("sSudoku", "") ?: ""
        set(v) { p.edit().putString("sSudoku", v).apply() }
    var saveMines: String
        get() = p.getString("sMines", "") ?: ""
        set(v) { p.edit().putString("sMines", v).apply() }

    // ---- records ----
    var best2048: Int
        get() = p.getInt("best2048", 0)
        set(v) { if (v > best2048) p.edit().putInt("best2048", v).apply() }
    /** Best (lowest) solve seconds; 0 = none yet. */
    var bestSudoku: Int
        get() = p.getInt("bestSudoku", 0)
        set(v) { if (v > 0 && (bestSudoku == 0 || v < bestSudoku)) p.edit().putInt("bestSudoku", v).apply() }
    var bestMines: Int
        get() = p.getInt("bestMines", 0)
        set(v) { if (v > 0 && (bestMines == 0 || v < bestMines)) p.edit().putInt("bestMines", v).apply() }
}
