# Setup and configuration

Everything here is optional: `docker compose up --build` runs the journal with the demo and no keys. Settings go in a `.env` file next to `docker-compose.yml` (copy `.env.example`); it's gitignored.

| Variable | Default | What it does |
| --- | --- | --- |
| `DEMO_DATA` | `true` | Read-only demo account with generated data |
| `MONGODB_URI`, `MONGODB_DATABASE` | local container, `training_journal` | Use your own MongoDB (e.g. Atlas) |
| `TZ` | `UTC` | Time zone for "today" and week boundaries |
| `WEB_PORT` | `4000` | Port on this machine |
| `GARMIN_TOKEN_KEY` | (off) | Turns on Garmin linking; encrypts saved Garmin tokens |
| `GARMIN_SYNC_EVERY` | `PT6H` | How often linked accounts sync |
| `FDC_API_KEY` | `DEMO_KEY` | USDA food search key; without one, a few searches an hour |
| `FOOD_COUNTRY` | `united-states` | Which country's packaged foods search returns first |
| `PARSE_PROVIDER` | `ollama` | Model for Describe it: `ollama`, `anthropic` or `off` |
| `OLLAMA_URL`, `OLLAMA_MODEL` | host, `qwen2.5:3b` | Where the local model is and which one |
| `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` | (none), `claude-opus-5-5` | Claude, when `PARSE_PROVIDER=anthropic` |

## Garmin sync

Garmin has no public API for personal accounts, so the `garmin-sync` service signs in the way the Garmin Connect app does (with the [garminconnect](https://github.com/cyberjunky/python-garminconnect) library) and returns Garmin's login token to the API.

1. Set `GARMIN_TOKEN_KEY` in `.env` (`openssl rand -base64 32`). Linking is off without it.
2. In the app, open Settings and enter your Garmin email and password, plus the code Garmin sends if you use two-step sign-in.
3. The first sync reads the past year. After that the API syncs every linked account every 6 hours (`GARMIN_SYNC_EVERY`).

How the login is handled:

- The Garmin password goes to Garmin once and is never stored or logged.
- Garmin's token is stored encrypted with AES-256-GCM. The key lives only in the environment, and each token is tied to its account, so a token copied onto another account won't decrypt.
- `garmin-sync` has no published port. Only the API can reach it, inside the Docker network.
- If Garmin rejects the token later, syncing stops for that account and Settings asks you to link again.

Only summary numbers are imported. GPS tracks, start locations and Garmin's activity names, which usually include the city, are left out. Strength sessions come in without sets, so you add those in the journal.

For a one-off import from the command line, `loader/garmin_import.py --dry-run` prints what it would import, and without `--dry-run` it signs in to the journal and sends it.

## Food search and barcodes

The API looks foods up in two free databases and caches answers for a day:

- **USDA FoodData Central** for whole foods ("banana", "chicken breast"), with household portions like "1 medium (118 g)". It needs a free key from [api.data.gov](https://api.data.gov/signup) in `FDC_API_KEY`; without one it allows only a few searches an hour.
- **Open Food Facts** for packaged foods and barcodes. No key needed. `FOOD_COUNTRY` (default `united-states`) picks which country's products search returns.

Barcode scanning uses the browser's BarcodeDetector where there is one (Chrome on Android) and the [ZXing](https://github.com/zxing-js/browser) library otherwise (Safari on iPhone), loaded only when the camera opens. Browsers only allow the camera on https or localhost, so on a phone open the app through its Tailscale https address. You can always type the numbers under the barcode instead.

## Describe it (local model)

A language model reads the message, but only to pull out the facts: what kind of thing it is, the numbers, the exercise and food names. Everything after that is plain, tested code ([`DraftBuilder.java`](../api/src/main/java/com/fitnesstracker/journal/parse/DraftBuilder.java)):

- Exercise names are matched to the ones you already use, so "bench" lands on "Bench press" and your Strength history doesn't split in two.
- Sets go into today's lifting session if there is one. A correction finds the set or run it's about.
- A run Garmin already synced gets your notes instead of being logged twice.
- Each ingredient of a meal is looked up in the food databases above, and its macros worked out from the grams. Ingredients it can't find are flagged for you to search or remove.
- Numbers a small model is prone to make up (a run time the message never gave) are dropped.

By default the model is [Ollama](https://ollama.com) running `qwen2.5:3b` (a 1.9 GB download) on the same computer, outside Docker so it can use the GPU on a Mac:

```sh
brew install ollama && brew services start ollama
ollama pull qwen2.5:3b
```

On an 8 GB M1 a message takes about 5 seconds, longer for the first one after a quiet spell while the model loads. If the journal is hosted somewhere without room for a local model, set `PARSE_PROVIDER=anthropic` and `ANTHROPIC_API_KEY` to use Claude instead (`ANTHROPIC_MODEL` picks which; the default is `claude-opus-5-5`). `PARSE_PROVIDER=off` turns it off, and the page says so.

To see how well a model reads messages, run the sample messages through it (16 cases: runs, sets in lb and kg, corrections, meals, mixed messages):

```sh
cd api && PARSE_EVAL=1 ./mvnw test -Dtest=ParseEvalTest                         # qwen2.5:3b: 15 of 16
cd api && PARSE_EVAL=1 OLLAMA_MODEL=llama3.2:3b ./mvnw test -Dtest=ParseEvalTest  # compare another model
```

## Using it on your phone

The app is a normal website, so a phone needs a way to reach the computer running it. [Tailscale](https://tailscale.com) is the simplest private option. Install it on the computer and the phone, sign in to the same account on both, then on the computer run:

```sh
tailscale serve --bg 4000
```

That gives the app an HTTPS address on your tailnet, such as `https://your-mac.your-tailnet.ts.net`, which only your own devices can open. Open it in Safari or Chrome on the phone and choose Add to Home Screen.
