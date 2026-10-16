# Subsistema 01 — Telas e Pontos de Entrada

## 1. Identificadores Observados e Origem

- **Classes Principais:**
  - `com.crunchyroll.crunchyroid.splash.ui.SplashActivity` (Manifesto / Pacote de Inicialização)
  - `com.crunchyroll.crunchyroid.MainActivity` (Manifesto / Pacote Raiz)
  - `com.crunchyroll.crunchyroid.ShowDetailsActivity` (Manifesto / Pacote de Apresentação)
  - `com.crunchyroll.crunchyroid.player.PlayerActivity` (Manifesto / Pacote de Mídia)
  - `com.crunchyroll.crunchyroid.presentation.navigation.TvNavHost` (Código / Compose)

---

## 2. Enquadramento e Análise Estrutural

### 2.1 Padrão Single-Activity Orientado a Compose
- **Tag:** `[IDEIA]`
- **Observação Factual:** O manifesto Android declara um número reduzido de Activities para a magnitude do catálogo, destacando-se a `MainActivity` como hospedeira principal e a `PlayerActivity` isolada em processo/janela otimizada para reprodução. A montagem da árvore de telas ocorre via `ComponentActivity.setContent` do Jetpack Compose.
- **Grau de Confiança:** Forte evidência estática (análise combinada de `AndroidManifest.xml` e referências a `androidx.compose.ui.platform.ComposeView`).
- **Lacunas Conhecidas:** Seletor de flags de runtime (Feature Flags) pode alternar fluxos legados para telas específicas em versões anteriores de dispositivos.

### 2.2 Tratamento do Ciclo de Vida da TV
- **Tag:** `[IDEIA]`
- **Observação Factual:** A `PlayerActivity` possui configuração de manifesto `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboard|keyboardHidden"` e bloqueio de orientação para paisagem (`android:screenOrientation="sensorLandscape"`), evitando recriação desnecessária de janelas ao alternar entradas HDMI ou acordar o painel da TV.
- **Grau de Confiança:** Confirmado por manifesto.
- **Lacunas Conhecidas:** Comportamento exato do PiP (Picture-in-Picture) em modelos com Android TV 12+ requer observação dinâmica.

### 2.3 Expressão de Nomes e Atributos de Identificação
- **Tag:** `[EXPRESSÃO]`
- **Observação Factual:** Nomes de pacotes literais, nomes internos de módulos de build e identificadores semânticos de classes (`SplashActivity`, `PlayerActivity`).
- **Grau de Confiança:** Confirmado por manifesto.
- **Diretriz Clean-Room:** Esses identificadores não devem ser importados ou espelhados literalmente no Makimono; deve-se reter apenas o princípio arquitetural da separação estrita entre o host de navegação e o ambiente imersivo do player.
