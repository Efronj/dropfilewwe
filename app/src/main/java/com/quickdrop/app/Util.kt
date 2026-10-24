package com.quickdrop.app

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale

fun fmtSize(b: Long): String {
    if (b <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return if (i == 0) "$b B" else String.format(Locale.US, "%.1f %s", v, units[i])
}

fun fmtSpeed(bps: Double): String = if (bps <= 0) "starting…" else fmtSize(bps.toLong()) + "/s"

fun fmtEta(total: Long, done: Long, bps: Double): String {
    if (bps <= 1 || total <= 0) return ""
    val s = ((total - done) / bps).toInt().coerceAtLeast(0)
    return if (s < 60) "· ${s}s left" else "· ${s / 60}m ${s % 60}s left"
}

fun iconFor(mime: String, isText: Boolean): String = when {
    isText -> "💬"
    mime.startsWith("image/") -> "🖼️"
    mime.startsWith("video/") -> "🎬"
    mime.startsWith("audio/") -> "🎵"
    mime == "application/pdf" -> "📕"
    mime == "application/vnd.android.package-archive" -> "📦"
    else -> "📄"
}

fun nearbyPerms(): Array<String> {
    val l = mutableListOf<String>()
    if (Build.VERSION.SDK_INT <= 32) l += Manifest.permission.ACCESS_FINE_LOCATION
    if (Build.VERSION.SDK_INT >= 31) {
        l += Manifest.permission.BLUETOOTH_ADVERTISE
        l += Manifest.permission.BLUETOOTH_CONNECT
        l += Manifest.permission.BLUETOOTH_SCAN
    }
    if (Build.VERSION.SDK_INT >= 33) l += Manifest.permission.NEARBY_WIFI_DEVICES
    return l.toTypedArray()
}

fun hasAll(ctx: Context, perms: Array<String>): Boolean =
    perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

fun mediaReadPerms(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/** The 25 biggest photos / videos on the phone. */
fun scanBigFiles(ctx: Context): List<BigFile> {
    val out = ArrayList<BigFile>()
    val args = Bundle().apply {
        putString(
            ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE},${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
        )
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.SIZE))
        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        putInt(ContentResolver.QUERY_ARG_LIMIT, 25)
    }
    val proj = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.MIME_TYPE,
    )
    runCatching {
        ctx.contentResolver.query(MediaStore.Files.getContentUri("external"), proj, args, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out.add(
                    BigFile(
                        MediaStore.Files.getContentUri("external", id),
                        c.getString(1) ?: "file",
                        c.getLong(2),
                        c.getString(3) ?: "application/octet-stream",
                    )
                )
            }
        }
    }
    return out
}

fun makeQr(s: String): ImageBitmap {
    val m = QRCodeWriter().encode(s, BarcodeFormat.QR_CODE, 600, 600, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until m.width) {
        for (y in 0 until m.height) {
            bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp.asImageBitmap()
}
