# Tabela de Mapeamento Tipográfico (T1.3)

Esta tabela define a regra estrita de conversão de tamanhos literais (`android:textSize="...sp"`) para os tokens do Design System Makimono (`TextAppearance.Makimono.TV.*`).

| Tamanho literal atual | Token novo | Uso típico |
|---|---|---|
| 9, 10, 10.5 sp | `Caption` | Badges compactos, metadados secundários, watermark |
| 11, 11.5, 12, 12.5 sp | `Caption` | Metadados gerais, duração restante |
| 13 sp | `Callout` | Botões secundários, chips, contexto |
| 14, 15 sp | `Body` | Texto corrido, sinopses, descrições |
| 16, 17 sp | `Headline` | Títulos de cards, itens de lista, botões primários |
| 18, 19 sp | `Title3` | Cabeçalhos de seção menores, diálogos |
| 20, 21, 22 sp | `Title2` | Cabeçalho de prateleiras ("Continuar assistindo") |
| 28, 30, 32 sp | `Title1` | Títulos de telas principais |
| 34, 36 sp | `Display` | Título do Hero principal |

### Exceções Especiais
- **Tempos e Contadores (`12:45`, `24:10`, `Ep. 03`):** Usam obrigatoriamente `TextAppearance.Makimono.TV.Numeric`, que habilita números tabulares (`tnum`) para evitar jitter horizontal durante a contagem.
