MuMuPlayer · Android 12 emulado como SM-A546E · 1920×1080 · variante debug isolada `zechs.drive.stream.visualreview`.

- `exo-controls.xml`: a seta `btnBack` está clicável e tem bounds `[63,68][140,145]`.
- `exo-dpad-focus.xml`: após DPAD_UP, o foco está em `btnBack`.
- Toque em (101,106), dentro dos bounds da seta: `PlayerActivity` fechou e `VisualReviewActivity` voltou ao topo.
- DPAD_CENTER com a seta focada: `PlayerActivity` fechou e `VisualReviewActivity` voltou ao topo.
- BACK em camadas: primeira pressão manteve `PlayerActivity` e dispensou a HUD; segunda pressão voltou à fixture.

O player foi aberto sem arquivo/ID de Drive, portanto nenhuma conta ou mídia real foi carregada. O SurfaceView sem vídeo aparece preto em captura; as evidências mantidas são os dumps de acessibilidade/foco e os estados de Activity observados pelo `dumpsys`. A variante principal `zechs.drive.stream` não foi reinstalada nem alterada.
