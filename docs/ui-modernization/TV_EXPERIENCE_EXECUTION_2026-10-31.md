# Makimono TV — execução da evolução visual e funcional

## Base e contrato

Direção aprovada: composição cinematográfica sóbria, foco branco, TV primeiro e
compatibilidade móvel. Preservar Views/XML, rotas, Room e motores existentes.
As jornadas e capturas usam somente a biblioteca real; nenhum banco demonstrativo
é criado. As políticas continuam cobertas por testes unitários isolados.

Base: main, a3c2579. O usuário liberou a árvore para esta execução, autorizando
preservar as alterações locais de Seedbox/Configurações. Os arquivos modificados
no início foram preservados fora do repositório para staging seletivo.

## Execução incremental

- Entrega 0: estados de Detalhes, listagem de pastas aninhadas, informação
  conhecida, isolamento da busca e proveniência da build — em implementação.
- Entrega 1: componentes, menus, rail e foco — pendente.
- Entrega 2: Home editorial e disponibilidade explícita — pendente.
- Entrega 3: detalhes, temporadas e histórico — pendente.
- Entrega 4: primeiro uso guiado e retomável — pendente.
- Entrega 5: player e telas secundárias — pendente.
- Homologação: verify-all, ADB/D-pad 1080p/720p e capturas reais — pendente.

## Verificação inicial

O verify-all inicial falhou na compilação de SeedboxRepositoryTest: os novos
testes usavam SeedboxCancelJobResponse e SeedboxCancelAllJobsResponse, mas a API
atual retorna Response<Unit>. A correção mantém a operação, a verificação de
sucesso e acrescenta a verificação da chamada exata. Nenhum teste foi ignorado.

## Limites de evidência

Crunchyroll: capturas históricas de TV e notas delimitadas do dossiê. Netflix:
APK móvel; não representa medidas ou foco de TV. Testes históricos não certificam
esta árvore. A validação física em TV e legendas ASS/SSA continua exigindo humano.
