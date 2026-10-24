"""Unit tests for the single-origin proxy (proxy/proxy.py).

Runs the proxy app against a fake upstream WS echo server and asserts that
client frames are proxied upstream and replies are proxied back, and that the
web client HTML is served at /. Run with:

    python3 proxy/test_proxy.py

Exits non-zero if any check fails. Uses plain asyncio.run (the unittest
IsolatedAsyncioTestCase harness hangs with aiohttp's event loop on Python 3.14).
"""
import asyncio
import importlib.util
import pathlib
import sys

from aiohttp import ClientSession, WSMsgType, web

PROXY_PY = pathlib.Path(__file__).resolve().parent / "proxy.py"


def _load_proxy():
    spec = importlib.util.spec_from_file_location("hermes_proxy", PROXY_PY)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


async def _run() -> int:
    proxy = _load_proxy()

    # Fake upstream: echoes text prefixed with "echo:".
    upstream = web.Application()

    async def echo(request):
        ws = web.WebSocketResponse(autoping=True)
        await ws.prepare(request)
        async for msg in ws:
            if msg.type == WSMsgType.TEXT:
                await ws.send_str("echo:" + msg.data)
            elif msg.type in (WSMsgType.CLOSE, WSMsgType.ERROR):
                break
        return ws

    upstream.router.add_get("/ws", echo)
    up_runner = web.AppRunner(upstream)
    await up_runner.setup()
    up_site = web.TCPSite(up_runner, "127.0.0.1", 0)
    await up_site.start()
    up_port = up_site._server.sockets[0].getsockname()[1]
    proxy.UPSTREAM_WS = f"ws://127.0.0.1:{up_port}/ws"

    proxy_runner = web.AppRunner(proxy.app)
    await proxy_runner.setup()
    proxy_site = web.TCPSite(proxy_runner, "127.0.0.1", 0)
    await proxy_site.start()
    proxy_port = proxy_site._server.sockets[0].getsockname()[1]

    failures = []

    async def check(name, ok, detail=""):
        if ok:
            print(f"  PASS  {name}")
        else:
            print(f"  FAIL  {name}  {detail}")
            failures.append(name)

    # 1. serves the client HTML at /
    try:
        async with ClientSession() as s:
            async with s.get(f"http://127.0.0.1:{proxy_port}/") as resp:
                body = await resp.text()
                await check(
                    "serves client HTML",
                    resp.status == 200 and "Hermes Mobile" in body,
                    f"status={resp.status}",
                )
    except Exception as exc:  # noqa: BLE001
        await check("serves client HTML", False, repr(exc))

    # 2. proxies a client frame upstream and the reply back
    try:
        async with ClientSession() as s:
            async with s.ws_connect(f"http://127.0.0.1:{proxy_port}/ws") as ws:
                await ws.send_str("ping-from-client")
                msg = await asyncio.wait_for(ws.receive(), timeout=5)
                await check(
                    "proxies client<->upstream",
                    msg.type == WSMsgType.TEXT and msg.data == "echo:ping-from-client",
                    f"got {msg.type}:{getattr(msg, 'data', '')}",
                )
    except Exception as exc:  # noqa: BLE001
        await check("proxies client<->upstream", False, repr(exc))

    if failures:
        print(f"RESULT: {len(failures)} failure(s)")
    else:
        print("RESULT: all checks passed")

    # Best-effort cleanup with a short timeout: the proxy's WS handler may still
    # hold its upstream connection open, and AppRunner.cleanup() blocks waiting
    # for it. A test script doesn't need a graceful server shutdown.
    for runner in (proxy_runner, up_runner):
        try:
            await asyncio.wait_for(runner.cleanup(), timeout=2)
        except Exception:
            pass

    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(_run()))
