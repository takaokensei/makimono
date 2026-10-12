# Decisão e Regressão para `androidx.security:security-crypto` (`T-903`)

## Contexto (`Q-012`)

O aplicativo utiliza `androidx.security:security-crypto:1.1.0-alpha06` em dois pontos isolados:
1. `EncryptedSessionStore` (`encrypted_drive_session`): armazenamento de tokens OAuth do Google Drive atrás da interface de domínio `SecretStore`.
2. `MalSessionManager` (`mal_encrypted_prefs`): armazenamento de tokens OAuth2 PKCE do MyAnimeList.

## Decisão Arquitetural

1. **Fixar `1.1.0-alpha06` no curto prazo com isolamento por interface (`SecretStore`):**
   - `1.1.0-alpha06` é o último artefato publicado da linha `1.1.x` compatível com `minSdk = 21` (a versão `1.0.0` estável exige `minSdk = 23`).
   - Migrar o formato de arquivo em disco sem necessidade invalidaria sessões já autenticadas em aparelhos existentes.
2. **Política Fail-Closed no Drive e Fallback Resiliente no MAL:**
   - `EncryptedSessionStore` opera em modo *fail-closed* (recusa gravar credenciais OAuth do Google Drive em texto plano caso o Keystore falhe) e expõe a interface `SecretStore` para facilitar substituição futura por Tink + DataStore caso `minSdk` suba para 23 (`D-10`).
   - O contrato de leitura, escrita, remoção e limpeza está coberto por `EncryptedSessionStoreTest` e `SessionManagerTest`.
