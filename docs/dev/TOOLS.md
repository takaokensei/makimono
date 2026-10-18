# 🧰 Ferramentas e Integrações para Agentes — Makimono

Este documento descreve as ferramentas auxiliares homologadas para uso conjunto com agentes de IA (Antigravity e Codex) no repositório Makimono: **Context7** (consulta de documentação técnica atualizada) e **Graphify** (análise e navegação de grafo de código).

---

## 1. Context7 (Documentação Técnica em Tempo Real via MCP)

O **Context7** é um servidor MCP (Model Context Protocol) que permite aos agentes de IA consultar documentação técnica oficial, atualizada e versionada diretamente das fontes canônicas de bibliotecas externas, evitando o uso de APIs obsoletas ou métodos depreciados provenientes de pré-treinamento desatualizado.

### 1.1 Configuração de Segurança de Credenciais
> [!IMPORTANT]
> **O repositório Makimono é público.** Sob nenhuma circunstância a chave de API do Context7 deve ser salva em arquivos versionados do repositório, commits ou logs de execução.

A chave deve ser fornecida estritamente por meio de variáveis de ambiente do sistema operacional ou no arquivo de configuração do MCP fora da árvore do Git:

#### No Windows (PowerShell):
```powershell
# Define a variável permanentemente no escopo do usuário (requer reiniciar a sessão/IDE):
[System.Environment]::SetEnvironmentVariable('CONTEXT7_API_KEY', 'ctx7sk-SEU-TOKEN-AQUI', 'User')

# Ou na sessão atual do terminal:
$env:CONTEXT7_API_KEY = "ctx7sk-SEU-TOKEN-AQUI"
```

#### No Linux / macOS (Bash / Zsh):
```bash
# Adicionar ao ~/.bashrc ou ~/.zshrc:
export CONTEXT7_API_KEY="ctx7sk-SEU-TOKEN-AQUI"
```

---

### 1.2 Como Configurar o MCP do Context7 na IDE

#### No Antigravity (ou Gemini Code Assist):
Edite o arquivo global de configuração MCP em `~/.gemini/antigravity/mcp_config.json` (ou no painel de configurações de MCP da IDE):

```json
{
  "mcpServers": {
    "context7": {
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp"],
      "env": {
        "CONTEXT7_API_KEY": "${env:CONTEXT7_API_KEY}"
      }
    }
  }
}
```

#### No OpenAI Codex / Claude Desktop / Cursor:
No arquivo de configuração de servidores MCP (`claude_desktop_config.json` ou similar):

```json
{
  "mcpServers": {
    "context7": {
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp"],
      "env": {
        "CONTEXT7_API_KEY": "SUA_CHAVE_OU_VARIAVEL_DE_AMBIENTE"
      }
    }
  }
}
```

> [!NOTE]
> **Conexão Remota vs Local:** Se utilizar o endpoint HTTP remoto do Context7 Cloud, configure o cliente MCP para apontar para `https://api.context7.com/mcp` transmitindo o token via cabeçalho `Authorization: Bearer <TOKEN>`.

---

### 1.3 Regra Mandatória de Consulta para o Agente

> [!CAUTION]
> **Regra Obrigatória:** Antes de implementar ou refatorar qualquer código que faça uso de bibliotecas de terceiros ou frameworks do ecossistema AndroidX/Google, o agente **DEVE consultar a documentação da versão exata** via Context7. Nunca assuma assinaturas de métodos com base em versões antigas.

#### Versões Canônicas Utilizadas no Makimono (`app/build.gradle`):

