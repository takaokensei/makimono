# Matriz de Evidências Técnicas por Componente (Etapa V3)

**Data:** 14 de Outubro de 2026  
**Projeto:** Makimono (`intelligent-mendel`)  
**Enquadramento:** Premissa documental fornecida pelo usuário (`Act No. 19 of 2026`). Protocolo estrito Clean-Room.

---

### Cartão 01 — Shell, Sidebar (Rail Navigation), Back e Foco
- **ID e Pergunta:** `EVD-P1-01` — Como o rail expande, como o foco navega entre barra e conteúdo e qual o contrato do botão Back?
- **Componente e Prioridade:** Shell, Navigation Rail e Back Policy (P1).
- **APK / SHA-256 / Versão / Splits:** `base.apk` (SHA-256: `901ECDAADE...`), v3.75.0 (build 22365), splits `x86_64` e `xhdpi`.
- **Ferramenta / Versão / Chamada:** REA v6.4.0 (`inspect-android-class`, `inspect-android-method`), ADB v37.0.0 (`uiautomator dump`).
- **Evidência Bruta / Evidence ID / Localização:** `ev_07b5781d17b0cf694ed5db3060363212824cd3bcb7fc0e5261e3ba25216468b0`, capturas `cr_tv_live_ui.xml`, `step6_rail.xml`, `step7_back.xml`.
- **Classe/Método ou Roteiro:** `com.crunchyroll.crunchyroid.main.ui.MainActivity.onCreate` e nós `mainMenuNavDrawer`, `homeNavDrawer`, `exitDialog`.
- **Fato Observado:**
  1. O rail colapsado mede 80dp (160px); ao receber foco, expande para 264dp (528px) e empurra o conteúdo em vez de sobrepor como scrim opaco.
  2. Pressionar Back dentro do conteúdo ancora o foco no menu lateral; pressionar Back no menu lateral abre diálogo de confirmação (`exitDialog`), prevenindo saída acidental.
- **Inferência e Confiança:** Alta confiança (comprovado estaticamente no código e empiricamente em runtime no AVD TV).
- **Cobertura e Limitações:** Comportamento estável verificado no AVD oficial.
- **Dependências e Compatibilidade Makimono:** Alta compatibilidade. O Makimono pode implementar esse contrato de forma desacoplada em Views/XML ou Compose com `RailNavigationPolicy` e `BackHandler`.
- **Classificação e Justificativa:** **Classe B — Reproduzível por Especificação.** O código decompilado é ofuscado e desnecessário para transplantar; a regra comportamental está perfeitamente compreendida e é facilmente reproduzível com código limpo.
- **Prova Executada / Resultado:** Prova 01 implementada em `makimono-dossie/provas/prova_01_rail_sidebar_back/` validando ancoragem e expansão.
- **Esforço Registrado e Restante Estimado:** 2h registradas; 4–6h para integração completa no layout principal do Makimono.
- **Próxima Ação:** Conectar a política à `MainActivity` do Makimono.

---

### Cartão 02 — Home, Hero Banner e Prateleiras (Shelves)
- **ID e Pergunta:** `EVD-P1-02` — Qual a hierarquia visual do Hero, como os carrosséis horizontais se comportam e como o foco é restaurado entre linhas?
- **Componente e Prioridade:** Home Screen, Hero Component e Shelves com Focus Memory (P1).
- **APK / SHA-256 / Versão / Splits:** `base.apk` (SHA-256: `901ECDAADE...`), v3.75.0.
- **Ferramenta / Versão / Chamada:** REA v6.4.0 (`inspect-android-class`), ADB v37.0.0 (`uiautomator dump`).
- **Evidência Bruta / Evidence ID / Localização:** Capturas `step2_down.xml`, `step3_right2.xml`, `step4_up.xml`, `step5_down_test_memory.xml`.
- **Classe/Método ou Roteiro:** `MainActivity.j1(...)`, `TvPromotionalAsset`, `panelCard_1_*`, `homeFeedComponent`.
- **Fato Observado:**
  1. O Hero ocupa altura de 452dp (904px) e contém background gradiente, título, tags, sinopse de até 3 linhas e 2 botões de ação ("START WATCHING", "MORE INFO").
  2. Cada prateleira horizontal é um carrossel onde os cards medem 145dp com 8dp de espaçamento.
  3. **Memorização de Foco:** Ao navegar para o item 3 (`panelCard_1_3`), subir para o Hero e descer novamente, o foco retorna exatamente ao item 3.
