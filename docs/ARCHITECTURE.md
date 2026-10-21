# CabinOS ONE architecture

`core` is a pure Kotlin/JVM domain module. `app` is a native Android application using Compose, Navigation Compose, lifecycle-aware StateFlow collection, Hilt, Coroutines and Room. Android framework integrations live behind provider interfaces. No cloud AI or backend is required to run the demo.

```mermaid
flowchart TD
  Vehicle[VehicleDataProvider / simulation telemetry] --> Engine[UnifiedVehicleContextEngine]
  Decision[SafetyDecisionEngine] --> Engine
  Hazards[RoadHazardRepository + HazardRanker] --> Engine
  Activities[ActivityRecommendationRepository] --> Engine
  Engine --> State[One StateFlow of AppState]
  State --> Echo[EchoRoad driving dashboard]
  State --> Stop[StopSense schedule + recommendations]
  State --> Cabin[CabinOS sessions + tasks + audio]
  State --> Persistence[Room checkpoint / history / preferences]
  Commands[Korean deterministic assistant] --> Engine
```

## Safety and lifecycle

- Safety precedence: missing/disconnected/stale/invalid telemetry → UNKNOWN; any positive speed → DRIVING; zero speed in D/R → STOPPED_IN_TRAFFIC; reliable P with zero speed must be stable for 1.5 seconds before unlocking parked features. Lost telemetry, source changes and large clock gaps require fresh confirmation. A manual voice command cannot establish physical parking.
- CHARGING, RESTING and PREPARING_DEPARTURE are contextual refinements of a confirmed physical parked state. They never override motion or a loss of trust.
- A provider sends timestamped samples. Samples older than 5 seconds, future timestamps, negative speeds and NaN are rejected conservatively. The current app heartbeat is explicitly simulation telemetry; actual telemetry is **not** synthesized in real mode.
- Every mutation runs on Android Main. Coroutine collectors observe the same authoritative StateFlow. There are no independent StopSense/CabinOS departure timers.
- Parking debounce is a timestamped candidate, not a UI toggle. Sensor recovery must pass it again. There is no automatic session resumption after drive interruption.
- Running sessions use an absolute deadline bounded by `departure - preparation buffer - return time`. A shorter charge estimate truncates the deadline; a longer one does not silently extend the chosen session. Activity recommendations account for the complete round trip. Selecting a place reserves the return trip for an already-outside user. Starting an in-car session removes that return reservation.
- Manual departure overrides are marked user supplied; future charging updates use the earlier of the manual and charging deadlines. A repeated unchanged charging estimate does not slide departure forward.
- On process recreation Room restores settings, deadlines and progress, **never** the physical parked state. A running session restores paused. Timers use elapsed wall-clock deadlines; a clock jump fails closed until parking is re-established. They are not intended for exact alarms or background vehicle monitoring.

## EchoRoad

Hazards contain no vehicle identifier, use pseudonymous event IDs and expire. Relevance considers route ID, along-route distance, heading, confidence, age, severity and independent-report count. Duplicate IDs, expired events, past-route events and low-confidence events are removed. Alerts have a global cooldown and per-event suppression. Location denial removes route alerts. Route geometry and points of interest in the demo are illustrative, not actual navigation or verified businesses. Consent is stored locally; no sharing backend or external data transmission is implemented.

## Persistence

Room stores typed preferences, sessions, a checkpoint and user tasks. Logical state writes are transactional. A restore pauses an interrupted active session. History is displayed with a 100-record domain cap; the current database does not yet implement a complete deletion/retention UI. Android backups are disabled. There are no embedded API keys. Task labels have a local length limit. Actual SQLite/runtime persistence is covered by the device smoke script when a device is available.

## Android adapters

Android framework TTS/STT, LocationManager and an external `geo:` intent use public platform APIs. Recognition explicitly requests Korean and prefers offline operation, but availability and offline support are controlled by the installed recognition service. With no recognizer, preset commands and typed parked-only commands remain available. TTS requires a Korean voice pack. Local ambient audio is generated PCM, not a streaming service. Motion or a paused session stops audio.

Vehicle, charging and Pleos-specific navigation integrations remain unavailable pending official SDK documentation/artifacts. Real mode therefore reports no usable telemetry, presents UNKNOWN, and does not unlock parked functionality. See SDK_CAPABILITIES.md. Retrofit/OkHttp are deliberately not included: there is no verified external API contract to call.