| Biblioteca | Versão Real do Makimono | Identificador para Context7 | Tópicos Críticos para Consulta |
|---|---|---|---|
| **AndroidX Media3** | `1.4.1` | `androidx.media3` | `PlayerView`, interceptação de D-pad, estado de buffering, `TrackSelectionParameters`, `DefaultRenderersFactory` |
| **AndroidX Room** | `2.6.1` | `androidx.room` | `Flow` reativo, coroutines, migrações sem perda (`MIGRATION_*`), DAOs atômicos |
| **Glide** | `4.13.2` | `com.github.bumptech.glide` | `CustomTarget`, placeholders, decodificação de thumbnails de vídeo, cache em disco |
| **Retrofit / OkHttp** | `2.9.0` / `4.12.0` | `retrofit2`, `okhttp3` | Interceptores OAuth, renovação de token, backoff de retry e timeout |
| **Moshi** | `1.15.0` | `com.squareup.moshi` | Adapters JSON codegen, `@JsonClass(generateAdapter = true)` |
| **Hilt / WorkManager** | `2.51.1` / `2.9.0` | `dagger.hilt`, `androidx.work` | `@HiltWorker`, injeção assistida por fábrica, periodicidade de sincronização |
| **Android TV Provider** | `1.0.0` | `androidx.tvprovider` | `PreviewChannel`, `PreviewProgram`, canal do sistema "Watch Next" |

> [!NOTE]
> **Sobre o AndroidX Leanback:** O Makimono **NÃO** inclui a dependência `androidx.leanback:leanback`. O app utiliza layouts AndroidX / Material3 e ConstraintLayout com navegação D-pad customizada e `tvprovider`. Se houver recomendação externa de Leanback, ela não deve ser adotada sem discussão arquitetural prévia.

#### Exemplos de Consultas Context7:
```text
Context7 Query: "androidx.media3 version 1.4.1 how to handle custom D-pad keys and OSD show/hide without stealing focus"
Context7 Query: "androidx.room version 2.6.1 upsert and transaction with Kotlin coroutines"
Context7 Query: "glide version 4.13.2 load image into custom ImageView with crossfade and obsidian dark placeholder"
```

---

## 2. Graphify (Grafo de Conhecimento e Dependências do Código)

O **Graphify** é um analisador estático que mapeia relações de dependência, chamadas e hierarquias de símbolos de um repositório, gerando um grafo de conhecimento local (`graphify-out/graph.json`) consultável por agentes e desenvolvedores.

### 2.1 Avaliação de Compatibilidade com Kotlin / Android
- **Suporte Homologado:** O Graphify suporta código Kotlin (`.kt`) e Java nativamente por meio de gramáticas locais **Tree-sitter**.
- **Privacidade e Execução Offline:** A extração foi validada no modo `--code-only` com backend desligado (`GRAPHIFY_BACKEND=none`), garantindo que **nenhum trecho de código ou token seja enviado para serviços de nuvem ou LLMs externas**.
- **Desempenho no Makimono:** A extração analisou com sucesso 426 arquivos de código-fonte Kotlin/C++, gerando um grafo estruturado com **5.192 nós, 12.616 arestas e 296 comunidades estruturais**.

---

### 2.2 Como Gerar o Grafo Localmente

O pacote oficial do Graphify é publicado no PyPI sob o nome `graphifyy` (com duplo 'y') e pode ser executado via `uvx` (recomendado) ou instalado no ambiente Python:

```powershell
# 1. Configurar execução estritamente local sem LLM e sem envio de logs
$env:GRAPHIFY_BACKEND = "none"
$env:GRAPHIFY_QUERY_LOG_DISABLE = "1"

# 2. Executar a extração de AST estática sobre o código
uvx --from graphifyy graphify extract . --code-only
```

> [!IMPORTANT]
> **Higiene de Repositório (`.graphifyignore` e `.gitignore`):**
> - O arquivo [`.graphifyignore`](../../.graphifyignore) instrui o Graphify a ignorar diretórios de build, cache e dossiês (`build/`, `.gradle/`, `.idea/`, `makimono-dossie/`, `backend/`, `benchmark/`, `mpv/build/`, `docs/archive/`).
> - A pasta `graphify-out/` possui ~7MB e armazena caminhos absolutos locais da máquina do desenvolvedor; por segurança e portabilidade, ela **nunca deve ser commitada** e está incluída no [`.gitignore`](../../.gitignore).

---

### 2.3 Como Consultar o Grafo via Linha de Comando

