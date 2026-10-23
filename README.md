<p align="center">
  <img src="docs/logo.png" align="center" width="160" alt="Makimono (巻物) — alternativa à Netflix para o seu Google Drive no Android TV" />
</p>
<h1 align="center">
  Makimono (巻物)
</h1>
<p align="center">
  <b>Sua própria "Netflix" a partir do Google Drive — player de streaming para Android TV e Android</b><br>
  Alternativa open source a Netflix, Plex e Jellyfin para quem guarda filmes, séries e anime no Google Drive: sem servidor, sem mensalidade, com legendas fansub ASS/SSA, controle por D-pad pensado para TV, perfis isolados e motor nativo MPV.
</p>

<p align="center">
  Desenvolvido com <a href="https://developers.google.com/drive/api">Google Drive API</a>, <a href="https://developer.android.com/media/media3">AndroidX Media3</a> e <a href="docs/architecture/native-mpv.md">libmpv</a>.
</p>

<div align="center">
  <a href="https://github.com/takaokensei/makimono/releases">
    <img alt="GitHub release" src="https://img.shields.io/github/v/release/takaokensei/makimono?style=for-the-badge">
  </a>
  <a href="https://github.com/takaokensei/makimono/blob/main/LICENSE">
    <img alt="License" src="https://img.shields.io/github/license/takaokensei/makimono?style=for-the-badge">
  </a>
  <img alt="Platform" src="https://img.shields.io/badge/Android%20%7C%20Android%20TV-3DDC84?style=for-the-badge&logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white">
</div>

<p align="center">
  <sub>
    <b>Palavras-chave:</b> Netflix alternative · Google Drive media player · Google Drive streaming · Android TV media player · Plex alternative · Jellyfin alternative · self-hosted streaming · anime player · ASS/SSA subtitles · MPV Android TV · open source media player · player de vídeo Google Drive · alternativa à Netflix
  </sub>
</p>

<br>

---

## 🎬 O que é o Makimono?

**Makimono** (巻物, "pergaminho" em japonês) é um **cliente de streaming para Android TV e Android que transforma o seu Google Drive numa biblioteca estilo Netflix**: capas, episódios, "continuar assistindo", perfis e reprodução direto da nuvem.

Se você já organiza filmes, séries e anime no Drive e procura uma **alternativa à Netflix, ao Plex ou ao Jellyfin** sem precisar manter servidor em casa, o Makimono foi feito para isso. Seus arquivos continuam na sua conta Google; o app só cuida da experiência de assistir.

### ✨ Principais recursos

- **Streaming direto do Google Drive** — sem servidor próprio, sem transcodificação em nuvem, sem assinatura.
- **Feito para a TV** — navegação 100% por controle remoto (D-pad), foco previsível e interface pensada para leitura a 3 metros ("10-foot experience").
- **Legendas fansub ASS/SSA com estilos preservados** — fontes, cores, posicionamento e efeitos renderizados pelo motor nativo **libmpv**.
- **Dois motores de reprodução** sob o mesmo contrato (`PlayerEngine`): **AndroidX Media3 (ExoPlayer)** e **MPV nativo**, para cobrir a maior variedade de vídeos e codecs.
- **Perfis com isolamento de dados** — cada pessoa da casa tem seu histórico e progresso.
- **Integração com o sistema Android TV** — `MediaSession` (controles de mídia do sistema) e **Watch Next** (continuar assistindo na tela inicial da TV).
- **Local-first** — metadados em cache local (Room) para abrir rápido e navegar sem depender da rede a cada tela.
- **Interface bilíngue** — paridade completa de strings entre inglês e português.
- **Design System "Obsidian"** — tema escuro OLED, glassmorphism e acento Electric Cyan, com contraste validado (WCAG).
- **Open source (Apache 2.0)** e com suíte de testes e verificações automatizadas.

### 🤔 Para quem é?

| Se você... | O Makimono ajuda porque... |
| --- | --- |
| Guarda filmes, séries ou anime no Google Drive | Transforma as pastas do Drive em uma biblioteca navegável com capas e episódios |
| Quer uma alternativa à Netflix sem pagar assinatura nem hospedar servidor | Toca direto do seu Drive, sem infraestrutura extra |
| Assiste anime com legendas fansub | Renderiza ASS/SSA com os estilos originais via MPV |
| Usa Android TV, TV Box ou Google TV | Foi desenhado primeiro para D-pad e telas grandes, com versão mobile também |
| Divide a TV com a família | Perfis isolados mantêm histórico e progresso separados |

### ❓ Perguntas frequentes

**O Makimono é uma alternativa à Netflix?**
Ele não oferece catálogo próprio: é o player e a biblioteca para o **seu** conteúdo no Google Drive, com experiência semelhante à de um serviço de streaming.

**Preciso de servidor, como no Plex ou no Jellyfin?**
Não. O app conversa diretamente com a Google Drive API; não há servidor para instalar ou manter.

**Funciona em Android TV, Google TV e TV Box?**
Sim, em Android TV 11+ (1080p / 4K UHD) e também em celulares e tablets com Android 5.0+.

**Suporta legendas ASS/SSA de fansubs?**
Sim. Os estilos são preservados pelo motor nativo libmpv.

**Posso usar com várias pessoas na mesma TV?**
Sim, com perfis isolados.

---

## 🌍 English summary

**Makimono** is an open-source **Google Drive media player and Netflix-style streaming client for Android TV and Android**. It is a serverless **alternative to Plex and Jellyfin** for people who keep movies, TV shows and anime on Google Drive: native **MPV** playback, full **ASS/SSA fansub subtitle styling**, D-pad-first TV navigation, isolated user profiles, Android TV `MediaSession` and Watch Next integration, and a local-first metadata cache. No server to host, no subscription — your files stay in your own Google account.

