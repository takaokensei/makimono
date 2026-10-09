# Reteste da seleção de perfis

- **Dispositivo:** Moto G34 5G / Android 15; pacote `.visualreview` com fixture local.
- **Inspeção visual:** os três perfis exibem avatares circulares sem placa retangular; nomes e papéis aparecem abaixo. O perfil ativo/focado tem contorno ciano, e “Adicionar perfil” conserva um alvo circular separado.
- **Percurso D-pad:** início no perfil ativo Cauã; RIGHT ×3 alcançou “Adicionar perfil”; DOWN alcançou `btnManageProfiles` sem dead-end. A hierarquia registra content descriptions para perfil ativo, administrador, Kids e adicionar.
- **Evidências:** `2026-10-07-profile-current-round-1.png/xml`, `2026-10-07-profile-current-right-end.xml` e `2026-10-07-profile-current-down-manage.xml` neste diretório.
- **Limites:** inspeção da fixture sintética; não cria nem edita perfis, não comprova persistência/ciclo de vida, TalkBack ou TV física.
