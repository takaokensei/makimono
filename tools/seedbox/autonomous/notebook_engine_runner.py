"""
Makimono Autonomous Notebook Engine Runner (Oracle Cloud / Docker / Linux 24/7)
Executa 100% da lógica canônica do DarkMahou_P2P_v12_upload_retomavel (5.400+ linhas).
Inclui:
- Aria2RPCEngine com retomada resiliente
- Auto-Mix consolidado PT-BR (Nyaa.si + DarkMahou)
- DiskSpaceGate (regulação inteligente de disco para nunca esgotar espaço)
- Muxing automático de legendas (.ass/.srt para .mkv via ffmpeg)
- Upload resumável persistente v12 com MD5 check para o Google Drive
- MakimonoLiveJobBoard: telemetria ao vivo enviada ao Cloudflare Worker
- OracleAntiIdleGuard: proteção de memória RAM contra ocioso da Oracle Cloud
"""

from __future__ import annotations
import os
import sys
import time
import json
import shutil
import urllib.request
import urllib.error
import threading
from pathlib import Path

# Configura stdout para UTF-8 seguro em qualquer terminal
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

# Importa o motor canônico completo
import darkmahou_engine_core as core

MAKIMONO_WORKER_URL = os.environ.get("MAKIMONO_WORKER_URL", "https://makimono-pairing.cauavitorfigueredo.workers.dev")
MAKIMONO_SEEDBOX_TOKEN = os.environ.get("MAKIMONO_SEEDBOX_TOKEN", "")

OAUTH_CLIENT_ID = os.environ.get("OAUTH_CLIENT_ID", "235586231139-672hsh1bsecejbjut4fkk80o394d23l5.apps.googleusercontent.com")
OAUTH_CLIENT_SECRET = os.environ.get("OAUTH_CLIENT_SECRET", "")
OAUTH_REFRESH_TOKEN = os.environ.get("OAUTH_REFRESH_TOKEN", "")

PASTA_DESTINO_PADRAO = os.environ.get("PASTA_DESTINO", "Animes")
QUALIDADE_PREFERIDA = os.environ.get("QUALIDADE_PREFERIDA", "1080p")
MOTOR_DOWNLOAD = os.environ.get("MOTOR_DOWNLOAD", "RPC (recomendado)")
DOWNLOADS_SIMULTANEOS = int(os.environ.get("DOWNLOADS_SIMULTANEOS", "6"))
MARGEM_SEGURANCA_DISCO_GB = float(os.environ.get("MARGEM_DISCO_GB", "5.0"))
POLL_INTERVAL_SECS = int(os.environ.get("POLL_INTERVAL", "15"))
ORACLE_KEEPALIVE = os.environ.get("ORACLE_KEEPALIVE", "false").lower() in ("true", "1", "yes")

def _api_request(endpoint: str, method: str = "GET", body: dict = None) -> dict:
    url = f"{MAKIMONO_WORKER_URL.rstrip('/')}{endpoint}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": f"Bearer {MAKIMONO_SEEDBOX_TOKEN.strip()}",
            "Content-Type": "application/json",
            "User-Agent": "Makimono-NotebookRunner-Oracle/1.0"
        },
        method=method
    )
    with urllib.request.urlopen(req, timeout=25) as resp:
        return json.loads(resp.read().decode("utf-8"))

def _patch_job_status(job_id: str, status: str, progress_pct: int = None, speed: str = None, folder_id: str = None, detail: str = None):
    payload = {"status": status}
    if progress_pct is not None: payload["progress_pct"] = progress_pct
    if speed is not None: payload["transfer_speed"] = speed
    if folder_id is not None: payload["drive_folder_id"] = folder_id
    if detail is not None: payload["status_detail"] = detail
    try:
        _api_request(f"/v1/seedbox/daemon/jobs/{job_id}", method="PATCH", body=payload)
    except Exception as e:
        print(f"⚠️ Erro ao atualizar status do job {job_id} no Worker: {e}", file=sys.stderr)

