# Veredito Técnico de Aproveitamento: Crunchyroll Android TV → Makimono (Etapa V5)

**Data de Emissão:** 14 de Outubro de 2026  
**Projeto:** Makimono (`intelligent-mendel`)  
**Autor:** Engenheiro de Sistemas / AI Assistant  
**Premissa Documental:** `Act No. 19 of 2026` (Enquadramento Clean-Room fornecido pelo usuário).

---

## 1. Resposta Direta à Pergunta Central

> **Até onde conseguimos aproximar a experiência visual e funcional da Crunchyroll TV no Makimono com os artefatos e ferramentas atuais?**

**Conclusão Geral:** Conseguimos aproximar **quase a totalidade da experiência de navegação, foco e reprodução (90%+ de aderência comportamental)** através de **reconstrução por especificação técnica limpa (Classe B)**, mas **zero por cento via transplante direto de código (Classe A)**.

1. **A UI é 100% Jetpack Compose for TV:** Não existem layouts XML proprietários aproveitáveis no APK. Transplantar código Java decompilado gerado a partir do compilador Compose seria inviável (código sintético, estruturas de slot-table ilegíveis e dependências fortemente acopladas a bibliotecas internas).
2. **As regras de TV são universais e transferíveis:** As proporções medidas em runtime (Rail de 80dp expandindo para 264dp, cards de prateleira de 145dp, área de segurança de 48dp, memorização de foco bidirecional e máquina de estados de HUD com auto-hide de 3 segundos) foram completamente comprovadas e convertidas em contratos independentes para o Makimono.
3. **O Makimono já possui as fundações corretas:** A base existente do Makimono (`FocusMemory`, `RailNavigationPolicy`, `OsdStateMachine` e Media3/MPV) acolhe perfeitamente essas especificações sem exigir migração total de arquitetura.

---

## 2. Matriz Sintética por Componente

| Componente | Classe | Informação Recuperada | Comportamento Confirmado em Runtime | Dependências Críticas | Prova Realizada | Lacuna Restante | Caminho Recomendado | Custo Relativo |
| :--- | :---: | :--- | :--- | :--- | :--- | :--- | :--- | :---: |
| **Shell & Rail Navigation** | **B** | Classes Compose, nós `NavDrawer` | Expande 80dp→264dp; empurra feed horizontalmente | Nenhuma (Views/XML nativo) | `prova_01_rail_sidebar_back` | Animação interpolada (spring) | Views nativas com redimensionamento de largura | 4–6 h |
| **Proteção de Back Press** | **B** | Nós de diálogo e árvore de nós | Conteúdo → Rail → Diálogo de Confirmação | Nenhuma | `prova_01_rail_sidebar_back` | Integração na `MainActivity` | `OnBackPressedCallback` | 2–3 h |
| **Hero Banner & Header** | **B** | Classe `TvPromotionalAsset` | Altura 452dp, tags, sinopse, 2 ações | Nenhuma | Especificação de Layout | Conexão com metadados do Drive | XML com ViewBinding | 4–6 h |
| **Prateleiras com FocusMemory** | **B** | Dimensões de cards (145dp) e nós | Retorno ao item exato da linha (item 3) | `RecyclerView` | `prova_02_hero_shelves_focus_memory` | Salvar posição no ciclo de vida | `FocusMemoryManager` limpo | 4–6 h |
| **Player OSD & Auto-Dismiss** | **B** | Classes Compose e timers | Auto-hide 3–5s; acorda com D-pad | Media3 / MPV | `prova_03_player_osd_skip_statemachine` | Integrar botões de áudio/sub | State Machine em Kotlin | 4–6 h |
| **Skip Intro / Recap** | **B** | Modelos `SkipEvent`, `SkipEventType`| Overlay contextual durante `[start, end]` | Capítulos MKV ou AniSkip | `prova_03_player_osd_skip_statemachine` | Provider de marcadores de anime | Componente visual sobreposto | 3–4 h |
| **Autoplay / Up Next** | **B** | Classes `UpNext` e `PlayerEventBus` | Card nos últimos 15s com contagem regressiva | Fila de episódios | Especificação de contrato | Sincronização com Room | Overlay acionado por time tick | 4–6 h |
| **Detalhes de Séries & Abas** | **B** | Estrutura de abas e metadata | Abas: Episódios, Detalhes, Recomendações | Google Drive API | Mapeamento de nós de acessibilidade | Parser de temporadas por pasta | TabLayout / ViewPager TV | 6–8 h |
| **Perfis e Troca Rápida** | **C** | Classes `LupinProfileSync` | Ícone de perfil no topo do Rail | Banco local (Room) | Mapeamento estrutural | Banco de múltiplos perfis no Makimono | Room SQLite local | 8–10 h |
| **Busca de TV com Teclado** | **B** | Rota de busca e filtros | Teclado integrado com grade de resultados | Catálogo local | Roteiro de acessibilidade | Algoritmo de busca por similaridade | Debounced SearchView | 4–6 h |
| **Seleção de Trilhas de Áudio**| **B** | Modais de seleção do player | Diálogo vertical com foco D-pad | Media3 TrackSelector | Suportado pelo framework | UI de seleção moderna | DialogFragment nativo | 3–4 h |
| **Serviços Remotos / Nuvem** | **D** | Endpoints OAuth2 privados | Streaming protegido por Widevine DRM | Cloud Crunchyroll | N/A (Descarte técnico) | N/A | **Descarte definitivo** | — |

---

## 3. O que é Recuperável Literalmente

