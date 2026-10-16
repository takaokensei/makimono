# Cobertura do Makimono TV — Matriz de Rastreabilidade

**Data:** 15/10/2026

| `feature_id` | Arquivo / Símbolo no Makimono | Teste Automatizado | Evidência em Dispositivo (`emulator-5554`) | Estado Atual |
| :--- | :--- | :--- | :--- | :--- |
| `NAV-01` | `RailController.kt`, `dimens.xml` (`tv_nav_rail_width=56dp`), `layout-television/fragment_home.xml`, `layout-land/fragment_home.xml`, `layout-sw720dp-land/fragment_home.xml` | `RailControllerTest.kt`, `MakimonoRailCoordinatorTest.kt` | `makimono-dossie/capturas/q11_rail_56dp_collapsed.png` | **Verificado (R00/R05)** |
| `NAV-02` | `RailController.setRailExpanded()`, `dimens.xml` (`tv_nav_rail_expanded_width=240dp`) | `RailControllerTest.kt`, `ContractRegressionAuditTest.kt` | `makimono-dossie/capturas/q11_rail_240dp_expanded.png`, `q11_rail_collapsed_again.png` | **Verificado (R00/R05)** |
| `NAV-03` | `RailController.setupNavigationRail()`, `FocusCoordinator.configureRailVerticalFocus()`, variantes `fragment_home.xml` | `RailControllerTest.kt` | `makimono-dossie/capturas/q11_rail_profile_focused.png`, `q11_rail_favoritos_focused.png` | **Verificado (R00/R05)** |
| `NAV-04` | `HomeFragment.setupBackPressBehavior()`, `RailController.focusActiveRailItem()` | `MakimonoRailCoordinatorTest.kt`, `ContractRegressionAuditTest.kt` | `makimono-dossie/capturas/q11_back_to_rail.png`, `q12_exit_dialog.png` | **Verificado (R00/R05)** |
| `FOC-01` | `FocusMemory.kt`, `ShelvesController.kt`, `HomeFragment.kt`, `FilesFragment.kt`, `SeriesDetailFragment.kt`, `MakimonoFocusMemoryManager.kt` | `FocusMemoryTest.kt`, `MakimonoFocusMemoryManagerTest.kt`, `ContractRegressionAuditTest.kt` | Restauração de foco entre Home ↔ SeriesDetail | **Verificado (R00)** |
| `LIB-01` | `MainActivity.autoDetectLibraryRootsIfNeeded()`, `HomeViewModel.discoverAnimesRoot()` | Testes unitários de descoberta e filtro de raiz | Pasta `oneblacki` carregada em `emulator-5554` (`q11_home_entered.png`) | **Verificado (R00)** |
| `MET-01` | `AnimePosterResolver.kt`, `TenraiAnimeService.kt`, `MalRepository.kt`, `AniSkipRepository.kt` | `AnimeTitleParserTest`, `CatalogQualityGateTest` | Pôsteres, backdrops, notas e sinopses reais renderizados na Home e Detalhes | **Verificado (R00)** |
| `HOM-01` | `HeroController.kt`, `HomeFragment.kt` | `TvFocusChecklistTest.kt` | `makimono-dossie/capturas/q11_home_entered.png` | **Verificado** |
| `HOM-02` | `ShelvesController.kt`, `HomeViewModel.kt` | `ShelvesController` / `FocusMemoryTest.kt` | `makimono-dossie/capturas/q11_rail_56dp_collapsed.png` | **Verificado** |
| `DET-01` | `SeriesDetailFragment.kt`, `SeriesDetailViewModel.kt` | Testes de episódios e foco | `makimono-dossie/capturas/q13_series_detail_entered.png` | **Verificado** |
| `PLY-01` | `PlayerActivity.kt`, `MakimonoPlayerOsdSkipCoordinator.kt` | `PlayerDpadFocusTest.kt`, `MakimonoPlayerOsdSkipCoordinatorTest.kt` | Capturas de reprodução e HUD ativo | **Verificado** |
