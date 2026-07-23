package com.x3braingames

import com.x3braingames.games.Game2048
import com.x3braingames.games.Mines
import com.x3braingames.games.Sudoku
import kotlin.math.ln

/** Host callbacks into the platform (sounds). */
interface Host {
    fun sfx(id: Int, pitch: Float = 1f, vol: Float = 1f)
}

enum class Screen { MENU, G2048, SUDOKU, MINES }

/**
 * Screen router + rules glue. EVERY state-changing action funnels through
 * [saveCurrent], so the games survive an accidental exit move-for-move; the
 * menu shows a CONTINUE badge wherever a live save is waiting.
 *
 * Input contract (suite-standard): swipe = one discrete step, tap = act,
 * double-tap = the game's secondary act (flag / cancel picker),
 * triple-tap = back to the menu, always.
 */
class Engine(private val store: SettingsStore, private val host: Host) {

    var screen = Screen.MENU; private set
    var menuSel = 0
    var time = 0f; private set
    var banner = ""; private set      // transient overlay text
    var bannerT = 0f; private set

    val g2048 = Game2048()
    val sudoku = Sudoku()
    val mines = Mines()
    var sudokuReady = false; private set   // generated at least once

    // sound ids (match Sfx)
    companion object {
        const val S_TICK = 0; const val S_PLACE = 1; const val S_ERROR = 2
        const val S_BOOM = 3; const val S_FLAG = 4; const val S_WIN = 5
        val MENU_ITEMS = arrayOf("SUDOKU", "2048", "MINESWEEPER")
    }

    fun boot() {
        g2048.best = store.best2048
        store.save2048.takeIf { it.isNotEmpty() }?.let { g2048.restore(it) }
        store.saveSudoku.takeIf { it.isNotEmpty() }?.let { if (sudoku.restore(it)) sudokuReady = true }
        store.saveMines.takeIf { it.isNotEmpty() }?.let { mines.restore(it) }
    }

    fun update(dt: Float) {
        time += dt
        bannerT = (bannerT - dt).coerceAtLeast(0f)
        when (screen) {
            Screen.SUDOKU -> sudoku.tick(dt)
            Screen.MINES -> mines.tick(dt)
            else -> {}
        }
    }

    private fun flash(text: String) { banner = text; bannerT = 2.2f }

    // ------------------------------------------------------------ saving

    private fun saveCurrent() {
        when (screen) {
            Screen.G2048 -> { store.save2048 = g2048.serialize(); store.best2048 = g2048.best }
            Screen.SUDOKU -> store.saveSudoku = sudoku.serialize()
            Screen.MINES -> store.saveMines = mines.serialize()
            Screen.MENU -> {}
        }
    }

    /** Activity onPause: whatever is open gets one last write. */
    fun onAppPause() = saveCurrent()

    fun continueBadge(item: Int): Boolean = when (item) {
        0 -> store.saveSudoku.isNotEmpty() && sudoku.hasProgress()
        1 -> store.save2048.isNotEmpty() && g2048.hasProgress()
        else -> store.saveMines.isNotEmpty() && mines.hasProgress()
    }

    // ------------------------------------------------------------ input

    /** dir: 0=up 1=down 2=left 3=right */
    fun swipe(dir: Int) {
        when (screen) {
            Screen.MENU -> {
                if (dir == 0) menuSel = (menuSel + MENU_ITEMS.size - 1) % MENU_ITEMS.size
                if (dir == 1) menuSel = (menuSel + 1) % MENU_ITEMS.size
                host.sfx(S_TICK, 1.4f, 0.5f)
            }
            Screen.G2048 -> {
                if (g2048.move(dir)) {
                    if (g2048.mergedValues.isEmpty()) host.sfx(S_TICK, 1.1f, 0.6f)
                    else for (v in g2048.mergedValues) {
                        // pitch climbs with the tile: 4→low, 2048→high
                        val p = (0.7f + 0.14f * (ln(v.toFloat()) / ln(2f) - 2f)).coerceIn(0.6f, 2f)
                        host.sfx(S_PLACE, p, 0.9f)
                    }
                    if (g2048.reached2048 && banner.isEmpty()) flash("2048! KEEP GOING")
                    if (g2048.over) { flash("NO MOVES LEFT"); host.sfx(S_ERROR, 0.8f, 0.9f) }
                    saveCurrent()
                } else host.sfx(S_TICK, 0.7f, 0.35f)
            }
            Screen.SUDOKU -> {
                if (sudoku.pickerOpen) {
                    val step = if (dir == 3 || dir == 1) 1 else -1
                    sudoku.pickerSel = (sudoku.pickerSel + step + 10) % 10
                    host.sfx(S_TICK, 1.5f, 0.5f)
                } else {
                    when (dir) {
                        0 -> sudoku.moveCursor(0, -1); 1 -> sudoku.moveCursor(0, 1)
                        2 -> sudoku.moveCursor(-1, 0); else -> sudoku.moveCursor(1, 0)
                    }
                    host.sfx(S_TICK, 1.2f, 0.4f)
                }
            }
            Screen.MINES -> {
                when (dir) {
                    0 -> mines.moveCursor(0, -1); 1 -> mines.moveCursor(0, 1)
                    2 -> mines.moveCursor(-1, 0); else -> mines.moveCursor(1, 0)
                }
                host.sfx(S_TICK, 1.2f, 0.4f)
            }
        }
    }

