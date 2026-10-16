# Especificação — Componentes Visuais e Tokens TV

**Data:** 15/10/2026

## 1. Paleta e Superfícies (`colors.xml`)
- **Base Obsidian:** `#070A10` (`obsidian_bg_base`) e `#0E131F` (`obsidian_surface`).
- **Acento Primário:** Laranja energético `#F47521` / `#FF6B00` para foco, indicadores ativos e CTAs primários.
- **Scrims Cinematográficos:** Gradiente lateral (`bg_scrim_left`) e inferior (`bg_scrim_bottom`) sobre o backdrop 16:9 do Hero para garantir contraste mínimo WCAG AA sobre qualquer arte.

## 2. Cards de Prateleira e Catálogo
- **Continue Watching / Minha Fila (`ItemContinueWatchingShelfBinding`):** Proporção 16:9 panorâmica com barra de progresso inferior, badge de episódio e tempo restante.
- **Catálogo / Minha Lista / Recentes (`ItemAnimeCardBinding` / `StarredShelfAdapter`):** Proporção 2:3 de pôster vertical com escala suave em foco (`1.06x`), anel de foco nítido e título/subtítulo legíveis a 3 metros (10-foot UI).
