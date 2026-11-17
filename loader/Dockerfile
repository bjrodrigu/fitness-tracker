# Garmin sync service (sync_server.py). Internal only: docker compose doesn't publish its port.
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY garmin_import.py sync_server.py ./
RUN useradd --create-home garmin
USER garmin
ENV PYTHONUNBUFFERED=1
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD python -c "import urllib.request;urllib.request.urlopen('http://localhost:8000/health')"
CMD ["python", "sync_server.py"]
