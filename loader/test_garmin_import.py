from garmin_import import to_entry


def garmin(type_key="running", meters=8046.72, seconds=2700, **extra):
    raw = {
        "activityId": 123,
        "activityType": {"typeKey": type_key},
        "activityName": "Somewhere City Running",
        "startTimeLocal": "2026-11-10 06:15:00",
        "distance": meters,
        "duration": seconds,
        "averageHR": 147.6,
        "startLatitude": 1.0,
        "startLongitude": 2.0,
    }
    raw.update(extra)
    return raw


def test_run_is_converted_to_miles_and_seconds():
    e = to_entry(garmin())
    assert e["type"] == "RUN"
    assert e["date"] == "2026-11-10"
    assert e["startTime"] == "06:15:00"
    assert e["distanceMi"] == 5.0
    assert e["durationSec"] == 2700
    assert e["avgHeartRate"] == 148
    assert e["externalId"] == "garmin:123"
    assert e["title"] == "Run 5.0 mi"


def test_location_data_and_garmin_names_are_left_out():
    e = to_entry(garmin())
    text = repr(e)
    assert "Somewhere" not in text
    assert "Latitude" not in text and "Longitude" not in text


def test_treadmill_and_trail_get_tags():
    assert to_entry(garmin("treadmill_running"))["tags"] == ["treadmill"]
    assert to_entry(garmin("trail_running"))["tags"] == ["trail"]


def test_bad_data_is_skipped():
    assert to_entry(garmin(meters=0)) is None
    assert to_entry(garmin(seconds=0)) is None
    # 30 min/mi: a walk or a watch left running
    assert to_entry(garmin(meters=1609.344, seconds=1800)) is None


def test_strength_sessions_are_imported_without_sets():
    e = to_entry(garmin("strength_training", meters=0, seconds=3300))
    assert e["type"] == "STRENGTH"
    assert e["durationSec"] == 3300
    assert "distanceMi" not in e


def test_other_activities_are_ignored():
    assert to_entry(garmin("cycling")) is None
    assert to_entry(garmin("soccer")) is None
