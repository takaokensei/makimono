# Propostas de Evolução de Produto (P-01 a P-08) — Nível Netflix / Apple TV / Kodi

Baseado na pesquisa dos sistemas Kodi (Estuary Skin, CRenderManager), Stremio (Addon/Cinemeta architecture) e Apple TV HIG:

---

### P-01: Unificação Arquitetural do Player sob Contrato `PlayerEngine`
- **Problema:** `PlayerActivity.kt` e `MPVActivity.kt` duplicam ~2.200 linhas de código de OSD, ciclos de vida, controles de áudio e atalhos D-pad.
- **Solução:** Interface única `PlayerEngine` com implementações plugáveis `ExoPlayerEngine` e `MpvPlayerEngine`. Uma única Activity hospeda toda a UI do OSD e listeners de controle.
- **Impacto:** Eliminação imediata de dívida técnica e garantia de paridade total de recursos em ambos os motores.

---

### P-02: Sistema de Metadados e Scraper Local-First Inspirado no Kodi / Stremio
- **Problema:** Arquivos de mídia do Google Drive exibem nomes brutos de arquivo quando não há correspondência exata.
- **Solução:** Pipeline em background com regex inteligente (`EpisodeParser`) + Cinemeta/TMDB/AniDB local caching em Room, enriquecendo pôsteres em alta definição, fanart/backdrop, sinopses, classificação etária e trailers.
- **Impacto:** Experiência visual rica padrão Netflix/Apple TV sem lentidão de rede ao carregar prateleiras.

---

### P-03: Quick Audio / Subtitle Sync Offsets no OSD (Padrão Kodi)
- **Problema:** Arquivos de vídeo com desfasamento de áudio ou legenda externa forçam o usuário a reiniciar ou tolerar o dessincronismo.
- **Solução:** Diálogo integrado no OSD permitindo ajuste fino de delay em incrementos de ±50ms para áudio e legenda, persistido por episódio ou por pasta/série.
- **Impacto:** Nível profissional de controle de mídia, idêntico ao PotPlayer e Kodi.

---

### P-04: Transições Visuais e Motion HIG Padrão Apple TV
- **Problema:** Transições de tela em TVs de baixa performance podem sofrer quedas de frame se não utilizarem desacelerações de hardware.
- **Solução:** Adoção rigorosa dos tokens semânticos de `motion_spec.xml` com suporte dinâmico a `Reduce Motion` respeitando a preferência de acessibilidade do sistema Android TV.
- **Impacto:** Fluidez máxima de 60fps/120fps sem flickering de GPU.

---

### P-05: Continuous Playback & Binge-Watching Automático (Estilo Netflix)
- **Problema:** Ao término de um episódio, o usuário retorna à lista de arquivos ou necessita navegar manualmente.
- **Solução:** Card flutuante pré-final ("A seguir em 10s...") com pré-carregamento do token de stream do episódio subsequente e skip automático de créditos.
- **Impacto:** Redução de atrito para consumo contínuo.

---

### P-06: Suporte a Perfis Múltiplos com Isolamento de Histórico e Nuvem
- **Problema:** Diferentes usuários do mesmo aparelho compartilham o mesmo histórico de "Continuar Assistindo".
- **Solução:** Seletor de perfis na inicialização (padrão Netflix) com IDs isolados em Room e pastas raiz customizadas do Google Drive.
- **Impacto:** Privacidade e personalização completa para famílias ou múltiplos usuários.

---

### P-07: Auto Frame Rate & Match Dynamic Range (Apple TV Grade)
- **Problema:** Judder 3:2 em TVs HDMI 2.0/2.1 ao exibir filmes de 23.976fps/24fps em telas rodando a 60Hz.
- **Solução:** Utilização de `AutoFrameRatePolicy` para chaveamento seamless de modo de exibição no HDMI (QMS onde suportado) e `HdrModeManager` para ativar o canal de cores HDR10/Dolby Vision da TV.
- **Impacto:** Qualidade cinematográfica sem artefatos de movimento.

---

### P-08: Integração Profunda com Canais do Android TV Launcher (Leanback Watch Next)
- **Problema:** O usuário precisa abrir o aplicativo para ver onde parou.
- **Solução:** Alimentação do canal "Reproduzir a seguir" na tela inicial do Android TV via `TvWatchNextManager`, com pôsteres, banner e progresso em porcentagem.
- **Impacto:** Integração de primeira classe com o ecossistema Android TV e Google TV.
