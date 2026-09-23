Atualização 23/09/2026: ver [revisão e validação da v1.5.8](review-2026-09-23/README.md).

> Atualização de 22/09/2026: veja [a revisão sobre a v1.5.7](review-2026-09-22/README.md), com novas capturas e 145 testes JVM passando. Os resultados abaixo registram a rodada anterior.

# Makimono — auditoria e modernização Android TV/Mobile

Data: 20/09/2026. Referências: os cinco mockups fornecidos pelo usuário. A avaliação inicial resulta da inspeção do código anterior às alterações, não de uma pesquisa com usuários. Os mockups orientam a estética; dados, disponibilidade e funcionalidades vêm do app.

## Avaliação inicial: 5,5/10

- **Home e hierarquia — 6,5/10.** A versão TV já tinha rail, hero e prateleiras. O rail de 220–240dp ocupava espaço excessivo e as superfícies competiam com a arte. Existem layouts distintos para TV, landscape, tablet e portrait, que precisam continuar compatíveis.
- **Perfis — 3/10.** `ProfileManager` gravava JSON em SharedPreferences. A edição oferecia renomear/excluir; faltavam Room, imagens por URL, wallpapers e Admin/Kids. O ID já isolava histórico/favoritos/fila e deve ser preservado.
- **OSD — 5/10.** `player_control_view.xml` distribuía transporte e oito ações laterais na mesma faixa. Labels de 11sp e controles com ripple sem anel consistente dificultavam o uso a distância. Os dois motores têm handlers próprios que não podem ser substituídos indiscriminadamente.
- **D-Pad — 5,5/10.** Muitos itens já eram focáveis e tinham animação, mas alguns controles dependiam de ripple, os diálogos não restauravam foco explicitamente e `shelf_item_focus_border.xml` usava filhos `shape` diretamente em `layer-list`, sem `item`. No MPV, o clique de pular abertura estava no texto enquanto o contêiner recebia o foco.
- **Pastas/arquivos — 6/10.** Grade/lista, busca e metadados estavam disponíveis. Um vídeo com pôster recebido do resolvedor virava card 2:3, quebrando a leitura como episódio.

A nota geral é um julgamento de engenharia/design; não é uma média de métricas instrumentadas nem uma promessa de fidelidade pixel a pixel.

## P0 — persistência, paridade e navegação

Implementado:

- Room v7 com `ProfileEntity(id, nome, avatarUrl, backgroundUrl, isAdmin, isKids)` e campos legados para preservar avatar local, pasta e data de criação.
- Migração SQL 6→7 registrada, sem migração destrutiva. Importação do JSON somente quando a tabela de perfis está vazia. IDs legados e perfil ativo são preservados.
- Escritas assíncronas serializadas; estado publicado após o commit. Exclusão do perfil e de histórico/favoritos/fila/pastas seguidas na mesma transação. O último perfil não pode ser excluído.
- Backup/restauração transportam as URLs e os dois status, com defaults para backups antigos.
- Anel ciano com halo em camadas: overlay de foco compartilhado, sem substituir handlers de clique ou animações existentes.
- Contêiner de pular abertura conectado também no MPV.

## P1 — OSD em duas camadas

Implementado nos dois motores:

- Primária: anterior, retroceder, Play/Pause, avançar, próximo, legendas, áudio, episódios e “Mais opções”. Pular abertura permanece na ação contextual.
- Secundária: velocidade, proporção, capítulos, informações técnicas; girar tela continua disponível no mobile.
- Handlers de áudio, legendas (incluindo opções auxiliares), episódios, capítulos, escala, velocidade e informação Kodi do ExoPlayer foram preservados. MPV ganhou informações técnicas no menu, consultadas do motor.
- PiP, bloqueio, gestos, AniSkip, transporte e fila de próximos episódios permanecem existentes.
- Em larguras inferiores a 700dp, transporte e ações ocupam duas linhas para evitar sobreposição. Play/Pause usa seletores de cor/foco.
- Menu translúcido com borda e restauração de foco; blur depende do Android/dispositivo, com superfície escura legível como fallback.

## P2 — perfis e consistência visual

Implementado:

