# Perfil — abas do catálogo no viewport móvel

**Data:** 07/10/2026  
**Dispositivo:** Moto G34, Android 15, 720×1600, 280 dpi  
**Pacote:** `zechs.drive.stream.visualreview`  
**Escopo:** editor de perfil, sem salvar perfil ou alterar dados reais.

## Problema e correção

No editor em português, o botão “Papéis de parede” era cortado como “Papéis de” no tamanho de fonte padrão. As duas abas também tinham altura de 38 dp. As abas agora permitem quebra em até duas linhas, não aplicam reticências e têm altura mínima de 48 dp.

## Validação

- `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1`: BUILD SUCCESSFUL; 323 testes em 73 suítes, 0 falhas, 0 erros e 0 ignorados.
- Instalei o APK arm64 apenas no pacote isolado `.visualreview` e reabri o editor de perfil.
- A captura após a mudança mostra “Papéis de parede” completo em duas linhas. Troquei para essa aba e confirmei o rótulo e a galeria de fundos; BACK fechou o editor sem salvar.
- D-pad DOWN×3 + RIGHT×2 levou o foco ao avatar de Gojo; o contorno ciano ficou visível, e BACK fechou o editor sem persistir seleção.
- Não houve `FATAL EXCEPTION` no log consultado.

## Evidências

- `tabs-before.png`: truncamento reproduzido.
- `tabs-after.png`: texto completo e alvos ampliados.
- `wallpapers-selected.png`: categoria de fundos selecionada e conteúdo visível.
- `dpad-avatar-focus.png`: foco remoto no terceiro avatar; `dpad-avatar-focus.xml` confirma o rótulo acessível e `focused=true`.
- XMLs correspondentes registram os controles e a hierarquia exposta ao Android.

## Limites

Este smoke cobre um viewport móvel, toque e eventos D-pad simulados. Não é homologação em TV física nem substitui leitura com TalkBack, fonte 1,3×/2×, conexões lentas ou falhas da CDN. Nenhuma imagem foi salva no perfil.
