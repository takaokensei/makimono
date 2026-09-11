# 🎬 AUDITORIA TÉCNICA E REFINAMENTO DE UI: TRADUÇÃO DIRETA DO KODI ESTUARY PARA ANDROID TV

> **Para**: Arquiteto de Software Android Sênior e Especialista em 10-Foot UI / Design de Mídia.
> **Contexto**: O usuário testou a versão anterior na Smart TV (TCL P755) e constatou que a interface ainda está **amadora (Nota ~4.5/10)**. Falta o principal atrativo visual do Kodi: **a atmosfera cinematográfica com fotos de preview (fanart/posters) de alta qualidade**, proporções exatas da tela de 1920x1080 e ausência de redundâncias visuais.

---

## 📸 ANÁLISE DOS PRINTS DA TELA ATUAL VS. KODI REAL

Foram salvas capturas reais na pasta **`docs/kodi_reference/user_feedback/`** deste projeto:

1. **`current_home_amador.png` (O estado atual do app)**:
   - **Vazio e sem arte**: Fundo escuro plano sem vinheta nem fanart.
   - **Redundância bizarra**: Existem DOIS blocos de "CONTINUAR ASSISTINDO" empilhados na tela inicial — um card largo ocupando a tela inteira e, logo abaixo, outro card pequeno truncado (`[Erai-raws] Tensei Sli...`).
   - **Sem imagem de preview**: O card de vídeo não exibe miniatura, apenas um ícone genérico de play e texto.
   - **Menu lateral acanhado**: Ícones pequenos de 22dp e espaçamento genérico de celular em vez da presença visual marcante do Kodi.
2. **`kodi_estuary_target.png` (O padrão de excelência visual)**:
   - **Fundo com vinheta e textura**: Textura cinematográfica original (`primary.jpg`) escurecendo em degradê radial em direção às bordas.
   - **Prateleiras com Pôsteres e Miniaturas Ricas**: Imagens de preview com badges de progresso ciano (`#12A0C7`), proporções 16:9 ou 2:3.
   - **Menu Lateral Imponente**: 24% da largura da tela (462px / 1920px), item com 95px de altura e zona de ícone generosa de 95x95px.
3. **`mydrive_grid.png` e `mydrive_list.png`**:
   - Telas de arquivos limpas, mas ainda sem a riqueza estética de um media center.

---

## 💎 ELEMENTOS RIPPADOS DO REPOSITÓRIO OFICIAL DO KODI

Para viabilizar a tradução direta, foram clonados e extraídos do repositório da skin Estuary (`xbmc/skin.estuary`) para a pasta **`docs/kodi_reference/ripped_assets/`**:
- **`kodi_primary_bg.jpg`**: A textura de fundo oficial do Estuary (297 KB, gradiente petróleo com vinheta radial).
- **Ícones Oficiais do SideMenu**: `videos.png`, `movies.png`, `tv.png`, `favourites.png`, `addons.png`, `music.png`, `pictures.png`.
- **Ícones Utilitários**: `settings.png`, `power.png`, `search.png`, `filemanager.png`.
- **Máscaras e Foco**: `focus.png`, `osdfade.png`.

---

## 📐 CONSTANTES E PROPORÇÕES EXATAS DO KODI ESTUARY (Canvas 1920x1080)

Extraídas diretamente de `addons/skin.estuary/xml/Home.xml` e `colors/defaults.xml`:
* **Cores Oficiais**:
  * `background`: `#FF000000` (Preto puro na base da janela).
  * `primary_background`: `#FF0E597E` (Azul petróleo profundo).
  * `dialog_tint`: `#FF1A2123` (Ardósia escura para superfícies/cartões).
  * `dialog_header_tint`: `#FF147995`.
  * `button_focus`: `#FF12A0C7` (Ciano elétrico vibrante do foco D-Pad).
  * `white`: `#FFF0F0F0` | `grey`: `#FFA0A0A0` | `selected`: `#FFFFB70F`.