def _get_disk_free_gb() -> float:
    try:
        total, used, free = shutil.disk_usage(".")
        return round(free / (1024 ** 3), 1)
    except Exception:
        return 50.0

class OracleAntiIdleGuard:
    """
    Mantém ~22% da memória RAM alocada em buffer seguro.
    A regra de 'Idle Instance Reclamation' da Oracle Cloud exige que CPU < 20%,
    Rede < 20% E Memória < 20% ocorram simultaneamente por 7 dias.
    Garantir que a memória fique acima de 20% blinda a VM contra desligamento automático.
    """
    def __init__(self, target_pct: float = 0.22):
        self.target_pct = target_pct
        self._buffer = None
        self._running = False

    def start(self):
        try:
            import psutil
            total_ram = psutil.virtual_memory().total
            bytes_to_hold = int(total_ram * self.target_pct)
            print(f"🛡️ [OracleAntiIdleGuard] Ativando reserva de {bytes_to_hold / (1024**2):.1f} MB RAM (~{self.target_pct*100:.0f}%)...")
            self._buffer = bytearray(b"\x00" * bytes_to_hold)
            print(f"✅ [OracleAntiIdleGuard] Proteção anti-idle ativa. A VM não será desclassificada pela Oracle.")
        except Exception as e:
            # Fallback seguro caso psutil não esteja instalado
            try:
                # Aloca 500 MB como baseline preventiva
                self._buffer = bytearray(500 * 1024 * 1024)
                print(f"🛡️ [OracleAntiIdleGuard] Alocação fallback de 500 MB ativa.")
            except Exception:
                pass

class MakimonoLiveJobBoard(core.JobBoard):
    """
    Subclasse do JobBoard reativo da Célula 4.
    Além de gerenciar o progresso do lote, envia telemetria em tempo real
    (%, MB/s, ETA, peers e status textual) diretamente para o Cloudflare Worker
    a cada 2.0 segundos para a Android TV e aplicativo mobile exibirem suavemente.
    """
    def __init__(self, job_id: str, anime_folder_id: str = None):
        super().__init__()
        self.job_id = job_id
        self.anime_folder_id = anime_folder_id
        self._last_report_ts = 0.0

    def handle_event(self, slot: int, kind: str, payload) -> None:
        super().handle_event(slot, kind, payload)
        now = time.time()
        force_report = (kind == "done")
        if force_report or (now - self._last_report_ts >= 2.0):
            self._last_report_ts = now
            threading.Thread(target=self._send_telemetry_report, daemon=True).start()

    def _send_telemetry_report(self):
        try:
            jobs = list(self._jobs.values())
            if not jobs:
                return
            total_pct = sum(j.percent for j in jobs) / len(jobs)
            is_uploading = any(j.status_type == "uploading" for j in jobs)
            if is_uploading:
                pct = int(96 + min(3, (total_pct / 100.0) * 3))
                status_str = "UPLOADING"
            else:
                pct = int(min(95, max(8, total_pct)))
                status_str = "DOWNLOADING"

            speeds = [j.speed for j in jobs if j.speed and j.speed != "--"]
            etas = [j.eta for j in jobs if j.eta and j.eta != "--"]
            peers_list = [j.peers for j in jobs if j.peers]
            total_peers = sum(peers_list)

            speed_str = speeds[0] if speeds else "Ativo"
            eta_str = f" • ETA {etas[0]}" if etas else ""
            peers_str = f" • {total_peers} peers" if total_peers > 0 else ""

            if is_uploading:
                detail = f"Sincronizando no Google Drive ({pct}%)..."
            else:
                detail = f"Baixando Ep. 1 ({pct}% • {speed_str}{eta_str}{peers_str})"

            _patch_job_status(
                self.job_id,
                status_str,
                progress_pct=pct,
                speed=speed_str,
                folder_id=self.anime_folder_id,
                detail=detail
            )
        except Exception:
            pass

