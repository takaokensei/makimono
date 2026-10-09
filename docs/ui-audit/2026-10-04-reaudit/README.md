# Evidências da reauditoria — 04/10/2026

Parecer e cenários em [REAUDIT_2026-10-04.md](<C:/Users/Cauã V/Documents/antigravity/intelligent-mendel/docs/ui-modernization/REAUDIT_2026-10-04.md>). Capturas ADB do MuMu, não imagens simuladas por IA. Os previews têm dados de teste; produção é identificada no nome. PNG e XML são complementares; XML não prova que a mídia remota reproduziu.

## Seleção verificada

- `01-production-current.png`: editor de biblioteca aberto no início; cancelado sem salvar.
- `02-production-settings.png`: configurações do release instalado.
- `05-local-home.png`, `06-local-home-down.xml`, `07-local-home-grid.xml`: composição e sequência D-pad Home → shelf → grid.
- `08-local-details.png`, `09-local-details-tab.xml`, `10-local-details-episode.xml`: detalhes local normal e navegação.
- `11-local-profiles.png`: dupla borda branco/ciano no perfil focado.
- `12-local-settings.png`: lista atual, título e perfil renderizado.
- `15-media3-start.xml`, `16-media3-arrow-exit.xml`: seta por toque encerra PlayerActivity; retorno a preview vazio é esperado nesta rota de teste.
- `17-media3-before-back.xml`, `18-media3-after-back1.xml`, `19-media3-center-reopen.xml`: OSD fecha; CENTER reabre com foco Play/Pause.
- `20-media3-more.png`, `21-media3-modal-back.xml`: menu secundário e retorno.
- `22-mpv-start.png`, `23-mpv-ready.png`: imagem decodificada/OSD MPV. Sem XML válido correspondente.
- `24-mpv-paused.xml`, `25-mpv-hidden.xml`, `26-mpv-center-reopen.xml`, `27-mpv-up.xml`, `28-mpv-back-focus.xml`: pausa/input. Apesar do nome de `28`, o foco é **skipIntroRow**, não a seta; não contar como validação da seta.
- `29-local-files.png`, `30-local-files-list.xml`, `31-local-files-empty.xml`: toggle e busca sem resultado na fixture.
- `32-local-files-auth.png`: recuperação de sessão simulada e geometria de foco inconsistente.
- `33-local-settings-font200.png`, `34-local-details-font200.png`: fonte ampliada, reflow e dupla geometria da CTA.
- `36-profile-editor-ready.png`: editor e catálogo reais em fixture, sem persistência de alterações.
- `38-production-home.png`, `39-production-files.png`, `40-production-files-ready.png`: primeiro toque apenas focou Pastas, Home permaneceu visível. `39/40` têm nome da ação tentada, não da tela efetivamente aberta.
- `41-production-files-activated.png`: segundo toque abriu Files, raiz de biblioteca.
- `42-production-folder-open.png`: pasta Bakemonogatari, 25 itens.
- `43-production-files-gfxinfo.txt`, `43-production-files-dpad.xml`: reset de métricas + 25 comandos D-pad, pausa 180 ms; 166 frames, 0 janky. Medição específica do release instalado no emulador.
- `44-mpv-exit-before.xml`, `45-mpv-arrow-exit.xml`: toque na seta MPV retorna à Activity da fixture.
- `46-mpv-back-before.xml`, `47-mpv-back-hidden.xml`: BACK oculta OSD; segundo BACK retorna à Activity da fixture (confirmado por dumpsys).
- `48-home-audit-final.png`: preview local ao final. `font_scale` confirmado 1.0.

## Capturas excluídas de conclusões

- `03` e `04`: Home restaurada na posição rolada; nome `home-top` não significa topo.
- `14-media3-back-osd`: input enviado antes de o player estabilizar; hierarquia e screenshot não sustentam o teste pretendido.
- XML de `22/23` foi removido por dump sem idle, que anteriormente reutilizava arquivo remoto antigo. Os PNG são imagens reais; as conclusões de foco usam `24`–`28`, não aquele XML.
- `35-profile-editor`: screenshot/hierarquia durante abertura do diálogo; usar `36`.

O helper passou a usar nome remoto único e só copiar XML quando o dump confirma sucesso, evitando reutilização silenciosa de evidência anterior.
