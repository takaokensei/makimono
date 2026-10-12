# Porta D-50 — Sincronização de Progresso entre Aparelhos

- **ID:** D-50 (Tarefa T-507)
- **Status:** Proposta de arquitetura (Sem alteração de escopo OAuth até decisão do dono)
- **Referências:** Kodi (MySQL / Trakt), Crunchyroll, Google Drive API v3

## Contexto e Restrição Técnica

Atualmente o Makimono autentica no Google Drive utilizando exclusivamente o escopo de leitura:

```text
https://www.googleapis.com/auth/drive.readonly
```

Esse escopo garante que o aplicativo **jamais possa modificar, sobrescrever ou excluir** arquivos do Google Drive do usuário (princípio de segurança por menor privilégio). Contudo, ele também impede que o aplicativo grave um arquivo de estado (ex.: `makimono-sync.json`) diretamente no Drive sem adicionar um escopo de escrita.

## Opções Avaliadas

| Opção | Escopo OAuth necessário | Impacto no usuário | Prós | Contras |
|---|---|---|---|---|
| **1. Backup manual / local (`BackupSerializer` existente)** | `drive.readonly` (atual) | Zero reautenticação | Já implementado e testado (`BackupSerializerTest`); preserva garantia estrita de somente-leitura no Drive | Requer exportação/importação manual entre TV e celular |
| **2. Pasta oculta de aplicação (`drive.appdata`)** | `drive.readonly` + `drive.appdata` | Exige novo consentimento OAuth ao atualizar | Permite sincronizar `watch_list`, favoritos e preferências de faixa na pasta isolada `appDataFolder` sem permissão de escrita nas pastas de vídeo do usuário | Exige tela de reautenticação para usuários atuais |
| **3. Integração externa (AniList / MyAnimeList)** | Nenhum escopo Google adicional (usa token MAL/AniList) | Opcional por perfil | Sincroniza episódios concluídos com rastreadores de anime já usados pela comunidade | Sincroniza apenas episódios completos (não posição exata em milissegundos) |

## Recomendação

Manter a **Opção 1 (`drive.readonly` + `BackupSerializer`)** como padrão na série v1.x/v2.0 para não invalidar sessões OAuth existentes em Android TV (onde digitar credenciais no controle remoto é custoso), e oferecer a **Opção 2 (`drive.appdata`)** apenas mediante aprovação explícita na Porta D-50.
