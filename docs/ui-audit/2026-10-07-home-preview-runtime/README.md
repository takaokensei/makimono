# Home: preview dinâmico do hero

## Implementação e evidência atual

O debounce de 250 ms foi extraído para `FeaturedPreviewScheduler` e é compartilhado pela Home real e pela Activity de revisão. `FeaturedHeroBinder` compartilha título, metadados, sinopse, logo, fallback, política de movimento reduzido e crossfade de 400 ms. Na Home integrada, a prévia exige que a aba Início permaneça ativa e o card continue anexado e focado; previews substituídos são cancelados e o job termina com o ciclo de vida da view.

A primeira revisão da fixture provou a atualização do texto, mas não a troca de arte: as séries diferentes reutilizavam o mesmo backdrop de Frieren. Corrigi a fixture de debug para Jujutsu Kaisen usar um backdrop horizontal distinto (`https://images.justwatch.com/backdrop/300823240/s1440/jujutsu-kaisen.jpg`). A mudança está restrita a `src/debug`; não adiciona arte de teste ao app de produção.

Na auditoria do fluxo de dados real encontrei um caso de fallback: após a busca externa não encontrar metadados, o ViewModel descartava a miniatura que já havia vindo do Drive. Agora mantém `posterUrl` ou, na ausência dele, `thumbnailLink` também nos resultados vazios/erro e quando o catálogo não fornece uma imagem. O restante do fluxo integrado continua sem sessão Drive e não foi testado com conta real.

## Evidências no Moto G34

- `home-distinct-initial.png`: hero inicial de Frieren.
- `home-after-dpad-card2.png` e `.xml`: no APK final instalado, D-pad focado o segundo card da fileira “Novos episódios”, com contorno de foco visível (`cardStarredItem`, bounds `[659,320][938,720]`).
- `home-after-dpad-hero.png` e `.xml`: no mesmo APK, depois do debounce, o hero mostra o backdrop horizontal de Jujutsu Kaisen, título e metadados sintéticos atualizados. O XML identifica `Jujutsu Kaisen` e `2026 · 5 episódios · ★ 8,4 · 12+`.
- `home-distinct-jjk-card-focus.png` e `home-distinct-jjk-hero-final.png`: capturas de uma execução anterior da mesma navegação, preservadas como comparação.
- `home-distinct-nav*.xml` e `home-distinct-top.png`: etapas auxiliares da navegação e retorno ao topo.

## Limites da validação

A Activity de revisão usa o scheduler e binder reais, mas popula dados sintéticos; não executa `HomeViewModel.previewFeatured`, a coleta `featuredAnime` do `HomeFragment` nem serviços de metadados. Portanto a transição entre duas artes distintas está provada no renderer compartilhado com D-pad, mas o caminho integrado por ViewModel/Drive segue pendente. O foco contínuo durante o crossfade não foi filmado quadro a quadro. Sem sessão Drive, não foram usados conteúdo ou conta reais; TV física, metadados de rede reais e comportamento em outros tamanhos de tela continuam sem validação.

## Build e testes

- Moto G34, Android 15, landscape 1600×720 px / 280 dpi; pacote isolado `zechs.drive.stream.visualreview`.
- `final-home-smoke.png`: Home de fixture abriu após instalar o APK final; a atividade de revisão permaneceu em primeiro plano.
- `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1`: **291 testes, 0 falhas, 0 erros, 0 skips; build passou**. O build incremental final após trocar a URL da fixture também passou.
- APK arm64 final instalado: SHA-256 `AA78C12C94300010E7E8C681516A7CCD888AE5A917DC1741CB1DDD035118D97D`.