- **Inferência e Confiança:** Alta confiança (validada empiricamente com dumps de nós).
- **Cobertura e Limitações:** Sem dependência de backend proprietário para a apresentação visual.
- **Dependências e Compatibilidade Makimono:** Totalmente compatível com `RecyclerView` aninhado e `FocusMemoryManager` já previstos na arquitetura do Makimono.
- **Classificação e Justificativa:** **Classe B — Reproduzível por Especificação.** Padrão clássico de TV perfeitamente mapeado e aplicável às seções de filmes/animes do Google Drive.
- **Prova Executada / Resultado:** Prova 02 implementada em `makimono-dossie/provas/prova_02_hero_shelves_focus_memory/`.
- **Esforço Registrado e Restante Estimado:** 2.5h registradas; 6–8h para substituir a tela inicial atual do Makimono.
- **Próxima Ação:** Implementar o adapter de carrossel com suporte a `FocusMemory`.

---

### Cartão 03 — Player OSD e Transporte
- **ID e Pergunta:** `EVD-P1-03` — Como os controles do player se estruturam, qual a máquina de estados do HUD e como opera o botão de Skip Intro?
- **Componente e Prioridade:** Player OSD, HUD temporizado e Skip Overlay (P1).
- **APK / SHA-256 / Versão / Splits:** `base.apk` (SHA-256: `901ECDAADE...`), v3.75.0.
- **Ferramenta / Versão / Chamada:** REA v6.4.0 (`inspect-android-class`), AAPT v0.2.
- **Evidência Bruta / Evidence ID / Localização:** Classes `PlayerActivity`, `SkipEvent`, `SkipEventType`, `UpNext`.
- **Classe/Método ou Roteiro:** `com.crunchyroll.crunchyroid.player.ui.PlayerActivity`, métodos `y2` (ContentAccessGate), `s1`, `n2`.
- **Fato Observado:**
  1. A reprodução é isolada em Activity dedicada (`PlayerActivity`) com aceleração gráfica (`SurfaceControl`).
  2. O OSD opera como camada superior com auto-hide de 3 a 5 segundos após inatividade do D-pad.
  3. Marcadores temporais (`SkipEvent`) disparam overlay contextual sem pausar o playback.
  4. Controles de acesso a streams (`ContentAccessGate`) exigem assinatura legítima para decodificação DRM.
- **Inferência e Confiança:** Alta confiança estrutural. A máquina de estados local e os componentes de UI são independentes do DRM.
- **Cobertura e Limitações:** A reprodução direta de títulos protegidos foi bloqueada por credenciais (Content Access Gate), mas a interface e o contrato de eventos foram completamente mapeados.
- **Dependências e Compatibilidade Makimono:** Total compatibilidade. O Makimono reproduz arquivos locais ou do Google Drive via Media3 / ExoPlayer e MPV, permitindo Skip Intro via capítulos de MKV ou AniSkip.
- **Classificação e Justificativa:** **Classe B — Reproduzível por Especificação.** A máquina de estados de HUD e Skip Intro é reproduzível nativamente no player do Makimono sem qualquer código proprietário.
- **Prova Executada / Resultado:** Prova 03 implementada em `makimono-dossie/provas/prova_03_player_osd_skip_statemachine/`.
- **Esforço Registrado e Restante Estimado:** 2h registradas; 4–6h para plugar no `PlayerActivity` do Makimono.
- **Próxima Ação:** Integrar o listener de capítulos do ExoPlayer/MPV com o overlay de Skip.

