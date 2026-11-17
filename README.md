# Training Journal

A training log for running and lifting. Each run or strength session is a journal entry with notes, and the dashboard turns them into weekly mileage, pace trends, best efforts and lift progression. Runs can be imported from Garmin Connect.

![Training dashboard: summary numbers, an 8-week grid of daily miles with strength days marked, weekly mileage bars and an average pace line](docs/screenshots/training.png)

## Try it

You need Docker. No accounts or keys.

```sh
docker compose up --build
```

Open http://localhost:4000. On first start the API fills an empty database with about six months of generated training: a build to a half marathon, a recovery week, and a second build, with strength sessions twice a week. None of it is real data.

## What it does

- **Journal:** every entry, grouped by week with weekly totals. Filter by runs or strength, and search titles, notes, tags and exercise names.
- **Entries:** open one and edit it in place. Changes save as you type. Runs have distance, time, pace, heart rate and effort. Strength sessions have exercises with sets of reps and weight, and show each set's estimated max.
- **Training:** this week against recent weeks, an 8-week log of daily miles with lifting days marked, weekly mileage, average pace by week, and best efforts at 1 mile, 5K, 10K, half and full marathon.
- **Strength:** estimated one-rep max over time for each lift (Epley formula), with the top set from each session.
- **Garmin import:** pulls runs and strength sessions from Garmin Connect and upserts them by activity ID, so re-running it doesn't duplicate entries or overwrite your notes.

| Journal | Strength |
| --- | --- |
| ![Journal list grouped by week](docs/screenshots/journal.png) | ![Back squat estimated max over six months](docs/screenshots/strength.png) |

## How it's built

```
web/      React + TypeScript (Vite), Recharts. Served by nginx, which proxies /api.
api/      Spring Boot 3 (Java 17) REST API on MongoDB.
loader/   Python script that imports from Garmin Connect into the API.
```

```
Garmin Connect ──► loader/garmin_import.py ──► POST /api/activities/import ─┐
                                                                            ▼
Browser ──► nginx (web) ──► /api/* ──► Spring Boot ──► MongoDB (activities)
```

API endpoints (OpenAPI docs at http://localhost:4000/api/docs):

| Method | Path | |
| --- | --- | --- |
| GET | `/api/activities?type=&from=&to=&q=&tag=` | List entries, newest first. `q` searches titles, notes, tags and exercise names. |
| GET, PUT, DELETE | `/api/activities/{id}` | Read, replace or delete one entry |
| POST | `/api/activities` | Create an entry |
| POST | `/api/activities/import` | Bulk upsert by `externalId` (used by the Garmin loader) |
| GET | `/api/stats?weeks=16&days=140` | Weekly totals, best efforts, lift progression and daily miles |

Distances and times are stored as numbers (miles, seconds); pace and volume are worked out from them. Stats are computed in plain Java over the entry list (`StatsCalculator`), which keeps them easy to unit test.

## Importing from Garmin

With the app running:

```sh
cd loader
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python garmin_import.py --dry-run     # see what would be imported
python garmin_import.py --since 2026-01-01
```

It asks for your Garmin email, password and MFA code (or reads `GARMIN_EMAIL` and `GARMIN_PASSWORD`) and caches login tokens in `~/.garminconnect`. Only summary numbers are imported. GPS tracks, start locations and Garmin's activity names, which usually include the city, are left out. Strength sessions come in without sets, so you add those in the journal.

To keep your own entries out of the sample data, start with `DEMO_DATA=false` (see `.env.example`).

## Running the pieces separately

```sh
docker compose up mongo -d                  # or point MONGODB_URI at your own MongoDB
cd api && DEMO_DATA=true SPRING_DATA_MONGODB_URI=mongodb://localhost:27017 ./mvnw spring-boot:run
cd web && npm install && npm run dev       # http://localhost:5173, proxies /api to :8080
```

For local runs, expose Mongo with `ports: ["127.0.0.1:27017:27017"]` in a `docker-compose.override.yml`. To use MongoDB Atlas, set `MONGODB_URI` in `.env` for Compose, or `SPRING_DATA_MONGODB_URI` when running the API directly. An `application-secrets.properties` file next to `application.properties` also works and is gitignored. It's excluded from the Docker build.

## Tests

```sh
cd api && ./mvnw verify          # unit tests + API tests against MongoDB via Testcontainers (needs Docker)
cd web && npm test
cd loader && pytest
```

CI runs all three plus a `docker compose up` smoke test.

## Background

This started in summer 2025 as a script that pulled my runs from Garmin and a small Spring Boot API with Atlas Search on top. In 2026 I rebuilt it into a journal: numeric run data instead of formatted strings, strength sessions, a frontend, sample data, Docker Compose and tests.
