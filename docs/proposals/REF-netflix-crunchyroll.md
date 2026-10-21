# Referência de UX e features — Netflix e Crunchyroll

Revisão: 21/10/2026. Base: `57d88b9`, branch `main`, working tree limpo antes da tarefa. O inventário anterior foi reconciliado com o código e com P-10 a P-18 e D-50; as entregas anteriores permanecem no histórico Git. Esta revisão distingue existência de código de homologação humana.

## Evidência e decisões

- Crunchyroll TV: capturas identificadas em `makimono-dossie/reconstruicao/evidencias/runtime/cr_ref_*.png`, especialmente detalhes, episódios e rail. Essas capturas são referência histórica do serviço, não retrato da build atual do Makimono.
- Netflix: [seleção de temporadas e episódios](https://help.netflix.com/en/node/125348), [busca por gênero e idioma](https://help.netflix.com/en/node/47765) e as propostas P-10–P-18 documentam os comportamentos usados. O APK Netflix disponível no dossiê é móvel; não se atribuem a ele medidas ou animações de TV.
- Código atual: HomeFragment, ContinueWatchingContextMenu, FocusEngine, SeriesDetailFragment, SeriesDetailViewModel, ProfileManager, DownloadRepository e políticas indicadas nas propostas.
- Regras visuais: `docs/design/DESIGN.md`, tokens Obsidian, sem blur dinâmico; 48dp/27dp nas bordas de TV e foco único.
- Context7 não está exposto nesta sessão. A API usada será conferida na [fonte oficial de MaterialAlertDialogBuilder 1.9.0](https://github.com/material-components/material-components-android/blob/1.9.0/lib/java/com/google/android/material/dialog/MaterialAlertDialogBuilder.java) e na [documentação Android de dialogs](https://developer.android.com/develop/ui/views/components/dialogs). Não se adicionará uma biblioteca para o seletor.

“Copiar” significa preservar o comportamento; “adaptar” significa ajustar a interação ao catálogo Drive/local e ao design Obsidian; “descartar” significa não introduzir essa variante. Recursos presentes serão mantidos sem nova implementação duplicada. Não são necessários código, logos ou assets dos serviços para os dois incrementos selecionados.

## Inventário reconciliado

| ID | Feature e origem | Benefício | Esforço incremental | Impacto TV | Dependências / estado atual | Decisão e justificativa |
|---|---|---|---|---|---|---|
| REF-01 | Badges de áudio — Crunchyroll | Conhecer idiomas antes do play | Já entregue | Texto >=12sp, sem novo foco | EpisodeAudioBadgePolicy; commit 4cf8327 | Adaptar, existente. Inferência de nome não equivale a inspeção de faixas; não ampliar nesta execução. |
| REF-02 | Assistido/progresso — ambos | Diferenciar episódios concluídos | Já entregue | Badge não interativo | WatchList e SeriesDetailEpisodeAdapter; 2541266 | Copiar, existente; preservar os testes. |
| REF-03 | Ações em Continuar assistindo — Netflix | Retomar, reiniciar, abrir série e remover sem abrir player | Já entregue | Menu/long-press; foco volta ao card; Desfazer | ContinueWatchingContextMenu e HomeActionHandler, incluindo Snackbar | Adaptar, existente; manter remoção reversível e isolamento por perfil. |
| REF-04 | Filtros de gênero — Crunchyroll | Reduzir a procura no catálogo | Já entregue | Pills com D-pad; selecionado distinto de foco | AnimeGenrePolicy e GenreFilterAdapter; 0a45d53 | Adaptar, existente, sem criar segunda linha de filtros. |
| REF-05 | Seletor compacto de temporadas — Netflix | Saltar direto para uma parte em franquias extensas | 3–5 h | Modal rolável; temporada atual marcada e focada; Back restaura gatilho | SeasonTab e selectSeasonTab existem; falta gatilho/modal | Adaptar e implementar. Mais de 4 partes usam seletor + card da parte atual, preservando seus chips de categoria. Até 4 mantêm lista atual. Limite é decisão Makimono. |
| REF-06 | Escala e realce de foco — ambos | Cursor de TV identificável | Já entregue | Um anel ativo; redução de movimento | FocusEngine já compartilhado | Adaptar, existente; não reescrever engine nem inventar parâmetros das referências. |
| REF-07 | Toggle Minha Lista — Netflix | Feedback imediato ao favoritar | Já entregue | Ação secundária próxima do play | SeriesDetailFragment/ViewModel; a5311cf | Copiar, existente; persistência e recursos do Makimono. |
| REF-08 | Hero, clearlogo e gêneros — ambos | Destaque editorial com identidade da obra | Já entregue | Scrim, overscan e ações legíveis | FeaturedHeroBinder/HeroController; c80d30f e 2f99756 | Adaptar, existente; aproveitar fontes de imagem já integradas. |
| REF-09 | Prévia de vídeo automática — Netflix | Descobrir conteúdo sem abrir detalhe | Alto | Decodificador, rede e movimento reduzido | P-18, sem fonte de preview confiável | Descartar nesta execução conforme P-18; manter a prévia estática e não duplicar proposta. |
| REF-10 | Downloads offline — Netflix/Crunchyroll móvel | Assistir sem conexão | Já existe, escopo maior para refinamentos | Cards e operações acessíveis por controle | DownloadRepository, DownloadWorker, DownloadsFragment e testes existem | Adaptar, existente. Corrige o inventário antigo que dizia haver só XML. Homologação offline real continua manual. |
| REF-11 | Três ações de Detalhes e menu contextual — ambos | Hero limpo sem perder opções | 2–4 h | Play, Minha Lista, Mais; menu com foco/restauração; margens seguras | Menu existente; Marcar assistido ainda é duplicado na barra; trailer aparece indisponível | Adaptar e implementar. Mover a ação direta duplicada para Mais, incluir Recomeçar para item em andamento e mostrar só opções disponíveis. |
| REF-12 | Tempo restante na retomada — Netflix | Saber quanto falta | Já entregue | Rótulo curto sem novo foco | P-10, PrimaryEpisodeSelection | Copiar, existente; não duplicar. |
| REF-13 | Pular abertura/recap/créditos — ambos | Reduzir ações repetidas | Já entregue | Foco sem roubar OSD | P-11, SkipController | Adaptar, existente; validar intervalos reais manualmente. |
| REF-14 | Próximo episódio e Ainda assistindo — ambos/Netflix | Maratona com cancelamento | Já entregue | Contagem, cancelar e retorno previsíveis | P-12/P-13, NextEpisodePolicy/PlaybackCoordinator | Copiar, existente; não alterar motores. |
| REF-15 | Idioma e atraso lembrados — Crunchyroll/Kodi | Evitar configurar todo episódio | Já entregue | Menus de faixa/arquivo | P-14/P-15, TrackPreferencePolicy | Adaptar, existente; ASS/SSA requer validação humana. |
| REF-16 | Capítulos/informação técnica — complemento local | Navegar e diagnosticar a mídia | Já entregue | OSD utilizável por D-pad | P-16/P-17 | Adaptar, existente; não replicar superfície técnica de outro serviço. |
| REF-17 | Perfis, busca, categorias e recomendações locais — ambos | Descoberta e personalização | Já entregue | Fluxos independentes e restauração de foco | Perfis persistidos, SearchFragment, prateleiras e Mais como este existem | Adaptar, existente. Não prometer recomendação de backend comercial; usar catálogo acessível. |
| REF-18 | Progresso entre aparelhos — ambos | Continuar em outro dispositivo | Alto | Sessão/conta e conflitos | D-50; backend/grants exigem desenho próprio | Descartar nesta execução: preservar contrato somente leitura, sem mudanças sensíveis de autenticação. |
| REF-19 | Assinatura, banners Premium, marca/arte dos serviços | Sem benefício para biblioteca pessoal | Desnecessário | Ruído visual | Não fazem parte do Makimono | Descartar; manter identidade, ícones e textos próprios existentes. |

## Ordem de execução e arquivos previstos

1. **REF-05:** SeasonPickerPolicy + testes; SeasonPickerDialog; SeriesDetailFragment; fragment_series_detail.xml em layout e layout-land (únicas variantes atuais); strings values/values-pt; VisualReviewActivity e testes de integração da UI para evidência sintética isolada.
2. **REF-11:** SeriesDetailActionsPolicy + testes; SeriesMoreActionsDialog; SeriesDetailFragment; os mesmos layouts e fixture se necessário; strings em ambos os idiomas; testes instrumentados para modal/Back/foco.

Sem alterar módulos, rotas, Drive, JNI ou libmpv. Nenhuma dependência nova é necessária. Uma tag local checkpoint-* antecede cada item. Cada commit exige verify-all completo verde; a base também será verificada. Testes lógicos antecedem a integração. O relatório consolidado seguirá WORKFLOW.md ao fim, incluindo limites e validações físicas pendentes.

## Contratos de aceite

- Seletor lista todas as partes sem truncar acesso, identifica seleção por ID, foca a seleção atual ao abrir, permite navegar/selecionar com D-pad e restaura foco no botão ao cancelar/selecionar.
- Seleção usa o fluxo existente do ViewModel, inclusive metadados, nota, episódios e chips de episódios/especiais/música/prévias; não cria outra fonte de estado.
- Barra de Detalhes mostra no máximo três ações; a ação removida da barra permanece no menu Mais. Play/retomar e lista preservam comportamento.
- Menu só oferece trailer se houver arquivo ou URL; Recomeçar inicia do zero sem apagar progresso antecipadamente; Marcar assistido mantém o comando existente; Follow continua disponível.
- Dialogs cabem nas margens seguras da janela, fecham com Back e liberam referências ao destruir a view. Strings novas em resources com paridade.
- Homologação final diferencia testes unitários, instrumentação/ADB no emulador e testes humanos pendentes do AGENTS.md §4.
