# META — MAKIMONO TV PRO POLISH

Transforme o `takaokensei/makimono` em um media center Android/Android TV com acabamento de produto premium.

O objetivo é elevar a UI existente para um nível visual e de UX comparável a um produto comercial muito bem refinado, mantendo a arquitetura atual baseada em **Kotlin + Android Views/XML**.

Não faça uma reescrita em Jetpack Compose.

O foco é:

**qualidade visual + 10-foot UX + D-pad + motion + consistência + robustez.**

---

# 0. REGRA PRINCIPAL

Não produza apenas sugestões.

O agente deve:

```text
AUDITAR
→ ALTERAR CÓDIGO
→ BUILD
→ INSTALAR NO EMULADOR
→ INTERAGIR COM D-PAD/ADB
→ CAPTURAR SCREENSHOT
→ ANALISAR
→ CORRIGIR
→ TESTAR
→ COMMITAR
→ REGISTRAR LOG
→ PRÓXIMO CICLO
```

Continue iterando autonomamente.

Não pergunte o que fazer depois de cada melhoria.

---

# 1. BOOTSTRAP SEGURO

O projeto pode ter sido obtido via Codeload.

Se não houver `.git`:

```bash
git init
git branch -M ui/pro-polish
git add .
git commit -m "chore(ui): baseline before tv polish"
```

Se já houver Git, criar a branch:

```text
ui/pro-polish
```

Nunca trabalhar sem rollback.

Cada ciclo relevante deve gerar commit:

```text
ui(home): refine tv hero and shelves
ui(rail): improve dpad navigation
ui(player): polish tv osd
ui(details): refine series hierarchy
ui(profiles): improve profile selection
ui(motion): standardize focus transitions
```

---

# 2. LEITURA OBRIGATÓRIA ANTES DE EDITAR

Ler:

* `README.md`
* `docs/ANTIGRAVITY_IMPLEMENTATION_PLAN.md`
* `docs/ui-modernization/AUDIT.md`
* `docs/ui-modernization/review-2026-09-23/README.md`
* referências em `docs/kodi_reference/`
* layouts e resources em `app/src/main/res/`
* `VisualReviewActivity`
* `TvFocusRing`
* `motion_spec.xml`

Descobrir a arquitetura real antes de criar abstrações.

Preservar:

* Kotlin;
* XML/ViewBinding;
* Android TV;
* layouts mobile existentes;
* D-pad;
* `TvFocusRing`;
* ExoPlayer;
* MPV;
* Room;
* Glide;
* integração Drive;
* perfis;
* player;
* estados existentes.

---

# 3. MEMÓRIA PERSISTENTE

Criar:

```text
FRONTEND_LOG.md
```

No início de cada ciclo, reler esse arquivo.

Registrar:

```text
## Cycle N

Area:
Observed problems:
Changes:
Files:
Build:
Instrumented tests:
Emulator:
D-pad:
Screenshot:
Visual score:
Regression:
Next focus:
```

O agente não deve confiar na memória da conversa.

Depois de qualquer compactação de contexto:

> reler `FRONTEND_LOG.md` antes de editar qualquer arquivo.

---

# 4. BASELINE

Antes de modificar UI:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Quando houver emulator:

```bash
./gradlew connectedDebugAndroidTest
```

Registrar os resultados.

Também capturar baseline das principais telas usando a `VisualReviewActivity` debug.

Usar:

```text
1920x1080
density 320
landscape
```

Salvar:

```text
docs/ui-audit/before/
docs/ui-audit/after/
```

Não sobrescrever o baseline.

---

# 5. FERRAMENTA PRINCIPAL DE VALIDAÇÃO VISUAL

Usar a:

```text
app/src/debug/.../VisualReviewActivity.kt
```

Ela existe justamente para renderizar XML/adapters reais com fixtures, sem modificar Room ou a biblioteca do usuário.

Criar/estender fixtures apenas para visualização local.

Nunca transformar fixtures de revisão visual em dados de produção.

Capturar pelo menos:

```text
Home
Profiles
Details
Files
Player
Settings
Dialogs relevantes
```

Quando possível, validar também o fluxo real com dados reais.

---

# 6. TESTE REAL DE INTERAÇÃO

Não confiar somente no screenshot.

Usar ADB/emulator para enviar:

```text
DPAD_UP
DPAD_DOWN
DPAD_LEFT
DPAD_RIGHT
DPAD_CENTER / ENTER
BACK
```

Testar fisicamente o comportamento de foco.

Para cada tela verificar:

* foco inicial;
* foco visível;
* esquerda;
* direita;
* cima;
* baixo;
* seleção;
* retorno;
* modal;
* restauração do foco.

O usuário nunca deve ficar "perdido" dentro da interface.

---

# 7. RESOLUÇÃO PRINCIPAL

Prioridade principal:

```text
1920x1080
320 dpi
Android TV / 10-foot UI
```

Também validar, quando possível:

* 1280×720;
* 1920×1080;
* larguras estreitas relevantes;
* portrait apenas para telas realmente compartilhadas com mobile.

Não sacrificar a experiência mobile para consertar TV, nem vice-versa.

---

# 8. FASE A — DESIGN SYSTEM + SHELL

Primeiro estabilizar:

* cores;
* surfaces;
* rail;
* spacing;
* typography;
* cards;
* buttons;
* pills;
* borders;
* dialogs;
* focus;
* navigation.

O tema Kodi/Estuary atual deve continuar sendo a referência principal da experiência TV, conforme a direção já documentada.

Não introduzir outro estilo visual arbitrário.

### Atenção especial

O projeto contém vários temas:

* Tokyo Night;
* Dracula;
* Nord;
* Catppuccin;
* Kodi Estuary.

Não remover temas existentes.

Ajustar o sistema sem quebrar essas variantes.

O tema Kodi é a prioridade da experiência TV.

---

# 9. FASE B — TV RAIL

Tratar o rail como um componente de primeira classe.

Validar:

* largura;
* alinhamento;
* ícones;
* texto;
* estado ativo;
* foco;
* expansão;
* colapso;
* relação com o conteúdo;
* navegação D-pad.

Objetivo:

O rail deve parecer parte estrutural do produto, não um menu sobreposto improvisado.

O foco deve ser imediatamente identificável a vários metros de distância.

Não depender exclusivamente de ripple.

---

# 10. FASE C — HOME

A Home é a tela mais importante.

Polir:

* background;
* backdrop;
* vignette;
* hero;
* título;
* metadados;
* gêneros;
* ações;
* Continue Watching;
* fila;
* catálogo;
* contagem;
* tabs;
* busca.

Manter a regra:

> uma representação clara para cada função.

Não reintroduzir duplicações de "Continuar assistindo".

O conteúdo visual deve ter prioridade sobre decoração.

Imagens:

* backdrop → cinematográfico;
* série → poster;
* episódio → 16:9;
* scrim → apenas para legibilidade.

---

# 11. FASE D — CATÁLOGO / FILES

Validar:

* grid;
* list;
* search;
* sort;
* folders;
* files;
* focus;
* thumbnails;
* estados vazios;
* loading;
* erros.

Não permitir que imagens de anime sejam cortadas de forma incoerente.

Uma mídia de episódio deve continuar reconhecível como vídeo/episódio.

O foco não deve destruir a leitura da thumbnail.

---

# 12. FASE E — PROFILES

Polir:

* seleção;
* avatar;
* wallpaper;
* cards;
* Admin;
* Kids;
* adicionar perfil;
* editar;
* excluir;
* cancelamento;
* retorno.

Verificar:

```text
D-pad
→ selecionar
→ editar
→ cancelar
→ voltar
```

O foco deve retornar ao item correto.

Não implementar autenticação ou parental control real como parte dessa meta.

Preservar o significado atual de Admin/Kids.

---

# 13. FASE F — SERIES DETAILS

Polir:

