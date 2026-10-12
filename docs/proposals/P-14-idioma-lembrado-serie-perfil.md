# P-14 — Idioma de áudio e legenda lembrado por série e perfil

- **ID:** P-14
- **Status:** Implementado (`TrackPreferencePolicy` + `PlaybackPreferences`)
- **Referências:** Crunchyroll (por conta), Kodi (por série/arquivo)

## Objetivo

Ao selecionar uma faixa de áudio ou legenda em um episódio, lembrar a preferência para a série e o perfil ativo, aplicando-a automaticamente nos episódios seguintes com fallback para a preferência global do aplicativo.

## Implementação e Testes

- **`TrackPreferencePolicy` (`ui/player/TrackPreferencePolicy.kt`):** Resolve a faixa preferida na ordem: (1) preferência da série (`SeriesTrackPreference`), (2) preferência global do perfil (`preferredAudioLang` / `preferredSubtitleLang`), (3) faixa marcada como selecionada/padrão pelo contêiner.
- **Testes unitários:** `TrackPreferencePolicyTest` (`selectAudioTrack prefers series language over global language`, `selectSubtitleTrack disables subtitles when series explicitly disabled them`, `selectSubtitleTrack prefers non-forced track over forced track for same language`).
