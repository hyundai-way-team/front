#!/usr/bin/env python3
"""
Way 오프라인 지도 만들기 — 앱 실행 중에는 외부 지도 API 를 부르지 않도록, 지도 그림을 앱 안에 넣는다.

한 번 돌리면 app/src/main/assets/map/ 에 다음을 만든다.
  way.pmtiles   벡터 타일(OpenMapTiles 스키마, OpenStreetMap 데이터) — 서울 전체(상세 z12–14) + 성남 시흥동
  style.json    밝은 지도 스타일(OpenFreeMap Positron 을 고침, 한글 지명 우선)
  fonts/        라틴 글자 글리프(한글은 기기 글꼴로 그린다 — MapLibre localIdeographFontFamily)
  sprites/      아이콘

데이터 출처: OpenFreeMap(타일·스타일·글꼴) · © OpenMapTiles · © OpenStreetMap contributors (ODbL)
→ 앱 지도 화면에 출처를 표기해야 한다.

필요: python3, `pip install pmtiles certifi`
실행: python3 tools/map/build_offline_map.py              (타일·스타일·글꼴·아이콘 전부)
      python3 tools/map/build_offline_map.py --tiles-only (way.pmtiles 만 — 손본 style.json 등은 그대로)

way.pmtiles 를 다시 만들면 장소 색인도 다시 만든다: python3 tools/map/build_places_index.py
"""

import gzip
import json
import math
import os
import ssl
import sys
import time
import urllib.request

from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "map")
UA = "Way-offline-map-builder/1.0 (hyundai-way-team)"

# macOS python.org 판은 인증서 묶음이 없을 수 있어 certifi 가 있으면 쓴다
try:
    import certifi

    SSL = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL = None

# (이름, 남, 서, 북, 동, 최소 줌, 최대 줌)
# 상세(z12–14)는 서울 전체 — 오프라인 장소 검색·핀 설명(build_places_index.py)이 z14 타일에서 장소·도로를 뽑는다.
AREAS = [
    ("서울 전체(축소)", 37.40, 126.75, 37.70, 127.20, 0, 11),
    ("서울 전체(상세)", 37.42, 126.76, 37.71, 127.19, 12, 14),
    ("에뮬 기본 위치: 성남 시흥동", 37.395, 127.070, 37.425, 127.110, 12, 14),
]
FONT_RANGES = ["0-255", "256-511", "8192-8447"]


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60, context=SSL) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            if attempt == 3:
                raise
            print(f"  다시 시도 {url}: {e}", file=sys.stderr)
            time.sleep(2 * (attempt + 1))
    raise RuntimeError("unreachable")


def tile_range(south, west, north, east, z):
    n = 2**z

    def x_of(lng):
        return int((lng + 180.0) / 360.0 * n)

    def y_of(lat):
        r = math.radians(lat)
        return int((1.0 - math.asinh(math.tan(r)) / math.pi) / 2.0 * n)

    for x in range(x_of(west), x_of(east) + 1):
        for y in range(y_of(north), y_of(south) + 1):
            yield x, y


