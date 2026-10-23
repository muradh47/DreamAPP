"""Dream UK football listings adapter.

Run: pip install -r api/requirements.txt && uvicorn api.server:app --host 0.0.0.0 --port 8000
Source: LiveOnSat public UK listings. Respect source permissions and request limits.
"""
import re
import time
from datetime import datetime, timezone
from difflib import SequenceMatcher
from functools import lru_cache
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from fastapi import FastAPI, HTTPException, Query

app = FastAPI(title="Dream Football Listings", version="0.1.0")
SOURCE = "https://www.liveonsat.com/uk-england-all-football.php"
CACHE_SECONDS = 1800
_cached = {"at": 0, "matches": []}
HEADERS = {"User-Agent": "DreamFootballListings/0.1 (+contact site owner for data permission)"}


def normalise(value):
    value = re.sub(r"\\b(fc|afc|football club|hd|uhd|4k|uk|tv)\\b", " ", value.lower())
    return re.sub(r"[^a-z0-9]+", " ", value).strip()


def channel_matches(listing, channels):
    """Return IPTV channels matching a broadcaster or a fixture's team-named hub."""
    names = [listing.get("home", ""), listing.get("away", "")]
    broadcasters = [normalise(x) for x in listing.get("broadcasters", [])]
    matched = []
    for channel in channels:
        name = normalise(channel.get("name", ""))
        if not name:
            continue
        broadcast = any(b and (b in name or name in b) and min(len(b), len(name)) >= 5 for b in broadcasters)
        team = any(normalise(t) in name and len(normalise(t)) >= 5 for t in names)
        if broadcast or team:
            matched.append({**channel, "match_type": "broadcaster" if broadcast else "team_stream",
                            "confirmed_live": False})
    return matched


def parse_liveonsat(html):
    """Best-effort parser; upstream markup may change. Never invent channels."""
    soup = BeautifulSoup(html, "html.parser")
    results = []
    # LiveOnSat event titles appear in fixture rows, with broadcaster lines following.
    # Restrict extraction to event containers to avoid assigning broadcasters across fixtures.
    for node in soup.select(".match, .fixture, .event, .block, .div_event"):
        text = node.get_text(" ", strip=True)
        matchup = re.search(r"([A-Za-z][A-Za-z .&'’-]{2,55})\\s+v(?:s|ersus)?\\.?\\s+([A-Za-z][A-Za-z .&'’-]{2,55})", text)
        if not matchup:
            continue
        home, away = (x.strip() for x in matchup.groups())
        broadcasters = []
        for el in node.select("a, li, .channel, .broadcaster"):
            name = el.get_text(" ", strip=True)
            if name and name not in (home, away) and 3 <= len(name) <= 85:
                broadcasters.append(name)
        results.append({"home": home, "away": away,
                        "broadcasters": list(dict.fromkeys(broadcasters)),
                        "source": SOURCE, "kickoff": None})
    return results


@app.get("/health")
def health():
    return {"ok": True, "source": SOURCE, "cached_matches": len(_cached["matches"])}


@app.get("/api/football/listings")
def listings():
    if time.monotonic() - _cached["at"] < CACHE_SECONDS:
        return {"matches": _cached["matches"], "source": SOURCE, "cached": True}
    try:
        response = requests.get(SOURCE, headers=HEADERS, timeout=15)
        response.raise_for_status()
        matches = parse_liveonsat(response.text)
    except requests.RequestException as exc:
        if _cached["at"]:
            return {"matches": _cached["matches"], "source": SOURCE, "stale": True}
        raise HTTPException(503, "LiveOnSat listing source unavailable") from exc
    _cached.update(at=time.monotonic(), matches=matches)
    return {"matches": matches, "source": SOURCE, "cached": False,
            "warning": "Source markup may change; verify listings before relying on them."}


@app.post("/api/football/match-channels")
def match_channels(payload: dict):
    fixture = payload.get("fixture", {})
    channels = payload.get("channels", [])
    if not isinstance(channels, list) or len(channels) > 30000:
        raise HTTPException(400, "Invalid channels list")
    return {"channels": channel_matches(fixture, channels),
            "note": "Broadcaster and team-name matches are suggestions, not confirmed streams."}
