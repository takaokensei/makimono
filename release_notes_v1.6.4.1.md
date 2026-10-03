# Makimono v1.6.4.1

## Atualizações

- O atualizador agora autentica a consulta de releases e o download de APKs/checksums para repositório GitHub privado, usando um token de leitura fornecido no build.
- A autenticação só é anexada a URLs de `github.com` e `api.github.com`; links de redirecionamento externos não recebem o token.
- Mantém a assinatura compatível com as instalações atuais.