def build_tiles(template: str):
    wanted = set()
    for _, s, w, n, e, z0, z1 in AREAS:
        for z in range(z0, z1 + 1):
            for x, y in tile_range(s, w, n, e, z):
                wanted.add((z, x, y))
    print(f"타일 {len(wanted)}개 받는 중…")
    tiles = {}
    for i, (z, x, y) in enumerate(sorted(wanted)):
        data = get(template.format(z=z, x=x, y=y))
        if data[:2] != b"\x1f\x8b":
            data = gzip.compress(data)
        tiles[zxy_to_tileid(z, x, y)] = data
        if (i + 1) % 50 == 0:
            print(f"  {i + 1}/{len(wanted)}")
    path = os.path.join(OUT, "way.pmtiles")
    with open(path, "wb") as f:
        writer = Writer(f)
        for tid in sorted(tiles):
            writer.write_tile(tid, tiles[tid])
        s = min(a[1] for a in AREAS)
        w = min(a[2] for a in AREAS)
        n = max(a[3] for a in AREAS)
        e = max(a[4] for a in AREAS)
        writer.finalize(
            {
                "tile_type": TileType.MVT,
                "tile_compression": Compression.GZIP,
                "min_zoom": 0,
                "max_zoom": 14,
                "min_lon_e7": int(w * 1e7),
                "min_lat_e7": int(s * 1e7),
                "max_lon_e7": int(e * 1e7),
                "max_lat_e7": int(n * 1e7),
                "center_zoom": 14,
                "center_lon_e7": int(126.937 * 1e7),
                "center_lat_e7": int(37.556 * 1e7),
            },
            {
                "name": "Way offline map",
                "attribution": "© OpenMapTiles © OpenStreetMap contributors",
                "description": "; ".join(a[0] for a in AREAS),
            },
        )
    print(f"→ {path} ({os.path.getsize(path) / 1e6:.1f} MB)")


def korean_first(expr):
    """지명 표시를 한국어 이름 우선으로."""
    return ["coalesce", ["get", "name:ko"], ["get", "name"], ["get", "name_en"]]


def mentions_name(expr) -> bool:
    text = json.dumps(expr, ensure_ascii=False)
    return "name" in text and "ref" not in text


def build_style():
    style = json.loads(get("https://tiles.openfreemap.org/styles/positron"))
    style["name"] = "Way (Positron 기반)"
    style["sources"] = {
        "openmaptiles": {
            "type": "vector",
            "url": "pmtiles://asset://map/way.pmtiles",
            "attribution": "© OpenMapTiles © OpenStreetMap contributors",
        }
    }
    style["glyphs"] = "asset://map/fonts/{fontstack}/{range}.pbf"
    style["sprite"] = "asset://map/sprites/ofm"
    layers = []
    fonts = set()
    for layer in style["layers"]:
        if layer.get("source") == "ne2_shaded":
            continue  # 저줌 음영 래스터(외부) 빼기
        lay = layer.get("layout", {})
        if "text-field" in lay and mentions_name(lay["text-field"]):
            lay["text-field"] = korean_first(lay["text-field"])
        for f in lay.get("text-font", []):
            fonts.add(f)
        layers.append(layer)
    style["layers"] = layers
    with open(os.path.join(OUT, "style.json"), "w", encoding="utf-8") as f:
        json.dump(style, f, ensure_ascii=False, indent=1)
    print(f"→ style.json (레이어 {len(layers)}개, 글꼴 {sorted(fonts)})")
    return sorted(fonts)


def build_fonts(fonts):
    for font in fonts:
        folder = os.path.join(OUT, "fonts", font)
        os.makedirs(folder, exist_ok=True)
        for rng in FONT_RANGES:
            url = f"https://tiles.openfreemap.org/fonts/{urllib.request.quote(font)}/{rng}.pbf"
            with open(os.path.join(folder, f"{rng}.pbf"), "wb") as f:
                f.write(get(url))
    print(f"→ fonts/ ({len(fonts)}종 × {len(FONT_RANGES)}구간)")


def build_sprites():
    folder = os.path.join(OUT, "sprites")
    os.makedirs(folder, exist_ok=True)
    for suffix in ["", "@2x"]:
        for ext in ["json", "png"]:
            name = f"ofm{suffix}.{ext}"
            with open(os.path.join(folder, name), "wb") as f:
                f.write(get(f"https://tiles.openfreemap.org/sprites/ofm_f384/{name}"))
    print("→ sprites/")


def main():
    os.makedirs(OUT, exist_ok=True)
    tilejson = json.loads(get("https://tiles.openfreemap.org/planet"))
    print(f"OpenFreeMap {tilejson.get('version')} · {tilejson['tiles'][0]}")
    build_tiles(tilejson["tiles"][0])
    if "--tiles-only" in sys.argv[1:]:
        return
    fonts = build_style()
    build_fonts(fonts)
    build_sprites()


if __name__ == "__main__":
    main()
