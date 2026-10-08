# Plano de Elevação Visual e Interativa Makimono (Apple HIG Grade)

**Data:** 06/10/2026
**Referências Diretas Aplicadas:**
- Apple HIG: `Top Shelf` (Carousel Details, Respiro Hierárquico, Safe Margins)
- Apple HIG: `Collections` (Padding adequado, Focus/Hover sem colisão, Aspect ratios)
- Apple HIG: `Managing accounts` & `Pickers` (Seleção fluida de perfil, Insets de TV, Focus Rings)
- Apple HIG: `Loading` & `Progress indicators` (Skeletons sem bloqueio visual, Shimmer transicional)
- Apple HIG: `Split views` & `Settings` (Navegação balanceada, Insets 10-foot)

---

## 1. Diagnóstico dos Pontos de Fricção (Evidências Moto G34)
1. **Hero Spotlight na Home (`fragment_home.xml` e `layout-land/fragment_home.xml`):**
   - *Problema:* O título Display encavala sobre o overline ou colide com metadados quando há quebra de linha. Falta `lineSpacingMultiplier` calibrado, `includeFontPadding="false"` e margens verticais harmonizadas de 8dp–12dp (padrão Top Shelf Carousel Details).
2. **Seletor de Perfis (`fragment_profile_selection.xml`, `item_profile_card.xml`, `ProfilesAdapter.kt`):**
   - *Problema:* O anel de foco é estático (sem a física de mola / escala 1.08x do `FocusEngine`). Ao focar, o avatar precisa de elevação elástica e destaque luminoso sem empurrar os vizinhos.
3. **Diálogo de Edição de Perfis (`dialog_profile_editor.xml`):**
   - *Problema:* Layout excessivamente denso para TV/Landscape (1600x720). Precisa de insets amplos, inputs em estilo glass coerente e botões de ação confortáveis para D-pad.
4. **Estados de Tela e Carregamento (T3.6 / W2):**
   - *Problema:* A Home usa placeholders estáticos com ícone de play desproporcional em vez do `StateView` padronizado e esqueletos shimmer.
5. **Ratchet Tipográfico (W1):**
   - *Problema:* Restam 247 ocorrências literais de `android:textSize` nas variantes de Home, Settings e HUD. Devem ser unificadas nos tokens `TextAppearance.Makimono.TV.*`.

---

## 2. Etapas de Execução do Plano

### Fase 1: Hero Top Shelf e Hierarquia Tipográfica (Home)
- Calibrar o espaçamento vertical do bloco de conteúdo do Hero (`layout-land/fragment_home.xml` e `fragment_home.xml`):
  - Overline: `TextAppearance.Makimono.TV.Callout`, margin bottom 8dp.
  - Título Display: `TextAppearance.Makimono.TV.Display`, `lineSpacingMultiplier="1.05"`, margin bottom 8dp.
  - Metadados: `TextAppearance.Makimono.TV.Callout`, margin bottom 12dp.
  - CTAs primários ("Assistir agora" / "Detalhes"): Insets de 52dp com elevação e contraste HIG.

### Fase 2: Física Elástica no Seletor de Perfis (Managing Accounts)
- Integrar `FocusEngine.attach(binding.cardProfileRoot, FocusEngine.FocusKind.CARD)` em `ProfilesAdapter.kt`.
- Ajustar `item_profile_card.xml` com clipChildren="false" para permitir que o avatar cresça 1.08x sem corte e sem corte nos textos dos perfis.
- Ajustar proporções no `values-land/dimens.xml` para garantir que avatares fiquem bem dimensionados e nítidos em 720p/1080p landscape.

### Fase 3: Diálogo de Criação/Edição de Perfil em Glass TV
- Refatorar `dialog_profile_editor.xml` para garantir scrolling fluído com `maxHeight="380dp"`, campos de entrada com altura confortável (48dp), padding lateral e botões de ação alinhados com espaçamento D-pad.

### Fase 4: Integração de StateView e Shimmer na Home
- Substituir o bloco manual de erro/vazio `layoutHomeEmpty` pela inclusão do `component_state_view.xml`.
- Conectar o feedback visual ao estado reativo do catálogo.

### Fase 5: Ratchet de Tipografia dos Layouts Restantes
- Migrar `layout-land/fragment_home.xml`, `layout-television/fragment_home.xml`, `layout-sw720dp-land/fragment_home.xml` e `layout-port/fragment_home.xml` para os tokens tipográficos.
- Ratchet down do contador para menos de 180 literais.

### Fase 6: Validação e Evidências no Aparelho Físico
- Compilar (`assembleDebug`), rodar todos os testes unitários (269 testes).
- Instalar no Moto G34 e capturar screenshots comparativos das telas corrigidas.
