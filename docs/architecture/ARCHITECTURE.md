# Arquitetura — Makimono

Documento canônico de arquitetura do Makimono.  
Data de referência: Outubro de 2026.

---

## 1. Visão Geral

Makimono é um cliente de streaming de mídia direto do Google Drive voltado prioritariamente para telas de 3 metros (Android TV) e dispositivos móveis Android.

A arquitetura segue o princípio de camadas desacopladas com separação estrita de responsabilidades:

```text
zechs.drive.stream
├── domain/          # Kotlin puro. Sem Android. Modelos, casos de uso, políticas
│   ├── library/     # Biblioteca, pastas, temporadas, episódios, filme vs série
│   ├── playback/    # Progresso, próximo episódio, regras de conclusão
│   └── common/      # Result, AppError (com mensagem segura para UI)
├── data/            # Implementações. Android permitido
│   ├── drive/       # Cliente do Drive, autenticação OAuth, paginação
│   ├── metadata/    # Provedores de metadados externos (AniList, Jikan, Kitsu, TMDB)
│   ├── local/       # Room: entidades, DAOs, migrações
│   └── prefs/       # DataStore e configurações
├── playback/        # Motores e controle de sessão
│   ├── engine/      # PlayerEngine (contrato), Media3Engine, MpvEngine
│   ├── session/     # PlaybackSession, ProgressRecorder, SkipController
│   └── display/     # DisplayModeController (AFR, HDR)
├── platform/        # Integrações de SO: MediaSession, Watch Next, Now Playing
├── ui/              # Camada de apresentação
│   ├── tv/          # Telas otimizadas para D-pad / 10-foot experience
│   ├── mobile/      # Telas de toque
│   ├── common/      # Componentes reutilizáveis (StateView, cards, rail)
│   └── theme/       # Design System (Obsidian), tokens, formas
└── di/              # Módulos de injeção de dependência Hilt
```

---

## 2. Regras de Dependência

1. **`domain` é puro:** Não importa classes de `android.*`, `androidx.*`, `data`, `playback`, `platform` ou `ui`.
2. **`ui` não conhece implementações concretas:** Depende apenas de `domain` e de interfaces/contratos de `data` e `playback`.
3. **`playback` não importa `ui`:** Motores de reprodução desconhecem componentes visuais.
4. **`data` não importa `ui`:** Repositórios e fontes de dados desconhecem apresentação.

Estas regras são validadas por testes automatizados de arquitetura.

---

## 3. Decisões Arquiteturais (ADRs)

- [ADR-001: Pareamento de TV e Login Remoto](adr/ADR-001-tv-pairing.md)
- [ADR-002: Motor de Reprodução (Media3 + MPV)](adr/ADR-002-motor-de-reproducao.md) *(a formalizar na Fase 4)*
- [ADR-003: Aceite de Risco de Segredos em Build](adr/ADR-003-segredos-no-build.md) *(Fase 0)*
- [Guia do MPV Nativo](native-mpv.md)
