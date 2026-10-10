# Especificação de Movimento e Interação (Motion System)

**Versão:** 1.0  
**Status:** Implementado (Tokens ativos em `motion_spec.xml`)  
**Decisão de Referência:** D9 (Estilo de movimento com propósito e contenção — Nível Apple)  
**Norma de Design:** Apple Human Interface Guidelines (*Motion*) adaptado para Android TV / 10-foot experience.

---

## 1. Filosofia e Princípios

O movimento no Makimono não existe para entretenimento visual, mas como ferramenta cognitiva de orientação espacial e feedback tátil/remoto instantâneo.

1. **Movimento com Propósito (Meaningful Motion):**  
   Nenhuma animação é executada sem comunicar uma mudança de estado ou hierarquia espacial. Se uma transição não orienta o usuário, ela não deve se mover.
2. **Contenção e Brevidade (Restraint & Brevity):**  
   A saída de um elemento visual nunca é mais lenta do que sua entrada. Interações de alta frequência (movimentação do D-pad, exibição do OSD, foco em listas) utilizam duração mínima ($\le 150\,\text{ms}$) e deslocamento físico restrito a no máximo $8\,\text{dp}$.
3. **Não-Bloqueio (Non-Blocking & Interruptible):**  
   Nenhuma animação bloqueia a fila de eventos do usuário. Todas as transições são contínuas, canceláveis e reversíveis imediatamente a partir do progresso atual ($p \in [0, 1]$), sem saltos de transparência ou reinício do zero.
4. **Respeito a Movimento Reduzido (Accessibility First):**  
   Quando `reduceMotionEnabled` estiver ativo (seja via preferência do perfil de usuário ou acessibilidade do Android), o sistema descarta deslocamentos espaciais e executa transições instantâneas ou *fades* rápidos lineares.

---

## 2. Tokens Semânticos de Movimento

Os tokens estão formalizados em `app/src/main/res/values/motion_spec.xml` e referenciados via recursos Android (`R.integer.*`):

| Token | Finalidade | Duração (ms) | Curva de Aceleração (Easing) | Classe AndroidX / Interpolador |
|---|---|:---:|---|---|
| `motion_osd_show` | Entrada do OSD e painéis de controle | 220 ms | Desaceleração rápida | `LinearOutSlowInInterpolator` |
| `motion_osd_hide` | Saída dos controles do player | 150 ms | Aceleração para fora | `FastOutLinearInInterpolator` |
| `motion_focus` | Anel e escala de foco do D-pad na TV | 150 ms | Padrão (ease-in-out) | `FastOutSlowInInterpolator` |
| `motion_card` | Card "Próximo Episódio" e cards satélite | 220 ms | Padrão suave | `FastOutSlowInInterpolator` |
| `motion_pill_enter`| Entrada de pílulas de gesto (brilho, volume, busca) | 200 ms | Padrão | `FastOutSlowInInterpolator` |
| `motion_pill_exit` | Saída de avisos e pílulas de gesto | 150 ms | Aceleração | `FastOutLinearInInterpolator` |
| `motion_crossfade` | Transição de esqueleto (placeholder) para conteúdo | 200 ms | Linear contínua | `LinearInterpolator` |

---

## 3. Diretrizes Técnicas de Implementação

### 3.1. Máquina de Estados e Animador Único
Todo componente animado composto (como o OSD do player) deve utilizar um único `ValueAnimator` governado por uma variável contínua de progresso $p \in [0, 1]$:
$$p = 0 \implies \text{totalmente recolhido/invisível}$$
$$p = 1 \implies \text{totalmente expandido/visível}$$

- Elementos superiores: $\text{translationY} = -8\,\text{dp} \times (1 - p)$, $\alpha = p$
- Elementos inferiores: $\text{translationY} = +8\,\text{dp} \times (1 - p)$, $\alpha = p$
- Elementos secundários (scrims, botões satélite): $\alpha = p$

### 3.2. Cancelamento Obrigatório no Caminho de Entrada
Toda animação que configure `withEndAction { visibility = View.GONE }` deve obrigatoriamente chamar `view.animate().cancel()` antes de redefinir visibilidade ou reiniciar animação de exibição. A omissão de `cancel()` resulta em cancelamento tardio apagando visualmente o elemento que o usuário acabou de acionar.

### 3.3. Proibição de Literais Hardcoded
É proibido o uso de valores numéricos literais (ex.: `setDuration(250)`, `setDuration(180)`) em chamadas de animação no código Kotlin. As durações devem ser obtidas via `context.resources.getInteger(R.integer.motion_*)`.

---

## 4. Auditoria de Conformidade
A integridade desta especificação é validada pela suíte de verificação estática contínua em `tools/checks/` e pelos testes unitários de ciclo de vida em `OsdStateMachineTest`.
