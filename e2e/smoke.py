#!/usr/bin/env python3
"""End-to-end smoke test for the Hermes Mobile gateway path.

Connects to the hermes-mobile WebSocket (through the public tunnel or a local
proxy), authenticates, sends a canary message, and asserts the agent replies
with the canary. Exits non-zero on any failure.

The device id must already be paired (e.g. the home device "phone-1"); an
unpaired id returns a pairing code instead of a reply, and this test will then
fail with "no canary".

Token is never hardcoded: read from $MOBILE_TOKEN, else MOBILE_TOKEN= in
~/.hermes/.env.

Usage:
    python3 e2e/smoke.py [ws_url] [device_id]
"""
import asyncio
import json
import os
import pathlib
import re
import sys

import websockets

CANARY = "e2e-canary"
DEFAULT_URL = "wss://animal-exile-tusk.ngrok-free.dev/ws"
DEFAULT_DEVICE = "phone-1"


def load_token() -> str:
    env = os.environ.get("MOBILE_TOKEN", "").strip()
    if env:
        return env
    env_file = pathlib.Path.home() / ".hermes" / ".env"
    if env_file.is_file():
        for line in env_file.read_text().splitlines():
            m = re.match(r"^\s*MOBILE_TOKEN\s*=\s*(\S+)\s*$", line)
            if m:
                return m.group(1)
    raise SystemExit("FAIL: MOBILE_TOKEN not set and not found in ~/.hermes/.env")


async def main(url: str, device_id: str) -> None:
    token = load_token()
    async with websockets.connect(url, open_timeout=20) as ws:
        await ws.send(json.dumps({
            "t": "hello", "token": token,
            "device": {"id": device_id, "name": "e2e smoke test"},
        }))
        ready = await asyncio.wait_for(ws.recv(), timeout=20)
        assert '"t":"ready"' in ready, f"no ready frame: {ready[:160]}"
        print("ready received")

        await ws.send(json.dumps({
            "t": "text", "id": "e2e-1", "text": f"reply with exactly: {CANARY}",
        }))

        for _ in range(120):
            try:
                raw = await asyncio.wait_for(ws.recv(), timeout=30)
            except asyncio.TimeoutError:
                raise SystemExit("FAIL: no reply within timeout")
            try:
                frame = json.loads(raw)
            except ValueError:
                continue
            if frame.get("t") == "message" and frame.get("final"):
                text = str(frame.get("text") or "")
                print("final reply:", text)
                if CANARY in text:
                    print("PASS: round-trip completed with canary")
                    return
                raise SystemExit(f"FAIL: final reply lacked canary {CANARY!r}: {text!r}")
        raise SystemExit("FAIL: no final message received")


if __name__ == "__main__":
    url = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_URL
    device = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_DEVICE
    asyncio.run(main(url, device))
