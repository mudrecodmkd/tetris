package com.nblox.offline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.random.Random

class GameEngine {
  companion object { const val W = 10; const val H = 20 }

  private val colors = mapOf(
    'I' to Color(0xFF74C0E8),
    'J' to Color(0xFF5179C9),
    'L' to Color(0xFFF0A03E),
    'O' to Color(0xFFF3D26B),
    'S' to Color(0xFF75C36A),
    'T' to Color(0xFF9A63C8),
    'Z' to Color(0xFFD25C67),
  )

  private val shapes = mapOf(
    'Z' to arrayOf(intArrayOf(1,1,0), intArrayOf(0,1,1)),
    'S' to arrayOf(intArrayOf(0,1,1), intArrayOf(1,1,0)),
    'I' to arrayOf(intArrayOf(1,1,1,1)),
    'J' to arrayOf(intArrayOf(1,0,0), intArrayOf(1,1,1)),
    'L' to arrayOf(intArrayOf(0,0,1), intArrayOf(1,1,1)),
    'O' to arrayOf(intArrayOf(1,1), intArrayOf(1,1)),
    'T' to arrayOf(intArrayOf(0,1,0), intArrayOf(1,1,1)),
  )
  private val types = shapes.keys.toList()

  private fun clone(m: Array<IntArray>) = Array(m.size){ y -> m[y].clone() }
  private fun rot(m: Array<IntArray>): Array<IntArray> {
    val R = m.size; val C = m[0].size
    val r = Array(C){ IntArray(R) }
    for (y in 0 until R) for (x in 0 until C) r[x][R-1-y] = m[y][x]
    return r
  }

  private fun emptyBoard() = Array(H){ IntArray(W) }
  private fun newBag(): MutableList<Char> = (types + types).shuffled().toMutableList()

  var board = emptyBoard()
  var cur: Piece? = null
  var next = mutableListOf<Piece>()
  private var bag = newBag()

  var level = 1
  var score = 0
  var lines = 0
  private var dropMs = 800f
  private var elapsed = 0f

  data class Piece(var t: Char, var m: Array<IntArray>, var x: Int, var y: Int)

  init { reset() }

  fun reset() {
    board = emptyBoard()
    level = 1; score = 0; lines = 0; dropMs = 800f; elapsed = 0f
    next.clear(); bag = newBag(); cur = null
    spawn()
  }

  private fun newPiece(t: Char? = null): Piece {
    val tt = t ?: run { if (bag.isEmpty()) bag = newBag(); bag.removeLast() }
    val m = clone(shapes[tt]!!)
    return Piece(tt, m, W/2 - m[0].size/2, -2)
  }

  private fun collides(p: Piece, ox: Int = 0, oy: Int = 0, mat: Array<IntArray> = p.m, g: Array<IntArray> = board): Boolean {
    for (y in mat.indices) for (x in mat[0].indices) {
      if (mat[y][x] == 0) continue
      val nx = p.x + x + ox; val ny = p.y + y + oy
      if (ny >= H || nx < 0 || nx >= W) return true
      if (ny >= 0 && g[ny][nx] != 0) return true
    }
    return false
  }

  private fun merge(p: Piece) {
    for (y in p.m.indices) for (x in p.m[0].indices) if (p.m[y][x] != 0 && p.y + y >= 0) board[p.y + y][p.x + x] = p.t.code
  }

  private fun clearLines() {
    var c = 0; var y = H - 1
    while (y >= 0) {
      if (board[y].all { it != 0 }) {
        for (yy in y downTo 1) board[yy] = board[yy-1].clone()
        board[0] = IntArray(W); c++
      } else y--
    }
    if (c > 0) {
      val table = mapOf(1 to 40, 2 to 100, 3 to 300, 4 to 1200)
      score += (table[c] ?: 0) * level
      lines += c
      level = 1 + (lines / 10)
      dropMs = maxOf(90f, 800f - (level - 1) * 35f)
    }
  }

  private fun spawn() {
    cur = if (next.isNotEmpty()) next.removeAt(0) else newPiece()
    while (next.size < 3) next.add(newPiece())
    if (collides(cur!!)) reset()
  }

  fun tick(human: Boolean, goal: Int, skill: Int, reaction: Int, error: Int, style: Int) {
    val react = when (reaction) { 0 -> 1.5f; 2 -> 0.7f; else -> 1.0f }
    elapsed += 16f * react
    if (elapsed > dropMs) {
      elapsed = 0f
      cur?.let {
        if (!collides(it, 0, 1)) it.y++
        else { merge(it); clearLines(); spawn() }
      }
    }
    if (human) aiThink(goal, skill, error, style)
  }

  // player actions
  fun move(dx: Int) { cur?.let { if (!collides(it, dx, 0)) it.x += dx } }
  fun rotate() { cur?.let { val r = rot(it.m); if (!collides(it, 0, 0, r)) it.m = r } }
  fun softDrop() { cur?.let { if (!collides(it, 0, 1)) it.y++ } }
  fun hardDrop() {
    var d = 0
    cur?.let {
      while (!collides(it, 0, 1)) { it.y++; d++ }
      score += 2 * d
      merge(it); clearLines(); spawn()
    }
  }