---

## 📋 Requisitos de Ambiente

- **JDK:** OpenJDK 17 ou superior (`JAVA_HOME` configurado)
- **Android SDK:** `compileSdk 34`, `targetSdk 33`, `minSdk 21`
- **Build Tools:** Android SDK Build-Tools 34.0.0
- **Dispositivo:** Android 5.0+ (Lollipop, API 21+) para celular/tablet ou Android TV 11+ (1080p / 4K UHD)

---

## 🛠️ Como Compilar do Zero

1. **Clonar o repositório:**
   ```bash
   git clone https://github.com/takaokensei/makimono.git
   cd makimono
   ```

2. **Configurar propriedades locais:**
   Copie o modelo de propriedades para criar o seu `local.properties`:
   ```bash
   cp local.properties.example local.properties
   ```
   Defina o caminho do Android SDK (`sdk.dir`) e as credenciais necessárias conforme descrito no arquivo de exemplo.

   > [!NOTE]
   > **Ambiente Windows:** Para evitar falhas de carregamento de classes de teste no Gradle decorrentes de caminhos com caracteres não-ASCII (ex: `C:\Users\Nome Com Acento\...`), execute o build e testes a partir de um caminho estritamente ASCII ou monte uma unidade virtual temporária (ex: `subst T: .` e acesse a partir de `T:\`).

3. **Compilar o APK de desenvolvimento:**
   ```bash
   ./gradlew assembleDebug
   ```
   O binário gerado estará em `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🧪 Como Rodar os Testes e o Lint

Execute as tarefas de validação e verificação de integridade:

```bash
# Executar todos os testes unitários
./gradlew testDebugUnitTest

# Executar verificação de qualidade de código (Lint com baseline ativa)
./gradlew lintDebug

# Executar verificação completa em um único comando
./gradlew testDebugUnitTest lintDebug assembleDebug
```

### Métricas de Integridade do Código (v1.9.0)

- **Testes Unitários:** 683 anotações `@Test` no código-fonte e **691 execuções** registradas no relatório JUnit XML (0 falhas, 0 erros). A contagem estática e a execução são medidas distintas; testes herdados são reconciliados por `python tools/checks/count_tests.py`.
- **Android Lint (Baseline):** 681 issues catalogadas em `app/lint-baseline.xml` com **0 erros bloqueantes** e **0 erros filtrados pela baseline**, além de paridade completa de internacionalização (`MissingTranslation`: 0, `MissingQuantity`: 0, `Untranslatable`: 0, `ContentDescription`: 0). Principais categorias: `HardcodedText` (286), `UnusedResources` (158), `SetTextI18n` (102), `InconsistentLayout` (18), `UnusedAttribute` (15). Verificação: `python tools/checks/count_lint_categories.py`.
- **Ferramentas de Verificação Contínua (`tools/checks/`):**
  - `check_strings.py`: Validação de paridade estrita de strings (551 tags `<string>` no total em `values/strings.xml`, sendo 548 traduzíveis validadas contra `values-pt/strings.xml` e 3 não-traduzíveis técnicas marcadas com `translatable="false"`).
  - `check_kotlin_literals.py`: Guarda contra literais de UI hardcoded (`Toast.makeText`, `setTitle`, `setMessage`, `setText`) em arquivos Kotlin.
  - `check_kotlin_style.py`: Verificação estática de estilo e higiene Kotlin com baseline versionada (`config/ktlint-baseline.json`).
  - `check_immutable_tag.py`: Guarda de imutabilidade de tags de release com autoteste (`--self-test`).
  - `check_contrast.py`: Validação de conformidade WCAG AAA/AA para todas as paletas de cores e gradientes sobre imagem.
  - `check_layout_colors.py` & `check_typography.py`: Auditoria de uso de tokens semânticos e tipografia.
  - `check_no_direct_log.py`: Guarda contra chamadas diretas a `android.util.Log` fora de `AppLog.kt`.
  - `check_readme_claims.py`: Validação contínua de paridade entre as afirmações do README e os valores reais do código.
  - `count_tests.py` & `count_lint_categories.py`: Auditoria automatizada de testes e baseline do Lint.

-----

## 🏛️ Arquitetura

O projeto adota uma arquitetura em camadas focada em separação de preocupações e local-first:

- **`domain`:** Modelos, regras de negócio puras (sem dependências de framework Android).
- **`data`:** Repositórios, cliente Google Drive, Room local e cache de metadados.
- **`playback`:** Motores de reprodução desacoplados sob contrato único (`PlayerEngine`), gerenciando Media3 e MPV nativo.
- **`platform`:** Integrações de plataforma Android TV (`MediaSession`, Watch Next).
- **`ui`:** Apresentação para Android TV (D-pad 10-foot experience, rail de 80dp) e mobile.

Para detalhes completos e decisões de design:
- [Documento Canônico de Arquitetura](docs/architecture/ARCHITECTURE.md)
- [Decisões Arquiteturais (ADRs)](docs/architecture/adr/)
- [Guia do MPV Nativo](docs/architecture/native-mpv.md)

---

## 📚 Documentação do Projeto

- **Design System & TV UX:** [Design System Obsidian](docs/design/DESIGN.md) e [Referências HIG](docs/design/hig-references.md)
- **Garantia de Qualidade:** [Matriz de Dispositivos](docs/qa/device-matrix.md), [Checklist de Foco TV](docs/qa/focus-checklist.md) e [Baseline Diagnóstica](docs/qa/baseline-0.md)
- **Histórico e Releases:** [Diretrizes de Release](docs/releases/README.md) e [Changelog Completo](CHANGELOG.md)

---

## 📜 Licença

Distribuído sob a licença Apache 2.0. Consulte [LICENSE](LICENSE) para mais informações.
