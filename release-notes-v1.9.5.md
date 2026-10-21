## Makimono v1.9.5 — Mobile Direct Sign-In (Netflix/Crunchyroll Standard) & Compact Season Picker

Esta release entrega o novo fluxo responsivo de login direto pelo celular (resolvendo a limitação de exigir leitura de QR code no mesmo aparelho) e o novo seletor de temporadas em modal compacto para séries extensas.

### 📱 Login pelo Celular (Padrão Crunchyroll & Netflix)
- **Detecção automática de plataforma:** A interface adapta-se dinamicamente entre Android TV (10-foot) e Celular/Tablet.
- **Autorização direta no navegador:** Usuários de smartphone contam agora com o botão de ação primária **"Entrar com Google no Navegador"**.
- **Ações de 1 toque para Clipboard:** Botões rápidos para copiar o código de confirmação formatado (`123 456`) e copiar o link direto de ativação.
- **Alternância para segundo dispositivo:** Botão expansível **"Escanear com outro aparelho"** permite exibir o QR Code sob demanda caso o usuário prefira autorizar a partir de um tablet ou outro smartphone.
- **Preenchimento automático no Cloudflare Worker:** O serviço de pareamento aceita os parâmetros `?code=` e `?c=`, preenchendo o código no formulário HTML automaticamente. O usuário só precisa tocar em "Continuar com Google".
- **Layout Responsivo:** Implementado `NestedScrollView` e restrições de tela que garantem rolagem suave e sem cortes em smartphones em orientação retrato e paisagem.

### 🎬 Seletor de Temporadas em Modal Dropdown Compacto (REF-05)
- **Modo Compacto Inteligente:** Para animes e séries com mais de 4 partes ou temporadas (ex: *Monogatari*, *Fate*, *Attack on Titan*, *Jojo*), a navegação substitui a longa fileira de abas horizontais pelo botão compacto de seleção `btnSeasonPicker` no topo da lista de episódios.
- **Diálogo Glass Obsidian:** Modal `MaterialAlertDialogBuilder` com foco automático na temporada atualmente ativa e rolagem vertical suave.
- **Respeito ao Overscan e Navegação TV:** Margens seguras de 48dp lateral e 27dp vertical preservadas, com restauração precisa do cursor D-pad ao confirmar ou cancelar.

### 🧪 Métricas de Qualidade e Integridade
- **Testes Unitários:** 683 anotações `@Test` no código-fonte e 691 execuções JUnit (0 falhas, 0 erros).
- **Cloudflare Worker:** 16 testes Vitest aprovados (100% verde).
- **Android Lint:** 0 novas violações em relação à baseline.
- **Design System & I18n:** 548 chaves de strings com paridade estrita entre inglês e português, checagens de contraste Obsidian aprovadas e zero chamadas de log brutas.
