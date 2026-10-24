package com.quickdrop.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

private fun copyText(ctx: Context, s: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("QuickDrop", s))
    toast(ctx, "Copied")
}

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = modifier)
}

// ═════════════════════════════ SHARE ═════════════════════════════

@Composable
fun ShareScreen(vm: MainVM) {
    val e = vm.engine
    val ctx = LocalContext.current
    val peers by e.peers.collectAsStateWithLifecycle()
    val connected by e.connected.collectAsStateWithLifecycle()
    val transfers by e.transfers.collectAsStateWithLifecycle()
    val status by e.status.collectAsStateWithLifecycle()
    val pending by e.pending.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(false) }
    var textDialog by remember { mutableStateOf(false) }

    val media = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        e.sendUris(uris)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        e.sendUris(uris)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 120.dp)
    ) {
        Title("QuickDrop")
        Text(status, fontSize = 14.sp, color = QD.Text2)
        Spacer(Modifier.height(16.dp))

        if (pending.isNotEmpty()) {
            GlassCard(tint = QD.Mint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${pending.size} item(s) ready · " +
                            if (connected == null) "tap a device to send" else "sending…",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text("✕", color = QD.Text2, fontSize = 20.sp, modifier = Modifier.pressable { e.clearPending() }.padding(8.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        val c = connected
        if (c == null) {
            Radar(peers) { e.connect(it) }
            Text(
                if (peers.isEmpty()) "Open QuickDrop on the other phone — it will show up here."
                else "Tap a device to connect",
                color = QD.Text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            GlassCard(tint = QD.Mint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(QD.Mint))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Connected", fontSize = 12.sp, color = QD.Text2)
                        Text(c.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    GlassButton("Disconnect") { e.disconnect() }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SendOrb { sheet = true }
            }
            Text(
                "Both phones can send — tap Send to share photos, videos, files or text.",
                color = QD.Text2,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (transfers.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            for (t in transfers.values) {
                TransferRow(t) { e.cancel(t.id) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (sheet) {
        SendSheet(
            onDismiss = { sheet = false },
            onMedia = {
                sheet = false
                media.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onFiles = {
                sheet = false
                files.launch(arrayOf("*/*"))
            },
            onClipboard = {
                sheet = false
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val txt = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                if (txt.isNullOrBlank()) toast(ctx, "Clipboard is empty") else e.sendText(txt)
            },
            onText = {
                sheet = false
                textDialog = true
            },
        )
    }
    if (textDialog) {
        TextDialog(onDismiss = { textDialog = false }) { e.sendText(it) }
    }
}

@Composable
private fun TransferRow(t: Transfer, onCancel: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (t.outgoing) "⬆️" else "⬇️", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (t.state) {
                        1 -> "Done · ${fmtSize(t.total)}"
                        2 -> "Failed"
                        3 -> "Canceled"
                        else -> "${fmtSpeed(t.speed)} ${fmtEta(t.total, t.done, t.speed)}"
                    },
                    fontSize = 12.sp,
                    color = QD.Text2,
                )
            }
            if (t.active) {
                Text("✕", fontSize = 20.sp, color = QD.Text2, modifier = Modifier.pressable(onCancel).padding(8.dp))
            }
        }
        if (t.active) {
            Spacer(Modifier.height(10.dp))
            GlassProgress(t.fraction)
        }
    }
}

@Composable
private fun SendSheet(
    onDismiss: () -> Unit,
    onMedia: () -> Unit,
    onFiles: () -> Unit,
    onClipboard: () -> Unit,
    onText: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xE61B1F3F))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(32.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Send", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            SheetRow("📷", "Photos & videos", onMedia)
            SheetRow("📁", "Files", onFiles)
            SheetRow("📋", "Paste clipboard", onClipboard)
            SheetRow("✍️", "Write text", onText)
        }
    }
}

@Composable
private fun SheetRow(icon: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).glass(20.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TextDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xE61B1F3F))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(32.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Write text", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            GlassField(text, { text = it }, "Type a message, link or note…", Modifier.height(140.dp), singleLine = false)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Cancel", Modifier.weight(1f)) { onDismiss() }
                GlassButton("Send", Modifier.weight(1f), accent = QD.Blue) {
                    onSend(text)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun IncomingDialog(vm: MainVM) {
    val e = vm.engine
    val inc by e.incoming.collectAsStateWithLifecycle()
    val i = inc ?: return
    Dialog(onDismissRequest = { e.declineIncoming() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xE61B1F3F))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(32.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("📲", fontSize = 40.sp)
            Text("${i.name} wants to connect", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Text("Check this code matches on both phones", fontSize = 13.sp, color = QD.Text2, textAlign = TextAlign.Center)
            Text(i.digits, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = QD.Mint, letterSpacing = 6.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Decline", Modifier.weight(1f)) { e.declineIncoming() }
                GlassButton("Accept", Modifier.weight(1f), accent = QD.Blue) { e.acceptIncoming(false) }
            }
            GlassButton("Accept & always trust", Modifier.fillMaxWidth(), accent = QD.Violet) { e.acceptIncoming(true) }
        }
    }
}

// ═════════════════════════════ HISTORY ═════════════════════════════

private fun openItem(ctx: Context, h: HistoryItem) {
    if (h.text != null) {
        copyText(ctx, h.text)
        return
    }
    val u = h.uri ?: return
    try {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(u), h.mime.ifEmpty { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (e: Exception) {
        toast(ctx, "Can't open this item")
    }
}

@Composable
fun HistoryScreen(vm: MainVM) {
    val ctx = LocalContext.current
    val list by vm.engine.history.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("History", Modifier.weight(1f))
            if (list.isNotEmpty()) GlassButton("Clear") { vm.engine.clearHistory() }
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing here yet.\nSent and received items will show up here.",
                    color = QD.Text2,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list) { h -> HistoryRow(h) { openItem(ctx, h) } }
            }
        }
    }
}

@Composable
private fun HistoryRow(h: HistoryItem, onClick: () -> Unit) {
    val isText = h.text != null
    Row(
        Modifier.fillMaxWidth().pressable(onClick).glass(22.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(iconFor(h.mime, isText), fontSize = 26.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isText) h.text.orEmpty() else h.name,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (isText) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            val ago = DateUtils.getRelativeTimeSpanString(h.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            Text(
                "${if (h.outgoing) "Sent to" else "From"} ${h.peer} · ${if (isText) "tap to copy" else fmtSize(h.size)} · $ago",
                fontSize = 12.sp,
                color = QD.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(if (h.outgoing) "⬆️" else "⬇️", fontSize = 16.sp)
    }
}

// ═════════════════════════════ TOOLS ═════════════════════════════

@Composable
fun ToolsScreen(vm: MainVM) {
    val e = vm.engine
    val connected by e.connected.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Title("Tools")
        DeviceCard(vm)
        StorageCard()
        BigFilesCard(e, connected != null)
        QrCard()
        TorchCard()
        BatteryCard()
    }
}

@Composable
private fun CardTitle(icon: String, title: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (sub != null) Text(sub, fontSize = 12.sp, color = QD.Text2)
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DeviceCard(vm: MainVM) {
    val e = vm.engine
    val trusted by e.trusted.collectAsStateWithLifecycle()
    var nick by remember { mutableStateOf(vm.nickname) }
    GlassCard {
        CardTitle("📱", "This device", "How others see you")
        GlassField(nick, { nick = it }, "Device name")
        Spacer(Modifier.height(10.dp))
        GlassButton("Save name", accent = QD.Blue) { vm.saveNick(nick) }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Visible to nearby devices", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Turn off to stop others finding you", fontSize = 12.sp, color = QD.Text2)
            }
            Switch(
                checked = vm.visible,
                onCheckedChange = { vm.setVisible(it) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = QD.Mint,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("Trusted devices (auto-accept)", color = Color.White, fontWeight = FontWeight.SemiBold)
        if (trusted.isEmpty()) {
            Text("None yet — choose “Accept & always trust” when someone connects.", fontSize = 12.sp, color = QD.Text2)
        } else {
            for (name in trusted) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ $name", color = Color.White, modifier = Modifier.weight(1f))
                    Text("Remove", color = QD.Pink, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable { e.untrust(name) }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StorageCard() {
    val st = remember { StatFs(Environment.getDataDirectory().path) }
    val total = st.totalBytes
    val free = st.availableBytes
    val used = total - free
    val frac = if (total > 0) used.toFloat() / total else 0f
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(88.dp)) {
                    val sw = 10.dp.toPx()
                    val tl = Offset(sw / 2, sw / 2)
                    val sz = Size(size.width - sw, size.height - sw)
                    drawArc(Color.White.copy(alpha = 0.14f), -90f, 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                    drawArc(QD.Mint, -90f, 360f * frac, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Text("${(frac * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("💾 Storage", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${fmtSize(used)} used of ${fmtSize(total)}", color = QD.Text2, fontSize = 13.sp)
                Text("${fmtSize(free)} free", color = QD.Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BigFilesCard(e: TransferEngine, canSend: Boolean) {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf<List<BigFile>?>(null) }
    val perms = remember { mediaReadPerms() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        files = scanBigFiles(ctx)
    }
    GlassCard {
        CardTitle("🔍", "Large file finder", "Find big photos & videos, then send or open them")
        GlassButton("Scan phone", accent = QD.Violet) {
            if (hasAll(ctx, perms)) files = scanBigFiles(ctx) else launcher.launch(perms)
        }
        val list = files
        if (list != null) {
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) Text("No photos or videos found (or access was denied).", color = QD.Text2, fontSize = 13.sp)
            for (f in list) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(iconFor(f.mime, false), fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(
                        Modifier.weight(1f).pressable {
                            openItem(ctx, HistoryItem("", f.name, f.size, f.mime, true, "", 0L, f.uri.toString(), null))
                        }
                    ) {
                        Text(f.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Text(fmtSize(f.size), color = QD.Text2, fontSize = 12.sp)
                    }
                    GlassButton("Send") {
                        if (canSend) e.sendUris(listOf(f.uri)) else toast(ctx, "Connect to a device on the Share tab first")
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCard() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    GlassCard {
        CardTitle("🔳", "QR code maker", "Turn a link, Wi-Fi password or text into a QR")
        GlassField(text, { text = it }, "Link or text")
        Spacer(Modifier.height(10.dp))
        GlassButton("Make QR", accent = QD.Pink) {
            if (text.isBlank()) return@GlassButton
            runCatching { makeQr(text) }
                .onSuccess { bmp = it }
                .onFailure { toast(ctx, "Text is too long for a QR code") }
        }
        val b = bmp
        if (b != null) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = b,
                    contentDescription = "QR code",
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun TorchCard() {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val id = remember {
        runCatching {
            cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }
    var on by remember { mutableStateOf(false) }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🔦 Flashlight", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (id == null) "No flash on this device" else "Quick torch toggle", fontSize = 12.sp, color = QD.Text2)
            }
            Switch(
                checked = on,
                enabled = id != null,
                onCheckedChange = { v ->
                    if (id != null) {
                        try {
                            cm.setTorchMode(id, v)
                            on = v
                        } catch (ex: Exception) {
                            toast(ctx, "Can't use the flashlight right now")
                        }
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = QD.Mint,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun BatteryCard() {
    val ctx = LocalContext.current
    val info = remember {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        "${if (pct >= 0) "$pct%" else "?"} · ${if (charging) "Charging" else "On battery"} · ${"%.1f".format(temp)}°C"
    }
    GlassCard {
        Text("🔋 Battery", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(info, fontSize = 13.sp, color = QD.Text2)
        Text("Tip: big transfers drain battery — plug in for multi-GB videos.", fontSize = 12.sp, color = QD.Text2)
    }
}
