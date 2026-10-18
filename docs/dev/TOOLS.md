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

*(Seção atualizada na Etapa 6)*
