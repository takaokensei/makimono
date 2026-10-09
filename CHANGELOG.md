# Changelog — Makimono

Histórico consolidado de lançamentos e versões do Makimono. As notas detalhadas de cada versão encontram-se em [`docs/releases/`](docs/releases/).

## [v1.6.4.9] — 2026-10-09
- **Arquitetura de Domínio:** Criação do módulo `domain/` puro e casos de uso canônicos com regras ArchUnit.
- **Motor de Reprodução:** Contrato comum `PlayerEngine`, `EngineSelector` inteligente, `PlaybackSession`, `ProgressRecorder` atômico e `SkipController`.
- **Decomposição de Telas de TV:** `HomeFragment` (551 linhas), `SettingsFragment` (487 linhas) e `FilesFragment` (525 linhas), todas abaixo do limite de 600 linhas.
- **Navegação & Foco D-pad:** Restauração de foco e clamp em estantes horizontais, distinção canônica de filmes vs séries e suíte `TvFocusChecklistTest` com 476 testes unitários no total.

## [v1.6.4.5] — 2026-10-08
- **UI & TV UX:** Padronização de rótulos de navegação, experiência de reprodução de filmes ("Filme" em vez de "Episódio 01"), piso tipográfico de 12sp.
- **Acessibilidade & Foco:** Hierarquia de foco em D-pad refinada, anéis de foco de 3dp, suporte a safe-insets de overscan na TV.
- **Robustez & Logging:** Introdução do `AppLog` centralizado com redação automática de tokens OAuth e URLs sensíveis.
- **Build & Dependências:** Baseline do lint versionado com `abortOnError true`, saneamento de binários nativos duplicados.

## [v1.6.4.1] — 2026-10-03
- **Correções Rápidas:** Ajustes de estabilidade na seleção de trilhas de áudio e legendas no MPV e ExoPlayer.

## [v1.6.4] — 2026-10-03
- **Navegação & Pastas:** Unificação do explorador de arquivos e suporte a seleção de diretórios raiz no Google Drive.

## [v1.6.2] — 2026-09-26
- **Dual Engine:** Aperfeiçoamento do contrato comum entre Media3 e libmpv para renderização precisa de legendas ASS/SSA.

## [v1.6.1] — 2026-09-25
- **Perfis:** Isolamento de histórico e filas de reprodução entre múltiplos perfis de usuário.

## [v1.6.0] — 2026-09-24
- **Interface TV:** Introdução do layout lateral em rail de 80dp e estantes horizontais na Home.

## [v1.5.9] — 2026-09-23
- **Sincronização:** Melhorias na paginação e cache offline de arquivos do Google Drive.

## [v1.5.8] — 2026-09-23
- **Lançamento Inicial:** Suporte a streaming direto do Drive via AndroidX Media3 e libmpv.
