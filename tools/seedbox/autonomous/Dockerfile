FROM python:3.11-slim

# Instala aria2 e ca-certificates
RUN apt-get update && \
    apt-get install -y --no-install-recommends aria2 ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY makimono_autonomous_daemon.py /app/

ENV PYTHONUNBUFFERED=1

CMD ["python", "makimono_autonomous_daemon.py"]