- Editor com nome, URLs customizadas, catálogo de retratos de personagens e banners de anime via AniList, Admin/Kids e validação de campos. A busca é explícita, evitando uma requisição por tecla do controle.
- Até 12 retratos por anime e banner quando disponível; imagens pelo Glide com cache. Metadados do catálogo ficam em cache local por 24h e servem como fallback sem rede. URLs customizadas exigem HTTPS.
- Avatar remoto na seleção e no atalho da Home; wallpaper muda com o perfil focado. Nenhum avatar novo foi gerado por IA; recursos legados permanecem como fallback.
- Cards de perfil com cantos arredondados, status e foco. Área de perfis rolável em portrait e centralizada em landscape.
- Paleta padrão derivada de Estuary em azul muito escuro/ciano, rail TV de 184dp, bordas translúcidas. Temas alternativos existentes continuam disponíveis.
- Cards de episódios sempre 16:9, mesmo quando há pôster. Pôsteres, banners, thumbnails e metadados de Home/arquivos continuam usando as fontes existentes.

Admin e Kids são status persistidos/editáveis. **Não são autenticação, PIN ou filtro parental.** O editor comunica essa distinção; uma política de classificação de conteúdo precisa de definição própria antes de implementar restrições.

## P3 — validação e polimento

Critérios de aceite:

- Abrir perfis com D-Pad, alternar Gerenciar, editar, cancelar e retornar ao card sem perder a posição.
- Buscar anime, navegar nos retratos, selecionar imagem, salvar, reiniciar e verificar persistência; testar URL inválida e rede indisponível.
- ExoPlayer e MPV: acessar cada ação primária e secundária, fechar com Back, retomar foco, testar menus longos e capítulos ausentes.
- Conferir Home, grade/lista, detalhe e OSD em portrait/landscape, 720p/1080p/4K, fontes ampliadas e aparelho TV real.
- Confirmar que fallback translúcido segue legível com blur indisponível e que a decodificação de vídeo não perde fluidez.

Validações executadas:

- `assembleDebug` e `assembleDebugAndroidTest`: concluídos na rodada de entrega.
- 10 testes JVM focados em backup, URLs de perfil e isolamento de histórico: passaram.
- 3 testes instrumentados no emulador: passaram. Dois cobrem Room (migração 6→7 com histórico, importação JSON e persistência após recriar o gerenciador); o terceiro verifica ausência de sobreposição dos grupos do OSD em 360/640/960dp e a remoção das ações secundárias da faixa primária. A primeira execução encontrou sobreposição em 360dp, corrigida com seleção das constraints antes da medição (`PlayerControlsRow`).
- Suíte JVM completa: 129/133 passaram. As quatro falhas em `TokenProviderTest` envolvem o cache/expiração de tokens (`expiresIn` relativo versus timestamp); esses arquivos de produção/teste não foram alterados nesta modernização.
- Seleção de perfil → Gerenciar → Editar: navegação verificada com eventos D-Pad no emulador; captura visual do formulário e dos perfis.
- Consulta pública ao AniList neste ambiente retornou HTTP 403, e Jikan retornou 504; não foi possível confirmar carregamento online do catálogo nesta rede. O editor oferece erro recuperável, cache e URLs próprias.

No Windows, o Java/Gradle falhou com caminhos temporários e classpaths contendo acentos. Foram usados aliases locais de caminho e um init script temporário, sem alterar a configuração permanente do repositório.

## Fontes técnicas

- [AniList — Media](https://docs.anilist.co/reference/object/media) e [Character](https://docs.anilist.co/reference/object/character): campos de banners e retratos usados no catálogo.
- [Android — Window blurs](https://source.android.com/docs/core/display/window-blurs): blur nativo e fallback quando indisponível.
- Schemas Room versionados em `app/schemas/zechs.drive.stream.data.local.WatchListDatabase/`.

## Limites da avaliação

Não atribuí uma nota final 10/10: isso exigiria validação visual de todas as telas com uma biblioteca autenticada, medição de fluidez e teste em hardware TV. Os screenshots de `docs/ui-modernization` são capturas do emulador, não mockups nem evidência de certificação Android TV. Credenciais existentes não foram copiadas nem solicitadas para implementar a UI.

## Entregáveis

- Código Kotlin/XML, schema Room v7, testes e este plano P0–P3.
- APKs de debug para arm64-v8a, armeabi-v7a, x86 e x86_64 em `app/build/outputs/apk/debug/`.
- Resumo legível por máquina em `test-summary.json`.
