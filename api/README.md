# Dream Football Listings API (prototype)

This is a **self-hosted prototype**, not a deployed service. It does not connect to the Android app yet.

## Setup

```bash
pip install -r api/requirements.txt
uvicorn api.server:app --host 0.0.0.0 --port 8000
```

Endpoints:
- `GET /health`
- `GET /api/football/listings` — cached LiveOnSat UK listings, best-effort HTML extraction
- `POST /api/football/match-channels` — JSON `{"fixture":{"home":"Arsenal","away":"Chelsea","broadcasters":["Sky Sports Main Event"]},"channels":[{"stream_id":"123","name":"UK: Sky Sports Main Event"}]}`

## Important limitations

- LiveOnSat has no verified official public API or reuse permission. Confirm permitted automated access and republication **before deploying** the fetcher; prefer a licensed feed.
- The HTML parser is a placeholder based on generic selectors and **has not been validated against the current site markup**. It may return zero matches. Do not treat it as a working production integration.
- The API is not hosted and the Android app still uses its existing fixture/EPG flow.
- Never send IPTV account credentials to this API. Send only the minimum channel IDs and display names needed for matching.
- Broadcast listings change. A channel-name match does not confirm a live stream or rights availability.
