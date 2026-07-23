package com.x3braingames.games

import kotlin.random.Random

/**
 * 2048 — the swipe-native classic. Standard rules: a swipe slides every tile,
 * equal neighbors merge once per move, a new 2 (or 4, 10%) appears after any
 * move that changed the board. The whole state round-trips through
 * [serialize]/[restore] so every move survives an accidental exit.
 */
class Game2048 {
    val cells = IntArray(16)          // 0 = empty, else the tile value
    var score = 0; private set
    var best = 0
    var over = false; private set
    var reached2048 = false           // one-time banner latch
    val mergedValues = ArrayList<Int>()   // merges from the LAST move (for sound)

    fun newGame() {
        cells.fill(0); score = 0; over = false; reached2048 = false
        spawn(); spawn()
    }

    /** dir: 0=up 1=down 2=left 3=right. Returns true if the board changed. */
    fun move(dir: Int): Boolean {
        if (over) return false
        mergedValues.clear()
        var changed = false
        for (lane in 0 until 4) {
            val idx = laneIndices(dir, lane)
            val vals = idx.map { cells[it] }.filter { it != 0 }.toMutableList()
            // merge adjacent equals once, front to back
            var i = 0
            val out = ArrayList<Int>(4)
            while (i < vals.size) {
                if (i + 1 < vals.size && vals[i] == vals[i + 1]) {
                    val v = vals[i] * 2
                    out.add(v); score += v; mergedValues.add(v)
                    if (v >= 2048) reached2048 = true
                    i += 2
                } else { out.add(vals[i]); i++ }
            }
            while (out.size < 4) out.add(0)
            for (k in 0 until 4) {
                if (cells[idx[k]] != out[k]) { cells[idx[k]] = out[k]; changed = true }
            }
        }
        if (changed) {
            if (score > best) best = score
            spawn()
            over = !anyMoveLeft()
        }
        return changed
    }

    private fun laneIndices(dir: Int, lane: Int): IntArray = when (dir) {
        0 -> intArrayOf(lane, lane + 4, lane + 8, lane + 12)            // up: read top→bottom
        1 -> intArrayOf(lane + 12, lane + 8, lane + 4, lane)            // down
        2 -> intArrayOf(lane * 4, lane * 4 + 1, lane * 4 + 2, lane * 4 + 3)  // left
        else -> intArrayOf(lane * 4 + 3, lane * 4 + 2, lane * 4 + 1, lane * 4) // right
    }

    private fun spawn() {
        val empty = (0 until 16).filter { cells[it] == 0 }
        if (empty.isEmpty()) return
        cells[empty.random()] = if (Random.nextFloat() < 0.9f) 2 else 4
    }

    private fun anyMoveLeft(): Boolean {
        if (cells.any { it == 0 }) return true
        for (r in 0 until 4) for (c in 0 until 4) {
            val v = cells[r * 4 + c]
            if (c < 3 && cells[r * 4 + c + 1] == v) return true
            if (r < 3 && cells[(r + 1) * 4 + c] == v) return true
        }
        return false
    }

    fun serialize(): String =
        cells.joinToString(",") + "|" + score + "|" + (if (over) 1 else 0) + "|" + (if (reached2048) 1 else 0)

    fun restore(s: String): Boolean = runCatching {
        val p = s.split("|")
        val cs = p[0].split(",").map { it.toInt() }
        require(cs.size == 16)
        for (i in 0 until 16) cells[i] = cs[i]
        score = p[1].toInt(); over = p[2] == "1"; reached2048 = p[3] == "1"
        true
    }.getOrDefault(false)

    fun hasProgress(): Boolean = cells.any { it != 0 } && !over
}
