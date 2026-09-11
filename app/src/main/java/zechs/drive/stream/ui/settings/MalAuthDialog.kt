package zechs.drive.stream.ui.settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import zechs.drive.stream.databinding.DialogMalAuthBinding
import zechs.drive.stream.utils.util.Constants
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.security.SecureRandom

class MalAuthDialog(
    private val onCodeReceived: (code: String, codeVerifier: String) -> Unit
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
    }

    private var _binding: DialogMalAuthBinding? = null
    private val binding get() = _binding!!

    private val codeVerifier = generateCodeVerifier()
    private var loopbackServerJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var codeHandled = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMalAuthBinding.inflate(layoutInflater)

        binding.btnCloseAuth.setOnClickListener {
            dismiss()
        }

        setupWebView()
        startLocalLoopbackServer()

        val authUrl = Uri.parse(Constants.MAL_OAUTH_BASE_URL + "v1/oauth2/authorize")
            .buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", Constants.MAL_CLIENT_ID)
            .appendQueryParameter("code_challenge", codeVerifier)
            .appendQueryParameter("code_challenge_method", "plain")
            .appendQueryParameter("redirect_uri", Constants.MAL_REDIRECT_URI)
            .build()
            .toString()

        binding.webViewAuth.loadUrl(authUrl)

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webViewAuth.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

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
    }

    private fun checkCallbackUrl(url: String): Boolean {
        if (url.startsWith("http://127.0.0.1:1420/auth/callback") || url.startsWith("http://127.0.0.1:1421/auth/callback")) {
            val uri = Uri.parse(url)
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrBlank() && !codeHandled) {
                codeHandled = true
                Log.d(TAG, "Authorization code intercepted successfully from WebView!")
                handleCode(code)
                return true
            }
        }
        return false
    }

    private fun handleCode(code: String) {
        activity?.runOnUiThread {
            onCodeReceived(code, codeVerifier)
            dismissAllowingStateLoss()
        }
    }

    private fun startLocalLoopbackServer() {
        loopbackServerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(Constants.MAL_REDIRECT_PORT)
                Log.d(TAG, "Local loopback server listening on port ${Constants.MAL_REDIRECT_PORT}")
                while (isActive && !codeHandled) {
                    val client = serverSocket?.accept() ?: break
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val requestLine = reader.readLine() ?: ""
                    Log.d(TAG, "Loopback received request: $requestLine")

                    if (requestLine.contains("/auth/callback") && requestLine.contains("code=")) {
                        val parts = requestLine.split(" ")
                        if (parts.size >= 2) {
                            val uri = Uri.parse("http://127.0.0.1:1420" + parts[1])
                            val code = uri.getQueryParameter("code")
                            if (!code.isNullOrBlank() && !codeHandled) {
                                codeHandled = true
                                val writer = OutputStreamWriter(client.getOutputStream())
                                writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n")
                                writer.write("<html><body style='background:#0A0918;color:#fff;text-align:center;padding-top:50px;font-family:sans-serif;'>")
                                writer.write("<h2>Makimono Autenticado!</h2><p>Conta MyAnimeList conectada com sucesso. Você pode fechar esta aba.</p>")
                                writer.write("</body></html>")
                                writer.flush()
                                client.close()
                                handleCode(code)
                                break
                            }
                        }
                    }
                    client.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Loopback server stopped or port busy: ${e.message}")
            }
        }
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
        loopbackServerJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        binding.webViewAuth.destroy()
        _binding = null
    }
}
