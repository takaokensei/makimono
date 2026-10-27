"""
Makimono Seedbox Daemon — Automação Remota Colab & Local
Escuta a fila de animes solicitados via Android TV / Mobile Makimono no Cloudflare Worker,
resolve metadados via Jikan/MAL, baixa via Aria2/Nyaa/DarkMahou e envia direto para o Google Drive.
"""

import os
import sys
import re
import time
import json
import shutil
import urllib.request
import urllib.error
from pathlib import Path

MAKIMONO_WORKER_URL = "https://makimono-pairing.cauavitorfigueredo.workers.dev"

def api_request(worker_url: str, token: str, endpoint: str, method: str = "GET", body: dict = None) -> dict:
    url = f"{worker_url.rstrip('/')}{endpoint}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": f"Bearer {token.strip()}",
            "Content-Type": "application/json",
            "User-Agent": "Makimono-Daemon/1.0"
        },
        method=method
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))

def patch_job_status(worker_url: str, token: str, job_id: str, status: str, progress_pct: int = None, speed: str = None, folder_id: str = None, detail: str = None):
    payload = {"status": status}
    if progress_pct is not None: payload["progress_pct"] = progress_pct
    if speed is not None: payload["transfer_speed"] = speed
    if folder_id is not None: payload["drive_folder_id"] = folder_id
    if detail is not None: payload["status_detail"] = detail
    try:
        api_request(worker_url, token, f"/v1/seedbox/daemon/jobs/{job_id}", method="PATCH", body=payload)
    except Exception as e:
        print(f"⚠️ Erro ao atualizar status do job {job_id} no Worker: {e}")

def get_disk_free_gb(path: str = ".") -> float:
    try:
        total, used, free = shutil.disk_usage(path)
        return round(free / (1024 ** 3), 1)
    except Exception:
        return 50.0