def processar_solicitacao(job: dict, drive_service):
    job_id = job.get("job_id")
    anime_query = job.get("anime_query", "").strip()
    ep_filtro_raw = str(job.get("episodes_filter", "all")).strip()
    prefer_ptbr = bool(job.get("prefer_ptbr", 1))
    magnet_uri = job.get("magnet_uri")

    print(f"\n" + "=" * 65)
    print(f"📥 [PEDIDO RECEBIDO DA TV] Job: {job_id}")
    print(f"🎬 Anime: '{anime_query}' | Episódios: {ep_filtro_raw} | Preferir PT-BR: {prefer_ptbr}")
    print("=" * 65)

    _patch_job_status(job_id, "SEARCHING", progress_pct=5, detail=f"Buscando catálogo para '{anime_query}'...")

    nome_canonico = anime_query
    try:
        sugs = core.get_anime_suggestions(anime_query)
        if sugs and sugs[0].get("title"):
            nome_canonico = sugs[0]["title"]
            print(f"💡 Anime identificado via Jikan: {sugs[0]['label']}")
    except Exception as e:
        print(f"ℹ️ Consulta Jikan: {e}")

    anime_folder_name = core.sanitizar_nome_arquivo(nome_canonico, max_len=120)
    raiz_id = core.obter_ou_criar_pasta_drive(drive_service, PASTA_DESTINO_PADRAO)
    drive_folder_id = core.obter_ou_criar_pasta_drive(drive_service, anime_folder_name, parent_id=raiz_id)
    
    DEST_DIR = Path(f"./downloads_seedbox/{anime_folder_name}")
    DEST_DIR.mkdir(parents=True, exist_ok=True)

    ep_set = set()
    if ep_filtro_raw.lower() not in ("all", "todos", "*", ""):
        ep_set = core.parse_episode_selection(ep_filtro_raw)
        print(f"🎯 Episódios desejados ({len(ep_set)}): {sorted(list(ep_set))}")
    else:
        print("🎯 Episódios desejados: Todos os disponíveis")

    catalogo_grupos = {}
    if magnet_uri:
        print(f"🧲 Link magnet direto informado: {magnet_uri[:45]}...")
        from types import SimpleNamespace
        opt = SimpleNamespace(
            label=f"{nome_canonico} (Magnet Direto)",
            magnet=magnet_uri,
            source="Magnet",
            quality=QUALIDADE_PREFERIDA,
            ep_num=None,
            is_ptbr=prefer_ptbr,
            stable_key=lambda: f"magnet_{job_id}"
        )
        opcoes_selecionadas = [opt]
    else:
        _patch_job_status(job_id, "SEARCHING", progress_pct=15, detail="Varrendo Nyaa.si e DarkMahou...")
        try:
            nyaa_res = core.search_nyaa_grouped(anime_query, QUALIDADE_PREFERIDA, apenas_ptbr=prefer_ptbr, episodios_filtro=ep_set, max_paginas=2)
            if not nyaa_res and nome_canonico != anime_query:
                nyaa_res = core.search_nyaa_grouped(nome_canonico, QUALIDADE_PREFERIDA, apenas_ptbr=prefer_ptbr, episodios_filtro=ep_set, max_paginas=2)
            if nyaa_res:
                catalogo_grupos.update(nyaa_res)
        except Exception as e:
            print(f"⚠️ Nyaa: {e}")

        try:
            dm_animes = core.smart_search_darkmahou(anime_query)
            if not dm_animes and nome_canonico != anime_query:
                dm_animes = core.smart_search_darkmahou(nome_canonico)
            if dm_animes:
                dm_opts = core.extrair_catalogo_darkmahou(dm_animes[0]['url'], QUALIDADE_PREFERIDA, 100, ep_set)
                if dm_opts:
                    catalogo_grupos[f"DarkMahou (Direto) ({len(dm_opts)} eps)"] = dm_opts
        except Exception as e:
            print(f"⚠️ DarkMahou: {e}")

        if not catalogo_grupos:
            _patch_job_status(job_id, "FAILED", detail=f"Nenhum release encontrado para '{anime_query}'.")
            print(f"❌ Nenhum release encontrado para '{anime_query}'.")
            return

        mix = core.gerar_auto_mix_consolidado(catalogo_grupos, ep_set, QUALIDADE_PREFERIDA)
        if mix:
            opcoes_selecionadas = mix
            print(f"✨ Auto-Mix Consolidado selecionado ({len(mix)} episódios).")
        else:
            melhor_chave = max(catalogo_grupos.keys(), key=lambda k: len(catalogo_grupos[k]))
            opcoes_selecionadas = catalogo_grupos[melhor_chave]
            print(f"✨ Grupo selecionado: {melhor_chave} ({len(opcoes_selecionadas)} episódios).")

    manifest = core.Manifest(DEST_DIR / ".manifest.json")
    pendentes = [opt for opt in opcoes_selecionadas if not manifest.is_done(opt.stable_key())]

    _existentes = set()
    _existentes |= core.detectar_episodios_existentes(DEST_DIR)
    if drive_service and drive_folder_id:
        try:
            _existentes |= core.detectar_episodios_existentes_drive(drive_service, drive_folder_id)
        except Exception as e_drv:
            print(f"⚠️ Drive existing check: {e_drv}")

    if _existentes:
        pendentes, ja_baixados = core.filtrar_opcoes_ja_existentes(pendentes, _existentes)
        for jb in ja_baixados:
            manifest.mark_done(jb.stable_key(), "detectado_no_drive", extra={"origem": "makimono_check"})

    if not pendentes:
        msg = f"Episódio(s) já presentes no seu Google Drive ({PASTA_DESTINO_PADRAO}/{anime_folder_name}/)."
        _patch_job_status(job_id, "COMPLETED", progress_pct=100, folder_id=drive_folder_id, detail=msg)
        print(f"✅ {msg}")
        return

    print(f"🚀 Iniciando download e upload de {len(pendentes)} episódio(s)...")
    _patch_job_status(job_id, "DOWNLOADING", progress_pct=10, folder_id=drive_folder_id, detail=f"Iniciando download de {len(pendentes)} ep(s)...")

    disk_gate = core.DiskSpaceGate(DEST_DIR, margem_seguranca_gb=MARGEM_SEGURANCA_DISCO_GB)
    live_board = MakimonoLiveJobBoard(job_id=job_id, anime_folder_id=drive_folder_id)

    if MOTOR_DOWNLOAD.startswith("RPC") and hasattr(core, "Aria2RPCEngine"):
        session_file = Path(DEST_DIR) / ".aria2_sessao_retomada.txt"
        engine = core.Aria2RPCEngine(
            staging_dir=None,
            max_concurrent_downloads=DOWNLOADS_SIMULTANEOS,
            stall_timeout_com_peers=180.0,
            stall_timeout_sem_peers=45.0,
            metadata_timeout=75.0,
            session_file=session_file,
        )
        ctrl_panel = core.JobControlPanel()
        resultados = core.run_batch_rpc(
            engine=engine,
            options=pendentes,
            dest_root=DEST_DIR,
            board=live_board,
            control_panel=ctrl_panel,
            drive_service=drive_service,
            drive_folder_id=drive_folder_id,
            disk_gate=disk_gate,
        )
    else:
        engine = core.Aria2Engine(bt_stop_timeout=60, metadata_timeout=75.0, stall_timeout_com_peers=180.0, stall_timeout_sem_peers=45.0)
        resultados = core.run_concurrent(
            engine=engine,
            options=pendentes,
            dest_root=DEST_DIR,
            board=live_board,
            max_workers=DOWNLOADS_SIMULTANEOS,
            max_retries=1,
            drive_service=drive_service,
            drive_folder_id=drive_folder_id,
            disk_gate=disk_gate,
        )

    for item, res in zip(pendentes, resultados):
        k = item.stable_key()
        if res.success:
            manifest.mark_done(k, str(res.final_path.name if res.final_path else "drive_upload"), extra={"attempts": res.attempts})
        else:
            manifest.mark_failed(k, res.error or "erro desconhecido")

    # Mux automático de legendas pendentes .ass/.srt
    try:
        core.muxar_legendas_pendentes_na_pasta(DEST_DIR)
    except Exception:
        pass

    sucessos = sum(1 for r in resultados if r.success)
    falhas = sum(1 for r in resultados if not r.success and not r.cancelled)

    if sucessos > 0:
        msg_final = f"Sucesso! {sucessos}/{len(pendentes)} eps salvos em {PASTA_DESTINO_PADRAO}/{anime_folder_name}/."
        _patch_job_status(job_id, "COMPLETED", progress_pct=100, folder_id=drive_folder_id, detail=msg_final)
        print(f"🎉 {msg_final}")
    else:
        msg_final = f"Falha no download dos episódios selecionados ({falhas} erros)."
        _patch_job_status(job_id, "FAILED", detail=msg_final)
        print(f"❌ {msg_final}")

