#!/usr/bin/env python3
"""
pip3 install -r requirements.txt

export EMAIL=<your garmin email>
export PASSWORD=<your garmin password>

"""
import datetime
from datetime import timezone
import json
import logging
import os
import sys
from getpass import getpass

import requests
from garth.exc import GarthHTTPError

from garminconnect import (
    Garmin,
    GarminConnectAuthenticationError,
    GarminConnectConnectionError,
    GarminConnectTooManyRequestsError,
)

email = os.getenv("EMAIL")
password = os.getenv("PASSWORD")
tokenstore = os.getenv("GARMINTOKENS") or "~/.garminconnect"
tokenstore_base64 = os.getenv("GARMINTOKENS_BASE64") or "~/.garminconnect_base64"

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def get_mfa():
    """Ask for the one-time code when the Garmin account has MFA enabled."""
    return input("MFA one-time code: ")
api = None

def display_json(api_call, output):
    """Format API output for better readability."""

    dashed = "-" * 20
    header = f"{dashed} {api_call} {dashed}"
    footer = "-" * len(header)

    print(header)

    if isinstance(output, (int, str, dict, list)):
        print(json.dumps(output, indent=4))
    else:
        print(output)

    print(footer)

def display_text(output):
    """Format API output for better readability."""

    dashed = "-" * 60
    header = f"{dashed}"
    footer = "-" * len(header)

    print(header)
    print(json.dumps(output, indent=4))
    print(footer)

def get_credentials():
    """Get user credentials."""

    email = input("Login e-mail: ")
    password = getpass("Enter password: ")

    return email, password

def init_api(email, password):
    """Initialize Garmin API with your credentials."""

    try:
        # Using Oauth1 and OAuth2 token files from directory
        print(
            f"Trying to login to Garmin Connect using token data from directory '{tokenstore}'...\n"
        )
        garmin = Garmin()
        garmin.login(tokenstore)

    except (FileNotFoundError, GarthHTTPError, GarminConnectAuthenticationError):
        # Session is expired. You'll need to log in again
        print(
            "Login tokens not present, login with your Garmin Connect credentials to generate them.\n"
            f"They will be stored in '{tokenstore}' for future use.\n"
        )
        try:
            # Ask for credentials if not set as environment variables
            if not email or not password:
                email, password = get_credentials()

            garmin = Garmin(
                email=email, password=password, is_cn=False, return_on_mfa=True
            )
            result1, result2 = garmin.login()
            if result1 == "needs_mfa":  # MFA is required
                mfa_code = get_mfa()
                garmin.resume_login(result2, mfa_code)

            # Save Oauth1 and Oauth2 token files to directory for next login
            garmin.garth.dump(tokenstore)
            print(
                f"Oauth tokens stored in '{tokenstore}' directory for future use. (first method)\n"
            )

            # Encode Oauth1 and Oauth2 tokens to base64 string and safe to file for next login (alternative way)
            token_base64 = garmin.garth.dumps()
            dir_path = os.path.expanduser(tokenstore_base64)
            with open(dir_path, "w") as token_file:
                token_file.write(token_base64)
            print(
                f"Oauth tokens encoded as base64 string and saved to '{dir_path}' file for future use. (second method)\n"
            )

            # Re-login Garmin API with tokens
            garmin.login(tokenstore)
        except (
            FileNotFoundError,
            GarthHTTPError,
            GarminConnectAuthenticationError,
            requests.exceptions.HTTPError,
        ) as err:
            logger.error(err)
            return None

    return garmin

def fetch_recent_runs(api, limit=10):
    if not api:
        print("Client not initialized.")
        return []
    
    results = []

    try:
        activities = api.get_activities(0, limit)
        runs = [
            act for act in activities
            if "running" in act.get("activityName", "").lower()
        ]

        for run in runs:
            run_date = datetime.datetime.strptime(run["startTimeLocal"], "%Y-%m-%d %H:%M:%S")
            if run_date.year < 2025:
                continue
            dist_in_mi = run["distance"] * 0.000621371

            # Convert duration from minutes to hours and minutes
            duration = datetime.timedelta(seconds=run["duration"])
            hours, remainder = divmod(duration.seconds, 3600)
            minutes, seconds = divmod(remainder, 60)
            duration_str = f"{hours}h {minutes}m {seconds}s"

            if run["distance"] <= 0:
                continue
            pace = run["duration"] / (run["distance"] * 0.000621371)
            pace_minutes = int(pace // 60)
            if pace_minutes > 20:
                continue
            pace_seconds = int(pace % 60)
            # Format pace as mm:ss/mi
            if pace_seconds < 10:
                pace_str = f"{pace_minutes}:0{pace_seconds}/mi"
            else:
                pace_str = f"{pace_minutes}:{pace_seconds}/mi"

            #print(f"Date: {run['startTimeLocal']}, Distance: {dist_in_mi:.2f} mi, Duration: {duration_str}, Pace: {pace_str}")

            run_data = {
                "activityName": "running",
                "date": run_date.strftime("%Y-%m-%d"),
                "time": run_date.strftime("%H:%M:%S"),
                "distance": round(dist_in_mi, 2),
                "duration": duration_str,
                "pace": pace_str
            }
            results.append(run_data)
     

        print(json.dumps(results, indent=1))
        return results

    except Exception as e:
        print(f"Error fetching runs: {e}")
        return []

while True:
    # Init API
    if not api:
        api = init_api(email, password)

    if api:
        print("Logged in successfully.")
        fetch_recent_runs(api, limit=10000)
        break
    else:
        api = init_api(email, password)