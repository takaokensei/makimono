# ADR-003: Aceite de Risco de Segredos embutidos no BuildConfig

- **Status:** Aceito (Decisão do Proprietário)
- **Data:** 2026-10-09
- **Contexto:** Makimono (aplicativo privado para uso pessoal)

---

## 1. Contexto

O aplicativo embute campos de configuração e credenciais diretamente no `BuildConfig` (definidos em `app/build.gradle` com fallback para `local.properties`). Isso inclui chaves OAuth do Google Drive, refresh token de automação pessoal e token de acesso ao GitHub.

## 2. Decisão

Por decisão expressa do desenvolvedor/proprietário do repositório, os segredos permanecerão configuráveis via `BuildConfig`/`local.properties` e não serão migrados para um serviço de backend com autenticação delegada enquanto o aplicativo for estritamente de **uso pessoal e privado**.

## 3. Consequências e Riscos Reconhecidos

- **Engenharia Reversa:** Qualquer pessoa que obtiver o binário compilado (APK) pode inspecionar e extrair o refresh token do Drive, credenciais OAuth e token do GitHub via descompilação simples (`apktool`, `jadx`).
- **Escopo Limitado:** O aceite deste risco é válido **única e exclusivamente** enquanto o app for de uso pessoal do desenvolvedor e o APK não for distribuído publicamente.
- **Revogação e Auditoria:** Qualquer plano futuro de distribuição pública, publicação de repositório open-source ou compartilhamento de builds exigirá obrigatoriamente a revisão desta decisão, remoção dos segredos do binário e adoção de fluxo OAuth com backend ou PKCE.
