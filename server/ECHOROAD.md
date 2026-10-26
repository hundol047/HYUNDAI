# EchoRoad gateway

Python 3 standard library. Run from the repository root:

```bash
python3 -m unittest discover -s server -p 'test_*.py'
python3 server/echoroad_gateway.py
```

Set `CABIN_ECHO_TOKENS_JSON` in the server's secret environment before starting. It is a JSON map from **unique random per-installation tokens (at least 24 ASCII characters)** to opaque installation IDs. Never use personal information as an ID, ship a shared token in the APK, or commit credentials. Token aliases for the same installation must map to the same ID. Missing/invalid configuration prevents startup. Generate tokens using a cryptographically secure generator and distribute them through an authenticated provisioning channel. This repository does not implement public registration or device attestation.

The service binds `127.0.0.1:8012` (override `CABIN_ECHO_PORT`). For Android connection, place it behind a trusted HTTPS reverse proxy with request/body/connection limits. Keep the upstream private. Disable access logs containing query coordinates or Authorization. The Android client rejects HTTP and redirects. The Python HTTP server is intended as the private upstream, not an Internet-facing TLS server. Railway deployment configuration is included; see the linked deployment guide.

Optional `CABIN_ACCIDENT_ZONES_JSON` points to a reviewed historical dataset imported with `scripts/fetch_accident_zones.py`. No sample or synthetic accident dataset is enabled. Invalid or expired input fails startup. Renew and restart before expiry. Data format and limitations: [Korean feature guide](../docs/WALKING_ECHOROAD_KO.md).

Protocol (all endpoints require `Authorization: Bearer <installation token>`):

- `POST /v1/observations`: JSON `id`, `lat`, `lon`, `heading`, `observedAtMs` (Unix milliseconds), `deceleration` (m/s²). Up to 2KB, age at most 30s, 30s between reports. Idempotent by installation + event ID. Acceptance does **not** mean the observation is published or proves an accident.
- `GET /v1/risks?lat=...&lon=...`: minimum 10s per installation; Android polls every 20s. Returns at most 50 corroborated clusters and 50 valid historical zones within 2km. No contributor IDs or tokens are returned. Empty results do not establish safety.

In-memory report lifetime: 15 minutes, including idle expiry. Restart clears reports. Limits: 10,000 observations, 5,000 configured token entries, 16 simultaneous connections. Invalid input, no authentication and rate limiting return 400, 401 and 429 respectively. Use an external process supervisor and metrics without raw location logging. Authentication alone cannot stop multiple maliciously provisioned identities; an operational identity issuance/revocation policy is required before public rollout.

Railway configuration and setup: [Korean deployment guide](../docs/RAILWAY_ECHOROAD_KO.md). The optional `CABIN_ECHO_HOST` overrides the local bind address, and Railway's `PORT` overrides `CABIN_ECHO_PORT`. `/healthz` returns a minimal unauthenticated process readiness response; risk queries remain authenticated. The Docker image sets the bind host to `0.0.0.0` for the platform's HTTPS proxy.

`GET /v1/status` requires Bearer authentication and checks credentials without receiving coordinates or inserting observations. Returns `status` and `accidentDatasetConnected`; HTTP 200 is authentication success, not proof of any live observations.
