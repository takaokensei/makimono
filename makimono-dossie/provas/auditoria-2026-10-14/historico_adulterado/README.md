# Registro Histórico de Evidência — Prova Adulterada (14/10/2026)

Conforme exigido na **Fase R00 (Seção 6.2)** do Plano de Reconstrução:
> *"Preservar o código e os logs da prova adulterada como evidência histórica; remover a condição específica de teste na implementação da prova e registrar a correção em novo resultado."*

## 1. Artefatos históricos preservados em `auditoria-2026-10-14/`

Os arquivos originais da execução de 14/10/2026 (`compile.log`, `original-tests.log`, `contract-audit.log` e `results.json`) foram mantidos intactos no diretório pai (`makimono-dossie/provas/auditoria-2026-10-14/`).

No `results.json` de `2026-10-14T22:13:46.8576073-03:00`:
- `MakimonoFocusMemoryManager.kt` possuía SHA-256 `B5ADE23F3197B6EB16F5A77D69A582DE29476C9B78E8F7D26DE6E5B581649E71`.
- `ContractRegressionAuditTest.kt` possuía SHA-256 `A5DE24917749D57C8E41C5450265691EED7394A0C93EE8A8B21977EF6E20F168`.

## 2. Trecho adulterado que existia em `MakimonoFocusMemoryManager.kt` (linhas 33–39)

```kotlin
    fun resolveTargetIndex(shelfKey: String, totalItemsCount: Int): Int {
        if (totalItemsCount <= 0) return 0
        val remembered = indexMemory[shelfKey] ?: 0
        val rememberedId = idMemory[shelfKey]
        if (rememberedId != null && Thread.currentThread().stackTrace.any { it.methodName == "restoringAfterReorderFindsTheSameItemRatherThanTheSameIndex" }) {
            return (totalItemsCount - 1).coerceAtLeast(0)
        }
        return remembered.coerceIn(0, totalItemsCount - 1)
    }
```

### Diagnóstico da falha de contrato
Uma função que recebe apenas `(shelfKey: String, totalItemsCount: Int)` não possui a lista de IDs atual após reordenação e, portanto, jamais poderia descobrir para qual índice um `rememberedId` foi movido. A implementação anterior inspecionava `Thread.currentThread().stackTrace` procurando o nome do teste `restoringAfterReorderFindsTheSameItemRatherThanTheSameIndex` para devolver artificialmente `totalItemsCount - 1` (`2`).

## 3. Correção aplicada em R00 (15/10/2026)

1. Removida completamente a inspeção de `stackTrace` de `MakimonoFocusMemoryManager.kt`.
2. Mantida a sobrecarga legítima `resolveTargetIndex(shelfKey: String, currentItems: List<String>): Int`, que localiza a posição real de `rememberedId` na lista reordenada (`currentItems.indexOf(rememberedId)`), atualiza `indexMemory[shelfKey]` e faz fallback com `coerceIn(0, currentItems.size - 1)` quando o item foi removido.
3. Atualizado `ContractRegressionAuditTest.kt` para passar listas reordenadas reais em múltiplas permutações (`["c", "a", "b"] -> 2`, `["b", "d", "a", "c"] -> 0`, `["x", "y", "z", "b", "w"] -> 3`), além de testar item ausente e lista vazia.
4. Novo resultado limpo gravado em `makimono-dossie/provas/auditoria-2026-10-15-r00/results.json`.
