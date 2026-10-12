# P-17 — Painel de informação técnica unificado

- **ID:** P-17
- **Status:** Implementado (`MpvTechnicalInfoDisplay` + `MediaInfoDisplay`)
- **Referências:** Kodi (Player Process Info)

## Objetivo

Unificar a formatação de informações técnicas (Motor, Codec de vídeo, Resolução, FPS do contêiner, Decodificador ativo, Áudio, Legenda, Cache/Buffer, Alcance Dinâmico HDR e Taxa de Quadros AFR) para ambos os motores (`ExoPlayer (Media3)` e `MPV (libmpv)`), sem inventar valores de hardware quando não reportados pelo motor (`Indisponível`).

## Implementação e Testes

- **`MpvTechnicalInfoDisplay.rows(...)`:** Inclui linhas opcionais de motor (`engine`), HDR (`hdr`) e AFR (`afr`).
- **`MpvTechnicalInfoDisplay.rowsForMedia3(...)`:** Converte propriedades do `Format` do Media3 para o mesmo contrato de linhas usado pelo MPV.
- **Testes unitários:** `MpvTechnicalInfoDisplayTest` (`rowsForMedia3 formats ExoPlayer formats into unified technical info rows` + testes de propriedades ausentes e decodificador via software).
