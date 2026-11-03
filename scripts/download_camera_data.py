"""Download the portal's public camera export in two validated parts.

Write original files outside the repository: they contain agency contact fields.
This uses the official download UI protocol, not the authenticated Open API.
"""
import argparse
import hashlib
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "https://www.data.go.kr"
DETAIL = BASE + "/data/15028200/standard.do"


def fetch(url):
    for attempt in range(3):
        request = urllib.request.Request(url, headers={"Referer": DETAIL, "User-Agent": "CabinOS_ONE data preparation"})
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                raw = response.read(30_000_001)
            if len(raw) > 30_000_000:
                raise ValueError("Response exceeds download limit")
            return raw
        except urllib.error.HTTPError as error:
            if error.code not in (500, 502, 503, 504) or attempt == 2:
                raise
            time.sleep(2 * (attempt + 1))
    raise RuntimeError("Download failed")


def combine(header, first, second):
    total = header["totalCount"]
    if not isinstance(total, int) or not 1 <= total <= 50_000:
        raise ValueError("Use the official Open API for exports exceeding the portal's 50,000-row limit")
    size = (total + 1) // 2
    if not isinstance(first, list) or not isinstance(second, list) or (len(first), len(second)) != (size, total - size):
        raise ValueError("Incomplete or unexpected page sizes; refusing to publish")
    keys = lambda rows: {json.dumps(row, sort_keys=True, ensure_ascii=False) for row in rows}
    if keys(first) & keys(second):
        raise ValueError("Overlapping pages; check snapshot consistency before publishing")
    names = {column["columCode"]: column["columNm"] for column in header["columList"]}
    return [{label: row.get(code) for code, label in names.items()} for row in first + second]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output_directory", type=Path)
    args = parser.parse_args()
    root = args.output_directory
    root.mkdir(parents=True, exist_ok=True)
    raw_header = fetch(BASE + "/download/columList.json?pk=15028200&ext=JSON")
    header = json.loads(raw_header)
    total = header["totalCount"]
    if not isinstance(total, int) or not 1 <= total <= 50_000:
        parser.error("Export exceeds the public download limit; use the official Open API")
    pages, audit = [], []
    for number in (1, 2):
        params = dict(publicDataPk="15028200", colNmList=header["tableVO"]["colNmList"],
                      totalCount=total, svcTableNm=header["tableVO"]["svcTableNm"],
                      perPage=(total + 1) // 2, page=number)
        raw = fetch(BASE + "/download/standard.json?" + urllib.parse.urlencode(params, doseq=True))
        data = json.loads(raw)
        pages.append(data)
        (root / f"camera-part-{number}.json").write_bytes(raw)
        audit.append(dict(part=number, rows=len(data), sha256=hashlib.sha256(raw).hexdigest()))
        print(f"Downloaded part {number}: {len(data)} rows", flush=True)
    records = combine(header, *pages)
    (root / "camera-national.json").write_text(json.dumps(dict(records=records), ensure_ascii=False), encoding="utf-8")
    (root / "camera-download-audit.json").write_text(json.dumps(dict(expectedRows=total,
        receivedRows=len(records), crossPartIdenticalRows=0, parts=audit), indent=2), encoding="utf-8")
    print(f"Validated {len(records)} rows; no identical rows across parts")


if __name__ == "__main__":
    main()
