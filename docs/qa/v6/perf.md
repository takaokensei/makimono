# Desempenho, Baseline Profile e Redução de Overdraw (Fase 8 — T-801 a T-806)

## 1. Suíte Macrobenchmark (`T-801`)

O módulo `:benchmark` contém os 4 conjuntos de medição instrumentada para TV:
- `StartupBenchmark` (`startupColdNoCompilation`, `startupColdPartialBaselineProfile`): mede `timeToInitialDisplayMs` (TTID) e `timeToFullDisplayMs` (TTFD) em partida a frio (`StartupMode.COLD`, 5 iterações).
- `NavigationBenchmark` (`railNavigationTrip`, `shelfScrollJank`, `seriesDetailOpenCached`, `seriesDetailOpenUncached`): mede `frameDurationCpuMs` e `frameOverrunMs` (P50, P90, P95, P99) na rolagem horizontal das prateleiras da Home e na troca de abas do rail.
- `PlayerBenchmark` (`playerFirstFrameLaunch`): mede o tempo até a superfície de vídeo (`player_view`) após disparo de reprodução.
- `BaselineProfileGenerator` (`generateBaselineProfile`): coleta regras ART para inicialização, navegação no rail, abertura de Detalhe e entrada no Player.

> **Nota de ambiente (Seção 16 do Plano v6):** medições em AVD x86_64 refletem emulação de host; medições de referência para orçamento de quadros em hardware real (Chromecast com Google TV / Mi Box / TV TCL/ Philco) são tarefa exclusiva do proprietário em dispositivo físico dedicado de TV.

## 2. Baseline Profile Versionado (`T-802`)

O arquivo `app/src/main/baseline-prof.txt` está versionado na árvore principal do módulo `:app`, cobrindo os caminhos críticos de inicialização e navegação D-pad:
- `ThisApp.<init>`, `ThisApp.onCreate`
- `MainActivity.<init>`, `MainActivity.onCreate`
- `HomeFragment`, `HomeViewModel`, `FeaturedHeroBinder`, `HomeShelvesBuilder`
- `ContinueWatchingAdapter$ViewHolder.bind`, `StarredShelfAdapter$ViewHolder.bind`, `WatchQueueShelfAdapter$ViewHolder.bind`
- `SeriesDetailFragment`, `SeriesDetailViewModel`
- `BasePlayerActivity`, `PlayerActivity`, `MPVActivity`
- `FocusEngine.attach`, `MotionPolicy.isReduced`, `MediaImageLoader`

A presença e integridade das regras críticas em `app/src/main/baseline-prof.txt` são verificadas pelo teste unitário `MediaImageLoaderTest`.

## 3. Redução de Overdraw (`T-803`)

Como `MainActivity` (`activity_main.xml`) e o tema base (`Theme.DriveStream.*`) já pintam o fundo da janela (`?attr/colorBgBase` / `@color/obsidian_bg_base`), foram removidos os atributos `android:background` redundantes do contêiner raiz de 7 layouts de fragmento, eliminando 1 passe completo de pintura full-screen (1920×1080) em cada tela:
- `app/src/main/res/layout/fragment_files.xml`
- `app/src/main/res/layout-land/fragment_files.xml`
- `app/src/main/res/layout/fragment_series_detail.xml`
- `app/src/main/res/layout-land/fragment_series_detail.xml`
- `app/src/main/res/layout-land/fragment_settings.xml`
- `app/src/main/res/layout/fragment_queue.xml`
- `app/src/main/res/layout/fragment_sign_in.xml`

## 4. Pré-carregamento de Cards nas Prateleiras (`T-804`)

- `MediaImageLoader.preloadCard(context, url)` faz prefetch no cache do Glide com a dimensão exata do card horizontal (`640×400`, `DiskCacheStrategy.AUTOMATIC`).
- `MediaImageLoader.selectNeighborPreloadUrls(urls, focusedIndex, radius = 2)` seleciona até 2 vizinhos à esquerda e 2 à direita do item focado no D-pad, integrado em `ShelvesController.kt` e testado em `MediaImageLoaderTest.kt`.

## 5. Pré-buffer do Próximo Episódio (`T-805`)

- `BingeBufferManager` (testado em `BingeBufferManagerTest.kt`, 9 testes) agenda o pré-aquecimento da conexão HTTP/Drive e metadados do próximo episódio da fila quando o episódio atual atinge a janela final (créditos / pós-play P-12), reduzindo a latência de transição entre episódios consecutivos.

## 6. Guarda de Thread Principal com `StrictMode` (`T-806`)

Em `ThisApp.onCreate()`, quando `BuildConfig.DEBUG` está ativo, `configureDebugStrictMode()` habilita:
- `StrictMode.ThreadPolicy`: `detectDiskWrites()`, `detectNetwork()`, `detectCustomSlowCalls()`, `penaltyLog()`
- `StrictMode.VmPolicy`: `detectLeakedSqlLiteObjects()`, `detectLeakedClosableObjects()`, `penaltyLog()`
