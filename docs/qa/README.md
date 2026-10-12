# 🔍 Garantia de Qualidade e Auditoria (QA) — Makimono

Este diretório centraliza os relatórios de qualidade, baselines de teste, auditorias automatizadas e diagnósticos de execução do Makimono.

---

## 🧭 Estrutura e Histórico de Ciclos

### 1. Ciclo Ativo: Plano v6
O ciclo v6 estabelece a padronização e elevação do app a partir da auditoria estrita do commit `ca26827`:
- **Documento Diretor:** Plano v6 de qualidade e arquitetura (`plano_v6_makimono.md`).
- **Relatórios de Execução:** Localizados em `docs/qa/v6/` à medida que as fases forem concluídas.

### 2. Ciclo v5 (Transição e Pruning)
Relatórios de execução da fase v5 focados em eliminação de advertências críticas e conformidade de tipos:
- [`v5/fase-1-relatorio.md`](v5/fase-1-relatorio.md) — Eliminação de `NewApi` (20 issues).
- [`v5/fase-2-relatorio.md`](v5/fase-2-relatorio.md) — Eliminação de `UnsafeOptInUsageError` (180 issues) e tratamento de blocos `catch` vazios.
- [`v5/fase-3-relatorio.md`](v5/fase-3-relatorio.md) — Guardas de CI, métricas JaCoCo e evidências de OSD.
- [`v5/fase-4-5-relatorio.md`](v5/fase-4-5-relatorio.md) — Roteamento desacoplado de motores de reprodução e sincronização de áudio.

### 3. Ciclos Anteriores (Histórico Preservado)
Os relatórios numerados na raiz de `docs/qa/` refletem etapas anteriores de estabilização do app (versões 1.4 a 1.6):
- [`fase-2-report.md`](fase-2-report.md) a [`fase-7-report.md`](fase-7-report.md) — Histórico de hardening, testes instrumentados e correções pontuais de D-pad.

---

## 📊 Documentos de Diagnóstico e Referência

- **Matriz de Dispositivos e Requisitos:** [`device-matrix.md`](device-matrix.md)
- **Checklist de Foco Android TV (10-foot experience):** [`focus-checklist.md`](focus-checklist.md)
- **Diagnóstico do Bug do OSD:** [`osd-bug.md`](osd-bug.md)
- **Baseline de Desempenho e Medições:** [`perf-baseline.md`](perf-baseline.md) e [`baseline-0.md`](baseline-0.md)
- **Spike de Mídia Local:** [`local-media/spike.md`](local-media/spike.md)
- **Auditorias Visuais Curadas:** [`audits/README.md`](audits/README.md)
