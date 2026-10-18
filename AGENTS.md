# 🤖 Diretrizes e Regras para Agentes (Makimono)

Guia canônico para agentes de IA (Antigravity, Codex) operando no repositório Makimono.

---

## 1. O Projeto
- **Nome & Plataforma:** Makimono — Cliente de streaming do Google Drive para Android e Android TV (10-foot experience).
- **Stack:** Kotlin, ViewBinding + XML, Media3 (1.4.1) + libmpv nativo, Room (2.6.1), Hilt (2.51.1).
- **Pacote Base:** `zechs.drive.stream`
- **Módulos:**
  - `:app` — Interface móvel/TV, repositórios, banco de dados Room e casos de uso.
  - `:mpv` — Wrapper C++ JNI e binding Kotlin da libmpv para renderização precisa de legendas ASS/SSA.
  - `:benchmark` — Macrobenchmarks e Baseline Profiles.
  - `:backend` — Serviços auxiliares e proxies quando aplicável.
- **Estrutura de `app/src/main/java/zechs/drive/stream`:**
  - `data/` — Modelos remotos/locais, Room DAOs, cliente Drive API, repositórios.
  - `domain/` — Modelos puros, contratos e regras de negócio sem dependência de framework Android.
  - `playback/` — Motores `PlayerEngine` (Media3 e MPV), gerenciamento de sessão e skip.
  - `ui/` — Telas e adaptadores (`home`, `series`, `player`, `files`, `settings`, `profile`).
  - `utils/` — Utilitários, gerenciamento de estado e `AppLog`.
  - `worker/` — Background workers periódicos (WorkManager).

---

## 2. Regras Invioláveis
1. **Nunca remover sem aviso:** Jamais remova feature, função, saída, comportamento ou teste sem avisar antes. Mudanças devem somar ou substituir com aviso explícito.
2. **Nunca enfraquecer testes:** Proibido pular, enfraquecer ou apagar testes para fazê-los passar. Se quebrou, o código é o problema.
3. **Segredos & Propriedades:** Nunca toque em segredos, tokens ou em `local.properties`. Repositório público: zero credenciais no Git.
4. **Escopo Estrito:** Sem refatorações fora do escopo da tarefa solicitada.

---

## 3. Fluxo de Commits e Git
- **Direto na `main`:** Não crie branches. Mantenha o working tree limpo antes de iniciar.
- **Commits Pequenos:** Faça um commit pequeno por etapa/subtarefa apenas com o `verify-all` verde.
- **Sem Push Automático:** Nunca faça `git push`. O usuário faz o push.
- **Apenas um Agente:** Nunca rode múltiplos agentes simultaneamente no mesmo working tree.
- **Convenção:** Conventional Commits imperativos em minúsculas (`feat:`, `fix:`, `refactor:`, `docs:`, `chore:`, `test:`), com escopo quando útil (ex: `feat(series): adiciona pills de metadados`).

---

## 4. Design System & Android TV (Canônico: `docs/design/DESIGN.md`)
Consulte sempre [`docs/design/DESIGN.md`](docs/design/DESIGN.md). Erros mais comuns a evitar:
- **Um único foco visível por vez:** Nunca mantenha dois elementos com anel de foco simultaneamente.
- **Selecionado ≠ Focado:** Aba/item selecionado é estado persistente; foco é o cursor ativo do D-pad.
- **Sem Blur Real:** Evite renderscript/blur dinâmico pesado em TV (baixa GPU). Use gradientes direcionais (`obsidian_media_scrim`).
- **Margens Seguras (Overscan):** 48dp nas laterais e 27dp no topo/base. Elementos interativos nunca tocam a borda.
- **Fontes do App:** Use apenas fontes existentes no projeto (Outfit/Space Grotesk); não invente Inter nem pesos inexistentes.
- **Tokens Obsidian:** Use cores semânticas (`@color/obsidian_*`). Proibido hexadecimais brutos e proibido misturar a paleta inteira num componente.

---

## 5. Padrão de Arquitetura para Regras de Apresentação e Negócio
- Isole toda regra de negócio ou de visibilidade condicional em classes de política puras e testáveis (ex: `PillVisibilityPolicy`, `SeriesMediaMetadataPolicy`).
- Escreva testes unitários cobrindo todos os ramos e casos de borda antes de plugar a política na UI.

---

## 6. Ambiente de Desenvolvimento Windows
- **Caminho Curto via `subst`:** Como caminhos com acentos (ex: `Cauã V`) podem quebrar workers do Gradle, use uma unidade virtual ASCII (ex: `subst Z: .` e rode a partir de `Z:\`).
- **Emulador TV:** 1920×1080 @320dpi (xhdpi), canvas base 960×540dp.
- **Screenshots:** Utilize a `VisualReviewActivity` (`zechs.drive.stream.visualreview`) para capturar telas de forma isolada.

---

## 7. Definição de Pronto (DoD)
Toda tarefa de código só é considerada pronta quando:
1. O código compila perfeitamente sem erros.
2. O script de verificação unificada `./scripts/verify-all.ps1` (ou `./scripts/verify-all.sh`) executar 100% verde (testes unitários, linter Android, compilação debug e guardas estáticas); use `--quick` apenas para iterações rápidas durante o desenvolvimento.
3. O linter Android (`lintDebug`) passa sem violações novas em relação à baseline.
4. O checklist de navegação D-pad de TV (`docs/qa/focus-checklist.md`) é respeitado, com evidência visual via `VisualReviewActivity` (`docs/qa/visual-review-screenshots.md`) para mudanças de UI.
5. Sem novos warnings introduzidos no build.
6. Commit pequeno criado com mensagem convencional descritiva.
