# ⛩️ Makimono Autonomous Seedbox Runner (Oracle Cloud / Docker 24/7)

Motor autônomo headless que executa **100% da lógica do notebook DarkMahou P2P v12 (5.400+ linhas)** 24 horas por dia, 7 dias por semana, sem precisar de navegador, PC ou celular aberto.

---

## ⚡ Recursos Integrados

1. **Auto-Mix Consolidado PT-BR:** Varredura inteligente no Nyaa.si e DarkMahou selecionando os melhores releases e fansubs.
2. **Aria2RPC com Retomada Resiliente:** Baixa o **Episódio 1 em altíssima velocidade** (Fast-Start) e depois o restante da temporada.
3. **Muxing Automático de Legendas:** Embutimento de legendas `.ass`/`.srt` em português dentro do arquivo `.mkv` via `ffmpeg`.
4. **Upload Resumável Persistente v12:** Upload direto para o Google Drive com verificação de integridade `MD5`.
5. **Telemetria ao Vivo (`MakimonoLiveJobBoard`):** Transmite progresso real (%, MB/s e ETA) a cada 3 segundos para o Cloudflare Worker e Android TV.
6. **Blindagem Anti-Idle (`OracleAntiIdleGuard`):** Mantém >20% de memória RAM reservada para evitar que a Oracle Cloud desligue a VM por ociosidade.

---

## 🚀 Como Subir na Oracle Cloud Always Free (10 TB/mês Grátis)

### Passo 1: Criar a VM Gratuita na Oracle Cloud
1. Crie uma conta no [Oracle Cloud Free Tier](https://www.oracle.com/cloud/free/).
2. No painel, crie uma **Instância de Computação**:
   * **Imagem:** Ubuntu 22.04 ou 24.04 (Always Free).
   * **Forma (Shape):** `VM.Standard.A1.Flex` (ARM Ampere: selecione 2 a 4 OCPUs e 12 a 24 GB de RAM) ou `VM.Standard.E2.1.Micro` (AMD).
   * **Disco:** 50 a 100 GB (Always Free permite até 200 GB totais).

### Passo 2: Instalar Docker na VM
Conecte via SSH à sua VM e execute:
```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

### Passo 3: Clonar e Rodar
```bash
git clone https://github.com/takaokensei/makimono.git
cd makimono/tools/seedbox/autonomous

# Configure seu Token gerado na Android TV (Configurações > Seedbox)
export MAKIMONO_SEEDBOX_TOKEN="sk_makimono_seu_token_aqui"

# Suba o serviço em segundo plano
docker compose up -d --build
```

### Passo 4: Verificar Logs
```bash
docker compose logs -f
```
O runner reportará:
```text
⛩️ MAKIMONO SEEDBOX AUTONOMOUS RUNNER (ORACLE CLOUD / LINUX 24/7)
🛡️ [OracleAntiIdleGuard] Ativando reserva de RAM...
📡 Conectado ao Cloudflare Worker: https://makimono-pairing.cauavitorfigueredo.workers.dev
🔑 Token Ativo: sk_makimono_...
🎧 Escutando pedidos da fila do Makimono 24/7...
```

Pronto! A partir deste momento, qualquer anime pesquisado na TV com o selo **[DOWNLOAD]** será baixado e enviado ao seu Google Drive automaticamente pela sua nuvem Oracle 24/7.
