# Subsistema 06 — Recursos Visuais, Temas e Design System

## 1. Identificadores Observados e Origem

- **Classes e Recursos:**
  - `res/values/colors.xml` e tokens de cor Compose (Design System da Aplicação)
  - `res/values/dimens.xml` (Métricas de Espaçamento e Margem de TV)
  - `com.crunchyroll.crunchyroid.ui.theme.ThemeKt` (Mapeamento de Tema Compose)
  - `res/layout/` (Diretório de Layouts XML)

---

## 2. Enquadramento e Análise Estrutural

### 2.1 Predominância de Jetpack Compose vs Layouts XML Legados
- **Tag:** `[IDEIA]`
- **Observação Factual:** A inspeção do APK demonstra uma ausência quase total de arquivos de layout XML (`res/layout/`) para as telas do catálogo, navegação ou player. O diretório `res/layout` limita-se a layouts de suporte (splash screen legada, contêineres de fragmentos e views de terceiros). Toda a experiência visual moderna é gerada declarativamente via código Kotlin com Jetpack Compose / Compose for TV.
- **Grau de Confiança:** Forte evidência estática (inventário direto de recursos compilados extraídos via AAPT e JADX).
- **Lacunas Conhecidas:** Proporção exata de componentes que ainda usam AndroidView interoperável para renderização de legendas complexas.

### 2.2 Princípios de Design Visual para Telas de TV (10-foot UI)
- **Tag:** `[IDEIA]`
- **Observação Factual:** O tema adota os princípios canônicos de interfaces de TV de 10 pés (*10-foot UI*):
  1. **Fundo com preto absoluto / cinza profundo:** Reduz a fadiga visual e maximiza o contraste em painéis OLED e LED de salas escuras.
  2. **Área Segura (*Overscan Safe Zones*):** Margens generosas (entre 48dp e 58dp) nas bordas externas para garantir visibilidade em televisores antigos com recorte de borda.
  3. **Tipografia de Alto Contraste:** Hierarquia clara com títulos pesados (*Bold/SemiBold*) e tamanhos mínimos legíveis a 3 metros de distância (mínimo de 14sp para textos secundários e 20sp+ para títulos).
- **Grau de Confiança:** Forte evidência estática a partir dos tokens de dimensão e tema.
- **Lacunas Conhecidas:** Comportamento dinâmico em telas ultra-wide ou projetores.

### 2.3 Ativos Proprietários, Paleta de Marca e Imagens
- **Tag:** `[EXPRESSÃO]`
- **Observação Factual:** Valores hexadecimais de marca proprietária (laranja característico `#FF640A`), ícones vetoriais proprietários, logotipos e ilustrações de erro/placeholder.
- **Grau de Confiança:** Forte evidência estática.
- **Diretriz Clean-Room:** Absolutamente proibida a cópia, uso ou inclusão de cores de identidade visual restrita da Crunchyroll, logotipos, fontes com direitos autorais ou ilustrações SVG no Makimono. O Makimono deve utilizar sua identidade visual própria, seus próprios ícones abertos (Material Symbols / Lucide) e paleta alinhada ao design do Makimono.
