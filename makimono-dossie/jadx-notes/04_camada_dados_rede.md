# Subsistema 04 — Camada de Dados, Perfis e Rede

## 1. Identificadores Observados e Origem

- **Classes Principais:**
  - `com.crunchyroll.crunchyroid.di.RepositoryModule` (Injeção de Dependências Hilt/Dagger)
  - `com.crunchyroll.crunchyroid.di.NetworkModule` (Configuração HTTP / OkHttp / Retrofit)
  - `com.crunchyroll.crunchyroid.lupin.LupinPreferenceManager` (Subsistema Lupin)
  - `com.crunchyroll.crunchyroid.lupin.LupinListState` (Gestão Reativa de Estado)
  - `com.crunchyroll.crunchyroid.lupin.LupinProfileSync` (Sincronização de Perfis)

---

## 2. Enquadramento e Análise Estrutural

### 2.1 Padrão Repository e Gestão de Múltiplos Perfis ("Lupin")
- **Tag:** `[IDEIA]`
- **Observação Factual:** A aplicação utiliza um subsistema interno de perfis e preferências (rotulado no código como família de classes `Lupin`). Cada perfil de usuário opera com um escopo isolado de preferências, histórico de reprodução e estado de watchlist (`LupinListState`), propagado reativamente via `StateFlow` e gerenciado por um orquestrador central de injeção de dependências.
- **Grau de Confiança:** Forte evidência estática (presença de 768 referências compiladas ao namespace e módulos Hilt correspondentes).
- **Lacunas Conhecidas:** Mecanismo de persistência local exato (Room SQLite vs DataStore vs EncryptedSharedPreferences).

### 2.2 Estratégia de Cache e Atualização em Background
- **Tag:** `[IDEIA]`
- **Observação Factual:** A camada de repositório emprega uma política *Cache-Then-Network* com política de invalidação temporal (TTL). A navegação em TV exige carregamento instantâneo para evitar congelamentos de interface (*jank*), exibindo primeiro o snapshot local em cache enquanto uma requisição assíncrona reconcilia atualizações de progresso e catálogo.
- **Grau de Confiança:** Forte evidência estática a partir dos interceptores de rede e repositórios observados.
- **Lacunas Conhecidas:** Duração exata do TTL para itens da watchlist.

### 2.3 Endpoints Literais, Contratos JSON e Cabeçalhos Privados
- **Tag:** `[EXPRESSÃO]`
- **Observação Factual:** URLs de APIs privadas, formatos de payload de autenticação OAuth proprietários e tokens de cabeçalho.
- **Grau de Confiança:** Forte evidência estática em strings de configuração de rede.
- **Diretriz Clean-Room:** Nenhuma URL, endpoint de serviço, chave de API ou contrato de rede proprietário da Crunchyroll é aplicável ou permitido no Makimono. O Makimono se comunica exclusivamente com as APIs públicas do Google Drive e serviços abertos de metadados de anime (Kitsu / AniList / TMDB).
