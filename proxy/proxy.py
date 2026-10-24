"""Single-origin server: serves the mobile web client at / and proxies /ws to the
hermes-mobile WebSocket on 127.0.0.1:8787. One ngrok tunnel -> this port."""
import asyncio
import pathlib

from aiohttp import web, WSMsgType, ClientSession

UPSTREAM_WS = "ws://127.0.0.1:8787/ws"
HTML = pathlib.Path(__file__).resolve().parent / "index.html"
HOST = "127.0.0.1"
PORT = 8080
HEARTBEAT_SECONDS = 10.0  # ping the client when idle so ngrok never drops the tunnel


async def handle_index(request):
    if not HTML.exists():
        return web.Response(text="client not found", status=404)
    return web.FileResponse(HTML)


async def handle_ws(request):
    ws_client = web.WebSocketResponse(autoping=True, heartbeat=HEARTBEAT_SECONDS)
    await ws_client.prepare(request)

    async with ClientSession() as session:
        try:
            async with session.ws_connect(UPSTREAM_WS, autoping=True) as ws_up:
                async def client_to_up():
                    async for msg in ws_client:
                        if msg.type == WSMsgType.TEXT:
                            await ws_up.send_str(msg.data)
                        elif msg.type == WSMsgType.BINARY:
                            await ws_up.send_bytes(msg.data)
                        elif msg.type in (WSMsgType.CLOSE, WSMsgType.ERROR):
                            break

                async def up_to_client():
                    async for msg in ws_up:
                        if msg.type == WSMsgType.TEXT:
                            await ws_client.send_str(msg.data)
                        elif msg.type == WSMsgType.BINARY:
                            await ws_client.send_bytes(msg.data)
                        elif msg.type == WSMsgType.CLOSE:
                            await ws_client.close(code=msg.data, message=msg.extra)
                            break
                        elif msg.type == WSMsgType.ERROR:
                            break

                await asyncio.gather(client_to_up(), up_to_client())
        except Exception:
            pass
    return ws_client


app = web.Application()
app.router.add_get("/", handle_index)
app.router.add_get("/index.html", handle_index)
app.router.add_get("/ws", handle_ws)

if __name__ == "__main__":
    web.run_app(app, host=HOST, port=PORT, print=None)
