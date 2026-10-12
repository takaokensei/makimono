# P-18 — Prévia no destaque da Home (Só Desenho)

- **ID:** P-18
- **Status:** Proposta de desenho (Sem código — aguarda decisão D-4x)
- **Referências:** Netflix (Billboard), Apple HIG (Motion)

## Contexto e Problema

Em aplicativos como a Netflix, o destaque (Hero/Billboard) da tela inicial pode reproduzir um trecho silencioso ou trailer após alguns segundos de foco parado. No Makimono, os metadados vêm do AniList / TMDB e os arquivos de mídia residem no Google Drive do usuário ou no armazenamento local:

1. **Fontes externas (AniList / YouTube):** O AniList retorna identificadores de trailers do YouTube/Dailymotion, não URLs diretas de stream MP4/HLS reproduzíveis nativamente no SurfaceView sem extrator externo.
2. **Arquivos do Google Drive:** Iniciar uma sessão de streaming de um arquivo MKV de alto bitrate (10–40 Mbps) apenas para um fundo decorativo na Home consome cota de download da API do Google Drive, largura de banda e decodificador de hardware em TVs de entrada.
3. **Acessibilidade e Movimento (Apple HIG):** Com `ANIMATOR_DURATION_SCALE == 0` (`MotionPolicy.isReduced`), vídeos em reprodução automática no plano de fundo devem ser estritamente desativados.

## Opções Arquiteturais

| Opção | Descrição | Prós | Contras / Riscos |
|---|---|---|---|
| **A. Apenas arquivos Trailer/PV locais na pasta do Drive** | Reproduzir prévia silenciosa somente quando existir arquivo `PV`/`Trailer` (`SeriesDetailEpisodeSelection.firstTrailer`) ou mídia local | Sem dependência do YouTube; respeita biblioteca real | Consome requisição Drive se o usuário navegar rápido pelo Hero |
| **B. Ken Burns suave sobre o Banner HD (Atual)** | Manter transição estática/Ken Burns leve sobre o `bannerUrl` sem instanciar decodificador de vídeo na Home | Zero consumo de cota Drive e zero disputa de `MediaCodec` com o player | Sem vídeo em movimento no Hero |

## Recomendação

Manter a **Opção B** como padrão até decisão explícita do dono, garantindo que o decodificador de hardware e a cota da API do Google Drive sejam reservados exclusivamente para a sessão de reprodução iniciada pelo usuário.
