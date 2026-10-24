#!/usr/bin/env python3
"""Optional authenticated place gateway. Provider keys never leave this server.

Default: Nominatim-compatible world search. With a Kakao Local key, explicit
nearby searches in Korea use Kakao; worldwide queries continue using Nominatim.
Run behind HTTPS. No accounts, API subscriptions or paid services are enabled here.
"""
import dataclasses
import hmac
import json
import math
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class GatewayError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


@dataclasses.dataclass(frozen=True)
class Config:
    access_token: str
    kakao_key: str = ""
    allowed_origins: tuple = ("http://127.0.0.1:4173", "http://localhost:4173")

    def __post_init__(self):
        if not 12 <= len(self.access_token) <= 256 or not all("!" <= c <= "~" for c in self.access_token):
            raise ValueError("Configure a private 12–256 character CABIN_GATEWAY_TOKEN.")

    @classmethod
    def from_environment(cls):
        origins = tuple(x.strip() for x in os.environ.get("CABIN_ALLOWED_ORIGINS", "http://127.0.0.1:4173,http://localhost:4173").split(",") if x.strip())
        if "*" in origins:
            raise ValueError("Set explicit browser origins, not a wildcard.")
        return cls(os.environ.get("CABIN_GATEWAY_TOKEN", ""), os.environ.get("CABIN_KAKAO_REST_KEY", ""), origins)


