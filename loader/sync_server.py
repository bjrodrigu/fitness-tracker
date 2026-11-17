#!/usr/bin/env python3
"""Garmin sync service: the journal API calls this to talk to Garmin Connect.

It runs inside Docker Compose with no published port, so only the API can reach it. It keeps
nothing on disk. Garmin passwords pass through once, to get a login token, and are never stored
or logged; the token goes back to the API, which stores it encrypted.

    POST /link       {email, password}  -> {status: "linked", token} or {status: "needs_code", mfaId}
    POST /link/code  {mfaId, code}      -> {status: "linked", token}
    POST /fetch      {token, since}     -> {entries: [...], token}
    GET  /health

Errors come back as {error} with 401 when Garmin rejects the login or token, 429 when Garmin
asks us to slow down, and 502 for anything else on Garmin's side.
"""
from __future__ import annotations

import datetime as dt
import json
import logging
import os
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from garmin_import import to_entry

log = logging.getLogger("garmin-sync")

CODE_TTL_SEC = 10 * 60
_pending: dict[str, tuple[float, object, object]] = {}  # mfaId -> (expires, client, state)
_lock = threading.Lock()


class HttpError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


def _garmin_class():
    # Imported lazily so tests can run without the Garmin packages.
    from garminconnect import Garmin

    return Garmin


def _translate(e: Exception) -> HttpError:
    """Map Garmin library errors to HTTP errors without leaking details."""
    name = type(e).__name__
    status = getattr(getattr(e, "error", None), "response", None)
    code = getattr(status, "status_code", None)
    if "TooManyRequests" in name or code == 429:
        return HttpError(429, "Garmin asked us to slow down. Try again in a few minutes.")
    # garth reports a sign-in page it didn't expect (wrong password, locked account) this way.
    if "Authentication" in name or code in (401, 403) or "Unexpected title" in str(e):
        return HttpError(401, "Garmin didn't accept that login.")
    return HttpError(502, "Garmin Connect didn't answer. Try again later.")


def link(body: dict, garmin_class=None) -> dict:
    email, password = body.get("email"), body.get("password")
    if not email or not password:
        raise HttpError(400, "Email and password are required.")
    Garmin = garmin_class or _garmin_class()
    client = Garmin(email=email, password=password, is_cn=False, return_on_mfa=True)
    try:
        status, state = client.login()
    except Exception as e:  # noqa: BLE001 - every Garmin failure becomes an HTTP error
        raise _translate(e) from None
    if status == "needs_mfa":
        mfa_id = secrets.token_urlsafe(24)
        with _lock:
            _expire_pending()
            _pending[mfa_id] = (time.monotonic() + CODE_TTL_SEC, client, state)
        return {"status": "needs_code", "mfaId": mfa_id}
    return {"status": "linked", "token": client.garth.dumps()}


def finish_link(body: dict) -> dict:
    with _lock:
        _expire_pending()
        entry = _pending.pop(body.get("mfaId") or "", None)
    if entry is None:
        raise HttpError(401, "That code request expired. Enter your Garmin email and password again.")
    _, client, state = entry
    try:
        client.resume_login(state, str(body.get("code", "")).strip())
    except Exception as e:  # noqa: BLE001
        err = _translate(e)
        if err.status == 401:
            err.message = "Garmin didn't accept that code. Enter your email and password again."
        raise err from None
    return {"status": "linked", "token": client.garth.dumps()}


def fetch(body: dict, garmin_class=None, today: dt.date | None = None) -> dict:
    token = body.get("token")
    if not token:
        raise HttpError(400, "token is required.")
    try:
        since = dt.date.fromisoformat(body.get("since", ""))
    except ValueError:
        raise HttpError(400, "since must be a date like 2026-01-31.") from None
    Garmin = garmin_class or _garmin_class()
    client = Garmin()
    end = (today or dt.date.today()) + dt.timedelta(days=1)
    try:
        client.login(token)
        raw = client.get_activities_by_date(since.isoformat(), end.isoformat())
    except Exception as e:  # noqa: BLE001
        raise _translate(e) from None
    entries = [e for e in (to_entry(r) for r in raw) if e]
    return {"entries": entries, "token": client.garth.dumps()}


def _expire_pending() -> None:
    now = time.monotonic()
    for k in [k for k, (exp, _, _) in _pending.items() if exp < now]:
        del _pending[k]


ROUTES = {"/link": link, "/link/code": finish_link, "/fetch": fetch}


class Handler(BaseHTTPRequestHandler):
    server_version = "garmin-sync"

    def do_GET(self):  # noqa: N802
        if self.path == "/health":
            self._send(200, {"ok": True})
        else:
            self._send(404, {"error": "Not found"})

    def do_POST(self):  # noqa: N802
        route = ROUTES.get(self.path)
        if route is None:
            self._send(404, {"error": "Not found"})
            return
        try:
            length = int(self.headers.get("Content-Length") or 0)
            body = json.loads(self.rfile.read(length) or b"{}")
            self._send(200, route(body))
        except HttpError as e:
            log.info("%s -> %s", self.path, e.status)
            self._send(e.status, {"error": e.message})
        except json.JSONDecodeError:
            self._send(400, {"error": "Body must be JSON."})
        except Exception:  # noqa: BLE001
            log.exception("%s failed", self.path)
            self._send(500, {"error": "Sync service error."})

    def _send(self, status: int, payload: dict) -> None:
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):  # request lines only; bodies are never logged
        log.debug(fmt, *args)


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    port = int(os.getenv("PORT", "8000"))
    log.info("Listening on :%d", port)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()
