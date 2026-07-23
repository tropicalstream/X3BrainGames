package com.x3braingames.games

import kotlin.random.Random

/**
 * Minesweeper — 12×9, 14 mines. The first reveal is always safe (mines are
 * laid only after it, excluding its whole neighborhood), zeros flood open,
 * and every reveal/flag is serialized so a mid-game exit costs nothing.
 */
class Mines {
    companion object { const val W = 12; const val H = 9; const val MINES = 14 }

    val mine = BooleanArray(W * H)
    val open = BooleanArray(W * H)
    val flag = BooleanArray(W * H)
    var cursor = W * H / 2 + W / 2
    var started = false; private set   // mines laid?
    var dead = false; private set
    var won = false; private set
    var elapsed = 0f
    var boomAt = -1                    // the mine that ended it, for the renderer

    fun newGame() {
        mine.fill(false); open.fill(false); flag.fill(false)
        started = false; dead = false; won = false
        elapsed = 0f; boomAt = -1
        cursor = W * H / 2 + W / 2
    }

    fun tick(dt: Float) { if (started && !dead && !won) elapsed += dt }

    fun moveCursor(dx: Int, dy: Int) {
        val r = (cursor / W + dy + H) % H
        val c = (cursor % W + dx + W) % W
        cursor = r * W + c
    }

    fun minesLeft(): Int = MINES - flag.count { it }

    fun neighbors(i: Int): List<Int> {
        val r = i / W; val c = i % W
        val out = ArrayList<Int>(8)
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val rr = r + dr; val cc = c + dc
            if (rr in 0 until H && cc in 0 until W) out.add(rr * W + cc)
        }
        return out
    }

    fun count(i: Int): Int = neighbors(i).count { mine[it] }

    /** Reveal at the cursor. Returns true if the state changed. */
    fun reveal(): Boolean {
        val i = cursor
        if (dead || won || open[i] || flag[i]) return false
        if (!started) layMines(i)
        if (mine[i]) {
            dead = true; boomAt = i
            for (k in 0 until W * H) if (mine[k]) open[k] = true
            return true
        }
        flood(i)
        checkWin()
        return true
    }

    fun toggleFlag(): Boolean {
        val i = cursor
        if (dead || won || open[i]) return false
        flag[i] = !flag[i]
        return true
    }

    private fun layMines(safe: Int) {
        started = true
        val excluded = HashSet<Int>(neighbors(safe)); excluded.add(safe)
        val spots = (0 until W * H).filter { it !in excluded }.shuffled(Random(System.nanoTime()))
        for (k in 0 until MINES) mine[spots[k]] = true
    }

    private fun flood(start: Int) {
        val stack = ArrayDeque<Int>()
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (open[i] || flag[i]) continue
            open[i] = true
            if (count(i) == 0) neighbors(i).forEach { if (!open[it]) stack.addLast(it) }
        }
    }

    private fun checkWin() {
        if ((0 until W * H).all { open[it] || mine[it] }) won = true
    }

    fun serialize(): String =
        pack(mine) + "|" + pack(open) + "|" + pack(flag) + "|" +
            (if (started) 1 else 0) + "|" + (if (dead) 1 else 0) + "|" + (if (won) 1 else 0) + "|" +
            elapsed.toInt() + "|" + cursor + "|" + boomAt

    fun restore(s: String): Boolean = runCatching {
        val p = s.split("|")
        unpack(p[0], mine); unpack(p[1], open); unpack(p[2], flag)
        started = p[3] == "1"; dead = p[4] == "1"; won = p[5] == "1"
        elapsed = p[6].toFloat(); cursor = p[7].toInt().coerceIn(0, W * H - 1)
        boomAt = p[8].toInt()
        true
    }.getOrDefault(false)

    private fun pack(a: BooleanArray) = a.joinToString("") { if (it) "1" else "0" }
    private fun unpack(s: String, into: BooleanArray) {
        require(s.length == into.size)
        for (i in into.indices) into[i] = s[i] == '1'
    }

    fun hasProgress(): Boolean = started && !dead && !won
}
