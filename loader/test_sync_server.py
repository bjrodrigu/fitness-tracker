import datetime as dt

import pytest

import sync_server
from sync_server import HttpError


class FakeGarth:
    def __init__(self, token):
        self.token = token

    def dumps(self):
        return self.token


class FakeGarmin:
    """Stands in for garminconnect.Garmin."""

    activities = []
    needs_mfa = False
    login_error = None
    seen = {}

    def __init__(self, email=None, password=None, **kwargs):
        self.garth = FakeGarth("token-after-login")

    def login(self, tokenstore=None):
        FakeGarmin.seen["tokenstore"] = tokenstore
        if FakeGarmin.login_error:
            raise FakeGarmin.login_error
        if tokenstore is None and FakeGarmin.needs_mfa:
            return "needs_mfa", {"state": 1}
        return None, None

    def resume_login(self, state, code):
        if code != "123456":
            raise GarminConnectAuthenticationError("bad code")
        self.garth = FakeGarth("token-after-code")

    def get_activities_by_date(self, start, end):
        FakeGarmin.seen["range"] = (start, end)
        return FakeGarmin.activities


class GarminConnectAuthenticationError(Exception):
    pass


@pytest.fixture(autouse=True)
def reset(monkeypatch):
    FakeGarmin.activities = []
    FakeGarmin.needs_mfa = False
    FakeGarmin.login_error = None
    FakeGarmin.seen = {}
    monkeypatch.setattr(sync_server, "_garmin_class", lambda: FakeGarmin)
    sync_server._pending.clear()


def run(activity_id, type_key="running", meters=8046.72, seconds=2700):
    return {"activityId": activity_id, "activityType": {"typeKey": type_key}, "startTimeLocal": "2026-11-10 06:15:00",
            "distance": meters, "duration": seconds, "startLatitude": 1.0}


def test_link_returns_token():
    assert sync_server.link({"email": "a@b.co", "password": "pw"}) == {"status": "linked", "token": "token-after-login"}


def test_link_with_code():
    FakeGarmin.needs_mfa = True
    first = sync_server.link({"email": "a@b.co", "password": "pw"})
    assert first["status"] == "needs_code"
    with pytest.raises(HttpError) as wrong:
        sync_server.finish_link({"mfaId": first["mfaId"], "code": "000000"})
    assert wrong.value.status == 401
    # A wrong code uses up the request; the user starts again.
    assert first["mfaId"] not in sync_server._pending


def test_link_with_right_code():
    FakeGarmin.needs_mfa = True
    first = sync_server.link({"email": "a@b.co", "password": "pw"})
    done = sync_server.finish_link({"mfaId": first["mfaId"], "code": " 123456 "})
    assert done == {"status": "linked", "token": "token-after-code"}


def test_bad_login_is_401_without_details():
    FakeGarmin.login_error = GarminConnectAuthenticationError("401 for url https://sso.garmin.com/?password=pw")
    with pytest.raises(HttpError) as e:
        sync_server.link({"email": "a@b.co", "password": "pw"})
    assert e.value.status == 401
    assert "pw" not in e.value.message


def test_fetch_converts_and_filters():
    FakeGarmin.activities = [run(1), run(2, type_key="cycling"), run(3, meters=0)]
    out = sync_server.fetch({"token": "t" * 600, "since": "2026-11-01"}, today=dt.date(2026, 11, 17))
    assert [e["externalId"] for e in out["entries"]] == ["garmin:1"]
    assert "startLatitude" not in out["entries"][0]
    assert out["token"] == "token-after-login"
    assert FakeGarmin.seen["range"] == ("2026-11-01", "2026-11-18")


def test_fetch_validates_input():
    with pytest.raises(HttpError):
        sync_server.fetch({"since": "2026-11-01"})
    with pytest.raises(HttpError):
        sync_server.fetch({"token": "t", "since": "yesterday"})
