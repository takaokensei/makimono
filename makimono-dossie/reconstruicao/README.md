# Dossiê Sucessor — Reconstrução Abrangente Makimono TV

**Data de abertura:** 15/10/2026  
**Escopo:** Reconstrução sistemática dos comportamentos, componentes, estados e navegação da experiência de referência (Crunchyroll Android TV `com.crunchyroll.crunchyroid` v3.61.2) adaptados à biblioteca pessoal no Google Drive (`zechs.drive.stream.visualreview`).

---

## 1. Estrutura do Dossiê (`makimono-dossie/reconstruicao/`)

- [`inventario-funcional.md`](inventario-funcional.md) — Denominador versionado de funcionalidades, telas, componentes e estados (`feature_id`).
- [`cobertura-da-referencia.md`](cobertura-da-referencia.md) — Mapeamento de cada `feature_id` no APK de referência (`observado em runtime`, `observado estaticamente`, `inferido`, `desconhecido`).
- [`cobertura-do-makimono.md`](cobertura-do-makimono.md) — Rastreabilidade de implementação no Makimono (`feature_id → evidência → especificação → decisão → arquivo/símbolo → teste → resultado no dispositivo`).
- [`decisoes.md`](decisoes.md) — Registro arquitetural de equivalências, adaptações para biblioteca pessoal no Google Drive e pipeline de metadados.
- [`pendencias.md`](pendencias.md) — Registro de bloqueios resolvidos na Fase R00 e backlog das fases R01–R14.
- `evidencias/manifesto.json` — Manifesto verificável por SHA-256 relacionando capturas, dumps XML, relatórios de teste e APKs às respectivas execuções.
- `especificacoes/` — Contratos funcionais por superfície (`navegacao.md`, `componentes.md`, `home.md`, `busca.md`, `detalhes.md`, `perfis.md`, `player.md`, `estados-e-erros.md`).
- `validacao/` — Roteiros repetíveis em `emulator-5554`, resultados datados e métricas.

---

## 2. Regras de Rastreabilidade e Evidência

1. **Vínculo de Artefatos:** Nenhum relatório ou captura é aceito sem associação explícita ao SHA-256 do APK e dos arquivos-fonte relevantes em `evidencias/manifesto.json`.
2. **Proibição de Comportamento Artificial:** Nenhuma implementação de produção ou protótipo pode inspecionar nomes de testes ou `stackTrace` para fabricar resultados.
3. **Isolamento de Dispositivo:** Todas as validações de TV utilizam exclusivamente `-s emulator-5554`.
4. **Preservação de Histórico:** Resultados anteriores (incluindo `auditoria-2026-10-14/`) permanecem preservados; correções geram novos adendos datados (`auditoria-2026-10-15-r00/`).
