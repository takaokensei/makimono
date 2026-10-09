# Q02.3 — merge estável de páginas e capas assíncronas

- Build instalado somente em `zechs.drive.stream.visualreview` no Moto G34 (serial `0086442783`); o pacote principal `zechs.drive.stream` não foi atualizado.
- A fixture de Files abriu com 12 cartões. Um DOWN moveu o foco para o cartão da segunda fileira e a grade rolou para manter o item focado visível. Esta é uma verificação de regressão da tela; a fixture não usa paginação real.
- `FilesPageMergerTest` cobre IDs repetidos entre páginas/na mesma página, preservação da ordem/posição, adoção dos metadados mais recentes, remoção do sentinel e não mutação do snapshot anterior.
- O trabalho assíncrono de capas agora tem geração por snapshot; página mais nova/erro cancela enriquecimento anterior. Resultados de capa são reaplicados pelo ID sobre o snapshot atual, e atualizações emitem cópias novas em vez de publicar uma lista antiga indexada.
- Build/teste: `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`, 202 testes, 0 falhas/erros/skips.
- Limite: sem sessão Drive, não foi possível observar duplicatas ou corrida de poster no serviço real; retentar após falha de rede, cancelamento ao sair da pasta e comportamento com páginas que se sobrepõem precisam de teste autenticado.