* **Métricas do Rail Lateral (`control id="9000"` em `estuary_Home.xml`)**:
  * Largura do menu lateral: **462px / 1920px** = **24.06%** da largura total da tela (em Android TV 960dp width canvas = ~**230dp–240dp**).
  * Altura de cada item do menu: **95px / 1080px** = **8.8%** (~**56dp**).
  * Bloco do ícone: **95x95px** (~**48dp–56dp** com ícone centralizado de 32dp–36dp, não míseros 22dp).

---

## 🚀 SOLUÇÃO DE ARQUITETURA: COMO TER AS "FOTOS DE PREVIEW LINDÍSSIMAS DO KODI" DE FORMA LEVE

No Kodi, o media center faz scraping local de fanarts. No nosso app de Google Drive:
1. **Google Drive API nativamente gera miniaturas de quadros de vídeo!**
   - Na chamada da API em [`DriveApi.kt`](file:///c:/Users/Cauã%20V/Documents/antigravity/intelligent-mendel/app/src/main/java/zechs/drive/stream/data/remote/DriveApi.kt):
     Basta incluir o campo `thumbnailLink` no parâmetro `fields`:
     ```kotlin
     fields: String = "nextPageToken, files(id, name, size, mimeType, iconLink, thumbnailLink, shortcutDetails, starred)"
     ```
   - O Google Drive retorna a URL de miniatura gerada automaticamente do vídeo (ex.: `https://lh3.googleusercontent.com/...=s220`).
2. **Propagação de Dados**:
   - Adicionar `thumbnailLink: String?` em `DriveFile.kt` e `FilesResponse.kt`.
   - Adicionar `thumbnailLink: String? = null` na entidade Room `WatchList.kt`.
   - Ao registrar reprodução no `WatchListRepository`/`PlayerActivity`, gravar o `thumbnailLink`.
3. **Renderização via Glide com Zero Sobrecarga**:
   - O app já tem o Glide com integração OkHttp autenticada configurada!
   - Nos cartões da prateleira de "Continuar Assistindo" (`item_continue_watching_shelf.xml`):
     - Usar um `ImageView` com proporção 16:9 (`layout_constraintDimensionRatio="16:9"`).
     - Carregar a `thumbnailLink` com Glide (com placeholder escuro e `centerCrop`).
     - Aplicar gradiente preto sutil na base da miniatura para legibilidade do título e da barra de progresso em `#12A0C7`.

---

## 📋 SUA TAREFA: EXECUTAR O PLANO E GERAR O PATCH `.diff`

Elabore sua auditoria e forneça a implementação prática em 4 etapas:

### 1. Diagnóstico Crítico (Nota 0 a 10)
Analise as falhas apontadas em `current_home_amador.png` (redundância de cards, ausência de imagens de preview, proporções erradas do rail).

### 2. Especificação da Tradução Direta do Kodi
Explique como os assets de `docs/kodi_reference/ripped_assets/` e o `thumbnailLink` do Google Drive transformarão a Home num media player com acabamento profissional.

### 3. Plano de Mudanças
* Eliminação do card duplicado `cardContinueWatching` (mantendo apenas o carrossel horizontal de alta qualidade).
* Redimensionamento do Left Rail para os 24% da tela com itens de 56dp de altura e ícones grandes.
* Inclusão do background oficial com vinheta (`kodi_primary_bg.jpg` ou gradiente exato `#0E597E` / `#000000`).
* Adição de `thumbnailLink` na API do Drive, no modelo de dados e no card da prateleira.

### 4. Patch Completo (`.diff`)
Gere o patch consolidado em formato **Unified Diff (`git diff`)** pronto para aplicação via `git apply`:
- Atualização de layouts XML (`fragment_home.xml`, `item_continue_watching_shelf.xml`, seletores de foco).
- Atualização do modelo `DriveFile`, `FilesResponse`, `WatchList`, `DriveApi.kt` e adapters Glide.
- Garantia total de compilação sem erros via `./gradlew assembleDebug`.