---

### Cartão 04 — Detalhes, Temporadas e Episódios (P2)
- **ID e Pergunta:** `EVD-P2-04` — Como as temporadas e episódios se organizam na tela de detalhes?
- **Componente e Prioridade:** Catálogo e Navegação de Temporadas (P2).
- **Fato Observado:**
  1. Header com pôster amplo, rating etário, tags ("Sub \| Dub"), sinopse completa e botão primário ("Add to Watchlist" / "Play").
  2. Abas horizontais: `EPISODES`, `DETAILS`, `MORE LIKE THIS`.
  3. Seletor de temporadas à esquerda e grade horizontal de episódios à direita com cards informando número do episódio, duração e barra de progresso.
- **Classificação e Justificativa:** **Classe B — Reproduzível por Especificação.** Padrão altamente recomendado para a tela de detalhes de séries de anime no Makimono.
- **Esforço Estimado:** 6–8h de desenvolvimento próprio.

---

### Cartão 05 — Perfis, Avatares e Edição (P2)
- **ID e Pergunta:** `EVD-P2-05` — Como a troca rápida de perfis é estruturada?
- **Fato Observado:**
  1. O primeiro item do menu lateral (`switchProfileNavDrawer`) exibe o avatar circular do perfil ativo.
  2. O subsistema `LupinProfileSync` mantém o estado de cada perfil (watchlist, progresso e histórico isolados).
- **Classificação e Justificativa:** **Classe C — Parcialmente Reproduzível.** A experiência visual de troca é simples de reproduzir, mas o backend de perfis do Makimono precisa ser persistido localmente via Room SQLite.
- **Esforço Estimado:** 8–10h (UI + persistência local de múltiplos perfis).

---

### Cartão 06 — Busca, Menus, Loading e Erros (P2)
- **ID e Pergunta:** `EVD-P2-06` — Como a busca na TV acomoda teclado e resultados parciais?
- **Fato Observado:**
  1. A rota de busca abre teclado virtual de TV ou campo de busca integrado na parte superior com grade dinâmica de resultados abaixo.
  2. Estados de loading utilizam placeholders animados de proporção idêntica aos cards reais.
- **Classificação e Justificativa:** **Classe B — Reproduzível por Especificação.**
- **Esforço Estimado:** 4–6h de desenvolvimento no Makimono.

---

### Cartão 07 — Áudio, Legendas e Autoplay (P3)
- **ID e Pergunta:** `EVD-P3-07` — Como as trilhas de legenda e áudio são selecionadas na TV?
- **Fato Observado:**
  1. Diálogo modal acessível via botão no OSD do player, com lista vertical de faixas de áudio e legendas.
  2. Card flutuante de "Up Next" com contagem regressiva nos últimos 15 segundos do episódio para binge-watching.
- **Classificação e Justificativa:** **Classe B — Reproduzível por Especificação.** Totalmente suportado via APIs nativas do ExoPlayer (`TrackSelectionParameters`) e libmpv.
- **Esforço Estimado:** 4–6h.

---

### Cartão 08 — Conta, Dados e Serviços Remotos (P3)
- **ID e Pergunta:** `EVD-P3-08` — O que pode ser aproveitado dos serviços de autenticação e catálogo?
- **Fato Observado:**
  1. O aplicativo comunica-se exclusivamente com microsserviços privados sob OAuth2 e endpoints de streaming assinados.
- **Classificação e Justificativa:** **Classe D — Sem Utilidade Prática no Makimono.** Os serviços remotos são inteiramente proprietários e incompatíveis com a missão do Makimono (que consome Google Drive e APIs públicas de anime).
- **Esforço Estimado:** N/A (Descarte técnico justificado).
