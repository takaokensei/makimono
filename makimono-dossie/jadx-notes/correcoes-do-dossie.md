# Auditoria Crítica e Correções das Notas Preliminares (Etapa V1)

**Data:** 14 de Outubro de 2026  
**Finalidade:** Ajustar rigorosamente o registro documental, separando fatos decompilados comprovados de inferências estruturais e hipóteses de especificação.

---

## 1. Retificação de Nomes Canônicos de Pacotes e Classes

Nas notas preliminares de inventário inicial (Fase 2), alguns identificadores foram registrados de forma simplificada. A inspeção direta do `AndroidManifest.xml` (Evidence ID `ev_07b5781d17b0cf694ed5db3060363212824cd3bcb7fc0e5261e3ba25216468b0`) e das classes DEX retifica:

| Identificador Preliminar | Nome Canônico Decompilado | Localização Factual no APK |
| :--- | :--- | :--- |
| `MainActivity` | `com.crunchyroll.crunchyroid.main.ui.MainActivity` | `classes.dex` (Hilt ComponentActivity) |
| `ShowDetailsActivity` | `com.crunchyroll.crunchyroid.showdetails.ui.ShowDetailsActivity` | `classes.dex` (Hilt FragmentActivity) |
| `PlayerActivity` | `com.crunchyroll.crunchyroid.player.ui.PlayerActivity` | `classes.dex` (Hilt FragmentActivity) |
| `StartupActivity` | `com.crunchyroll.crunchyroid.startup.ui.StartupActivity` | `classes.dex` |
| `SplashActivity` | `com.crunchyroll.crunchyroid.splash.ui.SplashActivity` | `classes.dex` |

---

## 2. Diagnóstico da Stack Visual: Comprovação Empírica de 100% Jetpack Compose

- **Afirmação Preliminar:** Suposição de que o app poderia conter layouts XML legados misturados com Compose.
- **Auditoria Factual:**
  1. O inventário de recursos via AAPT lista 244 arquivos em `res/layout/`. **Nenhum** layout pertence a telas do app; todos derivam de dependências externas (`androidx.appcompat:abc_*`, `androidx.leanback:lb_*`, `androidx.media3:exo_*` e `androidx.media3:media3_*`).
  2. O método `MainActivity.onCreate` decompilado (via REA/JADX) executa `K0(ComposableLambdaKt.c(626118510, true, ...))` — que corresponde à chamada inlined de `ComponentActivity.setContent`.
  3. A inspeção em runtime da árvore de acessibilidade no AVD Android TV (`cr_tv_live_ui.xml`) revela que a raiz do contêiner `android:id/content` hospeda diretamente um `androidx.compose.ui.platform.ComposeView` em fullscreen (`[0,0][1920,1080]`).
- **Conclusão:** A interface do aplicativo na versão 3.75.0 é integralmente desenhada em Jetpack Compose for TV.

---

## 3. Retificação de Valores Paramétricos e Hipóteses

Determinados valores citados em notas da Fase 2 eram **hipóteses de especificação técnica para o Makimono** e não constantes literais extraídas da descompilação:

1. **Escala de Foco (1.05x a 1.08x):**
   - *Status real:* Hipótese de especificação. O código decompilado utiliza modificadores de animação do Compose (`Animatable.animateTo` ou `animateFloatAsState`), mas os valores exatos de escala não foram extraídos como literais fixos da bytecode. A medida em runtime indica elevação sutil de Z e padding de contorno de 8px (4dp).
2. **Buffer de Pulo (`end_ms + 100ms`):**
   - *Status real:* Hipótese de especificação. O evento `SkipEvent` contém `start_ms` e `end_ms`, mas o offset exato de `100ms` é uma recomendação de UX para o player do Makimono evitar pousar no último frame da abertura, não uma regra decompilada da Crunchyroll.
3. **TTL de Cache da Watchlist:**
   - *Status real:* Hipótese de especificação. A aplicação adota o padrão *Cache-Then-Network*, porém o intervalo numérico exato de expiração local é gerenciado por flags remotas (`AppRemoteConfigRepo`).

---

## 4. Diagnóstico de Ofuscação e Limitações de Ferramental

1. **Nível de Ofuscação (R8/ProGuard):**
   - Métodos internos de composição Kotlin sofreram ofuscação de nomes (ex.: `T0`, `U0`, `W0`, `j1`, `k1`), e lambdas foram convertidas em classes sintéticas (`e0`, `m1`).
   - As fronteiras públicas de componentes do Android (`onCreate`, `onPause`, `onResume`), modelos de domínio expostos ao Hilt (`VideoContent`, `ShowMetadata`, `LupinStatus`, `TvPromotionalAsset`) e nomes de nós de acessibilidade (`resource-id` no Compose semantics) **permanecem intactos e perfeitamente legíveis**.
2. **Comportamento do REA CLI:**
   - O comando `rea search-android-classes` falha com `unreadable_output` ao tentar listar classes em massa em APKs multidex divididos com classes internas sintéticas duplicadas.
   - Os comandos pontuais `rea inspect-android-class` e `rea inspect-android-method` funcionam com 100% de precisão quando a classe exata é fornecida.
