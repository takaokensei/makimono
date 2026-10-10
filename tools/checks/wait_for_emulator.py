import subprocess
import time
import sys

print("Aguardando emulador 'emulator-5554' ficar online...", flush=True)
start_time = time.time()
timeout = 180  # 3 minutos max

while time.time() - start_time < timeout:
    try:
        res = subprocess.run(
            ["adb", "-s", "emulator-5554", "get-state"],
            capture_output=True,
            text=True,
            timeout=5
        )
        state = res.stdout.strip()
        if state == "device":
            print(f"Emulador online! Verificando boot_completed...", flush=True)
            res_boot = subprocess.run(
                ["adb", "-s", "emulator-5554", "shell", "getprop", "sys.boot_completed"],
                capture_output=True,
                text=True,
                timeout=5
            )
            boot = res_boot.stdout.strip()
            if boot == "1":
                print(f"Emulador totalmente inicializado com sucesso em {time.time() - start_time:.1f}s!", flush=True)
                sys.exit(0)
            else:
                print(f"Boot em andamento (sys.boot_completed='{boot}'). Aguardando...", flush=True)
        else:
            print(f"Estado do emulador: '{state}' ({time.time() - start_time:.1f}s decorridos)...", flush=True)
    except Exception as e:
        print(f"Polling erro: {e}", flush=True)
    time.sleep(5)

print("Timeout aguardando inicializacao do emulador.", flush=True)
sys.exit(1)
