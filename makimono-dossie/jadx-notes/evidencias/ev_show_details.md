# Evidência Decompilada: ShowDetailsActivity

**Evidence ID:** `ev_07b5781d17b0cf694ed5db3060363212824cd3bcb7fc0e5261e3ba25216468b0`  
**Classe:** `com.crunchyroll.crunchyroid.showdetails.ui.ShowDetailsActivity`  
**Fonte:** JADX Headless MCP Engine v0.7.1 via REA v6.4.0  
**Enquadramento:** `[IDEIA]` Navegação Hierárquica de Séries e Temporadas.

---

## Assinatura e Estrutura Observada

```text
Class: com.crunchyroll.crunchyroid.showdetails.ui.ShowDetailsActivity
Superclass: com.crunchyroll.crunchyroid.showdetails.ui.Hilt_ShowDetailsActivity -> FragmentActivity
```

---

## Mapeamento de Nós de Acessibilidade em Runtime

A inspeção em execução no emulador (`details.xml`) revela a topologia hierárquica completa da tela de detalhes:

1. **Header Principal da Série:**
   - `showDetailsHeroBackgroundImage`: Pôster/Banner de fundo amplo (`[0,0][1920,904]`).
   - `showDetailsHeroTitle`: Título canônico da série com tags de áudio e ano.
   - `showDetailsHeroMeta`: Badges etárias e informações de lançamento ("Sub | Dub", "fall-2017").
   - `showDetailsHeroDescription`: Sinopse detalhada com limite visual de leitura.
   - `showDetailsHeroWatchlistButton`: Botão primário com foco inicial ("Add to Watchlist" / "Play").
2. **Abas de Conteúdo:**
   - `showDetailsTabButtonVideo`: Aba de Episódios (selecionada por padrão).
   - `showDetailsTabButtonDetails`: Aba de Metadados e Ficha Técnica.
   - `showDetailsTabButtonMoreLikeThis`: Aba de Títulos Recomendados.
3. **Navegação de Episódios:**
   - `showDetailsVideoSeasonContainer`: Seletor de Temporadas (lado esquerdo).
   - `showDetailsVideoEpisodeContainer`: Carrossel horizontal de episódios da temporada ativa (lado direito).

---

## Conclusão para o Makimono

Essa estrutura de abas e separação entre temporadas e episódios mapeia-se de forma direta para a navegação de pastas de animes no Google Drive (onde cada pasta interna corresponde a uma temporada e os arquivos de vídeo representam os episódios).
