from api.server import channel_matches, normalise, token_match


def test_aliases():
    assert normalise("Man Utd FC") == "manchester united"
    assert token_match("Manchester United", "EPL HUB: Man Utd") is False
    assert token_match("Arsenal", "iFollow Arsenal HD")
    assert not token_match("Leeds", "Leedsville")


def test_broadcaster_and_team_streams():
    fixture = {"home": "Arsenal", "away": "Chelsea",
               "broadcasters": ["Sky Sports Main Event"]}
    channels = [
        {"stream_id": "1", "name": "UK: Sky Sports Main Event"},
        {"stream_id": "2", "name": "EPL Hub Arsenal"},
        {"stream_id": "3", "name": "USA: Fox Sports"},
    ]
    matched = channel_matches(fixture, channels)
    assert [str(x["stream_id"]) for x in matched] == ["1", "2"]
    assert all(not x["confirmed_live"] for x in matched)


def test_no_false_positive():
    assert channel_matches({"home": "Leeds", "away": "Chelsea", "broadcasters": []},
                           [{"stream_id": "1", "name": "Leedsville News"}]) == []
