# 🎨 Mockups de Referência — Makimono

Este diretório contém os mockups canônicos de alta fidelidade utilizados como referência visual e espacial para a interface do Makimono no Android TV (10-foot experience).

---

## 🖼️ Mockup: Tela de Detalhes da Série (Alvo v4)

- **Arquivo:** [`series-detail-v4.png`](series-detail-v4.png)
- **Tela / Destino:** Detalhes da Franquia / Série (`SeriesDetailFragment` / `VisualReviewActivity`)
- **Resolução de Referência:** 1920 × 1080 pixels (Full HD Android TV)
- **Densidade Alvo:** 320 dpi (xhdpi)
- **Canvas Base:** 960 × 540 dp
- **Fator de Conversão de Medidas:** `1 px do mockup = 0.5 dp` no código Android (ex.: 96px no mockup = 48dp de margem no layout XML).

---

## ⚖️ Matriz de Resolução de Conflitos (Mockup vs. DESIGN.md)

O mockup é a **referência de hierarquia visual, proporção espacial e densidade de informações**. No entanto, quando houver qualquer divergência em relação a tokens semânticos e diretrizes técnicas do projeto, **o [`DESIGN.md`](../DESIGN.md) sempre prevalece (DESIGN.md vence)**:

| Elemento | No Mockup Visual | No Makimono (DESIGN.md) | Resolução / Regra Aplicada |
|---|---|---|---|
| **Acento Ativo / Foco** | Laranja (`#FF7A00` / `#FF9800`) em chips ativos e barras | Ciano Torii (`@color/obsidian_accent_primary` `#22D3EE`) | **DESIGN.md vence:** Substituir laranja por ciano primário em anéis de foco, seleções e progresso em andamento. |
| **Status Concluído** | Ícone de check sutil | Verde esmeralda (`@color/obsidian_success` `#54D18A`) | **DESIGN.md vence:** Manter verde apenas para status de reprodução 100% concluída. |
| **Tipografia** | Fonte Inter (vários pesos) | Fontes declaradas do app (`Outfit` / `Space Grotesk`) | **DESIGN.md vence:** Usar fontes do sistema/app, proibido introduzir Inter ou pesos inexistentes. Piso de 12sp. |
| **Backdrop e Scrim** | Scrim direcional suave | Scrim direcional vetorial (`obsidian_media_scrim`) | **Alinhados:** Backdrop 1080p sem overlay uniforme escuro. Gradiente horizontal (esquerda) e vertical (base). Sem blur real. |
| **Margens Seguras** | Margens laterais e verticais | 48dp laterais, 27dp verticais (Overscan TV) | **Alinhados:** Elementos focáveis e texto respeitam safe insets estritamente. |

---

## 📌 Como Inspecionar e Validar
Para validar a paridade visual entre a implementação XML e o mockup:
1. Compile o APK debug: `./gradlew assembleDebug`
2. Abra a tela de visualização fixture: `zechs.drive.stream.visualreview.VisualReviewActivity`
3. Compare o layout renderizado a 1920×1080 @320dpi contra `series-detail-v4.png`.
