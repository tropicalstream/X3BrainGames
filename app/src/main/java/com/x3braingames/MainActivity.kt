package com.x3braingames

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.x3braingames.audio.Sfx
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * X3BrainGames — Sudoku · 2048 · Minesweeper, from one menu.
 *
 * Input contract (suite-standard, FABLE_X3_STARTER_GUIDE Part II):
 *  - right temple pad swipe → ONE discrete step per swipe (menu nav, grid
 *    cursor, 2048 move); the firm click arrives as a KEY, never a touch
 *  - taps chain: 1 = act · 2 = the game's secondary (flag / picker cancel)
 *    · 3 = back to the menu, everywhere
 *  - left temple pad (cyttsp6) is the system volume — swallowed
 *  - every state change autosaves; onPause writes one last time
 */
class MainActivity : Activity(), Host {

    private lateinit var store: SettingsStore
    private lateinit var sfx: Sfx
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var gameView: GameView
    private lateinit var sbsRoot: BinocularSbsLayout

    private val handler = Handler(Looper.getMainLooper())

    // Multi-tap chain: taps within the window extend the count; the chain
    // resolves 1/2/3 after it closes.
    private var tapCount = 0
    private var lastTapAt = 0L
    private var tapGuard = 0L
    private var resolveTap: Runnable? = null

    // Swipe accumulation.
    private var touchActive = false
    private var touchStartT = 0L
    private var sumX = 0f
    private var sumY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dropFirst = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        sfx = Sfx(this).also { it.loadAsync() }
        engine = Engine(store, this)
        renderer = Renderer(engine, store)
        gameView = GameView(this, engine, renderer)
        sbsRoot = BinocularSbsLayout(this).apply {
            addView(gameView)
            sbsEnabled = store.sbs
        }
        setContentView(sbsRoot)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        engine.boot()
    }

    override fun sfx(id: Int, pitch: Float, vol: Float) = sfx.play(id, pitch, vol)

    // --------------------------------------------------------------- input

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isTapKey = event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER
        if (isTapKey) {
            if (event.action == KeyEvent.ACTION_UP) registerTap(SystemClock.uptimeMillis())
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val dir = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> 0
                KeyEvent.KEYCODE_DPAD_DOWN -> 1
                KeyEvent.KEYCODE_DPAD_LEFT -> 2
                KeyEvent.KEYCODE_DPAD_RIGHT -> 3
                else -> -1
            }
            if (dir >= 0) { engine.swipe(dir); return true }
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (engine.backKey()) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun registerTap(now: Long) {
        if (now - tapGuard < 35) return   // KEY+touch echo of one physical tap
        tapGuard = now
        tapCount = if (now - lastTapAt in 40..330) tapCount + 1 else 1
        lastTapAt = now
        resolveTap?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            resolveTap = null
            val n = tapCount; tapCount = 0
            when {
                n >= 3 -> engine.tripleTap()
                n == 2 -> engine.doubleTap()
                else -> engine.tap()
            }
        }
        resolveTap = r
        handler.postDelayed(r, 340L)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val name = ev.device?.name ?: ""
        if (name.contains("cyttsp6", ignoreCase = true)) return true // volume pad

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchActive = true
                touchStartT = SystemClock.uptimeMillis()
                sumX = 0f; sumY = 0f
                lastX = ev.x; lastY = ev.y
                dropFirst = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchActive) {
                    touchActive = true; touchStartT = SystemClock.uptimeMillis()
                    sumX = 0f; sumY = 0f; lastX = ev.x; lastY = ev.y; dropFirst = true
                } else {
                    val dx = ev.x - lastX
                    val dy = ev.y - lastY
                    lastX = ev.x; lastY = ev.y
                    if (dropFirst) dropFirst = false else { sumX += dx; sumY += dy }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (touchActive) resolveGesture(SystemClock.uptimeMillis())
                touchActive = false
            }
            MotionEvent.ACTION_CANCEL -> touchActive = false
        }
        return true
    }

    private fun resolveGesture(now: Long) {
        val dist = sqrt(sumX * sumX + sumY * sumY)
        val threshold = max(55f, 0.11f * resources.displayMetrics.widthPixels)
        if (dist >= threshold) {
            val dir = if (abs(sumX) >= abs(sumY)) { if (sumX > 0) 3 else 2 } else { if (sumY < 0) 0 else 1 }
            engine.swipe(dir)
        } else if (now - touchStartT <= 320) {
            registerTap(now)
        }
    }

    // ------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        gameView.start()
    }

    override fun onPause() {
        engine.onAppPause()   // the very last move is already saved; belt+braces
        gameView.stop()
        super.onPause()
    }

    override fun onDestroy() {
        sfx.release()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
