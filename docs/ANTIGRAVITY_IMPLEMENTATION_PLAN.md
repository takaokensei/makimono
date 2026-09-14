# Plano de implementação para o Antigravity

## Objetivo

Transformar o Makimono em um media center pessoal para Android TV, com aparência
cinematográfica inspirada no Kodi Estuary, navegação confiável por D-pad e
atualização do APK a partir de uma release privada do GitHub.

O aplicativo não será preparado para distribuição pública. O escopo é uma
instalação pessoal, vinculada ao Google Drive do proprietário e a um repositório
privado. O token de leitura das releases pode ser empacotado no APK porque o
usuário aceitou esse trade-off; ele continua fora do Git e é lido de
`local.properties`.

## Auditoria do estado de referência

As capturas usadas nesta auditoria estão em `docs/kodi_reference/user_feedback/`:

- `current_home_amador.png`: fundo plano, rail pequeno, card de continuar
  assistindo duplicado, ausência de thumbnail e composição de celular em uma TV.
- `kodi_estuary_target.png`: rail visualmente dominante, fundo com textura e
  vinheta, prateleiras de imagens e foco ciano explícito.
- `mydrive_grid.png` e `mydrive_list.png`: referências para as telas de arquivos.

O código já tinha iniciado uma correção: `thumbnailLink` existia em boa parte da
cadeia Drive/Room, o segundo card havia sido removido da Home e havia assets do
Kodi no projeto. Ainda havia, porém, cinco problemas de produto:

1. O tema inicial era Tokyo Night, apesar do objetivo visual ser Kodi Estuary.
2. O rail de TV era um overlay recolhível de 220dp, frequentemente invisível,
   com itens de 44dp e ícones de 18dp.
3. A Home usava fundo sólido e deixava as thumbnails sem presença visual.
4. Os cards de continuar assistindo eram pequenos para uma tela de 10 pés.
5. O updater consultava a página pública do GitHub e tinha fallback HTML, portanto
   não funcionaria corretamente depois que o repositório fosse privado.

Também foi encontrado um risco funcional: `WatchList.watchProgress()` podia
dividir por zero, ultrapassar 100% e considerar 95% não concluído.

## Princípios de UX aplicados

As decisões seguem o padrão de 10-foot UI do Android TV e os princípios
observáveis do Kodi Estuary:

- **Hierarquia em distância:** título e metadados com tamanhos legíveis a cerca
  de três metros, sem depender de texto minúsculo para explicar uma ação.
- **Foco sempre visível:** todo item acionável é focável e usa borda/superfície
  ciano; a direção esquerda/direita não pode “perder” o cursor.
- **Rail estável:** o menu permanece no lado esquerdo em TV; não é necessário
  abrir um painel sobre o conteúdo para descobrir onde está a navegação.
- **Imagem antes de decoração:** thumbnails 16:9 para episódios e pôsteres 2:3
  para séries; scrim inferior apenas para preservar legibilidade.
- **Uma ação, uma representação:** não recriar o mesmo “Continuar assistindo”
  em um hero e em um card separado. A Home deve ter uma única prateleira para
  retomadas, além do destaque opcional e do catálogo.
- **Consistência visual:** usar petróleo/preto/ciano do Estuary, espaçamento
  repetível e estados vazio, carregando, erro, foco e selecionado explícitos.
- **Progressive disclosure:** detalhes secundários ficam em badges e na tela de
  detalhes; a Home privilegia retomar, explorar e abrir a biblioteca.

Referências de princípio:

- Android TV: `https://developer.android.com/training/tv/start/start`
- Android TV design: `https://developer.android.com/design/ui/tv`
- Material Navigation Rail: `https://m3.material.io/components/navigation-rail/overview`
- Código e assets do Estuary já versionados em `docs/kodi_reference/`.

## Mudanças incluídas neste patch

### 1. Atualização privada

Arquivos:

- `app/build.gradle`: lê `github.apiToken` de `local.properties` e expõe apenas
  `BuildConfig.GITHUB_API_TOKEN` para o build pessoal.
