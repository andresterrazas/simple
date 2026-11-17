package com.andresterrazas.simple.wallpaper

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/** Servidor HTTP mínimo en la red local para subir fondos desde el celular. */
class UploadServer(
    private val title: String = "Elige una foto para usarla como fondo en tu Fire TV.",
    private val doneText: String = "Tu foto ya es el fondo de simple en la TV.",
    private val onImage: (ByteArray) -> Boolean,
) {
    private var server: ServerSocket? = null

    fun start() {
        if (server != null) return
        val s = ServerSocket(PORT)
        server = s
        Thread({
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: Exception) { break }
                Thread({ handle(client) }, "simple-upload-client").apply { isDaemon = true }.start()
            }
        }, "simple-upload").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            runCatching {
                s.soTimeout = 30_000
                val input = BufferedInputStream(s.getInputStream())
                val out = s.getOutputStream()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                val method = parts.getOrNull(0) ?: ""
                val path = parts.getOrNull(1) ?: "/"
                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }
                if (method == "POST" && path.startsWith("/upload")) {
                    val len = headers["content-length"]?.toIntOrNull() ?: -1
                    if (len <= 0 || len > MAX_BYTES) {
                        respond(out, 413, page("La foto es demasiado grande", "Elige una imagen de menos de 25 MB.", true))
                        return
                    }
                    val body = ByteArray(len)
                    var read = 0
                    while (read < len) {
                        val n = input.read(body, read, len - read)
                        if (n < 0) break
                        read += n
                    }
                    val boundary = headers["content-type"]?.substringAfter("boundary=", "")?.trim('"', ' ') ?: ""
                    val file = if (boundary.isNotEmpty()) extractFile(body, boundary) else null
                    val ok = file != null && runCatching { onImage(file) }.getOrDefault(false)
                    if (ok) respond(out, 200, page("¡Listo!", doneText, true))
                    else respond(out, 400, page("No se pudo usar esa foto", "Intenta con otra imagen (JPG o PNG).", true))
                } else {
                    respond(out, 200, uploadPage(title))
                }
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 8192) break
        }
        return sb.toString()
    }

    private fun respond(out: OutputStream, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 413 -> "Payload Too Large"; else -> "OK" }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    companion object {
        const val PORT = 8686
        private const val MAX_BYTES = 25 * 1024 * 1024

        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()

        fun extractFile(body: ByteArray, boundary: String): ByteArray? {
            val delim = "--$boundary".toByteArray(Charsets.ISO_8859_1)
            val partEnd = "\r\n--$boundary".toByteArray(Charsets.ISO_8859_1)
            val headerSep = "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
            var pos = indexOf(body, delim, 0)
            while (pos >= 0) {
                val start = pos + delim.size
                if (start + 1 < body.size && body[start] == '-'.code.toByte() && body[start + 1] == '-'.code.toByte()) return null
                val headerEnd = indexOf(body, headerSep, start)
                if (headerEnd < 0) return null
                val partHeaders = String(body, start, headerEnd - start, Charsets.ISO_8859_1)
                val dataStart = headerEnd + headerSep.size
                val next = indexOf(body, partEnd, dataStart)
                if (next < 0) return null
                if (partHeaders.contains("filename=", ignoreCase = true) && next > dataStart) {
                    return body.copyOfRange(dataStart, next)
                }
                pos = next + 2
            }
            return null
        }

        private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
            if (pattern.isEmpty()) return from
            val first = pattern[0]
            var i = from
            val last = data.size - pattern.size
            while (i <= last) {
                if (data[i] == first) {
                    var j = 1
                    while (j < pattern.size && data[i + j] == pattern[j]) j++
                    if (j == pattern.size) return i
                }
                i++
            }
            return -1
        }

        fun qr(text: String, size: Int = 360): ImageBitmap {
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
            val px = IntArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                px[y * size + x] = if (matrix.get(x, y)) 0xFF111318.toInt() else 0xFFFFFFFF.toInt()
            }
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
        }

        private fun shell(body: String) = """<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>simple · fondo</title>
<style>
*{box-sizing:border-box}body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
font-family:-apple-system,system-ui,sans-serif;color:#fff;background:linear-gradient(140deg,#6e8fb8,#2e4567 45%,#e58a3c);}
.card{width:min(420px,92vw);padding:28px;border-radius:28px;background:rgba(255,255,255,.16);
backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);box-shadow:0 20px 60px rgba(0,0,0,.25)}
h1{margin:0 0 6px;font-size:28px}p{margin:0 0 20px;opacity:.85;line-height:1.4}
label{display:block;padding:18px;border-radius:20px;background:rgba(255,255,255,.18);text-align:center;cursor:pointer;margin-bottom:14px}
input[type=file]{display:none}button,a.btn{display:block;width:100%;padding:16px;border:0;border-radius:999px;background:#fff;
color:#1e2230;font-size:17px;font-weight:600;text-align:center;text-decoration:none}#n{font-size:14px;opacity:.8;margin-top:6px}
</style></head><body><div class="card">$body</div></body></html>"""

        private fun uploadPage(title: String) = shell("""<h1>simple</h1><p>$title</p>
<form method="post" action="/upload" enctype="multipart/form-data">
<label>📷 Elegir foto<input type="file" name="photo" accept="image/*" required
onchange="document.getElementById('n').textContent=this.files[0]?this.files[0].name:''"><div id="n"></div></label>
<button type="submit">Enviar a la TV</button></form>""")

        private fun page(title: String, text: String, back: Boolean) = shell(
            "<h1>$title</h1><p>$text</p>" + if (back) "<a class=\"btn\" href=\"/\">Subir otra foto</a>" else ""
        )
    }
}