  // Drawing
  fun drawBoard(ds: DrawScope) {
    val cell = ds.size.minDimension / 20f
    // bg
    ds.drawRect(Color(0xFFC3D5EC))
    // grid
    for (y in 0 until H) for (x in 0 until W) {
      drawCell(ds, x, y, null, cell, grid = true)
      val v = board[y][x]
      if (v != 0) drawCell(ds, x, y, Char(v), cell)
    }
    // ghost
    cur?.let { p ->
      var gy = p.y
      while (!collides(p, 0, gy - p.y + 1)) gy++
      for (y in p.m.indices) for (x in p.m[0].indices)
        if (p.m[y][x] != 0 && gy + y >= 0) drawCell(ds, p.x + x, gy + y, p.t, cell, alpha = 0.25f)
    }
    // current
    cur?.let { p ->
      for (y in p.m.indices) for (x in p.m[0].indices)
        if (p.m[y][x] != 0 && p.y + y >= 0) drawCell(ds, p.x + x, p.y + y, p.t, cell)
    }
  }

  fun drawNext(ds: DrawScope) {
    val cell = 18f
    var off = 0
    next.take(3).forEach { p ->
      for (y in p.m.indices) for (x in p.m[0].indices)
        if (p.m[y][x] != 0) {
          val ox = (x + off + 1) * cell; val oy = (y + 1) * cell
          ds.drawRect(colors[p.t]!!, topLeft = Offset(ox, oy), size = Size(cell - 3f, cell - 3f))
        }
      off += 4
    }
  }

  private fun drawCell(ds: DrawScope, x: Int, y: Int, t: Char?, cell: Float, alpha: Float = 1f, grid: Boolean = false) {
    val px = x * cell; val py = y * cell
    // cell frame
    if (grid) {
      ds.drawRect(Color(0xFFB7C6DB), topLeft = Offset(px, py), size = Size(cell, cell), style = Stroke(width = 1f))
    }
    if (t != null) {
      val c = colors[t] ?: Color(0xFF888888)
      ds.drawRect(c.copy(alpha = alpha), topLeft = Offset(px + 1, py + 1), size = Size(cell - 2, cell - 2))
    }
  }

  // --- Human-like AI (simple, safe) ---
  private fun cloneBoard() = Array(H){ y -> board[y].clone() }
  private fun holes(g: Array<IntArray>): Int {
    var h=0
    for (x in 0 until W) {
      var seen=false
      for (y in 0 until H) {
        if (g[y][x]!=0) seen=true else if (seen) h++
      }
    }
    return h
  }
  private fun heights(g: Array<IntArray>): IntArray {
    val hts = IntArray(W)
    for (x in 0 until W) {
      for (y in 0 until H) if (g[y][x]!=0) { hts[x] = H-y; break }
    }
    return hts
  }
  private fun bumpiness(g: Array<IntArray>): Int {
    val h = heights(g)
    var b=0
    for (x in 0 until W-1) b += abs(h[x]-h[x+1])
    return b
  }
  private fun simPlace(g: Array<IntArray>, p: Piece, px: Int, m: Array<IntArray>): Triple<Array<IntArray>, Int, Boolean> {
    var py = p.y
    while (!collides(Piece(p.t, m, px, py), 0, 1, m, g)) py++
    val gg = Array(H){ g[it].clone() }
    for (y in m.indices) for (x in m[0].indices) if (m[y][x]!=0 && py+y>=0) gg[py+y][px+x]=p.t.code
    var cleared = 0; var yy = H-1
    while (yy>=0) {
      if (gg[yy].all{it!=0}) { for (k in yy downTo 1) gg[k]=gg[k-1].clone(); gg[0]=IntArray(W); cleared++ }
      else yy--
    }
    // "no-lock" safeguard: avoid sealing deep wells
    val safe = holes(gg) <= holes(g) + 2
    return Triple(gg, cleared, safe)
  }
  private fun bestMove(g: Array<IntArray>, p: Piece, goal: Int, style: Int): Triple<Int, Int, Array<IntArray>>? {
    var bestScore = Int.MIN_VALUE
    var bestDx = 0; var bestRot = 0; var bestM = p.m
    var pm = p.m
    for (r in 0 until 4) {
      val w = pm[0].size
      for (x in -2 .. W - w + 2) {
        if (collides(Piece(p.t, pm, x, p.y), 0, 0, pm, g)) continue
        val (gg, cleared, safe) = simPlace(g, p, x, pm)
        if (!safe) continue
        val score =
          cleared * if (goal==4 && style==2) 220 else if (cleared>=goal) 160 else 60 -
          holes(gg)*12 - bumpiness(gg)*3 - heights(gg).sum()
        if (score > bestScore) {
          bestScore = score; bestDx = x - p.x; bestRot = r; bestM = pm
        }
      }
      pm = rot(pm)
    }
    if (bestScore==Int.MIN_VALUE) return null
    return Triple(bestDx, bestRot, bestM)
  }
  private var aiDelay = 0
  fun aiThink(goal: Int, skill: Int, error: Int, style: Int) {
    val p = cur ?: return
    // Human reaction pacing
    if (aiDelay > 0) { aiDelay--; return }
    aiDelay = when (skill) { 0 -> 5; 2 -> 2; else -> 3 } // frames between actions

    val move = bestMove(board, p, goal, style) ?: return
    var dx = move.first; var rotCnt = move.second
    // Imperfection
    if (Random.nextInt(100) < when (error) { 2 -> 9; 0 -> 2; else -> 5 }) {
      // small jitter
      dx += listOf(-1,0,1).random()
    }
    if (rotCnt>0) rotate()
    else if (dx<0) move(-1)
    else if (dx>0) move(1)
    else hardDrop()
  }
}