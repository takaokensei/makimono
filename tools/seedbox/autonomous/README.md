# ⛩️ Makimono Autonomous Seedbox (24/7 Cloud / Local)

Daemon autônomo headless projetado para operar **24 horas por dia sem precisar de PC, celular ou navegador aberto**.

---

## Como Funciona

1. O daemon fica em execução contínua (em container Docker ou serviço de fundo).
2. Escuta a fila no Cloudflare Worker a cada 15 segundos.
3. Quando o usuário pede um anime na **Android TV**:
   - O daemon consome o pedido imediatamente.
   - Baixa o **Episódio 1 em alta velocidade (16 conexões)** via Aria2c.
   - Faz o upload direto e resumível para o **Google Drive** do usuário.
   - A TV detecta o episódio pronto e abre a reprodução em poucos minutos!

---

## Onde Hospedar 24/7 Gratuitamente

### 1. Oracle Cloud Always Free (Recomendado — Grátis para sempre)
A Oracle oferece uma máquina virtual ARM com **4 núcleos, 24 GB de RAM e 200 GB de disco grátis permanentemente**.
```bash
# Clone e entre na pasta:
cd tools/seedbox/autonomous

# Configure suas credenciais no docker-compose.yml:
# OAUTH_REFRESH_TOKEN e MAKIMONO_SEEDBOX_TOKEN

# Inicie em segundo plano:
docker compose up -d
```

### 2. Render / Railway / Fly.io / HuggingFace Spaces
Suba este diretório como um Docker App. Configure as variáveis de ambiente:
- `MAKIMONO_SEEDBOX_TOKEN`: Seu token gerado no app.
- `OAUTH_REFRESH_TOKEN`: Seu token OAuth do Google Drive.

### 3. Execução Direta no Windows / Linux (Sem Docker)
```bash
pip install -r requirements.txt (ou use python nativo)
python makimono_autonomous_daemon.py
```