class Gateway:
    def __init__(self, config, opener=urllib.request.urlopen, interval=1.5):
        self.config, self.opener, self.interval = config, opener, interval
        self.lock = threading.Lock()
        self.slots = threading.BoundedSemaphore(2)
        self.cache = {}
        self.last_request = 0.0

    def prune_expired(self):
        now = time.monotonic()
        with self.lock:
            for key in list(self.cache):
                if now - self.cache[key][0] >= 60:
                    del self.cache[key]

    def fetch(self, host, path, params, headers):
        # Hosts are fixed in code; users cannot turn this into an arbitrary proxy.
        url = "https://" + host + path + "?" + urllib.parse.urlencode(params)
        request = urllib.request.Request(url, headers={"User-Agent": "CabinOS_ONE/0.7.0 (+https://github.com/hundol047/HYUNDAI)", "Accept": "application/json", **headers})
        with self.lock:
            wait = self.interval - (time.monotonic() - self.last_request)
            if wait > 0:
                time.sleep(wait)
            self.last_request = time.monotonic()
        try:
            with self.opener(request, timeout=12) as response:
                data = response.read(2_000_001)
                if len(data) > 2_000_000:
                    raise GatewayError(502, "Provider response is too large.")
                return json.loads(data)
        except (OSError, ValueError, urllib.error.HTTPError):
            # Never echo URLs, provider errors, headers or secret values.
            raise GatewayError(502, "Place provider unavailable. Check the server configuration.") from None

    @staticmethod
    def parameters(raw):
        q = raw.get("q", "").strip()
        if not 2 <= len(q) <= 180 or any(ord(c) < 32 for c in q):
            raise GatewayError(400, "Enter a place name, branch, city or address (2–180 characters).")
        excluded = [x for x in raw.get("exclude_ids", "").split(",") if x]
        if len(excluded) > 18 or any(len(x) > 160 or not all(c.isalnum() or c in "._:-" for c in x) for x in excluded):
            raise GatewayError(400, "Invalid excluded place identifiers.")
        near = raw.get("bounded") == "1"
        lat = lon = None
        if near:
            try:
                lat, lon = float(raw["lat"]), float(raw["lon"])
                if not math.isfinite(lat) or not math.isfinite(lon) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
                    raise ValueError()
                if not 500 <= float(raw.get("radius", "20000")) <= 20000:
                    raise ValueError()
                box = [float(x) for x in raw["viewbox"].split(",")]
                if len(box) != 4 or not all(math.isfinite(x) for x in box) or not -180 <= box[0] <= box[2] <= 180 or not -90 <= box[3] <= box[1] <= 90:
                    raise ValueError()
            except (KeyError, ValueError, TypeError):
                raise GatewayError(400, "Nearby search requires a valid location and viewbox.") from None
        language = raw.get("accept-language", "ko,en")
        if len(language) > 96 or not re.fullmatch(r"[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*(?:,[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*)?", language):
            raise GatewayError(400, "Invalid search language.")
        return q, excluded, near, lat, lon, language

    def search(self, raw):
        q, excluded, near, lat, lon, language = self.parameters(raw)
        kakao = bool(self.config.kakao_key and near and 33 <= lat <= 39 and 124 <= lon <= 132)
        key = json.dumps([kakao, q, raw.get("viewbox"), lat, lon, raw.get("radius", "20000"), excluded, language], ensure_ascii=False)
        with self.lock:
            cached = self.cache.get(key)
            if cached and time.monotonic() - cached[0] < 60:
                return cached[1]
        if not self.slots.acquire(blocking=False):
            raise GatewayError(429, "Search is busy; try again shortly.")
        try:
            if kakao:
                rows = []
                # A client page has six rows; Kakao pages have fifteen. Fill a
                # client page across the boundary rather than stopping at row 15.
                for page in range(min(3, 1 + len(excluded) // 15), 4):
                    body = self.fetch("dapi.kakao.com", "/v2/local/search/keyword.json", {"query": q, "x": lon, "y": lat, "radius": int(float(raw.get("radius", "20000"))), "sort": "distance", "size": 15, "page": page}, {"Authorization": "KakaoAK " + self.config.kakao_key})
                    if not isinstance(body, dict) or not isinstance(body.get("documents"), list):
                        raise GatewayError(502, "Invalid place provider response.")
                    for p in body["documents"]:
                        try:
                            y, x = float(p["y"]), float(p["x"])
                            if not math.isfinite(y) or not math.isfinite(x) or not -90 <= y <= 90 or not -180 <= x <= 180:
                                continue
                            identity = "kakao:" + str(p["id"])
                            if identity in excluded or any(r["place_id"] == identity for r in rows) or not p.get("place_name"):
                                continue
                            address = p.get("road_address_name") or p.get("address_name") or ""
                            link = p.get("place_url", "")
                            if link.startswith("http://place.map.kakao.com/"):
                                link = "https://" + link[len("http://"):]
                            rows.append({"place_id": identity, "name": p["place_name"], "display_name": address, "lat": str(y), "lon": str(x), "type": p.get("category_name", ""), "source": "Kakao Local", "extratags": {"phone": p.get("phone", ""), "website": link}})
                        except (KeyError, ValueError, TypeError):
                            continue
                    if len(rows) >= 6 or body.get("meta", {}).get("is_end", len(body["documents"]) < 15):
                        break
            if not kakao or not rows:
                # The coarse Korean bounds include neighbouring coastlines. An
                # empty local-provider result must not disable world search.
                params = {k: raw[k] for k in ("viewbox", "bounded", "exclude_place_ids") if k in raw}
                if "exclude_place_ids" in params and not all(x.isdigit() for x in params["exclude_place_ids"].split(",")):
                    raise GatewayError(400, "Invalid Nominatim identifiers.")
                params.update(q=q, format="jsonv2", limit=6, addressdetails=1, namedetails=1, extratags=1, **{"accept-language": language})
                rows = self.fetch("nominatim.openstreetmap.org", "/search", params, {})
                if not isinstance(rows, list):
                    raise GatewayError(502, "Invalid place provider response.")
                rows = [{**p, "source": "OSM / Nominatim"} for p in rows if isinstance(p, dict)]
            rows = rows[:6]
            with self.lock:
                if len(self.cache) >= 128:
                    self.cache.pop(next(iter(self.cache)))
                self.cache[key] = (time.monotonic(), rows)
            return rows
        finally:
            self.slots.release()


def handler_for(gateway):
    class Handler(BaseHTTPRequestHandler):
        server_version = "CabinOSPlaceGateway"

        def log_message(self, *_):
            pass  # No location/query/token access log.

        def send_json(self, status, body):
            encoded = json.dumps(body, ensure_ascii=False).encode()
            self.send_response(status)
            origin = self.headers.get("Origin")
            if origin in gateway.config.allowed_origins:
                self.send_header("Access-Control-Allow-Origin", origin)
                self.send_header("Vary", "Origin")
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(encoded)

        def do_OPTIONS(self):
            if self.headers.get("Origin") not in gateway.config.allowed_origins:
                return self.send_json(403, {"error": "Origin not allowed."})
            self.send_response(204)
            self.send_header("Access-Control-Allow-Origin", self.headers["Origin"])
            self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Authorization, Accept, Accept-Language")
            self.send_header("Content-Length", "0")
            self.end_headers()

        def do_GET(self):
            path = urllib.parse.urlsplit(self.path)
            if path.path == "/healthz":
                return self.send_json(200, {"status": "running", "kakaoConfigured": bool(gateway.config.kakao_key), "liveProviderVerification": "not established by health check"})
            if path.path != "/search":
                return self.send_json(404, {"error": "Not found."})
            if self.headers.get("Origin") and self.headers["Origin"] not in gateway.config.allowed_origins:
                return self.send_json(403, {"error": "Origin not allowed."})
            auth = self.headers.get("Authorization", "")
            if not hmac.compare_digest(auth.encode(), ("Bearer " + gateway.config.access_token).encode()):
                return self.send_json(401, {"error": "Gateway authentication required."})
            if len(self.path) > 6000:
                return self.send_json(400, {"error": "Request is too large."})
            try:
                params = {k: v[-1] for k, v in urllib.parse.parse_qs(path.query).items()}
                self.send_json(200, gateway.search(params))
            except GatewayError as e:
                self.send_json(e.status, {"error": e.message})
            except Exception:
                self.send_json(500, {"error": "Place search failed."})
    return Handler


class GatewayServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, gateway):
        self.gateway = gateway
        super().__init__(address, handler_for(gateway))

    def service_actions(self):
        self.gateway.prune_expired()


if __name__ == "__main__":
    config = Config.from_environment()
    server = GatewayServer((os.environ.get("CABIN_GATEWAY_HOST", "127.0.0.1"), int(os.environ.get("CABIN_GATEWAY_PORT", "8008"))), Gateway(config))
    print("Place gateway started; use an HTTPS reverse proxy for Android/browser clients.", flush=True)
    server.serve_forever()
