# Kodi Media Player Architectural Analysis & Feature Roadmap for Makimono

**Source Repository:** [xbmc/xbmc (Kodi)](https://github.com/xbmc/xbmc) & Official Kodi Addon Ecosystem
**Target Application:** Makimono (Google Drive Anime/Media Streaming for Android TV & Mobile)
**Date:** 2026-10-06
**Auditor:** Senior Full-Stack & AI Systems Engineer (Antigravity)

---

## Executive Summary

Kodi (formerly XBMC) is the open-source industry benchmark for **10-foot living room media playback** with over two decades of evolution in C++, OpenGL/DirectX, and media pipelines.

Makimono has already adopted Kodi's **Estuary visual design philosophy** and its **Technical OSD Info HUD**. However, Kodi contains a vast treasury of battle-tested media features, audio DSP algorithms, library automation paradigms, and playback resilience mechanics that can elevate Makimono from a streaming app to an **unrivaled Apple-grade & Kodi-grade media center**.

This document outlines **12 high-impact features** discovered in Kodi's codebase, mapped with implementation architecture, exact Android APIs, and priority for Makimono.

---

## 1. Feature Breakdown: Kodi vs. Makimono Roadmap

| # | Kodi Feature & Source Architecture | Problem Solved | Proposed Makimono Implementation | Priority |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **Additive Skip Steps (`seeksteps`)**<br>`CPlayerController::OnAction`<br>`advancedsettings.xml` | Pressionar D-pad repetidamente pula intervalos fixos chatos (ex: 10s em 10s demora pra pular 3 min). | **Progressive Step Jumping:**<br>1º toque = +10s<br>2º toque rápido = +30s<br>3º toque = +1m<br>4º toque = +3m<br>5º toque = +5m. Reseta após 1.2s sem input. | **P0 (Alta)** |
| **2** | **Night Mode / Dialogue Enhancement**<br>`GUIDialogAudioSettings.cpp`<br>`lavfi=[dynaudnorm]` & `Volume Amplification` | Filmes e animes têm explosões/música altíssimas e vozes muito baixas, forçando o usuário a mexer no volume da TV toda hora. | **Dual-Engine Audio DRC:**<br>• **MPV:** Ativar filtro `lavfi=[dynaudnorm=f=250:g=31:p=0.5]` via chave no menu de áudio.<br>• **ExoPlayer:** `DynamicsProcessing` (API 28+) acoplado à `audioSessionId`. | **P0 (Alta)** |
| **3** | **Video Bookmarks (Marcadores de Cena)**<br>`CVideoDatabase::AddBookmark`<br>Tabela `bookmark` no `MyVideos.db` | O usuário quer salvar momentos marcantes de um anime/filme (lutas épicas, abertura favorita, créditos). | **Makimono Scene Bookmarks:**<br>Criar tabela Room `video_bookmarks(id, fileId, profileId, timestampMs, label, thumbnailPath)`.<br>Botão no OSD exibe drawer com marcadores visuais. | **P1 (Média)** |
| **4** | **Auto Refresh Rate Matching (AFR)**<br>`CRenderManager::AdjustFrameRate`<br>Kodi Android TV Video Settings | "Judder" ou micro-engasgos em vídeos 24fps rodando em telas 60Hz. | **Android Display Mode Matching:**<br>Utilizar `Display.getSupportedModes()` e `Window.getAttributes().preferredDisplayModeId` (Android 6.0+) para alternar a TV para 24.000Hz/23.976Hz quando o arquivo for 24p. | **P1 (Média)** |
| **5** | **Binge-Watching "Up Next" Intelligent Queue**<br>`service.upnext` (Add-on)<br>`CGUIWindowFullScreen` | Ao terminar um episódio, o usuário quer pular para o próximo sem tocar no controle, mas com opção de cancelar. | **Seamless Binge Countdown:**<br>Detectar créditos finais via AniSkip ou aos últimos 35s. Exibir overlay PIP sutil com contador de 10s, miniatura do próximo episódio e auto-start com pré-carregamento de buffer. | **P0 (Alta)** |
| **6** | **Aspect Ratio Modes (Crop / Stretch / Zoom)**<br>`CVideoSettings::SetViewMode`<br>`Normal, Zoom, Stretch 16:9, Original` | Animes antigos em 4:3 (ex: Evangelion/Yu Yu Hakusho) ou filmes ultrawide 2.39:1 em TVs 16:9. | **View Mode Cycler:**<br>Adicionar botão de Aspect Ratio no HUD alternando:<br>1. Ajustar (Letterbox padrão)<br>2. Preencher (Zoom cortando topo/baixo)<br>3. Esticar (Stretch 16:9)<br>4. Original (1:1 pixel). | **P1 (Média)** |
| **7** | **Android TV Leanback Home Channels & Watch Next**<br>`TvContractCompat`<br>`SyncProgramsJobService.java` | Usuário precisa abrir o app para ver o que estava assistindo. | **Launcher Recommendations Channel:**<br>Alimentar a linha "Continuar Assistindo" da tela inicial do Google TV / Android TV usando `TvContractCompat.PreviewPrograms`. | **P2 (Desejável)** |
| **8** | **Auto Subtitle Fetcher (OpenSubtitles REST v2)**<br>`service.subtitles.opensubtitles-com` | Arquivo no Google Drive não tem legenda PT-BR embutida nem na pasta. | **In-App Subtitle Search:**<br>Integrar endpoint oficial da OpenSubtitles v2 REST API buscando pelo hash do arquivo ou pelo nome limpo do episódio, salvando localmente em cache. | **P1 (Média)** |
| **9** | **Artwork Hierarchy (Poster, Fanart, Clearlogo, Banner)**<br>`ListItem.getArt(key)`<br>Kodi Artwork Guidelines | Banners cortados ou esticados quando o anime tem proporção diferente. | **Multi-Asset Art Cache:**<br>Estruturar `FolderMetadata` para conter `fanartUrl` (16:9 1080p), `posterUrl` (2:3), `clearLogoUrl` (PNG transparente do título) e `bannerUrl`. Renderizar `clearLogo` sobre o hero da Home igual à Netflix/Apple TV. | **P0 (Alta)** |
| **10** | **Pitch-Corrected Playback Speed (`scaletempo`)**<br>`ActiveAE` / FFmpeg `scaletempo` | Ao acelerar o vídeo para 1.25x ou 1.5x, a voz fica fina ("voz de esquilo"). | **Pitch Preserved Speedup:**<br>• No MPV já é garantido por `scaletempo=yes` (padrão).<br>• No ExoPlayer garantir `PlaybackParameters(speed, /* pitch = */ 1.0f)` com áudio Sonic habilitado. | **P1 (Média)** |
| **11** | **Smart Playlists (Filtros Dinâmicos)**<br>`CSmartPlaylist`<br>Regras baseadas em ano, gênero, progresso | Listas estáticas não se adaptam aos gostos do usuário. | **Smart Shelves:**<br>Prateleiras automáticas geradas no Home: "Animes da Temporada Atual", "Não assistidos", "Animes Curtos (<13 eps)", "Top Avaliados no MAL". | **P2 (Desejável)** |
| **12** | **Audio & Subtitle Offset Persistence per Media**<br>`CVideoSettings::m_audioDelay` & `m_subtitleDelay` | O usuário calibra o atraso de áudio/legenda num episódio e perde a configuração ao fechar o player. | **Per-Video Sync Persistence:**<br>Armazenar `audioDelayMs` e `subtitleDelayMs` na tabela `WatchList` para reaplicar automaticamente ao retomar o mesmo episódio. | **P0 (Alta)** |

---

## 2. Deep Dive: Top 4 Features Imediatas para Implementação

### Feature 1: Additive Skip Steps (Pulo Progressivo no D-Pad)
* **Comportamento Kodi:**
  - 1º clique Esquerda/Direita: pula 10 segundos.
  - 2º clique dentro de 1 segundo: pula 30 segundos acumulados.
  - 3º clique rápido: pula 60 segundos.
  - 4º clique rápido: pula 180 segundos (3 minutos).
  - 5º clique rápido: pula 300 segundos (5 minutos).
* **Benefício:** Elimina a necessidade de abrir o scrubber e permite avançar blocos de 3 a 5 minutos (aberturas ou cenas longas) com apenas 4 cliques intuitivos no controle remoto da TV.

### Feature 2: Night Mode / Dynamic Audio Normalizer (DRC)
* **Comportamento Kodi:**
  - Processa o áudio em tempo real elevando frequências de voz humana e comprimindo picos de explosões e trilhas sonoras.
* **No Makimono (MPV):**
  - Comando direto: `MPVLib.setOptionString("af", "lavfi=[dynaudnorm=f=250:g=31:p=0.5]")`
  - Uma única opção no menu de Áudio ("Modo Noturno / Realçar Vozes") resolve o problema mais comum de assistir filmes em Smart TVs sem home theater dedicado.

### Feature 3: ClearLogo Artwork no Hero da Home
* **Comportamento Kodi Estuary:**
  - Em vez de renderizar texto cru para títulos de obras consagradas, exibe um PNG transparente do logotipo estilizado da franquia (`clearlogo.png`) sobreposto ao backdrop escurecido.
* **No Makimono:**
  - A API do AniList / Fanart.tv fornece `clearlogo`. Adicionar essa camada visual transforma a Home do Makimono imediatamente na estética premium do Apple TV+ / Kodi Estuary.

### Feature 4: Per-Media Sync Memory (Persistência de Sincronia de Áudio e Legenda)
* **Comportamento Kodi:**
  - Toda alteração em milissegundos feita no slider de delay é associada ao `idFile` no banco de dados local.
* **No Makimono:**
  - O usuário já possui o menu glass com ajuste de `±250ms`. Bastará persistir esse valor no Room database (`WatchList.audioDelayMs` / `subtitleDelayMs`) para nunca mais precisar recalibrar o mesmo arquivo.

---

## 3. Conclusão & Próximos Passos
Este plano de recursos une a robustez histórica do Kodi com a agilidade e modernidade do design Apple HIG. Cada uma dessas 12 melhorias é 100% viável no stack Kotlin + ExoPlayer/MPV do Makimono.
