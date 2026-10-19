# ADR-004 — pareamento QR Google Drive com Cloudflare Worker

- **Estado:** Implementado localmente; deploy e validação com OAuth real pendentes
- **Data:** 2026-10-19
- **Atualiza:** detalhes de implementação do fluxo de autorização descrito no ADR-001

## Contexto

O cliente Android não deve carregar refresh token Google nem client secret de servidor. A TV inicia um pareamento temporário; o usuário confirma o aparelho no celular e conclui OAuth Google no navegador. Um Cloudflare Worker com D1 mantém o grant Google e concede uma sessão Makimono independente para cada TV. A TV recebe tokens Google de acesso de curta duração apenas para chamadas diretas à API Drive; a mídia não passa pelo Worker.

## Decisões

1. **Sessões por aparelho.** `POST /v1/logout` revoga somente a sessão apresentada no bearer token e preserva o grant Google e as outras TVs. A ação separada `POST /v1/disconnect-all` tenta revogar o grant no Google e, após sucesso, apaga o grant local e revoga todas as sessões daquele `google_sub`. Falha ou resposta não-2xx do Google retorna erro e mantém o estado local para nova tentativa.
2. **Pareamento preso ao navegador que confirmou.** A página de código usa cookie CSRF `Secure; HttpOnly; SameSite=Lax`, prova HMAC do formulário e comparação estrita do `Origin`. Depois de um código correto, o Worker gera outro segredo aleatório, persiste apenas seu SHA-256 no desafio e entrega o segredo em cookie `Secure; HttpOnly; SameSite=Lax`. `/oauth/start` e `/oauth/callback` exigem esse mesmo cookie. O callback valida o vínculo antes de consumir o estado OAuth. Formulários aceitam apenas URL-encoded e têm limite de 4 KiB.
3. **Entrega de sessão uma única vez.** A reivindicação aprovada cria a sessão e consome o desafio de forma atômica. O token Makimono só aparece na primeira resposta de polling. Se a TV perder essa resposta, a próxima consulta vê `consumed`; não há recuperação que reenvie o bearer token. O Android apresenta “Não foi possível concluir. Gere um novo QR.” e permite reiniciar o fluxo.
4. **Logout offline.** Se a TV não consegue alcançar o Worker, `TokenProvider` guarda a ação e o token de sessão na persistência cifrada. A ação pendente é reenviada em uma conexão futura; erro de rede não é tratado como revogação confirmada.
5. **Armazenamento local do pareamento.** A sessão Makimono e ações pendentes ficam num payload AES-GCM autenticado, com chave protegida pelo Android Keystore. Em API 23+, a chave AES é gerada no Keystore; em API 21–22, uma chave AES aleatória é cifrada por uma chave RSA do Keystore. A leitura falha fechada se a chave/payload não puder ser validada. Na primeira migração, as credenciais Drive do armazenamento legado são removidas de SharedPreferences cifradas e DataStore.

## AndroidX Security Crypto e escopo de migração

`EncryptedSharedPreferences` e `MasterKey` estão marcados deprecated pela AndroidX Security Crypto 1.1.0. O novo armazenamento do pareamento não depende deles: usa Android Keystore e AES-GCM, mantendo a interface `SharedPreferences` apenas como adaptador interno.

`MalSessionManager` ainda usa `EncryptedSharedPreferences` para preservar tokens e preferências MyAnimeList instalados. Migrá-lo junto com a sessão Drive mudaria outro subsistema e poderia invalidar dados existentes; isso fica como tarefa separada, com migração compatível e testes de upgrade. A dependência `security-crypto` permanece necessária enquanto MAL não for migrado.

Referências oficiais: [AndroidX Security Crypto](https://developer.android.com/jetpack/androidx/releases/security) e [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

## Consequências e limitações

- O Worker não está publicado. `wrangler.jsonc` ainda contém ID D1 placeholder; não existe evidência de que OAuth, callback, consentimento e grant Google funcionem no ambiente real.
- O token de sessão de uso único reduz a janela de reentrega, mas uma resposta de rede perdida exige novo QR. A UI cobre esse caminho; a falha está documentada como limitação deliberada.
- A ação “Desconectar todos os dispositivos” depende de revogação Google. Se ela falhar, o Worker preserva estado e responde com erro; o cliente mantém uma tentativa pendente cifrada para conexão futura.
- As proteções do Android Keystore não substituem uma revisão criptográfica. A implementação precisa ser exercitada em API 21, API 23+ e TV real; hardware-backed key storage varia por dispositivo. Não há fallback plaintext.
- Testes locais e mocks comprovam somente o comportamento simulado. Antes de release, configurar domínio/API HTTPS, D1, segredos no Worker, redirect URI Google e executar pareamento real em aparelho.

## Verificação associada

O conjunto Worker verifica separadamente logout de uma TV, desconexão de todas as TVs, preservação de estado quando a revogação Google falha, vínculo de cookie/CSRF, consumo único da sessão e resposta de erro recuperável no Android. A verificação do app Android inclui o caso de URL QR sem host HTTPS válido.
