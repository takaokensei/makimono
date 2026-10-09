# Nomes de episódios e variações de abertura

- **Dispositivo:** Moto G34 5G, Android 15, ADB `0086442783`.
- **App:** variante isolada `zechs.drive.stream.visualreview`; fixture abriu `VisualReviewActivity` com `screen=details_episode_names`.
- **Cenário:** `Bakemonogatari_OP4a_.mkv`, `Bakemonogatari_OP4b_.mkv` e `Bakemonogatari - 01.mkv` foram formatados pela mesma política de apresentação usada pelo `SeriesDetailViewModel` e exibidos pelo adapter de produção.
- **Resultado:** cards mostram “Abertura 04A”, “Abertura 04B” e “Episódio 01 - Hitagi Crab, Part One”. Os extras não exibem badge `EP`; o episódio regular mantém `EP 01`. D-pad DOWN alcançou o primeiro card e RIGHT moveu o foco para o segundo, sem perder o anel de foco nem alterar os títulos.
- **Evidência:** `2026-10-07-details-episode-names-focused.png/xml` e `2026-10-07-details-episode-names-right.png/xml` neste diretório.
- **Build/testes:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`; 323 testes, 0 falhas/erros/skips. APK arm64 SHA-256 `EE72FA2105A038928AFC575A27591BB1E5CE7058EDDEEEF0AB06690B45408818`.
- **Limites:** dados sintéticos; não valida extração de metadados de uma biblioteca Drive real, TalkBack ou TV física. Nenhuma conta, histórico ou arquivo de usuário foi acessado.
