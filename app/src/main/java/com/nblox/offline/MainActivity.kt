package com.nblox.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { App() }
  }
}

@Composable
fun App() {
  MaterialTheme {
    GameScreen()
  }
}

@Composable
fun StatBox(title: String, value: String) {
  Column(
    Modifier
      .background(Color(0xFFB6CBE7), shape = RoundedCornerShape(8.dp))
      .padding(8.dp)
  ) {
    Text(title, color = Color(0xFF5B6B7C), fontSize = 12.sp)
    Text(value, color = Color(0xFF1F3145), fontSize = 22.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun GameScreen() {
  val eng = remember { GameEngine() }
  var paused by remember { mutableStateOf(false) }
  var showOptions by remember { mutableStateOf(true) } // Options only before Play
  var showHelp by remember { mutableStateOf(false) }

  // AI options (visible only in Options menu)
  var targetLines by remember { mutableStateOf(2) }
  var skill by remember { mutableStateOf(1) } // 0 easy,1 normal,2 hard
  var reaction by remember { mutableStateOf(1) } // 0 slow,1 normal,2 fast
  var errorChance by remember { mutableStateOf(1) } // 0 low,1 med,2 high
  var style by remember { mutableStateOf(0) } // 0 balanced,1 aggressive,2 tetris-hunter

  // human-like AI defaults (active)
  var aiOn by remember { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    while (isActive) {
      if (!paused && !showOptions) {
        eng.tick(human = aiOn, goal = targetLines, skill = skill, reaction = reaction, error = errorChance, style = style)
      }
      awaitFrame()
    }
  }

  Column(Modifier.fillMaxSize().background(Color(0xFFD7E6F5)).padding(12.dp)) {
    // Top
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      // Next preview
      Canvas(Modifier.size(160.dp, 100.dp).background(Color(0xFFB6CBE7), RoundedCornerShape(8.dp)).padding(6.dp)) {
        eng.drawNext(this)
      }
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          StatBox("level", eng.level.toString())
          StatBox("score", eng.score.toString())
          StatBox("lines", eng.lines.toString())
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("· pause ·", color = Color(0xFF5B6B7C))
          Text("TETRIS  n-blox", color = Color(0xFFF6C156), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.pointerInput(Unit) {
              detectTapGestures(onLongPress = { aiOn = !aiOn })
            }
          )
        }
      }
    }

    Spacer(Modifier.height(10.dp))

    // Board + overlays
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Canvas(
        Modifier
          .size(300.dp, 600.dp)
          .background(Color(0xFFC3D5EC), RoundedCornerShape(10.dp))
          .pointerInput(paused) {
            detectTapGestures(onDoubleTap = { if (paused) paused = false })
          }
      ) { eng.drawBoard(this) }

      if (paused && !showOptions) {
        Card { Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text("paused", fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(6.dp))
          Button(onClick = { paused = false }) { Text("resume") }
          Spacer(Modifier.height(4.dp))
          Button(onClick = { showHelp = true }) { Text("help") }
          Spacer(Modifier.height(4.dp))
          Button(onClick = { eng.reset(); showOptions = true; paused = false }) { Text("quit") }
        } }
      }

      if (showOptions) {
        Card { Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text("options", fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(8.dp))
          // AI settings (only here)
          Text("AI target lines")
          Row { listOf(1,2,4,10).forEach { v ->
            Button(onClick = { targetLines = v }, content = { Text("$v") }, modifier = Modifier.padding(2.dp))
          } }
          Spacer(Modifier.height(6.dp))
          Text("AI skill")
          Row { listOf("Easy","Normal","Hard").forEachIndexed { i, s ->
            Button(onClick = { skill = i }, content = { Text(s) }, modifier = Modifier.padding(2.dp))
          } }
          Spacer(Modifier.height(6.dp))
          Text("Reaction")
          Row { listOf("Slow","Normal","Fast").forEachIndexed { i, s ->
            Button(onClick = { reaction = i }, content = { Text(s) }, modifier = Modifier.padding(2.dp))
          } }
          Spacer(Modifier.height(6.dp))
          Text("Error chance")
          Row { listOf("Low","Medium","High").forEachIndexed { i, s ->
            Button(onClick = { errorChance = i }, content = { Text(s) }, modifier = Modifier.padding(2.dp))
          } }
          Spacer(Modifier.height(6.dp))
          Text("Play style")
          Row { listOf("Balanced","Aggressive","Tetris").forEachIndexed { i, s ->
            Button(onClick = { style = i }, content = { Text(s) }, modifier = Modifier.padding(2.dp))
          } }
          Spacer(Modifier.height(10.dp))
          Button(onClick = { eng.reset(); showOptions = false; paused = false }) { Text("play") }
        } }
      }
    }

    Spacer(Modifier.height(8.dp))

    // Controls
    if (!showOptions) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Button(onClick = { paused = !paused }) { Text("Start / Pause") }
      }
      Spacer(Modifier.height(6.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Button(onClick = { eng.move(-1) }) { Text("◀") }
        Button(onClick = { eng.rotate() }) { Text("↻") }
        Button(onClick = { eng.move(1) }) { Text("▶") }
      }
      Spacer(Modifier.height(6.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Button(onClick = { eng.softDrop() }) { Text("▼") }
        Button(onClick = { eng.hardDrop() }) { Text("⤓") }
      }
    }
  }

  if (showHelp) {
    AlertDialog(onDismissRequest = { showHelp = false },
      confirmButton = { TextButton({ showHelp = false }) { Text("OK") } },
      title = { Text("help") },
      text = { Text("Move ◀ ▶, rotate ↻, soft drop ▼, hard drop ⤓. Long‑press the title to toggle AI.") }
    )
  }
}