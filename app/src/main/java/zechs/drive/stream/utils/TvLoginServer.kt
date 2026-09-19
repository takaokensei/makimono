package zechs.drive.stream.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class TvLoginServer(
    val port: Int = 8080,
    private val clientId: String,
    private val redirectUri: String = "http://127.0.0.1:53682/",
    private val hasDefaultToken: Boolean = false,
    private val onAuthCodeReceived: (String) -> Unit,
    private val onActivateDefault: () -> Unit,
    private val isAlreadyAuthenticated: () -> Boolean
) {

    companion object {
        private const val TAG = "TvLoginServer"
        const val NONCE_TTL_MS = 120_000L // 2 minutes (SEC-04)
        const val MAX_BODY_BYTES = 65_536  // 64 KB limit
    }

    data class PairingSession(
        val nonce: String,
        val expiresAt: Long,
        var used: Boolean = false
    ) {
        val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    }

    @Volatile
    private var currentSession: PairingSession? = null

    fun generateNewPairingSession(ttlMs: Long = NONCE_TTL_MS): PairingSession {
        val randomBytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(randomBytes)
        val nonce = randomBytes.joinToString("") { "%02x".format(it) }
        val session = PairingSession(
            nonce = nonce,
            expiresAt = System.currentTimeMillis() + ttlMs
        )
        currentSession = session
        return session
    }

    fun getPairingNonce(): String {
        val session = currentSession
        if (session == null || session.isExpired || session.used) {
            return generateNewPairingSession().nonce
        }
        return session.nonce
    }

    internal fun setPairingSessionForTesting(session: PairingSession) {
        currentSession = session
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    var boundPort: Int = port
        private set

    fun start(scope: CoroutineScope): Boolean {
        getPairingNonce() // Initialize pairing nonce
        val existingSocket = serverSocket
        if (existingSocket != null && !existingSocket.isClosed) {
            Log.d(TAG, "Server already running on port $boundPort")
            return true
        }

        var currentPort = port
        var socket: ServerSocket? = null
        for (i in 0..5) {
            try {
                socket = ServerSocket(currentPort)
                boundPort = currentPort
                break
            } catch (e: Exception) {
                Log.w(TAG, "Port $currentPort occupied, trying ${currentPort + 1}")
                currentPort++
            }
        }

        if (socket == null) {
            Log.e(TAG, "Failed to bind server to any port")
            return false
        }

        serverSocket = socket
        Log.i(TAG, "TV Login Server listening on port $boundPort")

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                // Capture into a local val so a concurrent stop() nulling out
                // `serverSocket` from another thread can't race us between
                // the null/closed check and actually using the socket.
                val activeSocket = serverSocket ?: break
                if (activeSocket.isClosed) break
                try {
                    val clientSocket = activeSocket.accept()
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                } catch (e: Exception) {
                    if (!isActive || activeSocket.isClosed) break
                    Log.e(TAG, "Error accepting client", e)
                }
            }
        }
        return true
    }

    fun stop() {
        try {
            serverJob?.cancel()
            serverJob = null
            serverSocket?.close()
            serverSocket = null
            Log.i(TAG, "TV Login Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val fullPath = parts[1]
                val path = fullPath.substringBefore("?")

                // Read headers
                val headers = mutableMapOf<String, String>()
                var line: String? = reader.readLine()
                var contentLength = 0
                while (!line.isNullOrEmpty()) {
                    val headerParts = line.split(":", limit = 2)
                    if (headerParts.size == 2) {
                        val name = headerParts[0].trim().lowercase()
                        val value = headerParts[1].trim()
                        headers[name] = value
                        if (name == "content-length") {
                            contentLength = value.toIntOrNull() ?: 0
                        }
                    }
                    line = reader.readLine()
                }

                // Handle CORS preflight
                if (method == "OPTIONS") {
                    sendResponse(writer, 204, "No Content", "text/plain", "")
                    return
                }

                // Read body if POST
                var body = ""
                if (method == "POST") {
                    if (contentLength > MAX_BODY_BYTES) {
                        sendResponse(writer, 413, "Payload Too Large", "application/json", "{\"error\":\"Payload excede limite seguro (64KB)\"}")
                        try {
                            reader.skip(contentLength.toLong())
                        } catch (_: Exception) {}
                        return
                    }
                    if (contentLength > 0) {
                        val charBuffer = CharArray(contentLength)
                        var readTotal = 0
                        while (readTotal < contentLength) {
                            val count = reader.read(charBuffer, readTotal, contentLength - readTotal)
                            if (count == -1) break
                            readTotal += count
                        }
                        body = String(charBuffer, 0, readTotal)
                    }
                }

                when {
                    method == "GET" && (path == "/" || path == "/login" || path == "/pair") -> {
                        val queryNonce = if (fullPath.contains("nonce=")) {
                            fullPath.substringAfter("nonce=").substringBefore("&").takeIf { it.isNotBlank() }
                        } else null
                        val nonceToUse = queryNonce ?: getPairingNonce()
                        val html = buildHtmlPage(nonceToUse)
                        sendResponse(writer, 200, "OK", "text/html; charset=UTF-8", html)
                    }

                    method == "GET" && path == "/api/status" -> {
                        val authenticated = isAlreadyAuthenticated()
                        val json = "{\"authenticated\":$authenticated}"
                        sendResponse(writer, 200, "OK", "application/json; charset=UTF-8", json)
                    }

                    method == "POST" && path == "/api/auth-code" -> {
                        val parsed = parseJsonOrForm(body)
                        if (!validatePairingNonce(parsed, headers, fullPath, writer)) return
                        val code = extractAuthCode(parsed["code"] ?: parsed["url"] ?: "")
                        if (code.isNotBlank()) {
                            onAuthCodeReceived(code)
                            val json = "{\"success\":true,\"message\":\"Código recebido pela TV! Autenticando...\"}"
                            sendResponse(writer, 200, "OK", "application/json; charset=UTF-8", json)
                        } else {
                            sendResponse(writer, 400, "Bad Request", "application/json", "{\"error\":\"Código vazio\"}")
                        }
                    }

                    method == "POST" && path == "/api/session" -> {
                        // Persistent credentials must never cross the local network.
                        sendResponse(
                            writer,
                            410,
                            "Gone",
                            "application/json",
                            "{\"error\":\"Transferência direta de sessão desativada; use o código OAuth de uso único\"}"
                        )
                    }

                    method == "POST" && path == "/api/activate-default" -> {
                        val parsed = parseJsonOrForm(body)
                        if (!validatePairingNonce(parsed, headers, fullPath, writer)) return
                        onActivateDefault()
                        val json = "{\"success\":true,\"message\":\"Ativação com credenciais padrão solicitada.\"}"
                        sendResponse(writer, 200, "OK", "application/json; charset=UTF-8", json)
                    }

                    else -> {
                        sendResponse(writer, 404, "Not Found", "text/plain", "Endpoint não encontrado")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client request", e)
        }
    }

    private fun validatePairingNonce(
        parsed: Map<String, String>,
        headers: Map<String, String>,
        fullPath: String,
        writer: PrintWriter
    ): Boolean {
        val activeSession = currentSession
        val queryNonce = if (fullPath.contains("nonce=")) {
            fullPath.substringAfter("nonce=").substringBefore("&").takeIf { it.isNotBlank() }
        } else null

        val providedNonce = parsed["nonce"]
            ?: headers["x-pairing-nonce"]
            ?: queryNonce

        if (providedNonce.isNullOrBlank()) {
            sendResponse(writer, 401, "Unauthorized", "application/json", "{\"error\":\"Nonce de pareamento ausente\"}")
            return false
        }
        if (activeSession == null || providedNonce != activeSession.nonce) {
            sendResponse(writer, 401, "Unauthorized", "application/json", "{\"error\":\"Nonce de pareamento inválido\"}")
            return false
        }

        // Consume the nonce atomically so two simultaneous requests cannot both
        // exchange the same pairing session.
        synchronized (activeSession) {
            if (activeSession.isExpired) {
                sendResponse(writer, 410, "Gone", "application/json", "{\"error\":\"Sessão de pareamento expirada\"}")
                return false
            }
            if (activeSession.used) {
                sendResponse(writer, 409, "Conflict", "application/json", "{\"error\":\"Sessão de pareamento já utilizada\"}")
                return false
            }
            activeSession.used = true
            return true
        }
    }

    private fun parseJsonOrForm(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val trimmed = body.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?>? = com.google.gson.Gson().fromJson(trimmed, type)
                if (map != null) {
                    map.forEach { (k, v) ->
                        if (v != null) result[k] = v.toString()
                    }
                    return result
                }
            } catch (_: Exception) {}

            try {
                val json = JSONObject(trimmed)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = json.optString(key, "")
                }
                return result
            } catch (_: Exception) {}
        }

        // Fallback: form-urlencoded (code=xyz&url=...)
        val pairs = body.split("&")
        for (pair in pairs) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2) {
                val k = URLDecoder.decode(kv[0], "UTF-8")
                val v = URLDecoder.decode(kv[1], "UTF-8")
                result[k] = v
            }
        }
        return result
    }

    private fun extractAuthCode(value: String): String {
        val trimmed = value.trim()
        if (!trimmed.contains("code=")) return trimmed
        return try {
            URLDecoder.decode(
                trimmed.substringAfter("code=").substringBefore('&'),
                StandardCharsets.UTF_8.name()
            )
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun sendResponse(
        writer: PrintWriter,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${bodyBytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("Cache-Control: no-store\r\n")
        writer.print("X-Content-Type-Options: nosniff\r\n")
        writer.print("\r\n")
        writer.flush()
        writer.print(body)
        writer.flush()
    }

    private fun buildHtmlPage(nonce: String = getPairingNonce()): String {
        val googleAuthUrl = "https://accounts.google.com/o/oauth2/auth?" +
                "client_id=${clientId}&" +
                "redirect_uri=${redirectUri}&" +
                "response_type=code&" +
                "scope=${zechs.drive.stream.utils.util.Constants.DEFAULT_DRIVE_SCOPE}&" +
                "access_type=offline&" +
                "prompt=consent"

        return """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Makimono TV Connect</title>
    <style>
        :root {
            --bg-base: #1a1b26;
            --bg-surface: #24283b;
            --bg-card: #2f354d;
            --text-main: #c0caf5;
            --text-muted: #7aa2f7;
            --accent-primary: #7aa2f7;
            --accent-green: #9ece6a;
            --accent-red: #f7768e;
            --border-color: rgba(255, 255, 255, 0.12);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: var(--bg-base);
            color: var(--text-main);
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            line-height: 1.5;
            padding: 20px 16px 40px 16px;
        }
        .container {
            max-width: 480px;
            margin: 0 auto;
        }
        .header {
            text-align: center;
            margin-bottom: 24px;
        }
        .logo-title {
            font-size: 26px;
            font-weight: 800;
            letter-spacing: 2px;
            color: #ffffff;
            margin-bottom: 4px;
            text-transform: uppercase;
        }
        .logo-sub {
            font-size: 14px;
            color: var(--text-muted);
            font-weight: 500;
        }
        .badge-status {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: rgba(122, 162, 247, 0.15);
            border: 1px solid var(--accent-primary);
            color: var(--accent-primary);
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 13px;
            font-weight: 600;
            margin-top: 10px;
        }
        .badge-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background-color: var(--accent-primary);
            animation: pulse 1.5s infinite;
        }
        @keyframes pulse {
            0% { opacity: 0.4; }
            50% { opacity: 1; }
            100% { opacity: 0.4; }
        }
        .card {
            background-color: var(--bg-surface);
            border: 1px solid var(--border-color);
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 16px;
            box-shadow: 0 4px 16px rgba(0,0,0,0.25);
        }
        .card-title {
            font-size: 16px;
            font-weight: 700;
            color: #ffffff;
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .btn {
            display: block;
            width: 100%;
            padding: 13px 18px;
            border-radius: 10px;
            border: none;
            font-size: 15px;
            font-weight: 700;
            text-align: center;
            cursor: pointer;
            text-decoration: none;
            transition: all 0.2s ease;
        }
        .btn-primary {
            background-color: var(--accent-primary);
            color: #1a1b26;
            margin-bottom: 12px;
        }
        .btn-primary:active {
            transform: scale(0.98);
            filter: brightness(0.9);
        }
        .btn-success {
            background-color: var(--accent-green);
            color: #1a1b26;
        }
        .btn-secondary {
            background-color: var(--bg-card);
            color: #ffffff;
            border: 1px solid var(--border-color);
        }
        .input-group {
            margin-top: 12px;
            margin-bottom: 12px;
        }
        .input-label {
            font-size: 13px;
            font-weight: 600;
            margin-bottom: 6px;
            display: block;
            color: var(--text-main);
        }
        .input-box {
            width: 100%;
            background-color: var(--bg-base);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 12px 14px;
            color: #ffffff;
            font-size: 14px;
            outline: none;
        }
        .input-box:focus {
            border-color: var(--accent-primary);
        }
        .help-text {
            font-size: 12px;
            color: rgba(192, 202, 245, 0.7);
            margin-top: 6px;
            line-height: 1.4;
        }
        .toast-msg {
            display: none;
            padding: 12px 16px;
            border-radius: 8px;
            margin-top: 12px;
            font-size: 14px;
            font-weight: 600;
            text-align: center;
        }
        .toast-success { background: rgba(158, 206, 106, 0.2); color: var(--accent-green); border: 1px solid var(--accent-green); }
        .toast-error { background: rgba(247, 118, 142, 0.2); color: var(--accent-red); border: 1px solid var(--accent-red); }
        .success-overlay {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background-color: rgba(26, 27, 38, 0.96);
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 24px;
            text-align: center;
            z-index: 999;
        }
        .success-icon {
            font-size: 64px;
            margin-bottom: 16px;
            animation: bounce 0.6s ease;
        }
        @keyframes bounce {
            0% { transform: scale(0.5); opacity: 0; }
            60% { transform: scale(1.1); }
            100% { transform: scale(1.0); opacity: 1; }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1 class="logo-title">巻物 • MAKIMONO</h1>
            <p class="logo-sub">Sincronização de Login com Android TV</p>
            <div class="badge-status">
                <div class="badge-dot"></div>
                <span>TV Conectada na Rede Local</span>
            </div>
        </div>

        <!-- Google Drive Section -->
        <div class="card">
            <div class="card-title">
                <span>📁 Conectar Google Drive à TV</span>
            </div>
            <p class="help-text" style="margin-bottom: 14px;">
                1. Toque no botão abaixo para autorizar sua conta do Google neste celular:
            </p>
            <a href="$googleAuthUrl" target="_blank" class="btn btn-primary" id="btnGoogleAuth">
                1. Autorizar Conta Google
            </a>

            <div class="input-group">
                <label class="input-label">2. Cole aqui o link retornado ou o código:</label>
                <input type="text" id="inputAuthCode" class="input-box" placeholder="http://127.0.0.1:53682/?code=... ou 4/0Abc...">
                <p class="help-text">
                    Após aprovar no Google, copie o endereço da página (mesmo que dê erro de conexão) e cole aqui.
                </p>
            </div>
            <button class="btn btn-success" id="btnSendCode">
                2. Enviar para a TV
            </button>
            <div id="toastCode" class="toast-msg"></div>
        </div>

        ${if (hasDefaultToken) """
        <!-- Default Credentials Quick Activation -->
        <div class="card">
            <div class="card-title">
                <span>⚡ Ativação com 1 Toque</span>
            </div>
            <p class="help-text" style="margin-bottom: 14px;">
                Esta versão do Makimono possui credenciais embutidas. Toque abaixo para ativar imediatamente:
            </p>
            <button class="btn btn-primary" id="btnActivateDefault">
                Ativar com Credenciais Padrão
            </button>
            <div id="toastDefault" class="toast-msg"></div>
        </div>
        """ else ""}
    </div>

    <!-- Celebration Screen -->
    <div id="successOverlay" class="success-overlay">
        <div class="success-icon">🎉</div>
        <h2 style="color: #ffffff; margin-bottom: 8px;">TV Autenticada com Sucesso!</h2>
        <p style="color: var(--text-main); font-size: 15px; max-width: 320px;">
            A sua TV já recebeu a confirmação e o Makimono está carregando seu catálogo. Bom anime!
        </p>
    </div>

    <script>
        const pairingNonce = "$nonce";

        function showToast(elemId, msg, isSuccess) {
            const el = document.getElementById(elemId);
            if (!el) return;
            el.textContent = msg;
            el.className = 'toast-msg ' + (isSuccess ? 'toast-success' : 'toast-error');
            el.style.display = 'block';
        }

        // Check authentication status every 2 seconds
        let statusInterval = setInterval(async () => {
            try {
                const res = await fetch('/api/status');
                if (res.ok) {
                    const data = await res.json();
                    if (data.authenticated) {
                        clearInterval(statusInterval);
                        document.getElementById('successOverlay').style.display = 'flex';
                    }
                }
            } catch (e) {}
        }, 2000);

        // Send Google Auth Code / URL
        document.getElementById('btnSendCode').addEventListener('click', async () => {
            const code = document.getElementById('inputAuthCode').value.trim();
            if (!code) {
                showToast('toastCode', 'Por favor cole o link ou código retornado pelo Google.', false);
                return;
            }
            showToast('toastCode', 'Enviando para a TV...', true);
            try {
                const res = await fetch('/api/auth-code', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Pairing-Nonce': pairingNonce
                    },
                    body: JSON.stringify({ code: code, nonce: pairingNonce })
                });
                const data = await res.json();
                if (data.success) {
                    showToast('toastCode', 'Código enviado! A TV está conectando...', true);
                } else {
                    showToast('toastCode', data.error || 'Erro ao processar na TV', false);
                }
            } catch (e) {
                showToast('toastCode', 'Falha ao comunicar com a TV. Verifique a rede Wi-Fi.', false);
            }
        });

        const btnDefault = document.getElementById('btnActivateDefault');
        if (btnDefault) {
            btnDefault.addEventListener('click', async () => {
                showToast('toastDefault', 'Ativando...', true);
                try {
                    const res = await fetch('/api/activate-default', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'X-Pairing-Nonce': pairingNonce
                        },
                        body: JSON.stringify({ nonce: pairingNonce })
                    });
                    const data = await res.json();
                    showToast('toastDefault', data.message || 'Ativação enviada para a TV!', true);
                } catch (e) {
                    showToast('toastDefault', 'Falha ao comunicar com a TV.', false);
                }
            });
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
