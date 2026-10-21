# 🤖 Diretrizes e Regras para Agentes (Makimono)

Guia canônico para agentes de IA operando no repositório Makimono (`takaokensei/makimono`).

---

## 1. Regras Invioláveis de Segurança e Escopo
1. **Segredos:** Proibição absoluta de ler, copiar ou imprimir `local.properties`, `.dev.vars`, keystores (`*.jks`, `*.keystore`) ou segredos. Zero credenciais no Git.
2. **Sem Push Automático:** Trabalhe direto na `main` local; nunca faça `git push`, nunca publique releases. Tags locais `checkpoint-*` são permitidas.
3. **Integridade de Testes:** Proibido pular, enfraquecer ou apagar testes. Se quebrou, o código é o problema.
4. **C++ / libmpv:** `mpv/src/main/libs/*.so` e arquivos JNI gerados não se editam sem consultar `docs/architecture/native-mpv.md` e `ADR-002`.

---

## 2. Comandos de Verificação Canônica
- **Android / App:** `.\scripts\verify-all.ps1` (Windows) ou `bash scripts/verify-all.sh` (Linux). Use `--quick` apenas para iterações intermediárias.
- **Worker Backend:** `npm --prefix backend/cloudflare-worker test` (`vitest`).
- Toda mudança lógica exige `verify-all` 100% verde antes de commit.

---

## 3. Releases, APKs e ABIs
- **Convenção de Nomes:** `makimono-v<versao>-<abi>-release.apk` (ou `-debug.apk`).
- **Checksums:** Cada APK publicado deve possuir seu hash SHA-256 em `<apk>.sha256`.
- **Regra de ABI:** Casamento exato por ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`). `x86` nunca deve casar com `x86_64`.

---

## 4. Testes Manuais Insubstituíveis
Nenhum teste unitário substitui a validação humana nos seguintes cenários:
1. Reprodução real de streaming com o Google Drive (troca de tokens e preaquecimento).
2. Renderização precisa de legendas SSA/ASS complexas via `libmpv`.
3. Navegação física por D-pad em Android TV (1 foco ativo por vez, overscan de 48dp/27dp, design Obsidian).

---

## 5. Documentação e Design System
- **Para APIs Android, consulte a documentação oficial.** Consulte Context7 para versões canônicas (`docs/dev/TOOLS.md`).
- **Design Obsidian:** Siga [`docs/design/DESIGN.md`](docs/design/DESIGN.md). Proibido hexadecimais brutos e blur dinâmico em TV.
- **Fidelidade a ADRs:** Cite `ADR-003` (aceite histórico para app pessoal), `ADR-004` (pareamento Cloudflare) e `ADR-005` (classificação de chaves) estritamente pelo que dizem.
- **Ambiente Windows:** Use caminho curto/ASCII (ex: `L:\makimono` ou `subst`) para evitar quebras de daemon do Gradle com acentos.

---

## 6. Definição de Pronto (DoD)
1. Código compila sem erros nem novos warnings.
2. `verify-all` verde (testes unitários, linter Android `lintDebug` dentro da baseline e 9 quality guards).
3. Testes do Worker (`vitest`) aprovados se `backend/` for tocado.
4. Commits atômicos na `main` com mensagens imperativas convencionais (`feat:`, `fix:`, `refactor:`, `chore:`).
