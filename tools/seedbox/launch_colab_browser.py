"""
Inicia o navegador ou ativa a sessão do Google Colab para o Daemon Makimono.
Uso: python tools/seedbox/launch_colab_browser.py
"""

import sys
import webbrowser
import subprocess

COLAB_URL = "https://colab.research.google.com/drive/1AFK_iWrWR0JPq-TxNFUI_LTnX4y10jKE#scrollTo=3a6215f3"

def launch_colab():
    print("=" * 65)
    print("⛩️  MAKIMONO SEEDBOX — INICIALIZADOR DO GOOGLE COLAB")
    print(f"🔗 Abrindo Notebook: {COLAB_URL}")
    print("=" * 65)
    try:
        # Tenta abrir no Chrome se disponível para carregar a sessão autenticada com Google Colab Pro
        chrome_paths = [
            r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
            r"%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"
        ]
        opened = False
        for p in chrome_paths:
            expanded = os.path.expandvars(p)
            if os.path.exists(expanded):
                subprocess.Popen([expanded, COLAB_URL])
                opened = True
                print("🚀 Google Chrome aberto diretamente com a URL do Colab.")
                break
        
        if not opened:
            webbrowser.open(COLAB_URL)
            print("🚀 Navegador padrão aberto com a URL do Colab.")
    except Exception as e:
        print(f"⚠️ Erro ao abrir navegador: {e}")
        webbrowser.open(COLAB_URL)

if __name__ == "__main__":
    import os
    launch_colab()
