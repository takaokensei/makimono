FROM python:3.11-slim

# Instala ferramentas essenciais do sistema (aria2, ffmpeg, curl)
RUN apt-get update && \
    apt-get install -y --no-install-recommends aria2 ffmpeg curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Instala dependências Python do motor v12
RUN pip install --no-cache-dir \
    requests \
    beautifulsoup4 \
    urllib3 \
    google-api-python-client \
    google-auth \
    google-auth-oauthlib \
    google-auth-httplib2 \
    gdown \
    anitopy \
    psutil

WORKDIR /app

# Copia o motor canônico v12 (5.400+ linhas) e o runner autônomo
COPY darkmahou_engine_core.py /app/
COPY notebook_engine_runner.py /app/

ENV PYTHONUNBUFFERED=1
ENV ORACLE_KEEPALIVE=true

CMD ["python", "notebook_engine_runner.py"]
