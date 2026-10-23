# Dream Football API v0.2 (self-hosted prototype)

A standalone FastAPI service. It is **not deployed** or connected to the Android app.

## Start

```bash
pip install -r api/requirements.txt
uvicorn api.server:app --host 0.0.0.0 --port 8000
```

## Routes

- `GET /health`: health and broadcast-feed status.
- `GET /api/football/fixtures`: Premier League, Championship, League One and League Two fixtures via TheSportsDB.
- `GET /api/football/listings`: fixtures with broadcaster names if a separate authorised JSON broadcast feed is configured.
- `POST /api/football/match-channels`: suggest channels matching broadcasters or home/away club names.

Example:

```bash
curl -X POST http://localhost:8000/api/football/match-channels \
  -H 'Content-Type: application/json' \
  -d '{"fixture":{"home":"Arsenal","away":"Chelsea","broadcasters":["Sky Sports Main Event"]},"channels":[{"stream_id":"123","name":"UK: Sky Sports Main Event"},{"stream_id":"456","name":"EPL Hub Arsenal"}]}'
```

## Broadcast listings

LiveOnSat does not have a verified public API or confirmed automated-reuse permission. This service **does not scrape LiveOnSat**. For now, `broadcast_status` is `not_configured`. After securing permission or a licensed feed, set `DREAM_BROADCAST_FEED_URL` to a JSON endpoint returning `{"matches":[{"id":"<TheSportsDB event id>","broadcasters":["Sky Sports Main Event"]}]}`.

## Important

The Android app is not yet connected. Do not send Xtream passwords or playlist URLs to this backend; submit only channel names and stream IDs for matching. The matching algorithm is heuristic and does not confirm actual broadcast availability. Protect the API with authentication, TLS, rate limits and deployment monitoring before exposing it publicly.
