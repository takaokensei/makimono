@echo off
python -c "import sys, re; [print(f'{i}: {line.strip()[:90]}') for i, line in enumerate(open(sys.argv[1], encoding='utf-8', errors='ignore'), 1) if 'android:textSize' in line]" %*
