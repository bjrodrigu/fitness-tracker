#!/usr/bin/env python3
"""Import runs and strength sessions from Garmin Connect into the training journal.

The journal also syncs Garmin on a schedule once you link your account under Settings; this
script is for a one-off import from the command line.

    pip install -r requirements.txt
    python garmin_import.py --dry-run          # print what would be imported
    python garmin_import.py                    # send to http://localhost:4000/api

Journal login: set JOURNAL_EMAIL and JOURNAL_PASSWORD, or you'll be prompted.

Garmin login: set GARMIN_EMAIL and GARMIN_PASSWORD, or you'll be prompted. MFA codes are
prompted for. After the first login, OAuth tokens are cached in ~/.garminconnect and reused.

Only summary numbers are imported: date, start time, distance, duration, average heart
rate. GPS tracks, locations and Garmin's activity names (which usually include the city)
are left out. Re-running is safe: entries are matched on the Garmin activity ID, and notes
or titles you've edited in the journal are kept.
"""
from __future__ import annotations

import argparse
import datetime as dt
import getpass
import json
import os
import sys

METERS_PER_MILE = 1609.344
MAX_PACE_SEC_PER_MI = 20 * 60  # slower than this is a walk or a paused watch

RUN_TYPES = {"running", "treadmill_running", "trail_running", "track_running", "indoor_running", "street_running"}
STRENGTH_TYPES = {"strength_training"}


def to_entry(raw: dict) -> dict | None:
    """Convert one Garmin activity summary into a journal entry, or None to skip it."""
    type_key = (raw.get("activityType") or {}).get("typeKey", "")
    start = raw.get("startTimeLocal")
    if not start:
        return None
    started = dt.datetime.strptime(start, "%Y-%m-%d %H:%M:%S")
    duration = raw.get("duration") or 0
    entry = {
        "date": started.date().isoformat(),
        "startTime": started.strftime("%H:%M:%S"),
        "durationSec": round(duration) if duration > 0 else None,
        "externalId": f"garmin:{raw['activityId']}",
        "source": "garmin",
        "tags": [],
    }

    if type_key in RUN_TYPES:
        meters = raw.get("distance") or 0
        if meters <= 0 or duration <= 0:
            return None
        miles = meters / METERS_PER_MILE
        if duration / miles > MAX_PACE_SEC_PER_MI:
            return None
        hr = raw.get("averageHR")
        entry.update(
            type="RUN",
            title=f"{'Treadmill run' if type_key == 'treadmill_running' else 'Run'} {miles:.1f} mi",
            distanceMi=round(miles, 2),
            avgHeartRate=round(hr) if hr else None,
        )
        if type_key == "treadmill_running":
            entry["tags"] = ["treadmill"]
        elif type_key == "trail_running":
            entry["tags"] = ["trail"]
        return entry

    if type_key in STRENGTH_TYPES:
        entry.update(type="STRENGTH", title="Strength session")
        return entry

    return None


def login(email: str | None, password: str | None):
    # Imported here so the conversion code can be tested without the Garmin packages.
    from garminconnect import Garmin, GarminConnectAuthenticationError
    from garth.exc import GarthHTTPError

    tokenstore = os.path.expanduser(os.getenv("GARMINTOKENS", "~/.garminconnect"))
    try:
        garmin = Garmin()
        garmin.login(tokenstore)
        return garmin
    except (FileNotFoundError, GarthHTTPError, GarminConnectAuthenticationError):
        pass

    email = email or input("Garmin email: ")
    password = password or getpass.getpass("Garmin password: ")
    garmin = Garmin(email=email, password=password, is_cn=False, return_on_mfa=True)
    status, state = garmin.login()
    if status == "needs_mfa":
        garmin.resume_login(state, input("MFA code: "))
    garmin.garth.dump(tokenstore)
    print(f"Saved login tokens to {tokenstore}", file=sys.stderr)
    return garmin


def fetch(garmin, since: dt.date, limit: int) -> list[dict]:
    entries = []
    for raw in garmin.get_activities(0, limit):
        entry = to_entry(raw)
        if entry and entry["date"] >= since.isoformat():
            entries.append(entry)
    return entries


def send(entries: list[dict], api: str) -> dict:
    import requests

    base = api.rstrip("/")
    session = requests.Session()
    session.get(f"{base}/auth/config", timeout=30)  # sets the XSRF-TOKEN cookie

    def csrf() -> dict:
        return {"X-XSRF-TOKEN": session.cookies.get("XSRF-TOKEN", "")}

    email = os.getenv("JOURNAL_EMAIL") or input("Journal email: ")
    password = os.getenv("JOURNAL_PASSWORD") or getpass.getpass("Journal password: ")
    login = session.post(f"{base}/auth/login", json={"email": email, "password": password}, headers=csrf(), timeout=30)
    if login.status_code != 200:
        raise SystemExit(f"Journal login failed ({login.status_code}).")
    res = session.post(f"{base}/activities/import", json=entries, headers=csrf(), timeout=60)
    res.raise_for_status()
    return res.json()


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--since", type=dt.date.fromisoformat, default=dt.date.today() - dt.timedelta(days=365),
                   help="only import activities on or after this date (default: one year ago)")
    p.add_argument("--limit", type=int, default=1000, help="how many recent Garmin activities to look through")
    p.add_argument("--api", default=os.getenv("JOURNAL_API", "http://localhost:4000/api"),
                   help="journal API base URL (default: %(default)s)")
    p.add_argument("--dry-run", action="store_true", help="print entries as JSON instead of sending them")
    args = p.parse_args(argv)

    garmin = login(os.getenv("GARMIN_EMAIL") or os.getenv("EMAIL"), os.getenv("GARMIN_PASSWORD") or os.getenv("PASSWORD"))
    entries = fetch(garmin, args.since, args.limit)
    if args.dry_run:
        print(json.dumps(entries, indent=2))
        return 0
    result = send(entries, args.api)
    print(f"Imported {len(entries)} activities: {result['created']} new, {result['updated']} updated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