    fun tap() {
        when (screen) {
            Screen.MENU -> enter(menuSel)
            Screen.G2048 -> if (g2048.over) { g2048.newGame(); saveCurrent(); host.sfx(S_PLACE, 1f, 0.8f) }
            Screen.SUDOKU -> {
                if (sudoku.solved) { newSudoku(); return }
                if (sudoku.pickerOpen) {
                    val changed = sudoku.commit()
                    sudoku.pickerOpen = false
                    if (changed) {
                        if (sudoku.conflicted(sudoku.cursor)) host.sfx(S_ERROR, 1.2f, 0.7f)
                        else host.sfx(S_PLACE, 1.3f, 0.8f)
                        if (sudoku.solved) {
                            flash("SOLVED IN ${fmtTime(sudoku.elapsed)}")
                            store.bestSudoku = sudoku.elapsed.toInt()
                            host.sfx(S_WIN, 1f, 1f)
                        }
                        saveCurrent()
                    }
                } else {
                    if (sudoku.given[sudoku.cursor]) host.sfx(S_ERROR, 1.6f, 0.4f)
                    else {
                        sudoku.pickerOpen = true
                        val cur = sudoku.board[sudoku.cursor]
                        sudoku.pickerSel = if (cur == 0) 4 else cur - 1
                        host.sfx(S_TICK, 1.6f, 0.5f)
                    }
                }
            }
            Screen.MINES -> {
                if (mines.dead || mines.won) { mines.newGame(); saveCurrent(); host.sfx(S_TICK, 1f, 0.7f); return }
                if (mines.reveal()) {
                    when {
                        mines.dead -> { host.sfx(S_BOOM, 1f, 1f); flash("BOOM") }
                        mines.won -> {
                            host.sfx(S_WIN, 1f, 1f)
                            flash("CLEARED IN ${fmtTime(mines.elapsed)}")
                            store.bestMines = mines.elapsed.toInt()
                        }
                        else -> host.sfx(S_PLACE, 1.6f, 0.55f)
                    }
                    saveCurrent()
                } else host.sfx(S_TICK, 0.7f, 0.3f)
            }
        }
    }

    fun doubleTap() {
        when (screen) {
            Screen.SUDOKU -> if (sudoku.pickerOpen) { sudoku.pickerOpen = false; host.sfx(S_TICK, 0.8f, 0.5f) } else toMenu()
            Screen.MINES -> {
                if (mines.toggleFlag()) { host.sfx(S_FLAG, 1.3f, 0.7f); saveCurrent() }
                else host.sfx(S_TICK, 0.7f, 0.3f)
            }
            else -> toMenu()
        }
    }

    fun tripleTap() = toMenu()

    fun backKey(): Boolean {
        if (screen != Screen.MENU) { toMenu(); return true }
        return false
    }

    // ------------------------------------------------------------ flow

    private fun enter(item: Int) {
        host.sfx(S_PLACE, 1f, 0.7f)
        when (item) {
            0 -> {
                if (!sudokuReady || sudoku.solved) newSudoku() else screen = Screen.SUDOKU
            }
            1 -> {
                if (store.save2048.isEmpty() || !g2048.hasProgress() && g2048.cells.all { it == 0 }) g2048.newGame()
                screen = Screen.G2048
                saveCurrent()
            }
            else -> {
                screen = Screen.MINES
                if (store.saveMines.isEmpty()) { mines.newGame(); saveCurrent() }
            }
        }
    }

    private fun newSudoku() {
        sudoku.newGame()
        sudokuReady = true
        screen = Screen.SUDOKU
        saveCurrent()
    }

    private fun toMenu() {
        saveCurrent()
        screen = Screen.MENU
        host.sfx(S_TICK, 0.9f, 0.5f)
    }

    fun fmtTime(sec: Float): String {
        val s = sec.toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }
}
