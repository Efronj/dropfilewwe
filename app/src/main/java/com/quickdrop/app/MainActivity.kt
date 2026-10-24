package com.quickdrop.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat

class MainActivity : ComponentActivity() {
    private val vm: MainVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent { App(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Receives files / text shared from any app's Share sheet. */
    private fun handleShare(i: Intent?) {
        if (i == null) return
        val items = ArrayList<Outgoing>()
        when (i.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                val text = i.getStringExtra(Intent.EXTRA_TEXT)
                if (uri != null) items.add(Outgoing.Media(uri))
                else if (!text.isNullOrBlank()) items.add(Outgoing.Txt(text))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.forEach { items.add(Outgoing.Media(it)) }
            }
        }
        if (items.isNotEmpty()) {
            vm.engine.queueOrSend(items)
            Toast.makeText(this, "Pick a device in QuickDrop to send", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun App(vm: MainVM) {
    val ctx = LocalContext.current
    val perms = remember { nearbyPerms() }
    var granted by remember { mutableStateOf(hasAll(ctx, perms)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAll(ctx, perms)
    }
    LaunchedEffect(granted) { if (granted) vm.engine.start() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = Color.Transparent, contentColor = Color.White) {
            GlassBackground {
                if (granted) MainShell(vm) else PermissionScreen { launcher.launch(perms) }
            }
        }
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        GlassCard {
            Text("📡", fontSize = 44.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Allow nearby access",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "QuickDrop uses Bluetooth and Wi-Fi to find phones around you and move files at full speed. Nothing goes through the internet.",
                color = QD.Text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            GlassButton("Allow", Modifier.fillMaxWidth(), accent = QD.Blue) { onAllow() }
        }
    }
}

@Composable
private fun MainShell(vm: MainVM) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.engine.events.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (tab) {
                0 -> ShareScreen(vm)
                1 -> HistoryScreen(vm)
                else -> ToolsScreen(vm)
            }
        }
        GlassNav(
            tab,
            { tab = it },
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
        IncomingDialog(vm)
    }
}

@Composable
private fun GlassNav(tab: Int, onTab: (Int) -> Unit, modifier: Modifier) {
    val items = listOf("📡" to "Share", "🕘" to "History", "🧰" to "Tools")
    Row(
        modifier.fillMaxWidth().glass(34.dp, strength = 1.5f).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEachIndexed { i, pair ->
            val sel = i == tab
            Column(
                Modifier
                    .weight(1f)
                    .pressable { onTab(i) }
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (sel) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(pair.first, fontSize = 20.sp)
                Text(
                    pair.second,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sel) Color.White else QD.Text2,
                )
            }
        }
    }
}
