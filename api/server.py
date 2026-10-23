"""Dream Football API: fixtures, optional broadcaster listings, IPTV channel matching."""
import os
import re
import time
from datetime import datetime, timezone
from difflib import SequenceMatcher

import requests
from bs4 import BeautifulSoup
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="Dream Football API", version="0.2.0")
SPORTSDB = "https://www.thesportsdb.com/api/v1/json/123/eventsnextleague.php?id="
LEAGUES = {"4328": "Premier League", "4329": "Championship",
           "4330": "League One", "4331": "League Two"}
CACHE_SECONDS = 1800
_cache = {"at": 0.0, "fixtures": []}
SESSION = requests.Session()
SESSION.headers.update({"User-Agent": "DreamFootballAPI/0.2"})
# A licensed, JSON-compatible broadcast feed may be configured later.
BROADCAST_FEED_URL = os.getenv("DREAM_BROADCAST_FEED_URL", "")


class Channel(BaseModel):
    stream_id: str | int
    name: str
    category_name: str = ""


class Fixture(BaseModel):
    id: str = ""
    home: str
    away: str
    kickoff_utc: str = ""
    league: str = ""
    broadcasters: list[str] = Field(default_factory=list)


class MatchRequest(BaseModel):
    fixture: Fixture
    channels: list[Channel] = Field(max_length=30000)


ALIASES = {
    "man utd": "manchester united", "man united": "manchester united",
    "man city": "manchester city", "spurs": "tottenham",
    "wolves": "wolverhampton", "brighton hove albion": "brighton",
    "nottm forest": "nottingham forest", "west brom": "west bromwich albion",
    "qpr": "queens park rangers", "sheff utd": "sheffield united",
    "sheff wed": "sheffield wednesday", "boro": "middlesbrough",
}


def normalise(value: str) -> str:
    value = re.sub(r"[^a-z0-9]+", " ", value.lower())
    value = re.sub(r"\\b(fc|afc|football club|hd|uhd|4k|uk|tv)\\b", " ", value)
    value = " ".join(value.split())
    return ALIASES.get(value, value)


def token_match(team: str, channel: str) -> bool:
    team = normalise(team)
    channel = normalise(channel)
    if len(team) < 4:
        return False
    return bool(re.search(r"(?<![a-z0-9])" + re.escape(team) + r"(?![a-z0-9])", channel))


def channel_matches(fixture: dict, channels: list[dict]) -> list[dict]:
    broadcaster_names = [normalise(x) for x in fixture.get("broadcasters", [])]
    output = []
    seen = set()
    for channel in channels:
        name = channel.get("name", "")
        stream_id = str(channel.get("stream_id", ""))
        if not name or not stream_id or stream_id in seen:
            continue
        norm = normalise(name)
        broadcaster = any(len(b) >= 5 and
                          (token_match(b, norm) or token_match(norm, b))
                          for b in broadcaster_names)
        team = any(token_match(fixture.get(side, ""), name) for side in ("home", "away"))
        if not broadcaster and not team:
            continue
        seen.add(stream_id)
        output.append({**channel,
                       "match_type": "broadcaster" if broadcaster else "team_stream",
                       "confirmed_live": False})
    return sorted(output, key=lambda item: (item["match_type"] != "broadcaster", item["name"]))


def load_fixtures() -> list[dict]:
    if time.monotonic() - _cache["at"] < CACHE_SECONDS:
        return _cache["fixtures"]
    fixtures = []
    for league_id, league in LEAGUES.items():
        try:
            response = SESSION.get(SPORTSDB + league_id, timeout=12)
            response.raise_for_status()
            for event in response.json().get("events") or []:
                if not event.get("strHomeTeam") or not event.get("strAwayTeam"):
                    continue
                fixtures.append({
                    "id": str(event.get("idEvent", "")),
                    "home": event["strHomeTeam"],
                    "away": event["strAwayTeam"],
                    "kickoff_utc": ((event.get("dateEvent") or "") + "T" +
                                    (event.get("strTime") or "00:00:00") + "Z"),
                    "league": league,
                    "broadcasters": [],
                })
        except (requests.RequestException, ValueError, KeyError):
            continue
    if not fixtures and not _cache["fixtures"]:
        raise HTTPException(503, "Fixture provider unavailable")
    if fixtures:
        fixtures.sort(key=lambda item: item["kickoff_utc"])
        _cache.update(at=time.monotonic(), fixtures=fixtures)
    return _cache["fixtures"]


@app.get("/health")
def health():
    return {"ok": True, "version": "0.2.0",
            "fixture_cache_count": len(_cache["fixtures"]),
            "broadcast_feed_configured": bool(BROADCAST_FEED_URL)}


@app.get("/api/football/fixtures")
def fixtures():
    return {"fixtures": load_fixtures(), "source": "TheSportsDB",
            "broadcast_status": "not_configured" if not BROADCAST_FEED_URL else "configured"}


@app.get("/api/football/listings")
def listings():
    """Compatibility endpoint. Broadcast listings require an authorised JSON feed."""
    data = load_fixtures()
    if not BROADCAST_FEED_URL:
        return {"matches": data, "source": "TheSportsDB",
                "broadcast_status": "not_configured"}
    try:
        response = SESSION.get(BROADCAST_FEED_URL, timeout=12)
        response.raise_for_status()
        broadcast_data = response.json()
        mapping = {str(row["id"]): row.get("broadcasters", [])
                   for row in broadcast_data.get("matches", []) if row.get("id")}
    except (requests.RequestException, ValueError, KeyError, TypeError):
        return {"matches": data, "source": "TheSportsDB",
                "broadcast_status": "unavailable"}
    return {"matches": [{**fixture,
                         "broadcasters": mapping.get(fixture["id"], [])}
                        for fixture in data],
            "source": "TheSportsDB + configured broadcast feed",
            "broadcast_status": "available"}


@app.post("/api/football/match-channels")
def match_channels(payload: MatchRequest):
    fixture = payload.fixture.model_dump()
    channels = [channel.model_dump() for channel in payload.channels]
    return {"channels": channel_matches(fixture, channels),
            "note": "Channel name matches are suggestions; live carriage is unverified."}