- `local.properties.example`: documenta o novo valor local.
- `GithubApi.kt`: exige o header `Authorization` na API de releases.
- `GithubRepository.kt`: consulta somente a API autenticada; remove o fallback
  para página HTML pública e os URLs construídos sem autenticação.
- `AppUpdateManager.kt`: envia o token ao baixar o asset privado.
- `ApiModule.kt`: simplifica a injeção do repositório.

Configuração local mínima:

```properties
github.apiToken=SEU_TOKEN_PESSOAL_COM_ACESSO_DE_LEITURA
```

Não adicionar esse valor ao Git. O token dentro do APK pode ser extraído por uma
pessoa com acesso ao APK; isso é intencional neste app privado e não deve ser
reutilizado em produto distribuído.

### 2. Shell visual da TV

Arquivos:

- `themes.xml`, `AppSettings.kt` e `MainViewModel.kt`: Kodi Estuary passa a ser o tema inicial e o
  fallback quando não existe preferência salva.
- `MainActivity.kt`: considera Kodi como tema já aplicado no primeiro frame para
  evitar flash de Tokyo Night.
- `bg_anime_library.xml`: usa `kodi_primary_bg.jpg` com scrim horizontal.
- `bg_sidebar_panel.xml`, `nav_item_active_bg.xml` e `rail_item_focus_bg.xml`:
  rail opaco, seleção ciano e foco com contraste de TV.
- `dimens.xml`: centraliza 240dp de rail, item de 56dp, ícone de 32dp e cards
  de 300x169dp.
- `layout-land/fragment_home.xml`: define a composição dedicada de TV, com rail
  persistente de 240dp, cabeçalho de biblioteca, busca, hero opcional, uma única
  prateleira de retomadas e catálogo visível na Home.
- `fragment_home.xml` e `layout-port/fragment_home.xml`: continuam como
  fallback para telas não-TV, sem depender da composição horizontal de 10 pés.
- O rail de TV desloca o conteúdo para não ficar atrás do menu, aumenta os
  ícones e troca a marca visual para `MAKIMONO MEDIA`.

### 3. Conteúdo e foco

- `item_continue_watching_shelf.xml`: cards maiores em 16:9, scrim mais alto,
  tipografia maior, barra ciano de 4dp e foreground de foco.
- `item_drive_file_grid.xml`: foreground de foco separado da imagem, sem cobrir
  o pôster quando o item recebe foco.
- `HomeFragment.kt`: rail persistente no modo TV, navegação direita para o
  conteúdo e esquerda de volta para o item de menu correspondente. O layout
  retrátil continua compatível com variantes que realmente exibirem overlay.
- A Home TV exibe a biblioteca diretamente no primeiro painel, mantém apenas
  uma prateleira de “Continuar assistindo” e atualiza hero, contagem e estado
  vazio conforme os flows de biblioteca, histórico e destaque.
- `ContinueWatchingAdapter.kt`: escolhe a thumbnail ampliada do histórico.
- `shelf_colors.xml`: define o recurso de cor usado pelos cards que já referiam
  `shelf_card_stroke_color`.

### 4. Dados e regressão

- `WatchList.kt`: progresso limitado a 0..100, duração inválida tratada como 0,
  limiar de conclusão em 95% e thumbnail ampliada como extension property para
  não virar coluna Room.
- `WatchListTest.kt`: cobre duração zero, valores fora da faixa e limiar de 95%.

## Sequência recomendada para o Antigravity

1. Aplicar o diff na raiz do clone e confirmar que somente os arquivos listados
   foram alterados.
2. Criar `local.properties` a partir de `local.properties.example`, preencher
   credenciais do Drive e `github.apiToken`, sem versionar o arquivo.
3. Criar uma release privada no repositório com os nomes de APK esperados pelo
   `LatestRelease.getBestApkAsset()` (`arm64-v8a`, `armeabi-v7a`, `universal`,
   `x86` ou `x86_64`).
