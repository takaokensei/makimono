# Makimono v1.6.4.5 — Experiência Cinemática e Apple-Grade TV

Esta versão eleva a experiência do Makimono no Android TV a um padrão de excelência visual e fluidez cinemática, eliminando ruídos técnicos de navegação e aperfeiçoando o modo de filmes e séries.

## 🎬 Destaques da Versão

### 1. Experiência de Catálogo e Shelves Apple-Grade
- **Eliminação de Badges Técnicos:** Remoção definitiva de pílulas redundantes (`PASTA`) em cards de mídia do catálogo e estantes da tela inicial (*Minha Lista*, *Adicionados recentemente*, *Em alta*).
- **Apresentação Limpa:** Posters e backdrops agora dominam os cartões com arte cinemática em proporção 2:3, sem sobreposições poluentes.
- **Hero Cinemático:** Painel em destaque refinado com navegação nativa D-Pad, metadados TMDb e badges claros de resolução (4K, HEVC).

### 2. Modo de Filme Aprimorado
- **Detalhes Simplificados para Filmes:** Em mídias únicas ou classificadas como filme, o cabeçalho "Episódios" e abas redundantes de temporada única são ocultados automaticamente.
- **Card de Filme Dedicado:** Apresentação elegante com título canônico completo, badges de áudio/qualidade e sinopse completa.
- **Cartões de Episódio Polidos:** Correção do separador de ponto (`•`) órfão quando metadados de áudio ou duração não estão disponíveis.

### 3. Player OSD & Reprodução Cinemática (ExoPlayer & MPV)
- **Identificação Correta de Filme:** Título no cabeçalho exibe `"Filme Completo"` em vez do falso `"Episódio 01"`.
- **Drawer de Episódios Oculto:** O botão de gaveta de episódios é escondido automaticamente durante a reprodução de filmes.
- **Supressão de Chamadas Spurious:** Bloqueio de buscas automáticas desnecessárias de abertura/encerramento (MAL/AniSkip) em filmes que não são animes episódicos.
- **Modais Glass Refinados:** Seletor de faixas de áudio e legendas com estilo frosted glass e navegação por controle remoto otimizada.

### 4. Infraestrutura e Atualizações
- **Banco de Dados Room v9:** Suporte a cache persistente de metadados de pastas e resolução TMDb/AniList.
- **Workflow de Release Robusto:** Suporte aprimorado no GitHub Actions com detecção resiliente de secrets e pacotes ABI.