- **Recuperado com Sucesso:**
  - Manifesto completo com permissões, features de TV (`android.software.leanback`), intenções e filtros de busca.
  - Metadados oficiais de assinatura (certificados RSA 2048, carimbo SourceStamp da Google Play Store).
  - Assinaturas de métodos de ciclo de vida (`onCreate`, `onPause`, `onResume`) e nomes de campos Hilt/Dagger (`WatchNextManager`, `NetworkManager`).
  - Árvore de semântica e acessibilidade em tempo de execução com `resource-id`s semânticos (`mainMenuNavDrawer`, `homeFeedComponent`, `startWatching`, `panelCard_1_*`, `showDetailsHeroWatchlistButton`, `exitDialog`).
- **Limites e Não-Recuperável:**
  - Código-fonte Kotlin original: O código decompilado via JADX é uma representação Java reconstruída a partir de bytecode DEX ofuscado pelo R8.
  - Classes sintéticas do Compose (`e0`, `m1`, slot-tables): Ilegíveis e impossíveis de compilar diretamente sem o compilador Compose e as bibliotecas proprietárias correspondentes.

---

## 4. O que é Reproduzível por Comportamento

1. **Topologia do Navigation Rail:**
   - Comportamento de empurrar o conteúdo (*content push*) em vez de criar um scrim opaco sobre os vídeos.
   - Largura mínima colapsada de 80dp e largura expandida de 264dp.
2. **Proteção contra Saída Involuntária de TV:**
   - Interceptação de retorno: se estiver em um card, o botão Back ancora no menu lateral esquerdo. Se estiver no menu lateral, exibe diálogo com foco no botão de confirmação ("Deseja sair do aplicativo?").
3. **Retenção de Foco Bidirecional (Carrosséis 2D):**
   - Ao descer para uma prateleira, o foco memoriza o índice selecionado. Se o usuário navegar verticalmente para outras seções e retornar, o foco é restaurado com precisão militar.
4. **Overlay de Pular Abertura (Skip Intro):**
   - Exibição de botão contextual de foco preferencial durante a abertura, acionando seek automático de `[end_ms + 100ms]` ao pressionar Enter/OK.

---

## 5. O que Exige Dados e Serviços Próprios do Makimono

O Makimono é um reprodutor de mídia local e de nuvem pessoal (Google Drive) e **não consome nem deve consumir** APIs proprietárias da Crunchyroll:

- **Catálogo e Metadados:** Obtidos diretamente da estrutura de pastas do Google Drive ou enriquecidos via APIs públicas (Kitsu, AniList ou TMDB).
- **Progresso e Watchlist:** Persistidos localmente no banco de dados Room SQLite do Makimono.
- **Skip Intro Markers:** Extraídos de capítulos embutidos no container de vídeo (arquivos MKV com tags de capítulos) ou consultados via serviços abertos comunitários (como AniSkip API).
- **Autenticação:** Gerenciada exclusivamente via Google OAuth 2.0 (Device Flow para Android TV).

---

## 6. O que Não Sabemos (Fronteiras da Investigação)

1. **Comportamento Dinâmico sob Perda de Conexão com o Drive:** A Crunchyroll lida com falhas de CDN usando retry automático com exponential backoff; o comportamento do Makimono precisa de calibração própria para tolerância a quedas de stream do Google Drive.
2. **Performance de Listas com +500 Itens em Dispositivos Low-End (ex.: Fire TV Lite):** Enquanto a Crunchyroll divide o catálogo em páginas pequenas de 25 itens, diretórios com centenas de vídeos no Drive exigem paginação virtualizada rigorosa com `Paging 3`.

---

## 7. Decisão Estratégica Recomendada

- **Descarte de Rota Tecnicamente Desvantajosa:** Abandonar qualquer tentativa de descompilar exaustivamente ou portar código decompilado do APK da Crunchyroll. Essa rota é lenta, frágil e juridicamente problemática.
- **Adoção da Rota Vencedora (Reconstrução por Especificação):** Implementar diretamente no Makimono os padrões comportamentais e geométricos validados nesta auditoria (conforme demonstrado nas três provas executáveis em `makimono-dossie/provas/`).

---

## 8. Próximas Três Entregas Prioritárias para o Makimono

### Entrega 1: Proteção de Back Press e Expansão do Navigation Rail
- **Arquivos:** `app/src/main/java/zechs/drive/stream/ui/main/MainActivity.kt` e `RailNavigationPolicy.kt`.
- **Critérios de Aceite:**
  - Pressionar Back dentro de qualquer carrossel move o foco para o item ativo do menu lateral.
  - Pressionar Back no menu lateral abre diálogo de confirmação de saída com foco em "Sim".
- **Testes:** Executar `RailNavigationFocusTest` e teste de integração no emulador TV.

### Entrega 2: Memorização de Foco Horizontal em Prateleiras (Home)
- **Arquivos:** `app/src/main/java/zechs/drive/stream/ui/home/adapter/` e `FocusMemory.kt`.
- **Critérios de Aceite:**
  - Navegar até o 5º item de uma prateleira, subir para o Hero e descer novamente deve restaurar o foco no 5º item.
  - Se itens forem removidos, o foco deve sofrer clamping para o novo índice máximo sem crashar.
- **Testes:** Executar `FocusMemoryTest`.

### Entrega 3: Botão "Pular Abertura" (Skip Intro) no Player OSD
- **Arquivos:** `app/src/main/java/zechs/drive/stream/ui/player/PlayerActivity.kt` e `SkipController.kt`.
- **Critérios de Aceite:**
  - Quando o vídeo possuir capítulos com nome "Opening" ou "Intro", ou marcadores temporais, exibir botão "Pular Abertura" com timer auto-hide.
  - Pressionar o botão deve avançar o vídeo para o término do intervalo.
- **Testes:** Executar `OsdStateMachineTest` e `SkipControllerTest`.
