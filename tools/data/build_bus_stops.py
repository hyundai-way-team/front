#!/usr/bin/env python3
"""
정차 금지 구간(버스정류장 10m) 데이터 만들기 — 서버 /api/stop-zones 가 쓴다.

출처: 국토교통부_전국 버스정류장 위치정보(공공데이터포털 15067528, 파일데이터 CSV, 이용허락범위 제한 없음)
  https://www.data.go.kr/data/15067528/fileData.do 에서 CSV 를 받아 인자로 준다.
법 근거: 도로교통법 제32조 제4호 — 버스정류장 표지판 등으로부터 10m 이내 정차·주차 금지.

수도권(서울·인천·경기)만 남겨 server/src/main/resources/data/bus_stops_capital.csv.gz 로 쓴다.
열: id,name,lat,lng (정류장번호 · 정류장명 · 위도 · 경도)

실행: python3 tools/data/build_bus_stops.py <받은 CSV 경로>
"""

import csv
import gzip
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "server", "src", "main", "resources", "data", "bus_stops_capital.csv.gz")
# 수도권 대략 경계(남·서·북·동)
SOUTH, WEST, NORTH, EAST = 36.85, 126.30, 38.30, 127.90


def read_rows(path: str):
    raw = open(path, "rb").read()
    for enc in ("utf-8-sig", "cp949"):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    return csv.DictReader(io.StringIO(text))


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    kept, seen = [], set()
    for r in read_rows(sys.argv[1]):
        try:
            lat, lng = float(r["위도"]), float(r["경도"])
        except (KeyError, ValueError):
            continue
        if not (SOUTH <= lat <= NORTH and WEST <= lng <= EAST):
            continue
        sid = (r.get("정류장번호") or "").strip()
        name = (r.get("정류장명") or "").strip()
        key = (round(lat, 6), round(lng, 6))
        if not sid or key in seen:
            continue
        seen.add(key)
        kept.append((sid, name, f"{lat:.6f}", f"{lng:.6f}"))
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(["id", "name", "lat", "lng"])
    w.writerows(kept)
    with gzip.open(OUT, "wt", encoding="utf-8") as f:
        f.write(buf.getvalue())
    print(f"{len(kept)}개 → {OUT} ({os.path.getsize(OUT) / 1e6:.2f}MB)")


if __name__ == "__main__":
    main()