* hero;
* poster;
* título;
* título japonês;
* synopsis;
* metadata;
* rating;
* age rating;
* CTA;
* seasons;
* episodes;
* progress;
* foco.

O primeiro viewport deve transmitir claramente:

```text
O que é?
Que série é?
Qual episódio?
Qual ação principal?
```

Evitar excesso de informação pequena.

Títulos longos precisam funcionar.

Testar com conteúdo curto E longo.

---

# 14. FASE G — PLAYER / OSD

O player exige uma auditoria separada.

Validar:

* ExoPlayer;
* MPV;
* transport controls;
* seek;
* volume;
* subtitles;
* audio;
* episodes;
* chapters;
* speed;
* aspect ratio;
* info;
* More Options;
* AniSkip;
* next episode;
* PiP;
* lock;
* gestures.

Não substituir indiscriminadamente handlers específicos de cada engine.

A UI compartilhada pode ser melhorada, mas comportamento específico de ExoPlayer/MPV deve continuar intacto.

### OSD

Priorizar:

```text
primary actions
```

e esconder ações secundárias atrás de:

```text
More options
```

quando isso melhorar legibilidade.

O foco deve permanecer inequívoco.

Nunca desenhar `TvFocusRing` sobre:

* PlayerView;
* MPVView;
* SurfaceView;
* TextureView;
* root fullscreen.

Preservar a lógica de segurança existente do `TvFocusRing`.

---

# 15. FASE H — DIALOGS / SETTINGS

Polir:

* configurações;
* theme selector;
* subtitle settings;
* profile editor;
* MAL;
* player menu;
* episode drawer;
* rating dialog;
* update dialog;
* confirmation dialogs.

Todos devem ter:

* foco inicial;
* foco de saída;
* retorno de foco;
* hierarquia;
* botão primário;
* botão secundário;
* estado disabled;
* loading;
* erro.

Dialogs não podem parecer componentes de outro aplicativo.

---

# 16. MOTION

O projeto já possui `motion_spec.xml`.

Não criar dezenas de animações soltas.

Primeiro centralizar a linguagem de motion.

Preferir:

* alpha;
* scale;
* translation;
* crossfade;
* focus transitions.

Evitar animações que alterem continuamente layout, dimensões ou posição estrutural.

Não usar:

* bounce exagerado;
* elasticidade excessiva;
* animações lentas;
* efeitos chamativos;
* zoom exagerado;
* blur pesado em loops.

Para TV:

```text
focus feedback → rápido
dialog → curto
navigation → curto
hero → suave
image loading → discreto
```

O motion deve parecer premium, não "gamer".

Auditar o `motion_spec.xml` existente e padronizar seus tempos/easings antes de espalhar novos valores.

---

# 17. FOCUS

Focus é uma das métricas mais importantes do Makimono.

Cada elemento acionável precisa responder visualmente a:

```text
focused
selected
pressed
disabled
```

Verificar:

* borda;
* halo;
* scale;
* contraste;
* clipping;
* sobreposição;
* z-order.

Não modificar um foco existente sem verificar os outros estados.

O foco nunca deve desaparecer atrás de outra view.

---

# 18. IMAGENS

Usar a infraestrutura existente:

```text
MediaImageLoader
Glide
ThumbnailUrl
```

Evitar downloads duplicados.

Não carregar imagens gigantes quando uma thumbnail adequada existe.

Validar:

* placeholder;
* error;
* cache;
* crossfade;
* clipping;
* aspect ratio.

O resultado deve parecer rápido mesmo quando a rede está lenta.

---

# 19. ESTADOS

Nenhuma tela importante pode ter apenas:

```text
loaded
```

Projetar e validar:

```text
loading
empty
error
retry
loaded
focused
selected
disabled
offline
```

Especialmente:

* Home sem biblioteca;
* busca sem resultado;
* Drive indisponível;
* catálogo vazio;
* histórico vazio;
* queue vazia;
* perfil novo;
* player sem metadados;
* updater carregando/erro/sucesso.

---

# 20. PERFORMANCE

Observar:

