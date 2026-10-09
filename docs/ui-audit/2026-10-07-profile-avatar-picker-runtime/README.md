# Seletor de avatares — rolagem e foco

Data: 07/10/2026. Verificação em Moto G34 Android 15, somente na variante `zechs.drive.stream.visualreview` e com dados sintéticos.

O seletor já renderiza os avatares como imagens circulares de 112 dp; não existe placa cinza quadrada ao redor. O anel ciano acompanha apenas o foco do D-pad. Nesta rodada removi o título redundante da galeria, que aparecia truncado junto ao cabeçalho fixo do diálogo, preservando o espaçamento antes das abas.

Ao navegar para fileiras mais abaixo, o auto-scroll agora alinha a fileira focada quando encontra uma fileira anterior parcialmente cortada no topo. Isso mantém a navegação previsível e evita uma faixa de avatar cortada sob o título. RIGHT e DOWN moveram o foco entre avatares, com anel visível e sem moldura retangular; BACK fechou a prévia sem salvar.

Capturas: `profile-picker-before.png`, `profile-picker-final.png`, `profile-picker-navigation.png`; hierarquias correspondentes em XML. O APK arm64 de revisão foi instalado apenas no pacote `.visualreview`, SHA-256 `912818566B520E3F6FA031B8584020D7A2D9175B0FE25A0D8ACE21C1E08EBA3A`.

Validação: `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1` passou; 291 testes em 64 suítes, sem falhas, erros ou skips. `git diff --check` não encontrou erros de whitespace (apenas avisos de conversão LF/CRLF em arquivos existentes). TV física, TalkBack e seleção/salvamento real continuam pendentes.
