# API

The web app is a client of this API; nothing it does is off limits to a script. OpenAPI docs are served at http://localhost:4000/api/docs.

| Method | Path | |
| --- | --- | --- |
| POST | `/api/auth/signup`, `/api/auth/login`, `/api/auth/logout`, `/api/auth/demo` | Accounts and sessions |
| GET | `/api/auth/me` | The signed-in account, or 401 |
| GET, POST, DELETE | `/api/garmin`, `/api/garmin/link`, `/api/garmin/link/code`, `/api/garmin/sync` | Garmin status, linking (with Garmin's emailed code), sync now, unlink |
| GET | `/api/activities?type=&from=&to=&q=&tag=` | List entries, newest first. `q` searches titles, notes, tags and exercise names. |
| GET, PUT, DELETE | `/api/activities/{id}` | Read, replace or delete one entry |
| POST | `/api/activities` | Create an entry |
| POST | `/api/activities/import` | Bulk upsert by `externalId` (used by the import script) |
| GET | `/api/stats?weeks=16&days=140` | Weekly totals, best efforts, lift progression and daily miles |
| GET, POST, PUT, DELETE | `/api/food?date=`, `/api/food/{id}`, `/api/food/recent` | Food log for a day, edit one food, recently eaten foods |
| PUT, DELETE | `/api/weights/{date}` | One weigh-in per day |
| GET | `/api/nutrition?days=56` | Daily totals, weight with 7-day average, goal and progress |
| PUT, POST | `/api/nutrition/goal`, `/api/nutrition/suggest` | Save the goal; work out suggested targets |
| GET | `/api/foods/search?q=`, `/api/foods/barcode/{code}` | Search USDA and Open Food Facts; look up a barcode |
| POST, GET | `/api/parse`, `/api/parse/status` | Turn a message into draft entries (saves nothing); which model is on |

Every endpoint except `/api/auth/*` needs a signed-in session, and entries are always filtered by the session's account. Distances and times are stored as numbers (miles, seconds); pace and volume are worked out from them. Stats, nutrition progress and the goal calculator are plain Java over lists (`StatsCalculator`, `Progress`, `GoalCalculator`), which keeps them easy to unit test.

## Accounts and sessions

- Passwords are hashed with bcrypt. After 8 wrong passwords, that email is locked for 15 minutes.
- Sessions are HttpOnly cookies stored in MongoDB (Spring Session) and last 30 days, so a phone stays signed in.
- Every POST, PUT and DELETE needs a CSRF token (double-submit cookie).
- The demo account can read but not write.
- Entries saved before accounts existed belong to the first account created on that database.

Calling it from a script: GET `/api/auth/config` to get the `XSRF-TOKEN` cookie, then send it back as the `X-XSRF-TOKEN` header on POST, PUT and DELETE, with the session cookie from `/api/auth/login`. `loader/garmin_import.py` does exactly this.
