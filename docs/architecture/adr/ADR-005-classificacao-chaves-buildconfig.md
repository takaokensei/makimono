# ADR-005: Classificação de Sensibilidade das Chaves do BuildConfig e Governança de Credenciais

- **Status:** Aprovado (Decisão Formal do Proprietário)
- **Data:** 2026-10-20
- **Contexto:** Makimono — Atualização de Governança para Agentes e Pacificação Documental

---

## 1. Contexto

Historicamente, o `ADR-003` aceitou o risco de embutir credenciais no `BuildConfig` sob a premissa de aplicativo de uso estritamente pessoal. Posteriormente, o `ADR-004` iniciou a migração do pareamento do Google Drive para Cloudflare Worker, e o documento `docs/plan_v4.md` (Linhas 104, 389, 425 e 463) continha recomendações contraditórias demandando a rotação contínua de tokens do GitHub.

Com a publicação do código e a operação de agentes de IA locais no repositório, fez-se necessária uma classificação formal e definitiva de sensibilidade de cada chave presente no `BuildConfig`.

---

## 2. Decisão

Por decisão expressa e formal do desenvolvedor/proprietário do repositório, as chaves do `BuildConfig` são categorizadas estritamente conforme a matriz abaixo:

| Chave / Identificador | Classificação | Justificativa / Escopo de Acesso | Diretriz para Agentes |
|---|:---:|---|---|
| **`TMDB_API_KEY`** | **Não sensível** | Chave pública de consulta a metadados e pôsteres da API The Movie Database. Sem permissão de escrita ou dados de usuário. | Não tratar como bloqueio; não exigir rotação. Nunca imprimir valores em logs. |
| **`GITHUB_API_TOKEN`** | **Não sensível** | Token de acesso pessoal (PAT) fine-grained com permissão restrita a `contents:read` para verificação de novas versões via GitHub Releases pelo atualizador in-app. | Pacifica e revoga as recomendações de rotação do `plan_v4.md`. Não exigir revogação ou rotação; não imprimir valores. |
| **`MAL_CLIENT_ID`** | **Não sensível** | Identificador público da aplicação no MyAnimeList (cadastrada como aplicação tipo `other`, status `PUBLISHED`). | Tratado como identificador público padrão de cliente OAuth2 PKCE. |
| **`MAL_CLIENT_SECRET`** | **Sensível** | Segredo de cliente da API do MyAnimeList. **É o único segredo sensível residual presente no `BuildConfig`.** | Prioridade de mitigação futura: eliminação do binário conforme o Anexo A (migração para OAuth2 PKCE puro sem secret no cliente). |

---

## 3. Consequências e Governança

1. **Pacificação Documental:** Ficam superadas as anotações do `docs/plan_v4.md` (L104, L389, L425 e L463) que solicitavam rotação do `GITHUB_API_TOKEN`.
2. **Atuação dos Agentes:** Ferramentas e agentes de IA não devem levantar alertas ou interromper processos devido à presença de `TMDB_API_KEY`, `GITHUB_API_TOKEN` ou `MAL_CLIENT_ID`.
3. **Foco de Segurança:** Todos os esforços arquiteturais de segredo residual concentram-se no isolamento ou remoção do `MAL_CLIENT_SECRET`.
