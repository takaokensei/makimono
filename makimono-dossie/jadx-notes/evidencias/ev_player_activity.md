# Evidência Decompilada: PlayerActivity

**Evidence ID:** `ev_07b5781d17b0cf694ed5db3060363212824cd3bcb7fc0e5261e3ba25216468b0`  
**Classe:** `com.crunchyroll.crunchyroid.player.ui.PlayerActivity`  
**Fonte:** JADX Headless MCP Engine v0.7.1 via REA v6.4.0  
**Enquadramento:** `[IDEIA]` Arquitetura do Player e Portão de Acesso.

---

## Assinatura e Campos Observados

```text
Class: com.crunchyroll.crunchyroid.player.ui.PlayerActivity
Superclass: com.crunchyroll.crunchyroid.player.ui.Hilt_PlayerActivity -> FragmentActivity

Campos de Hardware de Vídeo:
- Surface (android.view.Surface)
- SurfaceControl (android.view.SurfaceControl)
- DisplayManager (android.hardware.display.DisplayManager)
- MediaManager (com.google.android.gms.cast.tv.media.MediaManager)
- AppRemoteConfigRepo (com.crunchyroll.core.remoteconfig.repo.AppRemoteConfigRepo)
- UserProfileInteractor (com.crunchyroll.core.di.UserProfileInteractor)
```

---

## Métodos Críticos Identificados

- `y2(NavController, VideoContent, ContentAccessGateType)`: Portão de validação de acesso ao conteúdo (*Content Access Gate*). Intercepta streams não autorizados e redireciona para telas de assinatura/login sem permitir reprodução desautorizada.
- `s1(LupinStatus, PlayerActivity, NavHostController, boolean, VideoContent, VideoContent, ShowMetadata)`: Orquestrador do estado de reprodução acoplado aos perfis do usuário.
- `n2(NavHostController, PlayerActivity, VideoContent, boolean, CoroutineScope, FocusRequester, NavGraphBuilder)`: Montagem do grafo Compose de controles com injeção de `FocusRequester` para focar diretamente na barra de transporte ou nos botões de controle.

---

## Fato Observado e Conclusão

1. A reprodução é rigorosamente contida em processo de apresentação desacoplado da navegação de catálogo.
2. Controles de acesso DRM / Assinatura são delegados a componentes internos (`ContentAccessGate`), não permitindo nem justificando contorno.
3. Para o Makimono, toda a camada de `ContentAccessGate` e DRM é substituída pela leitura direta de tokens OAuth do Google Drive e arquivos de mídia compatíveis com ExoPlayer e MPV.
