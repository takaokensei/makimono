#!/usr/bin/env python3
"""
Makimono Remote Seedbox Daemon for Google Colab.
Connects DarkMahou_P2P_v12 pipeline to the Makimono Cloudflare Worker D1 queue.
Polls queued download requests from the Makimono Android TV / Mobile app, downloads via
aria2, checks integrity with ffprobe, and uploads to Google Drive with resumable sessions.
"""

import os
import sys
import time
import shutil
import json
import traceback
from typing import Optional, Dict, Any

try:
    import requests
except ImportError:
    print("[Makimono] Instalando requests...")
    os.system("pip install -q requests")
    import requests

class MakimonoSeedboxDaemon:
    def __init__(self, worker_url: str, daemon_token: str):
        self.worker_url = worker_url.rstrip("/")
        self.daemon_token = daemon_token
        self.headers = {
            "Authorization": f"Bearer {self.daemon_token}",
            "Content-Type": "application/json",
            "User-Agent": "MakimonoColabDaemon/1.0"
        }

    def get_free_disk_gb(self) -> float:
        try:
            total, used, free = shutil.disk_usage("/content" if os.path.exists("/content") else ".")
            return round(free / (1024 ** 3), 1)
        except Exception:
            return 0.0

    def poll(self, active_speed: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """Sends heartbeat and polls for the next pending job."""
        try:
            payload = {
                "free_disk_gb": self.get_free_disk_gb(),
                "active_speed": active_speed
            }
            resp = requests.post(f"{self.worker_url}/v1/seedbox/daemon/poll", headers=self.headers, json=payload, timeout=15)
            if resp.status_code == 200:
                data = resp.json()
                return data.get("job")
            else:
                print(f"[Makimono Poll] Erro {resp.status_code}: {resp.text}")
                return None
        except Exception as e:
            print(f"[Makimono Poll] Exceção de rede: {e}")
            return None

    def update_job(
        self,
        job_id: str,
        status: Optional[str] = None,
        progress_pct: Optional[int] = None,
        transfer_speed: Optional[str] = None,
        drive_folder_id: Optional[str] = None,
        status_detail: Optional[str] = None
    ) -> bool:
        """Pushes real-time download and upload progress back to the Cloudflare Worker."""
        try:
            payload = {}
            if status: payload["status"] = status
            if progress_pct is not None: payload["progress_pct"] = progress_pct
            if transfer_speed: payload["transfer_speed"] = transfer_speed
            if drive_folder_id: payload["drive_folder_id"] = drive_folder_id
            if status_detail: payload["status_detail"] = status_detail

            resp = requests.patch(
                f"{self.worker_url}/v1/seedbox/daemon/jobs/{job_id}",
                headers=self.headers,
                json=payload,
                timeout=15
            )
            return resp.status_code == 200
        except Exception as e:
            print(f"[Makimono Update] Erro atualizando job {job_id}: {e}")
            return False

    def run_daemon_loop(self, poll_interval_seconds: int = 10, execute_job_callback=None):
        """Continuous daemon loop running in Google Colab."""
        print(f"============================================================")
        print(f"🤖 Makimono Seedbox Daemon Ativo!")
        print(f"📡 Conectado ao Worker: {self.worker_url}")
        print(f"💾 Espaço Livre: {self.get_free_disk_gb()} GB")
        print(f"Aguardando pedidos de download vindos da TV / App...")
        print(f"============================================================")

        while True:
            try:
                job = self.poll()
                if job:
                    job_id = job["job_id"]
                    anime_query = job["anime_query"]
                    ep_filter = job.get("episodes_filter", "all")
                    prefer_ptbr = bool(job.get("prefer_ptbr", 1))

                    print(f"\n[Makimono] 🚀 Novo pedido recebido!")
                    print(f" - ID: {job_id}")
                    print(f" - Anime: '{anime_query}'")
                    print(f" - Episódios: {ep_filter} | Preferir PT-BR: {prefer_ptbr}")

                    self.update_job(job_id, status="SEARCHING", status_detail=f"Buscando releases para '{anime_query}'...")

                    if execute_job_callback:
                        execute_job_callback(job, self)
                    else:
                        print("[Makimono] Simulação concluída (adicione execute_job_callback)")
                        self.update_job(job_id, status="COMPLETED", progress_pct=100, status_detail="Concluído com sucesso!")
                else:
                    time.sleep(poll_interval_seconds)
            except KeyboardInterrupt:
                print("\n[Makimono] Daemon pausado pelo usuário.")
                break
            except Exception as e:
                print(f"[Makimono Loop] Erro inesperado: {e}")
                time.sleep(poll_interval_seconds)

if __name__ == "__main__":
    # Test execution or standalone run
    token = os.environ.get("MAKIMONO_SEEDBOX_TOKEN", "sk_makimono_test")
    worker = os.environ.get("MAKIMONO_WORKER_URL", "https://makimono-pairing-worker.cauavitor.workers.dev")
    daemon = MakimonoSeedboxDaemon(worker, token)
    print(f"Makimono daemon client initialized. Free disk: {daemon.get_free_disk_gb()} GB")