```powershell
# 1. Explicar um nó, seus métodos e vizinhos imediatos:
uvx --from graphifyy graphify explain "<NomeDaClasseOuSimbolo>"

# 2. Encontrar a cadeia de dependências ou caminho mais curto entre dois nós:
uvx --from graphifyy graphify path "<Origem>" "<Destino>" --undirected

# 3. Gerar visualização em árvore interativa D3 v7:
uvx --from graphifyy graphify tree --output graphify-out/arvore.html
```

---

### 2.4 Resultados das 3 Perguntas de Validação

Durante a homologação local da ferramenta no repositório, foram submetidas 3 perguntas estruturais:

#### Pergunta 1: Quais classes dependem de `SeriesMediaMetadataPolicy`?
- **Comando:** `uvx --from graphifyy graphify explain "SeriesMediaMetadataPolicy"`
- **Resultado do Grafo:**
  - O nó canônico `SeriesMediaMetadataPolicy` (em `app/src/main/java/zechs/drive/stream/ui/series/SeriesMediaMetadataPolicy.kt`) foi mapeado com 6 arestas diretas para seus métodos de política (`quality`, `audio`, `subtitles`, `audioLabelForFile`, `qualityLabel`).
  - O grafo vincula a classe de política diretamente à sua suíte de testes `SeriesMediaMetadataPolicyTest`, com relacionamento de cobertura aos 5 testes unitários de casos de borda.
  - Na camada de apresentação, a política é consumida funcionalmente por `SeriesDetailFragment` e pelo presenter `presentSeriesEpisode`.

#### Pergunta 2: Qual é a cadeia de chamadas entre a seleção de um episódio na UI e o início da reprodução no `PlayerActivity`?
- **Comando:** `uvx --from graphifyy graphify path "SeriesDetailEpisodeAdapter" "PlayerActivity" --undirected`
- **Resultado do Grafo:**
  - O Graphify resolveu o caminho mínimo com **2 saltos** precisos:
  $$\text{SeriesDetailEpisodeAdapter} \xleftarrow{\text{imports}} \text{SeriesDetailFragment.kt} \xrightarrow{\text{imports}} \text{PlayerActivity}$$
  - O clique no item do adaptador despacha o callback para o `SeriesDetailFragment`, que constrói o Intent com os metadados do episódio e inicia o `PlayerActivity` (ou `MPVActivity`).

#### Pergunta 3: Onde estão definidas as cores do design system Obsidian e onde são consumidas?
- **Resultado do Grafo e Diagnóstico de Escopo:**
  - No código Kotlin (`.kt`), o Graphify mapeia o enum `ThemeMode.OBSIDIAN` e os testes de fallback de tema.
  - **Limitação de Parser Tree-sitter:** O Graphify em modo `--code-only` analisa exclusivamente arquivos de código-fonte (`.kt`, `.java`, `.cpp`, `.h`) e desconsidera arquivos de recursos XML Android (318 arquivos de recursos foram classificados como não-código).
  - **Arquitetura Canônica:** As cores semânticas do Obsidian estão centralizadas no arquivo XML de recursos [`app/src/main/res/values/colors.xml`](../../app/src/main/res/values/colors.xml) sob os tokens `@color/obsidian_*`, consumidas pelos layouts XML em `app/src/main/res/layout/` e auditadas continuamente pelas ferramentas estáticas em `tools/checks/check_layout_colors.py` e `tools/checks/check_contrast.py`.

---

### 2.5 Limitações Identificadas do Graphify no Projeto
1. **Recursos XML Fora da AST:** Relações declaradas em XML (como atributos de layout `android:background="@color/obsidian_bg_base"` ou intents no `AndroidManifest.xml`) não geram nós na árvore de código, necessitando de auditorias complementares (`tools/checks/`).
2. **Caminhos Direcionados em Frameworks Reativos:** A busca direcionada pura (`graphify path` sem `--undirected`) pode não encontrar conexões quando a navegação ocorre por injeção de dependência indireta ou callbacks funcionais; o modificador `--undirected` contorna essa limitação com eficácia.
3. **Caminhos Locais no Output:** O arquivo `graph.json` grava caminhos absolutos do sistema operacional da máquina onde foi gerado; por esse motivo, o artefato é efêmero e local.

