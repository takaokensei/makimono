package zechs.drive.stream.ui.settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import org.json.JSONObject
import zechs.drive.stream.databinding.DialogMalAuthBinding
import zechs.drive.stream.utils.NetworkUtils
import zechs.drive.stream.utils.QRCodeGenerator
import zechs.drive.stream.utils.util.Constants
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

class MalAuthDialog(
    private val onCodeReceived: (code: String, codeVerifier: String) -> Unit,
    private val clientId: String = Constants.MAL_CLIENT_ID
) : DialogFragment() {

    companion object {
        const val TAG = "MalAuthDialog"

        fun generateCodeVerifier(): String {
            val secureRandom = SecureRandom()
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
            val sb = StringBuilder(64)
            for (i in 0 until 64) {
                sb.append(chars[secureRandom.nextInt(chars.length)])
            }
            return sb.toString()
        }

        fun codeChallenge(verifier: String): String = verifier
    }

    private var _binding: DialogMalAuthBinding? = null
    private val binding get() = _binding!!

    private val codeVerifier = generateCodeVerifier()
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isAuthCompleted = false

    private enum class Mode {
        BROWSER,
        QR,
        MANUAL
    }

    private var currentMode = Mode.BROWSER

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMalAuthBinding.inflate(layoutInflater)

        binding.btnCloseAuth.setOnClickListener {
            dismiss()
        }

        binding.btnModeBrowser.setOnClickListener { switchMode(Mode.BROWSER) }
        binding.btnModeQr.setOnClickListener { switchMode(Mode.QR) }
        binding.btnModeManual.setOnClickListener { switchMode(Mode.MANUAL) }

        switchMode(Mode.BROWSER)
        setupQrMode()
        setupWebView()
        setupManualMode()
        startTvMalServer()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        binding.layoutWebMode.isVisible = (mode == Mode.BROWSER)
        binding.layoutQrMode.isVisible = (mode == Mode.QR)
        binding.layoutManualMode.isVisible = (mode == Mode.MANUAL)

        updateTabButtonStyle(binding.btnModeBrowser, mode == Mode.BROWSER)
        updateTabButtonStyle(binding.btnModeQr, mode == Mode.QR)
        updateTabButtonStyle(binding.btnModeManual, mode == Mode.MANUAL)
    }

    private fun updateTabButtonStyle(button: MaterialButton, isSelected: Boolean) {
        if (isSelected) {
            button.setBackgroundColor(Color.parseColor("#7AA2F7"))
            button.setTextColor(Color.parseColor("#0E0D14"))
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.setTextColor(Color.parseColor("#C0CAF5"))
        }
    }

    private fun setupQrMode() {
        val localIp = NetworkUtils.getLocalIpAddress() ?: "127.0.0.1"
        val isEmulator = NetworkUtils.isEmulatorAddress(localIp)

        val serverUrl = if (isEmulator) {
            binding.tvEmulatorNotice.isVisible = true
            "http://127.0.0.1:${Constants.MAL_REDIRECT_PORT}/mal"
        } else {
            binding.tvEmulatorNotice.isVisible = false
            "http://$localIp:${Constants.MAL_REDIRECT_PORT}/mal"
        }

        binding.tvServerUrl.text = "Ou acesse no navegador: $serverUrl"

        val qrBitmap = QRCodeGenerator.generateBitmap(
            content = serverUrl,
            sizePx = 512,
            darkColor = Color.parseColor("#0E0D14"),
            lightColor = Color.WHITE
        )

        if (qrBitmap != null) {
            binding.ivMalQrCode.setImageBitmap(qrBitmap)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webViewAuth, true)

        binding.webViewAuth.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progressBarAuth.progress = newProgress
                    binding.progressBarAuth.isVisible = newProgress in 1..99
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.progressBarAuth.isVisible = true
                    if (url != null && checkCallbackUrl(url)) {
                        view?.stopLoading()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBarAuth.isVisible = false
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (checkCallbackUrl(url)) {
                        view?.stopLoading()
                        return true
                    }
                    return false
                }
            }
        }

        binding.btnWebBack.setOnClickListener {
            if (binding.webViewAuth.canGoBack()) {
                binding.webViewAuth.goBack()
            }
        }

        binding.btnWebReload.setOnClickListener {
            binding.webViewAuth.reload()
        }

        val authUrl = Uri.parse(Constants.MAL_OAUTH_BASE_URL + "v1/oauth2/authorize")
            .buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("code_challenge", codeVerifier)
            .appendQueryParameter("code_challenge_method", "plain")
            .appendQueryParameter("redirect_uri", Constants.MAL_REDIRECT_URI)
            .build()
            .toString()

        binding.webViewAuth.loadUrl(authUrl)
    }

    private fun setupManualMode() {
        binding.btnSubmitManual.setOnClickListener {
            val input = binding.etManualInput.text?.toString()?.trim() ?: ""
            binding.tvManualError.isVisible = false

            if (input.isBlank()) {
                binding.tvManualError.text = "Por favor, cole a URL ou o código retornado."
                binding.tvManualError.isVisible = true
                return@setOnClickListener
            }

            // 1. If user pasted a callback URL or code parameter
            val extractedCode = extractCode(input)
            if (extractedCode.isNotBlank() && (extractedCode.length in 20..150) && !extractedCode.contains(" ")) {
                isAuthCompleted = true
                handleCode(extractedCode)
                return@setOnClickListener
            }

            binding.tvManualError.text = "Formato não reconhecido. Certifique-se de colar a URL ou código retornado."
            binding.tvManualError.isVisible = true
        }
    }

    private fun checkCallbackUrl(url: String): Boolean {
        if (url.startsWith("http://127.0.0.1:1420/auth/callback") ||
            url.startsWith("http://127.0.0.1:1421/auth/callback") ||
            url.contains("/auth/callback") ||
            url.contains("code=")
        ) {
            val uri = Uri.parse(url)
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrBlank() && !isAuthCompleted) {
                isAuthCompleted = true
                Log.d(TAG, "Authorization code intercepted from WebView!")
                handleCode(code)
                return true
            }
        }
        return false
    }

    private fun handleCode(code: String) {
        activity?.runOnUiThread {
            binding.tvStatusText.text = "Conectando com MyAnimeList..."
            onCodeReceived(code, codeVerifier)
            dismissAllowingStateLoss()
        }
    }

    private fun startTvMalServer() {
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(Constants.MAL_REDIRECT_PORT)
                Log.i(TAG, "MAL TV Server listening on port ${Constants.MAL_REDIRECT_PORT}")

                while (isActive && !isAuthCompleted) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClientRequest(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MAL server stopped or port busy: ${e.message}")
            }
        }
    }

    private fun handleClientRequest(socket: Socket) {
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

                var line: String? = reader.readLine()
                var contentLength = 0
                while (!line.isNullOrEmpty()) {
                    val headerParts = line.split(":", limit = 2)
                    if (headerParts.size == 2 && headerParts[0].trim().equals("content-length", ignoreCase = true)) {
                        contentLength = headerParts[1].trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }

                if (method == "OPTIONS") {
                    sendResponse(writer, 204, "No Content", "text/plain", "")
                    return
                }

                var body = ""
                if (method == "POST" && contentLength > 0) {
                    val charBuffer = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val count = reader.read(charBuffer, readTotal, contentLength - readTotal)
                        if (count == -1) break
                        readTotal += count
                    }
                    body = String(charBuffer, 0, readTotal)
                }

                when {
                    method == "GET" && (path == "/" || path == "/mal") -> {
                        val html = buildMalPhoneHtml()
                        sendResponse(writer, 200, "OK", "text/html; charset=UTF-8", html)
                    }

                    method == "GET" && path == "/api/status" -> {
                        val json = JSONObject().apply {
                            put("authenticated", isAuthCompleted)
                        }.toString()
                        sendResponse(writer, 200, "OK", "application/json; charset=UTF-8", json)
                    }

                    method == "GET" && (path == "/auth/callback" || fullPath.contains("code=")) -> {
                        val uri = Uri.parse("http://127.0.0.1:1420$fullPath")
                        val code = uri.getQueryParameter("code")
                        if (!code.isNullOrBlank() && !isAuthCompleted) {
                            isAuthCompleted = true
                            val successHtml = """
                                <!DOCTYPE html><html><body style='background:#1a1b26;color:#ffffff;text-align:center;padding:40px;font-family:sans-serif;'>
                                <h1>🎉 Makimono Conectado!</h1>
                                <p style='color:#7aa2f7;font-size:16px;'>Sua conta do MyAnimeList foi vinculada à TV com sucesso. Você pode fechar esta página.</p>
                                </body></html>
                            """.trimIndent()
                            sendResponse(writer, 200, "OK", "text/html; charset=UTF-8", successHtml)
                            handleCode(code)
                        } else {
                            sendResponse(writer, 400, "Bad Request", "text/plain", "Código inválido")
                        }
                    }

                    method == "POST" && path == "/api/mal-code" -> {
                        val parsed = parseJsonOrForm(body)
                        val rawCode = parsed["code"] ?: parsed["url"] ?: ""
                        val code = extractCode(rawCode)
                        if (code.isNotBlank() && !isAuthCompleted) {
                            isAuthCompleted = true
                            val json = JSONObject().apply {
                                put("success", true)
                                put("message", "Código recebido! A TV está conectando...")
                            }.toString()
                            sendResponse(writer, 200, "OK", "application/json; charset=UTF-8", json)
                            handleCode(code)
                        } else {
                            sendResponse(writer, 400, "Bad Request", "application/json", "{\"error\":\"Código vazio\"}")
                        }
                    }

                    method == "POST" && path == "/api/mal-token" -> {
                        sendResponse(
                            writer,
                            410,
                            "Gone",
                            "application/json",
                            "{\"error\":\"Transferência direta de token desativada; use o código OAuth\"}"
                        )
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

    private fun extractCode(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.contains("code=")) {
            Uri.parse(trimmed).getQueryParameter("code") ?: trimmed
        } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            Uri.parse(trimmed).getQueryParameter("code") ?: trimmed
        } else {
            trimmed
        }
    }

    private fun parseJsonOrForm(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val trimmed = body.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
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

    private fun buildMalPhoneHtml(): String {
        val malAuthUrl = "https://myanimelist.net/v1/oauth2/authorize?" +
                "response_type=code&" +
                "client_id=${clientId}&" +
                "code_challenge=${codeVerifier}&" +
                "code_challenge_method=plain&" +
                "redirect_uri=${Constants.MAL_REDIRECT_URI}"

        return """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Makimono • MyAnimeList TV Connect</title>
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
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.5;
            padding: 20px 16px 40px 16px;
        }
        .container { max-width: 480px; margin: 0 auto; }
        .header { text-align: center; margin-bottom: 24px; }
        .logo-title {
            font-size: 24px;
            font-weight: 800;
            letter-spacing: 2px;
            color: #ffffff;
            margin-bottom: 4px;
            text-transform: uppercase;
        }
        .logo-sub { font-size: 14px; color: var(--text-muted); font-weight: 500; }
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
        @keyframes pulse { 0% { opacity: 0.4; } 50% { opacity: 1; } 100% { opacity: 0.4; } }
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
        .btn-primary { background-color: var(--accent-primary); color: #1a1b26; margin-bottom: 12px; }
        .btn-primary:active { transform: scale(0.98); }
        .btn-success { background-color: var(--accent-green); color: #1a1b26; }
        .btn-secondary { background-color: var(--bg-card); color: #ffffff; border: 1px solid var(--border-color); }
        .input-group { margin-top: 12px; margin-bottom: 12px; }
        .input-label { font-size: 13px; font-weight: 600; margin-bottom: 6px; display: block; color: var(--text-main); }
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
        .input-box:focus { border-color: var(--accent-primary); }
        .help-text { font-size: 12px; color: rgba(192, 202, 245, 0.7); margin-top: 6px; line-height: 1.4; }
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
        .success-icon { font-size: 64px; margin-bottom: 16px; animation: bounce 0.6s ease; }
        @keyframes bounce { 0% { transform: scale(0.5); } 60% { transform: scale(1.1); } 100% { transform: scale(1.0); } }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1 class="logo-title">巻物 • MAKIMONO</h1>
            <p class="logo-sub">Login MyAnimeList para Android TV</p>
            <div class="badge-status">
                <div class="badge-dot"></div>
                <span>TV Conectada na Rede Local</span>
            </div>
        </div>

        <div class="card">
            <div class="card-title">
                <span>🔐 Autorização do MyAnimeList</span>
            </div>
            <p class="help-text" style="margin-bottom: 14px;">
                1. Toque abaixo para abrir o MyAnimeList e fazer login com facilidade no seu celular:
            </p>
            <a href="$malAuthUrl" target="_blank" class="btn btn-primary" id="btnMalAuth">
                1. Abrir MyAnimeList no Celular
            </a>

            <div class="input-group">
                <label class="input-label">2. Cole aqui o link retornado ou o código:</label>
                <input type="text" id="inputMalCode" class="input-box" placeholder="http://127.0.0.1:1420/auth/callback?code=... ou código">
                <p class="help-text">
                    Após fazer login e aprovar no MAL, copie a URL da barra de endereços do navegador (mesmo que aponte para 127.0.0.1) e cole acima.
                </p>
            </div>
            <button class="btn btn-success" id="btnSendMalCode">
                2. Enviar Código para a TV
            </button>
            <div id="toastMalCode" class="toast-msg"></div>
        </div>

    </div>

    <!-- Celebration -->
    <div id="successOverlay" class="success-overlay">
        <div class="success-icon">🎉</div>
        <h2 style="color: #ffffff; margin-bottom: 8px;">MyAnimeList Conectado à TV!</h2>
        <p style="color: var(--text-main); font-size: 15px; max-width: 320px;">
            A sua TV já recebeu a autorização e o scrobble automático de animes está ativo. Pode fechar esta página!
        </p>
    </div>

    <script>
        function showToast(elemId, msg, isSuccess) {
            const el = document.getElementById(elemId);
            if (!el) return;
            el.textContent = msg;
            el.className = 'toast-msg ' + (isSuccess ? 'toast-success' : 'toast-error');
            el.style.display = 'block';
        }

        // Poll TV authentication status
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

        // Send MAL Code
        document.getElementById('btnSendMalCode').addEventListener('click', async () => {
            const code = document.getElementById('inputMalCode').value.trim();
            if (!code) {
                showToast('toastMalCode', 'Por favor cole a URL ou código retornado.', false);
                return;
            }
            showToast('toastMalCode', 'Enviando código para a TV...', true);
            try {
                const res = await fetch('/api/mal-code', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ code: code })
                });
                const data = await res.json();
                if (data.success) {
                    showToast('toastMalCode', 'Código recebido pela TV! Autenticando...', true);
                } else {
                    showToast('toastMalCode', data.error || 'Erro ao processar', false);
                }
            } catch (e) {
                showToast('toastMalCode', 'Falha ao conectar com a TV. Verifique se ambos estão no mesmo Wi-Fi.', false);
            }
        });

    </script>
</body>
</html>
        """.trimIndent()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        binding.webViewAuth.destroy()
        _binding = null
    }
}
