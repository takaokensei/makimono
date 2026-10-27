"""
Makimono Autonomous Headless Seedbox Daemon (24/7 Cloud / Local)
Opera 100% autônomo sem precisar de navegador, PC ou celular aberto.
Suporta execução em Docker, VPS, Oracle Cloud Free Tier ou servidor doméstico.
"""

import os
import sys
import re
import time
import json
import shutil
import urllib.request
import urllib.parse
import urllib.error
import subprocess
from pathlib import Path

MAKIMONO_WORKER_URL = os.environ.get("MAKIMONO_WORKER_URL", "https://makimono-pairing.cauavitorfigueredo.workers.dev")
MAKIMONO_SEEDBOX_TOKEN = os.environ.get("MAKIMONO_SEEDBOX_TOKEN", "")

OAUTH_CLIENT_ID = os.environ.get("OAUTH_CLIENT_ID", "235586231139-672hsh1bsecejbjut4fkk80o394d23l5.apps.googleusercontent.com")
OAUTH_CLIENT_SECRET = os.environ.get("OAUTH_CLIENT_SECRET", "")
OAUTH_REFRESH_TOKEN = os.environ.get("OAUTH_REFRESH_TOKEN", "")

STORAGE_DIR = Path(os.environ.get("STORAGE_DIR", "./downloads_seedbox"))
POLL_INTERVAL = int(os.environ.get("POLL_INTERVAL", "15"))

def api_request(endpoint: str, method: str = "GET", body: dict = None) -> dict:
    url = f"{MAKIMONO_WORKER_URL.rstrip('/')}{endpoint}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": f"Bearer {MAKIMONO_SEEDBOX_TOKEN.strip()}",
            "Content-Type": "application/json",
            "User-Agent": "Makimono-Autonomous-Daemon/1.0"
        },
        method=method
    )
    with urllib.request.urlopen(req, timeout=25) as resp:
        return json.loads(resp.read().decode("utf-8"))

def patch_job(job_id: str, status: str, progress_pct: int = None, speed: str = None, folder_id: str = None, detail: str = None):
    payload = {"status": status}
    if progress_pct is not None: payload["progress_pct"] = progress_pct
    if speed is not None: payload["transfer_speed"] = speed
    if folder_id is not None: payload["drive_folder_id"] = folder_id
    if detail is not None: payload["status_detail"] = detail
    try:
        api_request(f"/v1/seedbox/daemon/jobs/{job_id}", method="PATCH", body=payload)
    except Exception as e:
        print(f"⚠️ Erro ao atualizar job {job_id} no Worker: {e}", file=sys.stderr)

def get_disk_free_gb() -> float:
    try:
        total, used, free = shutil.disk_usage(STORAGE_DIR.parent if STORAGE_DIR.exists() else ".")
        return round(free / (1024 ** 3), 1)
    except Exception:
        return 50.0

def get_drive_access_token() -> str:
    """Obtém novo access token do Google OAuth usando o refresh token."""
    if not OAUTH_REFRESH_TOKEN:
        return ""
    data = urllib.parse.urlencode({
        "client_id": OAUTH_CLIENT_ID,
        "client_secret": OAUTH_CLIENT_SECRET,
        "refresh_token": OAUTH_REFRESH_TOKEN,
        "grant_type": "refresh_token"
    }).encode("utf-8")
    req = urllib.request.Request("https://oauth2.googleapis.com/token", data=data, method="POST")
    with urllib.request.urlopen(req, timeout=20) as resp:
        res = json.loads(resp.read().decode("utf-8"))
        return res.get("access_token", "")

