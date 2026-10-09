# Diretrizes de Release e Versionamento — Makimono

Documento canônico para versionamento semântico, geração de changelog e notas de versão.

---

## 1. Esquema de Versão

Makimono utiliza versionamento no formato `vMAJOR.MINOR.PATCH` (e subtags de correção como `vMAJOR.MINOR.PATCH.HOTFIX` quando necessário):

- **MAJOR (v1.x.x):** Grandes saltos arquiteturais e marcos de produto.
- **MINOR (vx.6.x):** Novas funcionalidades, melhorias no reprodutor ou suporte a novas telas.
- **PATCH (vx.x.4):** Correções de bugs, ajustes de foco, alinhamento de tokens e polimento.
- **`versionCode`:** Inteiro monotonicamente incremental gerenciado no `app/build.gradle`.

---

## 2. Histórico de Versões

As notas detalhadas de cada versão são armazenadas individualmente neste diretório:
- `v1.5.8.md`
- `v1.5.9.md`
- `v1.6.0.md`
- `v1.6.1.md`
- `v1.6.2.md`
- `v1.6.4.md`
- `v1.6.4.1.md`
- `v1.6.4.5.md`

O arquivo `CHANGELOG.md` na raiz do repositório agrega o histórico completo consolidado a partir destas notas.
