package com.x3braingames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.x3braingames.games.Mines
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * All three boards drawn in the suite's vector-on-black idiom, in a 640×480
 * logical space scaled to whatever the eye gets. High contrast, thick
 * cursors, nothing smaller than the waveguide can resolve.
 */
class Renderer(private val engine: Engine, private val store: SettingsStore) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val r = RectF()

    private val CYAN = 0xFF7FD8FF.toInt()
    private val DIM = 0xFF5A6570.toInt()
    private val GOLD = 0xFFFFC24D.toInt()
    private val RED = 0xFFFF5A4D.toInt()
    private val GREEN = 0xFF5AE08A.toInt()
    private val WHITE = 0xFFF2F6FA.toInt()

    fun draw(canvas: Canvas, w: Int, h: Int) {
        canvas.drawColor(Color.BLACK)
        val s = min(w / 640f, h / 480f)
        canvas.save()
        canvas.translate((w - 640f * s) / 2f, (h - 480f * s) / 2f)
        canvas.scale(s, s)
        when (engine.screen) {
            Screen.MENU -> menu(canvas)
            Screen.G2048 -> g2048(canvas)
            Screen.SUDOKU -> sudoku(canvas)
            Screen.MINES -> mines(canvas)
        }
        banner(canvas)
        canvas.restore()
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, alpha: Int = 255) {
        text.textSize = size; text.color = color; text.alpha = alpha
        c.drawText(s, x, y, text)
    }

    private fun banner(c: Canvas) {
        if (engine.bannerT <= 0f || engine.banner.isEmpty()) return
        val a = (engine.bannerT.coerceAtMost(1f) * 255).toInt()
        fill.color = Color.BLACK; fill.alpha = (a * 0.75f).toInt()
        r.set(80f, 200f, 560f, 260f); c.drawRoundRect(r, 12f, 12f, fill)
        stroke.color = GOLD; stroke.alpha = a; stroke.strokeWidth = 2.5f
        c.drawRoundRect(r, 12f, 12f, stroke)
        label(c, engine.banner, 320f, 240f, 26f, WHITE, a)
        fill.alpha = 255; stroke.alpha = 255
    }

    // ------------------------------------------------------------ menu

    private fun menu(c: Canvas) {
        val pulse = 0.6f + 0.4f * sin(engine.time * 3.5f)
        label(c, "X3 BRAINGAMES", 320f, 62f, 40f, GOLD)
        label(c, "PICK UP AND PLAY", 320f, 90f, 15f, DIM)
        val stats = arrayOf(
            if (store.bestSudoku > 0) "BEST ${engine.fmtTime(store.bestSudoku.toFloat())}" else "9x9 · UNIQUE PUZZLES",
            if (store.best2048 > 0) "BEST ${store.best2048}" else "SWIPE THE TILES",
            if (store.bestMines > 0) "BEST ${engine.fmtTime(store.bestMines.toFloat())}" else "12x9 · 14 MINES"
        )
        for (i in 0 until 3) {
            val top = 118f + i * 92f
            val sel = i == engine.menuSel
            r.set(150f, top, 490f, top + 76f)
            fill.color = if (sel) 0xFF15242E.toInt() else 0xFF0C1116.toInt()
            c.drawRoundRect(r, 14f, 14f, fill)
            stroke.color = if (sel) GOLD else DIM
            stroke.strokeWidth = if (sel) 3.5f else 1.5f
            c.drawRoundRect(r, 14f, 14f, stroke)
            label(c, Engine.MENU_ITEMS[i], 320f, top + 33f, 26f, if (sel) WHITE else 0xFFB9C2CB.toInt())
            label(c, stats[i], 320f, top + 58f, 13f, if (sel) CYAN else DIM)
            if (engine.continueBadge(i)) {
                label(c, "▶ CONTINUE", 452f, top + 18f, 11f, GREEN)
            }
            if (sel) {
                label(c, "›", 140f, top + 46f, 30f, GOLD, (pulse * 255).toInt())
                label(c, "‹", 500f, top + 46f, 30f, GOLD, (pulse * 255).toInt())
            }
        }
        label(c, "SWIPE CHOOSE · TAP PLAY", 320f, 452f, 14f, DIM)
    }

    // ------------------------------------------------------------ 2048

    private fun g2048(c: Canvas) {
        val g = engine.g2048
        label(c, "SCORE ${g.score}", 200f, 52f, 20f, WHITE)
        label(c, "BEST ${g.best}", 460f, 52f, 20f, GOLD)
        val cell = 78f; val gap = 8f
        val bx = 320f - (cell * 4 + gap * 3) / 2f
        val by = 78f
        for (i in 0 until 16) {
            val row = i / 4; val col = i % 4
            val x = bx + col * (cell + gap); val y = by + row * (cell + gap)
            r.set(x, y, x + cell, y + cell)
            val v = g.cells[i]
            if (v == 0) {
                stroke.color = DIM; stroke.strokeWidth = 1.5f
                c.drawRoundRect(r, 8f, 8f, stroke)
            } else {
                val k = (ln(v.toFloat()) / ln(2f)).toInt()   // 1..11+
                val hue = (48f - k * 14f + 360f) % 360f
                val col2 = Color.HSVToColor(floatArrayOf(hue, 0.75f, 1f))
                fill.color = col2; fill.alpha = 34 + (k * 12).coerceAtMost(120)
                c.drawRoundRect(r, 8f, 8f, fill); fill.alpha = 255
                stroke.color = col2; stroke.strokeWidth = if (v >= 128) 3.5f else 2.2f
                c.drawRoundRect(r, 8f, 8f, stroke)
                label(c, "$v", x + cell / 2f, y + cell / 2f + 9f,
                    if (v < 128) 28f else if (v < 1024) 24f else 19f, WHITE)
            }
        }
        if (g.over) {
            fill.color = Color.BLACK; fill.alpha = 170
            c.drawRect(0f, 0f, 640f, 480f, fill); fill.alpha = 255
            label(c, "GAME OVER", 320f, 210f, 40f, RED)
            label(c, "SCORE ${g.score}", 320f, 250f, 22f, WHITE)
            label(c, "TAP FOR A NEW GAME", 320f, 292f, 16f, GOLD)
        }
        label(c, "SWIPE MOVE · TRIPLE-TAP MENU", 320f, 462f, 13f, DIM)
    }

    // ------------------------------------------------------------ sudoku

    private fun sudoku(c: Canvas) {
        val s = engine.sudoku
        label(c, "SUDOKU", 122f, 44f, 22f, GOLD)
        label(c, "⏱ ${engine.fmtTime(s.elapsed)}", 460f, 44f, 18f, WHITE)
        label(c, "${s.filledCount()}/81", 550f, 44f, 18f, CYAN)
        val cell = 40f
        val bx = 320f - cell * 4.5f; val by = 56f
        // cells + cursor first
        for (i in 0 until 81) {
            val row = i / 9; val col = i % 9
            val x = bx + col * cell; val y = by + row * cell
            if (i == s.cursor && !s.solved) {
                fill.color = GOLD; fill.alpha = 46
                c.drawRect(x, y, x + cell, y + cell, fill); fill.alpha = 255
                stroke.color = GOLD; stroke.strokeWidth = 3f
                c.drawRect(x + 1.5f, y + 1.5f, x + cell - 1.5f, y + cell - 1.5f, stroke)
            }
            val v = s.board[i]
            if (v != 0) {
                val color = when {
                    s.conflicted(i) -> RED
                    s.given[i] -> WHITE
                    else -> CYAN
                }
                label(c, "$v", x + cell / 2f, y + cell / 2f + 8f, 24f, color)
            }
        }
        // grid lines over
        for (k in 0..9) {
            val thick = k % 3 == 0
            stroke.color = if (thick) 0xFF9AA9B8.toInt() else DIM
            stroke.strokeWidth = if (thick) 2.6f else 1f
            c.drawLine(bx + k * cell, by, bx + k * cell, by + 9 * cell, stroke)
            c.drawLine(bx, by + k * cell, bx + 9 * cell, by + k * cell, stroke)
        }
        if (s.pickerOpen) {
            val pw = 40f
            val px = 320f - pw * 5f; val py = 424f
            fill.color = Color.BLACK; fill.alpha = 200
            r.set(px - 8f, py - 8f, px + pw * 10f + 8f, py + pw + 8f)
            c.drawRoundRect(r, 10f, 10f, fill); fill.alpha = 255
            stroke.color = GOLD; stroke.strokeWidth = 2f
            c.drawRoundRect(r, 10f, 10f, stroke)
            for (k in 0 until 10) {
                val x = px + k * pw
                val sel = k == s.pickerSel
                if (sel) {
                    fill.color = GOLD; fill.alpha = 60
                    c.drawRoundRect(RectF(x + 2f, py + 2f, x + pw - 2f, py + pw - 2f), 6f, 6f, fill)
                    fill.alpha = 255
                    stroke.color = GOLD; stroke.strokeWidth = 3f
                } else { stroke.color = DIM; stroke.strokeWidth = 1.2f }
                c.drawRoundRect(RectF(x + 2f, py + 2f, x + pw - 2f, py + pw - 2f), 6f, 6f, stroke)
                label(c, if (k == 9) "⌫" else "${k + 1}", x + pw / 2f, py + pw / 2f + 8f, 22f,
                    if (sel) WHITE else 0xFFB9C2CB.toInt())
            }
            label(c, "SWIPE CHOOSE · TAP SET · DOUBLE-TAP CANCEL", 320f, 420f - 6f, 12f, DIM)
        } else if (s.solved) {
            label(c, "SOLVED!", 320f, 448f, 24f, GREEN)
            label(c, "TAP FOR A NEW PUZZLE", 320f, 470f, 13f, GOLD)
        } else {
            label(c, "SWIPE MOVE · TAP PICK · DOUBLE-TAP MENU", 320f, 462f, 13f, DIM)
        }
    }

    // ------------------------------------------------------------ mines

    private fun mines(c: Canvas) {
        val m = engine.mines
        label(c, "⚑ ${m.minesLeft()}", 140f, 48f, 20f, GOLD)
        label(c, "MINESWEEPER", 320f, 48f, 20f, CYAN)
        label(c, "⏱ ${engine.fmtTime(m.elapsed)}", 510f, 48f, 18f, WHITE)
        val cell = 36f
        val bx = 320f - cell * Mines.W / 2f; val by = 66f
        val numColors = intArrayOf(0, CYAN, GREEN, GOLD, 0xFFCB8CFF.toInt(), RED, RED, RED, RED)
        for (i in 0 until Mines.W * Mines.H) {
            val row = i / Mines.W; val col = i % Mines.W
            val x = bx + col * cell; val y = by + row * cell
            r.set(x + 1f, y + 1f, x + cell - 1f, y + cell - 1f)
            if (!m.open[i]) {
                fill.color = 0xFF18242E.toInt()
                c.drawRoundRect(r, 4f, 4f, fill)
                stroke.color = 0xFF33465A.toInt(); stroke.strokeWidth = 1.2f
                c.drawRoundRect(r, 4f, 4f, stroke)
                if (m.flag[i]) {
                    stroke.color = GOLD; stroke.strokeWidth = 2.2f
                    c.drawLine(x + cell * 0.38f, y + cell * 0.75f, x + cell * 0.38f, y + cell * 0.25f, stroke)
                    fill.color = GOLD
                    val p = android.graphics.Path()
                    p.moveTo(x + cell * 0.38f, y + cell * 0.25f)
                    p.lineTo(x + cell * 0.72f, y + cell * 0.37f)
                    p.lineTo(x + cell * 0.38f, y + cell * 0.5f)
                    p.close(); c.drawPath(p, fill)
                }
            } else {
                stroke.color = 0xFF222A33.toInt(); stroke.strokeWidth = 1f
                c.drawRoundRect(r, 3f, 3f, stroke)
                if (m.mine[i]) {
                    val boom = i == m.boomAt
                    stroke.color = if (boom) RED else 0xFFB9C2CB.toInt(); stroke.strokeWidth = 2.4f
                    val cx = x + cell / 2f; val cy = y + cell / 2f; val rad = cell * 0.26f
                    c.drawCircle(cx, cy, rad * 0.7f, stroke)
                    for (k in 0 until 8) {
                        val a = k * (Math.PI / 4).toFloat()
                        c.drawLine(cx + sin(a) * rad * 0.5f, cy + kotlin.math.cos(a) * rad * 0.5f,
                            cx + sin(a) * rad * 1.25f, cy + kotlin.math.cos(a) * rad * 1.25f, stroke)
                    }
                } else {
                    val n = m.count(i)
                    if (n > 0) label(c, "$n", x + cell / 2f, y + cell / 2f + 7f, 19f, numColors[n.coerceAtMost(8)])
                }
            }
            if (i == m.cursor && !m.dead && !m.won) {
                stroke.color = GOLD; stroke.strokeWidth = 3f
                c.drawRoundRect(RectF(x + 0.5f, y + 0.5f, x + cell - 0.5f, y + cell - 0.5f), 4f, 4f, stroke)
            }
        }
        when {
            m.dead -> {
                label(c, "BOOM — TAP FOR A NEW FIELD", 320f, 428f, 18f, RED)
                label(c, "TRIPLE-TAP MENU", 320f, 452f, 12f, DIM)
            }
            m.won -> {
                label(c, "FIELD CLEARED!", 320f, 428f, 20f, GREEN)
                label(c, "TAP FOR A NEW FIELD · TRIPLE-TAP MENU", 320f, 452f, 12f, GOLD)
            }
            else -> label(c, "TAP REVEAL · DOUBLE-TAP FLAG · TRIPLE-TAP MENU", 320f, 448f, 13f, DIM)
        }
    }
}