4. Compilar com `./gradlew testDebugUnitTest assembleDebug`.
5. Instalar em uma TV 1920x1080 e validar primeiro o fluxo sem dados: login,
   Home vazia, erro de rede e loading.
6. Validar com dados reais: pasta `oneblacki`, pastas com pôster, vídeo com
   `thumbnailLink`, retomada de episódio e histórico com mais de um item.
7. Validar o D-pad nesta matriz mínima:
   - menu: cima/baixo e direita para o conteúdo;
   - primeira coluna do catálogo: esquerda para o rail;
   - primeiro card da prateleira: esquerda para o rail;
   - voltar: não fecha o app enquanto houver uma tela/modal de contexto.
8. Validar a release privada: consultar latest release com token, baixar asset
   compatível, mostrar progresso e abrir o instalador Android.
9. Comparar uma captura 1920x1080 com `kodi_estuary_target.png`, observando
   principalmente escala do rail, contraste do foco, proporção dos cards e
   ausência de uma segunda representação de “Continuar assistindo”.

## Critérios de aceite

- A primeira abertura sem preferência salva usa o tema Kodi e não mostra um
  frame inicial Tokyo Night.
- O rail mede aproximadamente 24% da área útil de TV, tem itens de 56dp e
  ícones de 32dp, e permanece legível sem animação de abertura.
- A Home exibe fundo com textura/vinheta, thumbnails de vídeo quando o Drive as
  fornece e barra de progresso ciano.
- Existe apenas uma prateleira de retomadas; o estado vazio não cria um card
  fictício duplicado.
- O D-pad nunca deixa o usuário sem foco visível ao atravessar rail, hero,
  prateleira e grid.
- O updater não consulta nem monta URLs de release pública sem autenticação.
- `WatchList` não quebra com duração zero e os testes unitários cobrem esse caso.
- `./gradlew testDebugUnitTest assembleDebug` passa no ambiente Android configurado.

## Fora do escopo intencional

- Cadastro de usuários, backend próprio, ACL, hardening de token ou distribuição
  pública.
- Migração para Jetpack Compose: o projeto atual usa XML/ViewBinding e a mudança
  visual deve permanecer pequena e reversível.
- Reescrita do player ExoPlayer/MPV, do parser de episódios ou da integração MAL.
- Download e armazenamento local de fanart; o app deve usar URLs do Drive e os
  posters já resolvidos, com cache do Glide.

## Segunda passagem: acabamento e performance

Esta passagem não aumenta o tamanho do código artificialmente. O objetivo é
reduzir trabalho repetido, deixar a UI responsiva e concentrar decisões visuais
em superfícies glassmorphism com hierarquia Swiss: grade disciplinada, títulos
curtos, contraste alto e foco ciano inequívoco.

- `MediaImageLoader.kt`: centraliza placeholder, cache automático, decode limitado,
  thumbnail progressiva e crossfade curto para cards, posters e backdrops.
- `ThumbnailUrl.kt`: normaliza apenas formatos conhecidos de thumbnail do Drive;
  URLs externas desconhecidas não recebem sufixos inválidos.
- `DriveRepository.kt`: adiciona paginação limitada e segura para não truncar
  bibliotecas grandes no primeiro lote.
- `SeriesDetailViewModel.kt`: publica primeiro o shell baseado nos arquivos locais
  e no Drive, depois enriquece com AniList/Tenrai sem bloquear a primeira pintura.
- `layout-land/fragment_series_detail.xml`: composição TV compacta, com hero
  dimensionado para deixar temporadas e títulos de episódios visíveis no primeiro
  viewport.
- `layout-land/fragment_settings.xml`: configurações TV com linhas glass, foco
  previsível e hierarquia visual consistente.
- Cards de episódio agora usam uma única `MaterialCardView` com clipping e foco
  próprios, evitando thumbnail quadrada dentro de fundo arredondado.
- A tela de detalhes tem retry explícito para falhas de rede e preserva o foco
  inicial durante a atualização assíncrona de metadados.
