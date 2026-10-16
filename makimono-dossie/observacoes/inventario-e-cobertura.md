# Inventário de Recursos, Pilha Técnica e Cobertura Estática (Etapa V1)

**Data:** 14 de Outubro de 2026  
**Alvo:** `com.crunchyroll.crunchyroid` (v3.75.0, build 22365)  
**Fonte de Evidência:** JADX Headless MCP Engine v0.7.1 (Evidence ID `ev_07b5781d17b0cf694ed5db3060363212824cd3bcb7fc0e5261e3ba25216468b0`), AAPT v0.2.

---

## 1. Inventário Delimitado de Recursos do APK

### 1.1 Análise de Layouts XML (`res/layout/`)
- **Total de Arquivos:** 244 arquivos XML compilados.
- **Origem dos Layouts:**
  - `abc_*.xml` (100% biblioteca `androidx.appcompat`)
  - `lb_*.xml` (100% biblioteca `androidx.leanback`, mantida por compatibilidade de compat-layers)
  - `exo_*.xml` e `media3_*.xml` (100% biblioteca `androidx.media3`, para views de controle e notificações de mídia)
  - `custom_dialog.xml`, `notification_*.xml` (suporte a notificações e diálogos do sistema)
- **Veredito:** **Zero layouts proprietários de tela.** Não existem arquivos XML que definam a tela Home, a tela de detalhes ou a tela do player. Toda a UI é construída programaticamente em Kotlin/Compose.

### 1.2 Recursos de Estilos, Cores e Dimensões
- **Temas Android (`res/values/styles.xml` / `themes.xml`):**
  - `Theme.Crunchyroll` (Tema raiz da aplicação estendendo `Theme.AppCompat.NoActionBar`).
  - `Theme.CRSplashScreen` (Tema de inicialização com background de janela opaco para evitar flash branco).
- **Cores (`res/values/colors.xml`):**
  - Paleta baseada em tons escuros: preto (`#000000`), cinza escuro de superfície (`#141519`), cinza de contorno (`#23252B`).
  - Cor de destaque de marca: Laranja Crunchyroll (`#FF640A`). *(Classificada como `[EXPRESSÃO]` — restrita e não aproveitável no Makimono).*

---

## 2. Amostra de Classes Inspecionadas e Métodos Decompilados

A investigação estática focou em 3 classes centrais representando as três áreas do frontend:

### 2.1 Shell / Navegação: `MainActivity`
- **Pacote Canônico:** `com.crunchyroll.crunchyroid.main.ui.MainActivity`
- **Superclasses:** `Hilt_MainActivity` → `FragmentActivity` → `ComponentActivity`.
- **Campos Principais:**
  - `NetworkManager` (`com.crunchyroll.connectivity.NetworkManager`)
  - `AppPreferences` (`com.crunchyroll.api.repository.preferences.AppPreferences`)
  - `WatchNextManager` (`com.crunchyroll.workmanager.WatchNextManager` - integração Leanback Watch Next)
  - `InAppReviewFeature`
- **Métodos Principais Decompilados:**
  - `onCreate(Bundle)`: Configura `WindowCompat`, registra deep links (`MainActivityStartDestination`) e inicializa a árvore Compose via chamada inlined a `setContent`.
  - `W0(...)` e `j1(...)`: Funções `@Composable` que injetam estados reativos:
    - `LupinListState` (`com.crunchyroll.core.lupin.state.LupinListState`)
    - `TvPromotionalAsset` (`com.crunchyroll.promotions.domain.model.TvPromotionalAsset` - dados do Hero banner)
    - `ConsentUiModel`

### 2.2 Apresentação do Catálogo: `ShowDetailsActivity`
- **Pacote Canônico:** `com.crunchyroll.crunchyroid.showdetails.ui.ShowDetailsActivity`
- **Superclasses:** `Hilt_ShowDetailsActivity` → `FragmentActivity`.
- **Estrutura Observada:**
  - Carrega metadados da série (`ShowMetadata`) e gerencia abas horizontais de Episódios, Detalhes e Recomendações (*More Like This*).
  - Mantém estado de seleção da temporada ativa e sincroniza a lista de episódios com o progresso do usuário.

### 2.3 Player de Mídia: `PlayerActivity`
- **Pacote Canônico:** `com.crunchyroll.crunchyroid.player.ui.PlayerActivity`
- **Superclasses:** `Hilt_PlayerActivity` → `FragmentActivity`.
- **Campos de Hardware de Vídeo:**
  - `android.view.Surface` e `android.view.SurfaceControl`
  - `android.hardware.display.DisplayManager`
  - `com.google.android.gms.cast.tv.media.MediaManager` (Cast Connect)
- **Métodos de Controle e Acesso:**
  - `y2(NavController, VideoContent, ContentAccessGateType)`: Ponto de verificação de permissão de acesso ao conteúdo (*ContentAccessGate*).
  - `s1(LupinStatus, PlayerActivity, NavHostController, boolean, VideoContent, VideoContent, ShowMetadata)`: Orquestrador do estado de reprodução.
  - `n2(NavHostController, PlayerActivity, VideoContent, boolean, CoroutineScope, FocusRequester, NavGraphBuilder)`: Grafo de navegação interno do Player em Compose com injeção de `FocusRequester`.

---

## 3. Matriz de Cobertura da Amostra

| Componente | Classe Alvo | Recuperação Estática | Compreensão da Regra | Reprodução Possível | Integração Makimono |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Shell & Rail** | `MainActivity` | Métodos Compose + Assinaturas | **Alta** (Navegação reativa e Backstack) | **Sim** (Views e Compose) | **Alta** (compatível com `FocusCoordinator`) |
| **Home & Hero** | `MainActivity` | Modelo `TvPromotionalAsset` | **Alta** (Hero + Prateleiras horizontais) | **Sim** (RecyclerView + Card) | **Alta** (substitui layout estático) |
| **Player OSD** | `PlayerActivity` | `FocusRequester` + `ContentAccessGate` | **Média** (Controles locais compreendidos; streams protegidos) | **Sim** (Media3 / MPV) | **Total** (Player do Makimono é local) |
| **Detalhes** | `ShowDetailsActivity`| Estrutura de abas e metadata | **Alta** (Abas horizontais e seasons) | **Sim** (ViewPager / Tabs) | **Alta** (Integra com Google Drive) |
| **Skip Event** | `SkipEvent` / `SkipEventType` | Modelo de dados de evento | **Alta** (Marcadores temporais `[start, end]`) | **Sim** (Capítulos MKV / AniSkip) | **Total** (Melhoria direta no Player) |
| **Perfis** | `LupinProfileSync` | Estrutura de gerenciamento | **Baixa** (Acoplado ao backend Lupin) | **Parcial** (Multi-usuário local) | **Média** (Exige Room/DataStore próprio) |
