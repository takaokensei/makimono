package zechs.drive.stream.backend

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Minimal, self-contained mobile page. It previews a request but deliberately cannot authorize it. */
internal object PairingPage {
    private val expiryFormatter = DateTimeFormatter
        .ofPattern("dd/MM/yyyy 'às' HH:mm 'UTC'", Locale.forLanguageTag("pt-BR"))
        .withZone(ZoneOffset.UTC)

    fun render(preview: PairingChallengePreview?, errorMessage: String? = null): String {
        val deviceDetails = preview?.let {
            """
            <dl class="device">
              <div><dt>Dispositivo</dt><dd>${escape(it.deviceName)}</dd></div>
              <div><dt>Solicitação expira</dt><dd><time datetime="${escape(it.expiresAt.toString())}">${escape(expiryFormatter.format(it.expiresAt))}</time></dd></div>
            </dl>
            """.trimIndent()
        }.orEmpty()
        val message = errorMessage ?: if (preview == null) {
            "Não encontramos uma solicitação ativa. Confira o código exibido na TV e tente novamente."
        } else {
            "Esta é uma prévia da solicitação. A autenticação Google ainda não está configurada neste ambiente."
        }
        val state = if (preview == null) "Solicitação indisponível" else "Prévia de pareamento"

        return """
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="referrer" content="no-referrer">
              <meta name="color-scheme" content="dark">
              <title>Conectar TV · Makimono</title>
              <style>
                :root { color-scheme: dark; font-family: Inter, system-ui, sans-serif; background: #050b12; color: #edf5ff; }
                * { box-sizing: border-box; }
                body { min-height: 100vh; margin: 0; display: grid; place-items: center; padding: 24px; background: radial-gradient(ellipse at 50% 0%, #102b3d 0%, #07121e 42%, #050b12 78%); }
                main { width: min(100%, 480px); padding: clamp(24px, 7vw, 40px); border: 1px solid #20394d; border-radius: 24px; background: #0a1521; box-shadow: 0 24px 80px #0008; }
                .brand { display: flex; align-items: center; gap: 12px; margin-bottom: 38px; color: #20d4ee; font-size: 14px; font-weight: 700; letter-spacing: .24em; }
                .mark { width: 28px; height: 28px; border: 3px solid #20d4ee; border-bottom: 0; border-radius: 8px 8px 0 0; position: relative; }
                .mark::before, .mark::after { content: ""; position: absolute; background: #20d4ee; }
                .mark::before { width: 36px; height: 3px; left: -7px; top: 4px; }
                .mark::after { width: 3px; height: 15px; left: 10px; top: 9px; }
                .eyebrow { margin: 0 0 10px; color: #56d8ed; font-size: 12px; font-weight: 700; letter-spacing: .16em; text-transform: uppercase; }
                h1 { margin: 0; font-size: clamp(26px, 7vw, 34px); line-height: 1.12; letter-spacing: -.035em; }
                .state { display: inline-flex; margin-top: 20px; padding: 7px 11px; border: 1px solid #265267; border-radius: 999px; color: #a9eefa; font-size: 13px; }
                .device { margin: 24px 0; padding: 18px; border-radius: 16px; background: #0e1e2b; }
                .device div + div { margin-top: 16px; }
                dt { color: #91a7b9; font-size: 13px; }
                dd { margin: 4px 0 0; overflow-wrap: anywhere; font-size: 17px; font-weight: 600; }
                .notice { margin: 22px 0 28px; padding: 14px 16px; border-left: 3px solid #20d4ee; border-radius: 0 12px 12px 0; background: #102532; color: #c6d6e4; font-size: 14px; line-height: 1.55; }
                .notice p { margin: 0; }
                form { border-top: 1px solid #20394d; padding-top: 24px; }
                label { display: block; margin-bottom: 9px; font-size: 14px; font-weight: 600; }
                input { width: 100%; min-height: 50px; padding: 0 14px; border: 1px solid #35546a; border-radius: 12px; background: #07111b; color: #edf5ff; font: inherit; letter-spacing: .12em; text-transform: uppercase; }
                input:focus-visible, button:focus-visible { outline: 3px solid #20d4ee; outline-offset: 3px; }
                button { width: 100%; min-height: 50px; margin-top: 12px; border: 0; border-radius: 12px; background: #20d4ee; color: #03131a; font: inherit; font-weight: 700; cursor: pointer; }
                .fine { margin: 16px 0 0; color: #8499aa; font-size: 12px; line-height: 1.5; }
                @media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior: auto !important; transition: none !important; animation: none !important; } }
              </style>
            </head>
            <body>
              <main>
                <div class="brand"><span class="mark" aria-hidden="true"></span><span>MAKIMONO</span></div>
                <p class="eyebrow">CONFIGURAÇÃO DA TV</p>
                <h1>Conectar ao Makimono</h1>
                <span class="state">${escape(state)}</span>
                $deviceDetails
                <section class="notice" role="status"><p>${escape(message)}</p></section>
                <form method="post" action="/pair/lookup" autocomplete="off">
                  <label for="userCode">Verificar código de outra TV</label>
                  <input id="userCode" name="userCode" inputmode="text" autocomplete="one-time-code" maxlength="10" pattern="[A-HJ-NP-Z2-9 -]{8,10}" placeholder="ABCD-1234" aria-describedby="code-help" required>
                  <button type="submit">Verificar solicitação</button>
                  <p class="fine" id="code-help">O código identifica a TV; ele não autoriza o acesso sozinho.</p>
                </form>
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
