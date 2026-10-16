# Especificação — Player, OSD e Skip (`PlayerActivity`)

**Data:** 15/10/2026

- **OSD e Inatividade:** O OSD recolhe automaticamente após `4000ms` em estado de reprodução ativa e permanece visível quando pausado.
- **Skip Intro / Ending:** Integração com `AniSkipRepository` (via `malId` e número do episódio) e fallback de salto rápido (`+85s`). Quando a janela de skip entra em vigor, o botão `btnSkipIntro` aparece e recebe prioridade de foco sem interromper a reprodução.
- **Retorno Seguro (`Back`):** Primeira pressão em `Back` com OSD aberto apenas fecha o OSD; segunda pressão (ou `Back` com OSD fechado) persiste o progresso de reprodução e retorna para a tela anterior.
