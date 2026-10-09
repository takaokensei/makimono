# Auditorias Visuais e Capturas Curadas

Este diretório contém exclusivamente capturas visuais curadas (máximo de 20 por ciclo de auditoria), documentando a validação em dispositivos reais (TV 1080p, 4K, emuladores).

## Estrutura

- `curated-tv-baseline/`: Conjunto de 9 capturas de referência da sessão real em Android TV 1080p (Perfil, Home, Rail lateral, Catálogo, Detalhes de filme, Player Media3 OSD, Player MPV render, Configurações, Seletor de pasta).

## Armazenamento Externo de Dumps

Os dumps completos brutos de iterações passadas (arquivos XML de hierarquia de views e capturas transitórias anteriores, totalizando ~715 MB) foram arquivados externamente para manter o repositório leve e ágil:
- Local de arquivo externo: `../ui-audit-archive/` (ou armazenamento de backup do desenvolvedor).
- Índice de auditoria: consulte as notas de versão em `docs/releases/` e `docs/qa/focus-checklist.md`.
