package com.x3braingames.games

import kotlin.random.Random

/**
 * Sudoku — the world's pick-up-and-go logic game. A fresh puzzle is generated
 * on demand: a full solution by randomized backtracking, then cells dug out
 * one by one only while the puzzle keeps a UNIQUE solution (checked with a
 * counting solver), landing around 36-40 givens — a comfortable mid weight.
 * Cursor + number picker fit the glasses' swipe-step / tap controls, and the
 * whole board state round-trips per move for the autosave.
 */
class Sudoku {
    val board = IntArray(81)          // current entries, 0 = empty
    val given = BooleanArray(81)      // clue cells (locked)
    val solution = IntArray(81)
    var cursor = 40                   // start centered
    var elapsed = 0f
    var solved = false; private set
    var pickerOpen = false
    var pickerSel = 0                 // 0..8 = digits 1..9, 9 = erase

    // ------------------------------------------------------------ lifecycle

    fun newGame(rng: Random = Random(System.currentTimeMillis())) {
        generateSolution(rng)
        dig(rng)
        elapsed = 0f; solved = false; cursor = 40; pickerOpen = false
    }

    fun tick(dt: Float) { if (!solved) elapsed += dt }

    // ------------------------------------------------------------ moves

    fun moveCursor(dx: Int, dy: Int) {
        val r = (cursor / 9 + dy + 9) % 9
        val c = (cursor % 9 + dx + 9) % 9
        cursor = r * 9 + c
    }

    /** Commit the picker's choice at the cursor. Returns true if it changed. */
    fun commit(): Boolean {
        if (given[cursor] || solved) return false
        val v = if (pickerSel == 9) 0 else pickerSel + 1
        if (board[cursor] == v) return false
        board[cursor] = v
        if ((0 until 81).all { board[it] != 0 } && (0 until 81).none { conflicted(it) }) solved = true
        return true
    }

    /** Is this cell in conflict with its row/col/box right now? */
    fun conflicted(i: Int): Boolean {
        val v = board[i]
        if (v == 0) return false
        val r = i / 9; val c = i % 9
        for (k in 0 until 9) {
            if (k != c && board[r * 9 + k] == v) return true
            if (k != r && board[k * 9 + c] == v) return true
        }
        val br = r / 3 * 3; val bc = c / 3 * 3
        for (rr in br until br + 3) for (cc in bc until bc + 3) {
            val j = rr * 9 + cc
            if (j != i && board[j] == v) return true
        }
        return false
    }

    fun filledCount() = (0 until 81).count { board[it] != 0 }

    // ------------------------------------------------------------ generator

    private fun generateSolution(rng: Random) {
        solution.fill(0)
        fill(0, rng)
        for (i in 0 until 81) { board[i] = solution[i]; given[i] = true }
    }

    private fun fill(pos: Int, rng: Random): Boolean {
        if (pos == 81) return true
        if (solution[pos] != 0) return fill(pos + 1, rng)
        val digits = (1..9).shuffled(rng)
        for (d in digits) {
            if (fits(solution, pos, d)) {
                solution[pos] = d
                if (fill(pos + 1, rng)) return true
                solution[pos] = 0
            }
        }
        return false
    }

    private fun fits(g: IntArray, i: Int, v: Int): Boolean {
        val r = i / 9; val c = i % 9
        for (k in 0 until 9) {
            if (g[r * 9 + k] == v || g[k * 9 + c] == v) return false
        }
        val br = r / 3 * 3; val bc = c / 3 * 3
        for (rr in br until br + 3) for (cc in bc until bc + 3) if (g[rr * 9 + cc] == v) return false
        return true
    }

    /** Remove cells while the puzzle stays uniquely solvable. */
    private fun dig(rng: Random) {
        val order = (0 until 81).shuffled(rng)
        var removed = 0
        for (i in order) {
            if (removed >= 45) break
            val keep = board[i]
            board[i] = 0
            if (countSolutions(board.copyOf(), 0, 2) != 1) {
                board[i] = keep          // ambiguity — put the clue back
            } else {
                given[i] = false
                removed++
            }
        }
    }

    private fun countSolutions(g: IntArray, pos: Int, cap: Int): Int {
        var p = pos
        while (p < 81 && g[p] != 0) p++
        if (p == 81) return 1
        var found = 0
        for (d in 1..9) {
            if (fits(g, p, d)) {
                g[p] = d
                found += countSolutions(g, p + 1, cap - found)
                g[p] = 0
                if (found >= cap) return found
            }
        }
        return found
    }

    // ------------------------------------------------------------ save

    fun serialize(): String =
        board.joinToString(",") + "|" +
            given.joinToString(",") { if (it) "1" else "0" } + "|" +
            solution.joinToString(",") + "|" +
            elapsed.toInt() + "|" + (if (solved) 1 else 0) + "|" + cursor

    fun restore(s: String): Boolean = runCatching {
        val p = s.split("|")
        val b = p[0].split(",").map { it.toInt() }; require(b.size == 81)
        val g = p[1].split(",").map { it == "1" }; require(g.size == 81)
        val sol = p[2].split(",").map { it.toInt() }; require(sol.size == 81)
        for (i in 0 until 81) { board[i] = b[i]; given[i] = g[i]; solution[i] = sol[i] }
        elapsed = p[3].toFloat(); solved = p[4] == "1"; cursor = p[5].toInt().coerceIn(0, 80)
        pickerOpen = false
        true
    }.getOrDefault(false)

    fun hasProgress(): Boolean = !solved
}