def main():
    print("=" * 65)
    print("⛩️ MAKIMONO SEEDBOX AUTONOMOUS RUNNER (ORACLE CLOUD / LINUX 24/7)")
    print("=" * 65)

    if not MAKIMONO_SEEDBOX_TOKEN:
        print("❌ ERRO: Variável MAKIMONO_SEEDBOX_TOKEN não configurada!")
        print("Gere seu token na TV em Configurações > Seedbox e configure no ambiente.")
        sys.exit(1)

    if ORACLE_KEEPALIVE:
        guard = OracleAntiIdleGuard()
        guard.start()

    print(f"📡 Conectado ao Cloudflare Worker: {MAKIMONO_WORKER_URL}")
    print(f"🔑 Token Ativo: {MAKIMONO_SEEDBOX_TOKEN[:12]}...{MAKIMONO_SEEDBOX_TOKEN[-6:]}")
    print(f"💾 Espaço Livre em Disco: {_get_disk_free_gb()} GB")

    drive_service = None
    if OAUTH_REFRESH_TOKEN:
        print("🔑 Inicializando conexão OAuth 2.0 Pessoal com o Google Drive...")
        try:
            drive_service = core.autenticar_drive_oauth(OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET, OAUTH_REFRESH_TOKEN)
            print("✅ Google Drive conectado com sucesso (Cota Pessoal)!")
        except Exception as e:
            print(f"⚠️ Aviso ao autenticar Drive OAuth: {e}")
    else:
        print("ℹ️ OAUTH_REFRESH_TOKEN não fornecido no ambiente. Uploads serão armazenados localmente.")

    print("\n🎧 Escutando pedidos da fila do Makimono 24/7...")
    while True:
        try:
            free_disk = _get_disk_free_gb()
            poll_res = _api_request("/v1/seedbox/daemon/poll", method="POST", body={
                "free_disk_gb": free_disk,
                "active_speed": "Idle"
            })
            job = poll_res.get("job")
            if job:
                processar_solicitacao(job, drive_service)
            else:
                print(f"💓 [Daemon Ativo] Aguardando novos downloads... (Disco livre: {free_disk} GB)", end="\r")
        except urllib.error.HTTPError as he:
            print(f"\n⚠️ HTTP Error {he.code}: {he.reason}")
        except Exception as e:
            print(f"\n⚠️ Exceção no Runner: {e}")
        time.sleep(POLL_INTERVAL_SECS)

if __name__ == "__main__":
    main()
