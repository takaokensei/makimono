# Seletor de pasta raiz da biblioteca — QA local (06/10/2026)

O campo de texto livre para a pasta raiz foi substituído por um navegador hierárquico do Google Drive. O seletor apresenta o caminho atual, permite subir um nível ou voltar ao Meu Drive, lista apenas subpastas e pagina resultados. A pasta escolhida só é persistida no perfil depois de confirmar; Cancelar/BACK não altera a preferência. A fixture usada no QA retorna uma notificação local e não grava preferências nem acessa a conta.

## Validação

- `testDebugUnitTest`: sucesso; 281 testes, 0 falhas, 0 erros, 0 ignorados.
- `assembleDebug`: sucesso com `-Pkapt.incremental.apt=false`. O KAPT incremental do checkout falhou ao ler um snapshot de classpath existente (`ClasspathEntryData.readObject`); a execução não incremental recompilou e empacotou normalmente sem limpar caches.
- APK de revisão instalado somente no Moto G34 (`0086442783`, Android 15, 1600×720 em paisagem), pacote isolado `zechs.drive.stream.visualreview`.
- Com D-pad, percorri `Meu Drive → oneblacki → Bakemonogatari → Temporada 01 → Episódios`; breadcrumbs acompanharam o caminho, BACK voltou aos pais e uma pasta vazia manteve **Usar esta pasta** focado e acessível.
- Confirmei a ação na fixture: o diálogo fechou e a Activity de revisão continuou aberta. Nenhuma preferência real foi gravada.
- `git diff --check` passou nos arquivos dessa entrega.

## Evidências

- `root-final.png` / `root-final.xml`: pasta `oneblacki` visível na raiz.
- `oneblacki.png` / `oneblacki.xml` e `bakemonogatari.png` / `bakemonogatari.xml`: navegação e breadcrumbs.
- `empty-final.png` / `empty-final.xml`: estado vazio com título/mensagem e foco na confirmação.
- `back-to-season.xml`, `back-to-root.xml` e `confirmed-fixture.xml`: retorno hierárquico e fechamento pela confirmação.

## Limites

A árvore é sintética e o QA foi feito em celular, em paisagem. Ainda falta validar a listagem/paginação com uma sessão real do Drive, pastas compartilhadas/atalhos, rede e controle remoto em TV física e resolução 1080p. Sem release, commit ou push.
