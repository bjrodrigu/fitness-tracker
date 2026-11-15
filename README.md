# Fitness Tracker

A prototype that pulls my runs from **Garmin Connect** and serves them through a small **Spring Boot + MongoDB Atlas** API with full-text search.

```
Garmin Connect ──► data_loader.py ──► JSON run records ──► POST /post ──► MongoDB Atlas
                    (garminconnect)    date, distance,                     │
                                       duration, pace                      ▼
                                                           GET /posts, GET /posts/{text}
                                                           (Atlas Search on pace)
```

## What's here

- **`data_loader.py`** logs in to Garmin Connect (OAuth tokens cached locally, MFA supported), pulls recent activities, keeps 2025 runs, and converts them to clean records: distance in miles, `h m s` duration, and `mm:ss/mi` pace. It also filters out bad data such as zero-distance runs and paces over 20 min/mi.
- **`activitypost/`** is a Spring Boot 3 REST API (Java 17) backed by MongoDB:
  - `GET /posts`: all runs
  - `POST /post`: add a run
  - `GET /posts/{text}`: Atlas Search `$search` aggregation on pace, sorted by distance
  - Swagger UI via springdoc at `/swagger-ui.html`

## Running it

```sh
# 1. Load runs from Garmin
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
EMAIL=you@example.com PASSWORD=... python data_loader.py

# 2. Start the API (needs a MongoDB Atlas cluster with a "default" search index)
cd activitypost
MONGODB_URI="mongodb+srv://<user>:<password>@<cluster>/" ./mvnw spring-boot:run
```

Instead of the env var, you can put `spring.data.mongodb.uri=...` in `activitypost/src/main/resources/application-secrets.properties`, which is gitignored.

## Status

Early prototype from summer 2025. Next steps were posting the loader's output straight to the API and adding a frontend for trends (weekly mileage, pace over time).