def drive_get_or_create_folder(access_token: str, folder_name: str, parent_id: str = "root") -> str:
    """Busca ou cria uma pasta no Google Drive."""
    query = f"name = '{folder_name}' and '{parent_id}' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
    url = f"https://www.googleapis.com/drive/v3/files?q={urllib.parse.quote(query)}&fields=files(id,name)"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {access_token}"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        files = data.get("files", [])
        if files:
            return files[0]["id"]

    body = json.dumps({
        "name": folder_name,
        "mimeType": "application/vnd.google-apps.folder",
        "parents": [parent_id]
    }).encode("utf-8")
    req_create = urllib.request.Request(
        "https://www.googleapis.com/drive/v3/files",
        data=body,
        headers={"Authorization": f"Bearer {access_token}", "Content-Type": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(req_create, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8")).get("id", "")

def drive_upload_file(access_token: str, local_path: Path, parent_folder_id: str) -> bool:
    """Upload simples ou resumível de arquivo para o Google Drive."""
    metadata = json.dumps({"name": local_path.name, "parents": [parent_folder_id]})
    size = local_path.stat().st_size

    # Inicia upload resumível
    init_req = urllib.request.Request(
        "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable",
        data=metadata.encode("utf-8"),
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json; charset=UTF-8",
            "X-Upload-Content-Length": str(size)
        },
        method="POST"
    )
    with urllib.request.urlopen(init_req, timeout=25) as resp:
        upload_url = resp.headers.get("Location")

    if not upload_url:
        return False

    with open(local_path, "rb") as f:
        put_req = urllib.request.Request(
            upload_url,
            data=f.read(),
            headers={"Content-Length": str(size)},
            method="PUT"
        )
        with urllib.request.urlopen(put_req, timeout=300) as resp:
            return resp.status in (200, 201)

def search_nyaa_torrents(anime_name: str, prefer_ptbr: bool = True) -> list:
    """Varre o feed RSS do Nyaa.si em busca do anime."""
    clean_q = re.sub(r'[^\w\s]', ' ', anime_name).strip()
    term = f"{clean_q} PT-BR" if prefer_ptbr else clean_q
    url = f"https://nyaa.si/?page=rss&q={urllib.parse.quote(term)}&c=1_2&f=0"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Makimono-RSS/1.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            xml_text = resp.read().decode("utf-8")
            items = re.findall(r'<item>(.*?)</item>', xml_text, re.DOTALL)
            results = []
            for item in items:
                title_match = re.search(r'<title><!\[CDATA\[(.*?)\]\]></title>', item) or re.search(r'<title>(.*?)</title>', item)
                link_match = re.search(r'<link>(.*?)</link>', item)
                if title_match and link_match:
                    t = title_match.group(1).strip()
                    l = link_match.group(1).strip()
                    results.append({"title": t, "magnet": l})
            if not results and prefer_ptbr:
                return search_nyaa_torrents(anime_name, prefer_ptbr=False)
            return results
    except Exception as e:
        print(f"⚠️ Erro ao consultar Nyaa: {e}", file=sys.stderr)
        return []

def download_with_aria2(magnet_or_torrent: str, output_dir: Path) -> bool:
    """Executa aria2c com 16 conexões e download sequencial de peças."""
    output_dir.mkdir(parents=True, exist_ok=True)
    cmd = [
        "aria2c",
        "--dir", str(output_dir),
        "--seed-time=0",
        "--summary-interval=5",
        "--max-connection-per-server=16",
        "--split=16",
        "--min-split-size=1M",
        "--torrent-sequential-download=true",
        magnet_or_torrent
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=900)
    return proc.returncode == 0

def process_job(job: dict):
    job_id = job.get("job_id")
    anime_query = job.get("anime_query", "").strip()
    ep_filter = str(job.get("episodes_filter", "all")).strip()
    prefer_ptbr = bool(job.get("prefer_ptbr", 1))

    print(f"\n📥 [NOVO PEDIDO DA TV] Job: {job_id} | Anime: '{anime_query}' | Eps: {ep_filter}")
    patch_job(job_id, "SEARCHING", progress_pct=10, detail=f"Buscando releases para '{anime_query}'...")

    # 1. Autenticação Drive
    access_token = get_drive_access_token()
    folder_id = None
    if access_token:
        root_id = drive_get_or_create_folder(access_token, "Animes", "root")
        folder_id = drive_get_or_create_folder(access_token, anime_query, root_id)

    # 2. Busca releases
    torrents = search_nyaa_torrents(anime_query, prefer_ptbr=prefer_ptbr)
    if not torrents:
        patch_job(job_id, "FAILED", detail=f"Nenhum release encontrado para '{anime_query}'.")
        print(f"❌ Nenhum release encontrado para '{anime_query}'.")
        return

    chosen = torrents[0]
    print(f"🎯 Release escolhido: {chosen['title']}")
    patch_job(job_id, "DOWNLOADING", progress_pct=25, folder_id=folder_id, detail=f"Baixando: {chosen['title'][:50]}...")

    # 3. Download via Aria2
    target_dir = STORAGE_DIR / re.sub(r'[\\/*?:"<>|]', "", anime_query).strip()
    ok = download_with_aria2(chosen["magnet"], target_dir)
    if not ok:
        patch_job(job_id, "FAILED", detail="Erro durante o download com Aria2.")
        return

    # 4. Upload para o Google Drive
    patch_job(job_id, "UPLOADING", progress_pct=80, folder_id=folder_id, detail="Enviando para o Google Drive...")
    uploaded = 0
    if access_token and folder_id:
        for f in target_dir.glob("*"):
            if f.is_file() and not f.name.endswith(".aria2"):
                success = drive_upload_file(access_token, f, folder_id)
                if success:
                    uploaded += 1

    msg = f"🎉 Concluído com sucesso! {uploaded} arquivo(s) salvos no Drive em Animes/{anime_query}/."
    patch_job(job_id, "COMPLETED", progress_pct=100, folder_id=folder_id, detail=msg)
    print(msg)

def main():
    STORAGE_DIR.mkdir(parents=True, exist_ok=True)
    print("=" * 65)
    print("⛩️  MAKIMONO AUTONOMOUS SEEDBOX DAEMON (24/7)")
    print(f"📡 Conectado ao Worker: {MAKIMONO_WORKER_URL}")
    print(f"🔑 Token: {MAKIMONO_SEEDBOX_TOKEN[:15]}...")
    print(f"💾 Espaço Livre: {get_disk_free_gb()} GB")
    print(f"⏱️ Intervalo de Poll: {POLL_INTERVAL}s")
    print("=" * 65)

    while True:
        try:
            free = get_disk_free_gb()
            res = api_request("/v1/seedbox/daemon/poll", method="POST", body={"free_disk_gb": free, "active_speed": "Idle"})
            job = res.get("job")
            if job:
                process_job(job)
            else:
                print(f"💓 [Daemon 24/7 Ativo] Aguardando pedidos da TV... ({free} GB livres)", end="\r")
        except urllib.error.HTTPError as he:
            print(f"\n⚠️ Erro HTTP Worker ({he.code}): {he.reason}")
        except Exception as e:
            print(f"\n⚠️ Exceção no loop: {e}")
        time.sleep(POLL_INTERVAL)

if __name__ == "__main__":
    main()
