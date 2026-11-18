# Development

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

CI runs all three plus a `docker compose up` smoke test that signs in to the demo through nginx.

The model eval (sample messages run through a real local model) is skipped unless `PARSE_EVAL=1`; see [Describe it](setup.md#describe-it-local-model).

## Layout

```
api/src/main/java/com/fitnesstracker/journal/
  activity/   runs and strength sessions          nutrition/  food log, weights, goal, progress
  auth/       sign-up, sessions, CSRF, throttle    foods/      USDA and Open Food Facts lookups
  garmin/     linking, encrypted tokens, sync      parse/      Describe it: model + draft builder
  stats/      weekly totals, best efforts, lifts   demo/       generated demo data
  user/       accounts                             config/     index migrations
web/src/
  pages/      one file per screen                  components/ layout, charts, sheets, scanner
  lib/        API client, auth, types, formatting
loader/       garmin-sync service and the one-off import script
docs/         these notes and the screenshots
```
