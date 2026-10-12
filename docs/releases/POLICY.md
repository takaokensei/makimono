# 🛡️ Política de Release e Integridade de Artefatos — Makimono

Este documento define a política oficial e inegociável de distribuição, empacotamento, versionamento e governança de releases do projeto Makimono.

---

## 1. Princípios Fundamentais

1. **Releases Exclusivas da Branch Principal (`main`):**
   - Nenhuma release ou artefato de distribuição pode ser gerado a partir de branches de feature, correções intermediárias ou forks não integrados.
   - Todo código em release deve ter passado previamente por pipeline de integração contínua (CI) verde.

2. **Imutabilidade Absoluta de Tags Git:**
   - Tags de versão (`vX.Y.Z`) são estritamente imutáveis.
   - **Proibição:** É estritamente proibido recriar, mover ou forçar atualização de tags existentes (`git tag -f`, `git push --force origin <tag>`).
   - Se uma release apresentar qualquer defeito imediatamente após a publicação, a correção **deve** gerar uma nova versão de patch incremental (`vX.Y.Z+1`), preservando a tag anterior para auditoria e rastreabilidade.
   - *Caso de Referência:* O incidente da `v1.7.1` (em que uma tag foi movida com `--force` após falha de teste) exemplifica o risco de inconsistência entre quem baixou o artefato no primeiro minuto e usuários subsequentes. A partir do ciclo v6, tags nunca mais são reescritas.

3. **Validação de Checksums (SHA-256):**
   - Cada artefato (`.apk`) gerado para distribuição pública deve acompanhar obrigatoriamente um arquivo de checksum SHA-256 (`.sha256`) gerado e assinado no momento do build.
   - O pipeline de CI e release no GitHub Actions deve validar os checksums antes da publicação.

4. **Publicação Automatizada via CI:**
   - Builds de release oficiais devem ser produzidos no ambiente hermético do GitHub Actions runner (`release.yml`), assinado com a chave de produção configurada via GitHub Secrets.
   - Compilações locais servem unicamente para testes de fumaça e validação em emuladores/dispositivos do desenvolvedor, devendo estar claramente rotuladas como builds locais/debug.

---

## 2. Checklist Pré-Release

Antes de qualquer nova release ser publicada:
- [ ] Todos os testes unitários (`./gradlew testDebugUnitTest`) passando com 0 falhas.
- [ ] Verificação de qualidade estática (`check_strings.py`, `check_readme_claims.py`, `check_no_direct_log.py`) sem desvios.
- [ ] `./gradlew lintDebug` executado sem erros bloqueantes além da baseline documentada.
- [ ] `versionCode` incrementado estritamente em `app/build.gradle`.
- [ ] `versionName` no `app/build.gradle` alinhado à tag pretendida.
- [ ] PR aprovado e mesclado na branch `main`.
- [ ] Tag criada em `main` e enviada sem parâmetros de force: `git tag -a vX.Y.Z -m "Release vX.Y.Z" && git push origin vX.Y.Z`.