* frame drops;
* scroll;
* RecyclerView;
* image loading;
* layout inflation;
* repeated bindings;
* animações;
* foco.

Evitar:

* lógica pesada dentro de bind;
* layout nesting desnecessário;
* listeners duplicados;
* recriações frequentes;
* imagens grandes;
* animações simultâneas em muitas cards.

Não sacrificar UX apenas para micro-otimizações irrelevantes.

---

# 21. QA AUTOMATIZADO

Depois de cada alteração relevante:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Quando possível:

```bash
./gradlew connectedDebugAndroidTest
```

Se uma alteração causar regressão:

1. identificar;
2. corrigir;
3. se necessário reverter o ciclo;
4. tentar abordagem diferente;
5. registrar no log.

Nunca apagar teste porque ele começou a falhar.

---

# 22. TESTE VISUAL ANTES/DEPOIS

Para cada grande fase:

```text
before/
after/
```

Comparar:

* composição;
* hierarquia;
* escala;
* contraste;
* foco;
* clipping;
* whitespace;
* densidade.

Não considerar melhoria apenas porque o código ficou mais elegante.

O resultado visual precisa efetivamente melhorar.

---

# 23. RUBRICA

Avaliar:

```text
TV hierarchy:
Rail:
Home:
Catalog:
Profiles:
Details:
Player:
Dialogs:
D-pad:
Focus:
Motion:
Typography:
Spacing:
Consistency:
States:
Performance:
Mobile compatibility:
Overall polish:
```

Notas de 0–10.

Uma área < 9 continua aberta.

Não esconder uma área ruim pela média.

---

# 24. FASEAMENTO

Máximo de ~3 ciclos antes de avançar:

```text
A — system / shell / rail
B — home
C — catalog / files
D — profiles
E — details
F — player
G — settings / dialogs
H — final responsive + performance + polish
```

Não passar horas refinando a mesma Home enquanto Player e Details ainda estiverem abaixo do padrão.

---

# 25. CRITÉRIO DE PARADA

Concluir somente quando:

* testes relevantes verdes;
* build funciona;
* sem regressões evidentes;
* D-pad funciona nos principais fluxos;
* foco sempre visível;
* nenhum clipping/overflow grave;
* Home parece produto final;
* catálogo parece produto final;
* Profiles parece produto final;
* Details parece produto final;
* Player OSD parece produto final;
* dialogs parecem pertencer ao mesmo sistema;
* motion consistente;
* screenshots finais melhores que baseline;
* nenhuma área crítica < 9 em duas rodadas consecutivas.

Quando a infraestrutura real estiver disponível, fazer também:

* biblioteca autenticada;
* reprodução real;
* TV física;
* títulos longos;
* fontes ampliadas;
* ambos os engines.

Não declarar que essas áreas foram validadas quando só o fixture foi utilizado.

---

# 26. RESTRIÇÕES IMPORTANTES

NÃO:

* migrar para Compose;
* reescrever arquitetura;
* modificar backend inexistente;
* alterar Room sem necessidade visual;
* mexer em autenticação sem necessidade;
* quebrar Drive;
* substituir ExoPlayer/MPV;
* remover temas;
* remover testes;
* mascarar erros com mocks permanentes;
* criar dependências pesadas para resolver detalhes simples;
* gerar assets artificiais sem necessidade;
* transformar preview fixtures em produção;
* fazer redesign completo sem primeiro entender o sistema existente.

---

# 27. COMPORTAMENTO DO AGENTE

Depois de terminar uma melhoria:

1. consultar `FRONTEND_LOG.md`;
2. abrir a aplicação novamente;
3. identificar o maior problema restante;
4. implementar;
5. testar;
6. registrar;
7. continuar.

Não pedir instruções ao usuário entre ciclos.

Não parar porque uma tela ficou "bonita".

O objetivo é elevar **o produto inteiro**.

A pergunta final de cada ciclo deve ser:

> "Qual é agora o maior defeito perceptível do Makimono como produto Android TV?"

Corrija esse defeito e continue.
