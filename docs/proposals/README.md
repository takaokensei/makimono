# 💡 Propostas de Produto e Evolução de Recursos — Makimono

Este diretório documenta as propostas estruturadas de produto (RFCs/Product Proposals) inspiradas em plataformas como Netflix, Crunchyroll, Kodi e Apple TV.

---

## 📑 Índice de Propostas

### 1. Série P-01 a P-08 (Ciclo v5)
Propostas preliminares consolidadas em [`proposals-p01-p08.md`](proposals-p01-p08.md):
- **P-01:** Botão primário "Continuar" com tempo restante.
- **P-02:** Memória de preferência de idioma de áudio e legenda por série e perfil.
- **P-03:** Pular abertura e créditos integrado com AniSkip e capítulos MKV.
- **P-04:** Próximo episódio no pós-play com contagem regressiva e cancelamento.
- **P-05:** Padronização de telas de estado vazio, erro e sem rede via `StateView`.
- **P-06:** Prévia e trailer no card de destaque.
- **P-07:** Arquitetura para downloads locais e cache offline.
- **P-08:** Sincronização de progresso entre múltiplos dispositivos (escopo Drive).

### 2. Série P-10 a P-18 (Ciclo v6 — "Próximo Nível")
As propostas para execução e implementação da Fase 4 do plano v6 (`plano_v6_makimono.md`) expandem os conceitos acima com critérios de aceite estritos:
- **P-10:** Continuar com tempo restante no hero card e detalhes da série.
- **P-11:** Botão dedicado para pular abertura, recapitulação e créditos com auto-foco sem perda de OSD.
- **P-12:** Cartão de pós-play com contagem regressiva de 10s e reprodução em cadeia.
- **P-13:** Verificação de inatividade de reprodução prolongada ("Ainda está assistindo?").
- **P-14:** Persistência de faixa de áudio e legenda por obra e por perfil de usuário.
- **P-15:** Persistência de offset de sincronização de áudio/legenda por arquivo de mídia.
- **P-16:** Navegador visual de capítulos e marcadores de tempo no OSD.
- **P-17:** Painel unificado de telemetria técnica e diagnóstico do motor de vídeo.
- **P-18:** Billboard com teaser audiovisual em reprodução em segundo plano.

---

## ⚖️ Critérios de Aceite e Implementação

Cada proposta da série v6 só entra em desenvolvimento após:
1. Aprovação expressa do mantenedor (`APROVADO P-1X`).
2. Existência de testes de caracterização prévios no reprodutor.
3. Não adicionar dependências externas desnecessárias nem comprometer o tempo de inicialização em TVs de entrada.
