package com.quickdrop.app

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Two-way file / text transfer built on Google's Nearby Connections API (the same
 * technology family Quick Share uses). With P2P_POINT_TO_POINT the library upgrades the
 * link to Wi-Fi Direct / hotspot automatically, which is what gives the high speed.
 *
 * Wire protocol (tiny):
 *   BYTES  "F|<payloadId>|<mime>|<size>|<name>"  -> describes the FILE payload that follows
 *   BYTES  "T|<text>"                             -> a text / clipboard message
 *   FILE   <content>                              -> the actual file (streamed, never copied first)
 */
class TransferEngine(
    private val app: Application,
    private val store: Store,
    private val scope: CoroutineScope,
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(app)
    private val service = "com.quickdrop.app.v1"
    private val strategy = Strategy.P2P_POINT_TO_POINT

    val peers = MutableStateFlow<List<Peer>>(emptyList())
    val connected = MutableStateFlow<Peer?>(null)
    val incoming = MutableStateFlow<Incoming?>(null)
    val transfers = MutableStateFlow<Map<Long, Transfer>>(emptyMap())
    val status = MutableStateFlow("Getting ready…")
    val history = MutableStateFlow(store.history())
    val pending = MutableStateFlow<List<Outgoing>>(emptyList())
    val trusted = MutableStateFlow(store.trusted)
    val events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private var started = false
    private val names = HashMap<String, String>()
    private val meta = ConcurrentHashMap<Long, Meta>()
    private val fileIds = HashSet<Long>()
    private val outgoingIds = HashSet<Long>()
    private val outUris = HashMap<Long, Uri>()
    private val inPayloads = HashMap<Long, Payload>()
    private val samples = HashMap<Long, Pair<Long, Long>>()
    private val finished = HashSet<Long>()

    // ───────────────────────── discovery / advertising ─────────────────────────

    fun start() {
        started = true
        client.stopAdvertising()
        client.stopDiscovery()
        peers.value = emptyList()
        if (connected.value != null) return
        status.value = if (store.visible) "Looking for nearby devices…" else "Hidden — you can still find others"

        if (store.visible) {
            client.startAdvertising(
                store.nickname,
                service,
                lifecycleCb,
                AdvertisingOptions.Builder().setStrategy(strategy).build(),
            ).addOnFailureListener { e ->
                status.value = "Can't be seen: ${e.message ?: "turn on Wi-Fi & Bluetooth"}"
            }
        }
        client.startDiscovery(
            service,
            discoveryCb,
            DiscoveryOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { e ->
            status.value = "Can't search: ${e.message ?: "turn on Wi-Fi & Bluetooth"}"
        }
    }

    fun restart() {
        if (started) start()
    }

    fun shutdown() {
        started = false
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
    }

    private val discoveryCb = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            names[endpointId] = info.endpointName
            peers.update { list -> list.filter { it.id != endpointId } + Peer(endpointId, info.endpointName) }
        }

        override fun onEndpointLost(endpointId: String) {
            peers.update { list -> list.filter { it.id != endpointId } }
        }
    }

    // ───────────────────────── connection ─────────────────────────

    fun connect(peer: Peer) {
        status.value = "Connecting to ${peer.name}…"
        client.requestConnection(store.nickname, peer.id, lifecycleCb)
            .addOnFailureListener { e ->
                status.value = "Couldn't reach ${peer.name}"
                events.tryEmit("Connection failed: ${e.message ?: "try again"}")
            }
    }

    fun acceptIncoming(trust: Boolean) {
        val inc = incoming.value ?: return
        if (trust) {
            store.trusted = store.trusted + inc.name
            trusted.value = store.trusted
        }
        incoming.value = null
        client.acceptConnection(inc.id, payloadCb)
    }

    fun declineIncoming() {
        val inc = incoming.value ?: return
        incoming.value = null
        client.rejectConnection(inc.id)
    }

    fun untrust(name: String) {
        store.trusted = store.trusted - name
        trusted.value = store.trusted
    }

    fun disconnect() {
        connected.value?.let { client.disconnectFromEndpoint(it.id) }
        connected.value = null
        failActive()
        start()
    }

    private fun failActive() {
        transfers.update { m -> m.mapValues { (_, t) -> if (t.active) t.copy(state = 2) else t } }
    }

    private val lifecycleCb = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            names[endpointId] = info.endpointName
            val isTrusted = store.trusted.contains(info.endpointName)
            if (info.isIncomingConnection && !isTrusted) {
                // Receiver confirms with the 4-digit code.
                incoming.value = Incoming(endpointId, info.endpointName, info.authenticationDigits)
            } else {
                if (!info.isIncomingConnection) {
                    status.value = "Waiting for ${info.endpointName} to accept · code ${info.authenticationDigits}"
                }
                client.acceptConnection(endpointId, payloadCb)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            incoming.value = null
            if (result.status.isSuccess) {
                val n = names[endpointId] ?: "Device"
                connected.value = Peer(endpointId, n)
                status.value = "Connected to $n"
                client.stopAdvertising()
                client.stopDiscovery()
                peers.value = emptyList()
                val queued = pending.value
                if (queued.isNotEmpty()) {
                    pending.value = emptyList()
                    send(queued)
                }
            } else {
                status.value = "Connection declined or failed"
                start()
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.value = null
            failActive()
            status.value = "Disconnected"
            start()
        }
    }

    // ───────────────────────── sending ─────────────────────────

    /** Used by the Share sheet: send now if connected, otherwise hold until a device is picked. */
    fun queueOrSend(items: List<Outgoing>) = send(items)

    fun sendUris(uris: List<Uri>) = send(uris.map { Outgoing.Media(it) })

    fun sendText(text: String) {
        if (text.isNotBlank()) send(listOf(Outgoing.Txt(text)))
    }

    private fun send(items: List<Outgoing>) {
        if (items.isEmpty()) return
        val peer = connected.value
        if (peer == null) {
            pending.update { it + items }
            events.tryEmit("Pick a device — it will send as soon as you connect")
            return
        }
        for (item in items) {
            when (item) {
                is Outgoing.Media -> pushMedia(peer, item.uri)
                is Outgoing.Txt -> pushText(peer, item.text)
            }
        }
    }

    fun clearPending() {
        pending.value = emptyList()
    }

    private fun pushMedia(peer: Peer, uri: Uri) {
        val r = app.contentResolver
        var name = "file"
        var size = 0L
        runCatching {
            r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    size = c.getLong(1)
                }
            }
        }
        val mime = r.getType(uri) ?: "application/octet-stream"
        val pfd = runCatching { r.openFileDescriptor(uri, "r") }.getOrNull()
        if (pfd == null) {
            events.tryEmit("Can't read $name")
            return
        }
        val payload = Payload.fromFile(pfd)
        val pid = payload.id
        fileIds.add(pid)
        outgoingIds.add(pid)
        outUris[pid] = uri
        meta[pid] = Meta(name, mime, size)
        client.sendPayload(peer.id, Payload.fromBytes("F|$pid|$mime|$size|$name".toByteArray(Charsets.UTF_8)))
        client.sendPayload(peer.id, payload)
        putTransfer(Transfer(pid, name, size, 0, true, 0.0, 0))
    }

    private fun pushText(peer: Peer, text: String) {
        val bytes = "T|$text".toByteArray(Charsets.UTF_8)
        if (bytes.size > 32_000) {
            events.tryEmit("Text is too long (max ~30 KB)")
            return
        }
        client.sendPayload(peer.id, Payload.fromBytes(bytes))
        addHistory(
            HistoryItem(
                "t${System.nanoTime()}", "Text", text.length.toLong(), "text/plain",
                true, peer.name, System.currentTimeMillis(), null, text,
            )
        )
        events.tryEmit("Text sent ✓")
    }

    fun cancel(pid: Long) {
        client.cancelPayload(pid)
    }

    // ───────────────────────── receiving ─────────────────────────

    private val payloadCb = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { handleBytes(it) }
                Payload.Type.FILE -> {
                    val pid = payload.id
                    fileIds.add(pid)
                    inPayloads[pid] = payload
                    putTransfer(Transfer(pid, meta[pid]?.name ?: "Incoming file", 0, 0, false, 0.0, 0))
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, u: PayloadTransferUpdate) {
            val pid = u.payloadId
            if (!fileIds.contains(pid)) return
            val out = outgoingIds.contains(pid)
            val now = SystemClock.elapsedRealtime()
            var speed = transfers.value[pid]?.speed ?: 0.0
            val prev = samples[pid]
            if (prev == null) {
                samples[pid] = now to u.bytesTransferred
            } else if (now - prev.first >= 400) {
                val inst = (u.bytesTransferred - prev.second) * 1000.0 / (now - prev.first)
                speed = if (speed == 0.0) inst else speed * 0.6 + inst * 0.4
                samples[pid] = now to u.bytesTransferred
            }
            val state = when (u.status) {
                PayloadTransferUpdate.Status.SUCCESS -> 1
                PayloadTransferUpdate.Status.FAILURE -> 2
                PayloadTransferUpdate.Status.CANCELED -> 3
                else -> 0
            }
            val name = meta[pid]?.name ?: if (out) "Sending…" else "Incoming file"
            val done = if (state == 1) u.totalBytes else u.bytesTransferred
            putTransfer(Transfer(pid, name, u.totalBytes, done, out, speed, state))

            if (state == 1) finish(pid, out)
            if (state != 0) {
                scope.launch {
                    delay(4000)
                    transfers.update { it - pid }
                }
            }
            if (state == 2) events.tryEmit("Transfer failed: $name")
        }
    }

    private fun handleBytes(b: ByteArray) {
        val s = String(b, Charsets.UTF_8)
        when {
            s.startsWith("F|") -> {
                val p = s.split("|", limit = 5)
                if (p.size == 5) {
                    val id = p[1].toLongOrNull()
                    if (id != null) meta[id] = Meta(p[4], p[2], p[3].toLongOrNull() ?: 0L)
                }
            }
            s.startsWith("T|") -> {
                val txt = s.substring(2)
                addHistory(
                    HistoryItem(
                        "t${System.nanoTime()}", "Text", txt.length.toLong(), "text/plain",
                        false, connected.value?.name ?: "Device", System.currentTimeMillis(), null, txt,
                    )
                )
                events.tryEmit("Text received — open History to copy it")
            }
        }
    }

    private fun finish(pid: Long, out: Boolean) {
        if (!finished.add(pid)) return
        val peerName = connected.value?.name ?: "Device"
        if (out) {
            val m = meta[pid]
            addHistory(
                HistoryItem(
                    "o$pid", m?.name ?: "file", m?.size ?: 0L, m?.mime ?: "",
                    true, peerName, System.currentTimeMillis(), outUris[pid]?.toString(), null,
                )
            )
            events.tryEmit("Sent ${m?.name ?: "file"} ✓")
        } else {
            val payload = inPayloads.remove(pid) ?: return
            scope.launch(Dispatchers.IO) {
                // The description message normally lands first; wait briefly if it hasn't yet.
                var m = meta[pid]
                var tries = 0
                while (m == null && tries < 30) {
                    delay(100)
                    m = meta[pid]
                    tries++
                }
                save(pid, payload, m, peerName)
            }
        }
    }

    /** Copies the received file into Pictures / Movies / Downloads → QuickDrop, sorted by type. */
    private fun save(pid: Long, payload: Payload, m: Meta?, peerName: String) {
        try {
            val src = payload.asFile()?.asUri() ?: throw IllegalStateException("file missing")
            val name = m?.name ?: "file_$pid"
            val mime = m?.mime ?: "application/octet-stream"
            val (collection, dir) = when {
                mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
                mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
            }
            val r = app.contentResolver
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/QuickDrop")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val dest = r.insert(collection, cv) ?: throw IllegalStateException("can't create file")
            r.openOutputStream(dest)?.use { o ->
                r.openInputStream(src)?.use { i -> i.copyTo(o, 256 * 1024) }
            }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            r.update(dest, done, null, null)
            runCatching { r.delete(src, null, null) }
            addHistory(
                HistoryItem(
                    "i$pid", name, m?.size ?: 0L, mime,
                    false, peerName, System.currentTimeMillis(), dest.toString(), null,
                )
            )
            events.tryEmit("Saved $name")
        } catch (e: Exception) {
            events.tryEmit("Couldn't save file: ${e.message}")
        }
    }

    // ───────────────────────── helpers ─────────────────────────

    private fun putTransfer(t: Transfer) {
        transfers.update { it + (t.id to t) }
    }

    private fun addHistory(h: HistoryItem) {
        history.update { listOf(h) + it }
        store.saveHistory(history.value)
    }

    fun clearHistory() {
        history.value = emptyList()
        store.saveHistory(emptyList())
    }
}
