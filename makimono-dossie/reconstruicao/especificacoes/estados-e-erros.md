# Especificação — Estados Vazios, Carregamento e Erros (`StateView`)

**Data:** 15/10/2026

- **Taxonomia de Erros (`AppError` → `StateView.StateType`):**
  - `AppError.Network` → `ERROR_NETWORK`
  - `AppError.Authentication` → `ERROR_AUTH`
  - `AppError.AccessDenied` → `ERROR_ACCESS_DENIED`
  - `AppError.RateLimited` → `ERROR_RATE_LIMITED`
  - `AppError.NotFound` → `ERROR_NOT_FOUND`
  - `AppError.Server` → `ERROR_SERVER`
- **Recuperação por Controle Remoto:** Todo estado de erro ou vazio (`layoutHomeEmpty`, `stateView`) oferece botão de ação focável via D-pad (`Tentar novamente` / `Explorar catálogo`).
