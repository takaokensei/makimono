# Spike: mídia local (Fase 0)

Data: 2026-10-11. Dispositivo: `MakimonoAndroidTV1080p` (`emulator-5554`, API 36). Nada foi testado no Moto G34.

## S-01: seletor de pastas/arquivos (SAF) em Android TV

| Comando | Resultado |
|---|---|
| `adb shell pm resolve-activity -a android.intent.action.OPEN_DOCUMENT_TREE` | resolve apenas para `com.android.tv.frameworkpackagestubs.Stubs$DocumentsStub` |
| `adb shell pm resolve-activity -a android.intent.action.OPEN_DOCUMENT -t "*/*"` | `No activity found` |

**Conclusão:** em imagens Android TV padrão não existe seletor SAF real. `OPEN_DOCUMENT_TREE` cai num stub do framework e `OPEN_DOCUMENT` não tem handler. O SAF não pode ser o caminho primário na TV.

**Decisão (substitui o item 3.1 do plano):**
1. **MediaStore é a rota primária em TV** (e funciona também em celular): vídeos agrupados por `BUCKET_DISPLAY_NAME`/volume, URIs `content://media/...`.
2. SAF (`OpenDocumentTree`) vira rota **opcional**, oferecida só quando `resolveActivity` encontra um handler que não seja o stub.
3. Permissão: `READ_MEDIA_VIDEO` (API 33+) e `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`). Hoje o manifesto não declara nenhuma das duas.
4. Legendas na rota MediaStore: `.srt/.ass/.vtt` não são mídia indexada como vídeo. Rota: consulta `MediaStore.Files` (`MEDIA_TYPE_NONE`) filtrando por extensão no mesmo `RELATIVE_PATH`/bucket do vídeo (API 29+); abaixo disso, caminho de arquivo (`DATA`). Escolha manual de legenda na TV exige navegador próprio com D-pad (não há `OpenDocument`).

## S-02: abertura de `content://` nos players

- **Media3:** `DefaultDataSource` abre `content://` nativamente. Hoje o app só usa `AuthenticatingDataSource` (HTTP), então falta o roteamento por esquema (tarefa L-103).
- **MPV:** `MPVView.play(path)` chama `loadfile <string>` sem tratamento de esquema; o app só passa URLs HTTP do Drive. Para URIs `content://` o caminho previsto é abrir um `ParcelFileDescriptor` e passar `fd://<n>` ao `loadfile`, mantendo o descritor aberto durante a reprodução. **NÃO VERIFICADO em execução**: será validado na Fase 2 (L-202) com um vídeo real no emulador.

## S-03: desempenho de varredura

Não aplicável com MediaStore (consulta indexada pelo sistema). A medição só será necessária se a rota SAF opcional for implementada.

## Go / no-go

| Rota | Decisão |
|---|---|
| Media3 + `content://` | GO |
| MediaStore como listagem primária | GO |
| SAF primário | NO-GO em TV; opcional |
| MPV + `fd://` | PENDENTE de teste (Fase 2) |
