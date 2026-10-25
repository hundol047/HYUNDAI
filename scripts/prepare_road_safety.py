"""Convert official CSV/JSON exports into a small, offline reference dataset.

Never infer a coordinate system, direction bearing or an unknown speed limit.
Source records stay outside the repository; only an allowlist is packaged.
"""
import argparse
import csv
import hashlib
import io
import json
import math
from collections import defaultdict
from datetime import date
from pathlib import Path


def read_rows(path):
    raw = Path(path).read_bytes()
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        text = raw.decode("cp949")
    if text.lstrip().startswith(("{", "[")):
        value = json.loads(text)
        rows = value.get("records") if isinstance(value, dict) else value
        if not isinstance(rows, list):
            raise ValueError("JSON must be the official records array")
    else:
        rows = list(csv.DictReader(io.StringIO(text)))
    return [{str(k).strip(): str(v or "").strip() for k, v in r.items()} for r in rows]


def convert(rows, kind, as_of):
    date.fromisoformat(as_of)
    groups = defaultdict(list)
    invalid = 0
    for row in rows:
        try:
            lat, lon = float(row["위도"]), float(row["경도"])
            if not (math.isfinite(lat) and math.isfinite(lon) and 32 <= lat <= 40 and 124 <= lon <= 132):
                raise ValueError("Not Korean WGS84 coordinates")
            observed = row.get("데이터기준일자") or as_of
            if date.fromisoformat(observed) > date.fromisoformat(as_of):
                raise ValueError("Future record date")
        except (KeyError, ValueError):
            invalid += 1
            continue
        road = row.get("도로명") or row.get("도로노선명", "")
        location = row.get("설치장소", "")
        # Retain only the provider's plausible numeric limit, never turn 0 into 0 km/h.
        try:
            speed = int(row.get("제한속도", ""))
        except ValueError:
            speed = 0
        speed = speed if kind == "camera" and 10 <= speed <= 140 else None
        groups[(lat, lon)].append((road, location, observed, speed))
    records = []
    conflicts = duplicates = 0
    for (lat, lon), entries in sorted(groups.items()):
        # Even identical coordinates can refer to different roads or facilities.
        unique = set(entries)
        if len(unique) != 1:
            conflicts += len(entries)
            continue
        road, _, observed, speed = next(iter(unique))
        duplicates += len(entries) - 1
        ident = hashlib.sha256(f"{kind}:{lat}:{lon}:{road}".encode()).hexdigest()[:20]
        records.append([ident, lat, lon, kind, road[:120], observed, speed])
    stats = dict(inputRows=len(rows), accepted=len(records), invalidRows=invalid,
                 conflictingCoordinateRows=conflicts, duplicateRows=duplicates)
    assert sum(stats[k] for k in ("accepted", "invalidRows", "conflictingCoordinateRows", "duplicateRows")) == len(rows)
    return records, stats


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("input", type=Path)
    p.add_argument("output", type=Path)
    p.add_argument("--kind", choices=["bump", "camera"], required=True)
    p.add_argument("--as-of", required=True)
    p.add_argument("--source-url", required=True)
    p.add_argument("--coverage", required=True)
    args = p.parse_args()
    rows = read_rows(args.input)
    # Projected national EPSG:5174 input needs an explicit, independently checked
    # transformation before this importer. Fail rather than silently dropping all rows.
    if not rows or "위도" not in rows[0] or "경도" not in rows[0]:
        p.error("WGS84 위도/경도 열이 필요합니다. TM 좌표 원본은 검증된 좌표 변환 후 입력하세요.")
    records, stats = convert(rows, args.kind, args.as_of)
    if not records:
        p.error("No validated coordinates; refusing to replace the dataset")
    data = dict(schema=1, source=args.source_url, coverage=args.coverage, asOf=args.as_of,
                rawSha256=hashlib.sha256(args.input.read_bytes()).hexdigest(), audit=stats,
                columns=["id", "latitude", "longitude", "kind", "road", "recordDate", "reportedSpeedKmh"],
                directionVerified=False, records=records)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()
