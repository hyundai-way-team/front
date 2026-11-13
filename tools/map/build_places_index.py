#!/usr/bin/env python3
"""
Way 오프라인 장소 색인 만들기 — 장소 검색·핀 위치 설명을 외부 API(카카오·네이버 등) 없이 앱 안에서 하려고,
앱에 넣은 지도(way.pmtiles)의 z14 타일에서 장소·도로·동네 이름을 뽑아 작은 이진 파일 하나로 묶는다.

입력: app/src/main/assets/map/way.pmtiles  (build_offline_map.py 가 만든 것 — 지도를 다시 받으면 이것도 다시 돌린다)
출력: app/src/main/assets/places/way-places.dat

뽑는 것 (OpenMapTiles 스키마)
  poi                  이름 있는 장소(가게·역·버스정류장 …). 이름은 name:ko 우선, 없으면 name.
                       역은 "신촌" → "신촌역", 버스정류장은 "신촌자이엘라" → "신촌자이엘라 버스정류장" 으로 붙여 둔다.
  transportation_name  이름 있는 차도 선 — 핀 설명의 도로명·도로 어느 쪽인지.
  place                시·구·동 이름 점 — 검색 결과 둘째 줄 주소("서울 마포구 연남동").
  boundary             (파일엔 안 넣음) 시·구·동 경계선 — 장소가 어느 구·동인지 가를 때만 쓴다.
                       경계선을 약 22 m 칸 격자에 벽으로 그리고, 구(동) 이름 점에서 물을 붓듯 채워
                       장소가 놓인 칸이 어느 이름 점에서 채워졌는지 본다(벽을 못 넘으니 구 경계를 넘지 않는다).

타일 버퍼(이웃 타일과 겹치는 가장자리)에 든 것은 버리고(점) 잘라 내서(선) 같은 것이 두 번 들어가지 않게 한다.

파일 형식(앱의 com.kscho.way.data.place.PlaceIndexReader 와 짝) — gzip 을 풀면:
  "WAYP"   4바이트
  u        판 번호(1)
  u u      기준 위도·경도(1e-6 도, 정수) — 아래 좌표는 모두 이 기준에서의 차이
  문자열표  u 개수, (u 바이트 수, UTF-8)…
  종류표    u 개수, (u class 문자열, u subclass 문자열)…            ← poi 의 OpenMapTiles class/subclass
  동네      u 개수, (u 이름, u 등급, u 윗주소 문자열, s 위도차, s 경도차)…
              등급 0 시 · 1 구 · 2 suburb · 3 동(quarter) · 4 neighbourhood · 5 그 밖
              윗주소 예) 연남동 → "서울 마포구", 마포구 → "서울"
  장소      u 개수, (u 이름, u 종류, u 동네 번호+1(0=모름), u 표시, s 위도차, s 경도차)…
              표시 1 = 길에서 안 보이는 곳(실내·지하·2층 이상) — 핀 설명의 "○○ 앞" 에는 안 쓴다
  도로      u 개수, (u 이름, u 등급, u 점 개수, (s 위도차, s 경도차)…)…
              등급 0 motorway · 1 trunk · 2 primary · 3 secondary · 4 tertiary · 5 minor · 6 service
  (u = LEB128 부호 없는 가변 정수, s = 지그재그 부호 있는 가변 정수.
   위도차·경도차는 같은 묶음 안 바로 앞 항목(도로는 바로 앞 점)과의 차이, 첫 항목은 기준과의 차이)

필요: python3, `pip install pmtiles` (build_offline_map.py 와 같다)
실행: python3 tools/map/build_places_index.py
"""

import gzip
import math
import os
import re
import struct
import sys
import time
from collections import defaultdict

from pmtiles.reader import MmapSource, all_tiles

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PMTILES = os.path.join(ROOT, "app", "src", "main", "assets", "map", "way.pmtiles")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "places", "way-places.dat")

MAGIC = b"WAYP"
VERSION = 1
DETAIL_ZOOM = 14  # 장소·도로는 가장 자세한 줌에서만
PLACE_ZOOMS = (12, 13, 14)  # 시·구 이름 점은 z14 타일 밖에 있을 수 있어 낮은 줌도 본다
M_PER_E6 = 0.111195  # 위도 1e-6 도 ≈ 0.111 m

# ── MVT(벡터 타일, protobuf) 읽기 — 필요한 만큼만 직접 푼다


def _varint(buf, i):
    result = 0
    shift = 0
    while True:
        b = buf[i]
        i += 1
        result |= (b & 0x7F) << shift
        if b < 0x80:
            return result, i
        shift += 7


def _fields(buf):
    i = 0
    n = len(buf)
    while i < n:
        key, i = _varint(buf, i)
        field, wire = key >> 3, key & 7
        if wire == 0:
            value, i = _varint(buf, i)
        elif wire == 1:
            value = buf[i : i + 8]
            i += 8
        elif wire == 2:
            length, i = _varint(buf, i)
            value = buf[i : i + length]
            i += length
        elif wire == 5:
            value = buf[i : i + 4]
            i += 4
        else:
            raise ValueError(f"모르는 protobuf wire type {wire}")
        yield field, wire, value


def _packed(buf):
    out = []
    i = 0
    while i < len(buf):
        v, i = _varint(buf, i)
        out.append(v)
    return out


def _unzigzag(v):
    return (v >> 1) ^ -(v & 1)


def _value(buf):
    for field, _, v in _fields(buf):
        if field == 1:
            return bytes(v).decode("utf-8", "replace")
        if field == 2:
            return struct.unpack("<f", v)[0]
        if field == 3:
            return struct.unpack("<d", v)[0]
        if field == 4:
            return v if v < 1 << 63 else v - (1 << 64)
        if field == 5:
            return v
        if field == 6:
            return _unzigzag(v)
        if field == 7:
            return bool(v)
    return None


def _geometry(cmds):
    """MoveTo/LineTo/ClosePath 명령 → 부분 좌표열 목록(타일 픽셀)."""
    parts = []
    cur = None
    x = y = 0
    i = 0
    while i < len(cmds):
        cmd = cmds[i]
        i += 1
        cid, count = cmd & 7, cmd >> 3
        if cid == 1:  # MoveTo
            for _ in range(count):
                x += _unzigzag(cmds[i])
                y += _unzigzag(cmds[i + 1])
                i += 2
                cur = [(x, y)]
                parts.append(cur)
        elif cid == 2:  # LineTo
            for _ in range(count):
                x += _unzigzag(cmds[i])
                y += _unzigzag(cmds[i + 1])
                i += 2
                cur.append((x, y))
        elif cid == 7 and cur:  # ClosePath
            cur.append(cur[0])
    return parts


def decode_tile(data, wanted):
    """{레이어 이름: (extent, [(geom type, props, parts)])} — [wanted] 레이어만."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    layers = {}
    for field, _, layer in _fields(data):
        if field != 3:
            continue
        name = None
        features = []
        keys = []
        values = []
        extent = 4096
        for lf, _, lv in _fields(layer):
            if lf == 1:
                name = bytes(lv).decode()
                if name not in wanted:
                    break
            elif lf == 2:
                features.append(lv)
            elif lf == 3:
                keys.append(bytes(lv).decode())
            elif lf == 4:
                values.append(_value(lv))
            elif lf == 5:
                extent = lv
        if name not in wanted:
            continue
        out = []
        for fb in features:
            tags = []
            gtype = 0
            geom = []
            for ff, fw, fv in _fields(fb):
                if ff == 2:
                    tags = _packed(fv) if fw == 2 else [fv]
                elif ff == 3:
                    gtype = fv
                elif ff == 4:
                    geom = _packed(fv) if fw == 2 else [fv]
            props = {keys[tags[k]]: values[tags[k + 1]] for k in range(0, len(tags) - 1, 2)}
            out.append((gtype, props, _geometry(geom)))
        layers[name] = (extent, out)
    return layers


# ── 좌표


class TileProjection:
    """타일 픽셀 → (위도, 경도) 1e-6 도 정수."""

    def __init__(self, z, x, y, extent):
        self.n = 2**z
        self.x = x
        self.y = y
        self.extent = extent

    def e6(self, px, py):
        xw = (self.x + px / self.extent) / self.n
        yw = (self.y + py / self.extent) / self.n
        lng = xw * 360.0 - 180.0
        lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * yw))))
        return round(lat * 1e6), round(lng * 1e6)


def meters(a, b):
    """두 점(1e-6 도) 사이 거리(m) — 도심 짧은 거리라 평면으로."""
    dy = (b[0] - a[0]) * M_PER_E6
    dx = (b[1] - a[1]) * M_PER_E6 * math.cos(math.radians(a[0] / 1e6))
    return math.hypot(dx, dy)


def clip_polyline(points, lo, hi):
    """타일 영역 [lo, hi]² 안쪽만 남기기(Liang–Barsky). 버퍼 부분은 이웃 타일에 있으니 버린다."""
    out = []
    cur = None
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        t0, t1 = 0.0, 1.0
        dx, dy = x1 - x0, y1 - y0
        ok = True
        for p, q in ((-dx, x0 - lo), (dx, hi - x0), (-dy, y0 - lo), (dy, hi - y0)):
            if p == 0:
                if q < 0:
                    ok = False
                    break
            else:
                t = q / p
                if p < 0:
                    if t > t1:
                        ok = False
                        break
                    t0 = max(t0, t)
                else:
                    if t < t0:
                        ok = False
                        break
                    t1 = min(t1, t)
        if not ok or t0 > t1:
            cur = None
            continue
        a = (x0 + t0 * dx, y0 + t0 * dy)
        b = (x0 + t1 * dx, y0 + t1 * dy)
        if cur is not None and t0 == 0.0:
            cur.append(b)
        else:
            cur = [a, b]
            out.append(cur)
        if t1 < 1.0:
            cur = None
    return out


def simplify(points, tolerance_m):
    """Douglas–Peucker (점은 1e-6 도 정수)."""
    if len(points) <= 2:
        return points
    cos_lat = math.cos(math.radians(points[0][0] / 1e6))

    def xy(p):
        return p[1] * M_PER_E6 * cos_lat, p[0] * M_PER_E6

    pts = [xy(p) for p in points]
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        i, j = stack.pop()
        (ax, ay), (bx, by) = pts[i], pts[j]
        dx, dy = bx - ax, by - ay
        len2 = dx * dx + dy * dy
        best, best_k = -1.0, -1
        for k in range(i + 1, j):
            px, py = pts[k]
            if len2 == 0:
                d = math.hypot(px - ax, py - ay)
            else:
                t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / len2))
                d = math.hypot(px - ax - t * dx, py - ay - t * dy)
            if d > best:
                best, best_k = d, k
        if best > tolerance_m:
            keep[best_k] = True
            stack.append((i, best_k))
            stack.append((best_k, j))
    return [p for p, k in zip(points, keep) if k]


# ── 이름


def korean_name(props):
    name = props.get("name:ko") or props.get("name")
    if not isinstance(name, str):
        return None
    name = " ".join(name.split())
    return name or None


STOP_SUFFIXES = ("정류장", "정류소", "승강장", "정거장", "터미널")
STATION_SUBCLASSES = {"subway", "station", "halt", "tram_stop", "light_rail", "monorail"}


def display_name(name, cls, subclass):
    """검색 결과·핀 설명에 그대로 쓰는 이름. 역·버스정류장은 무엇인지 알 수 있게 붙인다."""
    if cls == "bus" and subclass == "bus_stop":
        if name.endswith("(중)"):  # 중앙차로 정류장
            return name[:-3].rstrip() + " 중앙버스정류장"
        return name if name.endswith(STOP_SUFFIXES) else name + " 버스정류장"
    if cls == "railway" and subclass in STATION_SUBCLASSES:
        m = re.match(r"^(.*?)(\s*\(.*\))?$", name)
        base, paren = m.group(1), m.group(2) or ""
        if base and not base.endswith("역"):
            base += "역"
        return base + paren
    return name


def name_key(text):
    return re.sub(r"[\s.,·・()\[\]'\"/_-]", "", text).lower()


# ── 경계선(구·동 가르기)


class SegmentGrid:
    """경계선 조각을 격자에 담아 두고, 곧은 선이 몇 번 넘는지 센다."""

    CELL = 5000  # 1e-6 도 → 0.005° (약 500 m)

    def __init__(self):
        self.cells = defaultdict(list)
        self.segments = []

    def add(self, points, level):
        for a, b in zip(points, points[1:]):
            sid = len(self.segments)
            self.segments.append((a[0], a[1], b[0], b[1], level))
            for cy in range(min(a[0], b[0]) // self.CELL, max(a[0], b[0]) // self.CELL + 1):
                for cx in range(min(a[1], b[1]) // self.CELL, max(a[1], b[1]) // self.CELL + 1):
                    self.cells[(cy, cx)].append(sid)

    def crossings(self, p, q, levels):
        seen = set()
        count = 0
        py, px = p
        qy, qx = q
        for cy in range(min(py, qy) // self.CELL, max(py, qy) // self.CELL + 1):
            for cx in range(min(px, qx) // self.CELL, max(px, qx) // self.CELL + 1):
                for sid in self.cells.get((cy, cx), ()):
                    if sid in seen:
                        continue
                    seen.add(sid)
                    ay, ax, by, bx, level = self.segments[sid]
                    if level not in levels:
                        continue
                    d1 = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
                    d2 = (bx - ax) * (qy - ay) - (by - ay) * (qx - ax)
                    if (d1 > 0) == (d2 > 0) or d1 == 0 or d2 == 0:
                        continue
                    d3 = (qx - px) * (ay - py) - (qy - py) * (ax - px)
                    d4 = (qx - px) * (by - py) - (qy - py) * (bx - px)
                    if (d3 > 0) != (d4 > 0) and d3 != 0 and d4 != 0:
                        count += 1
        return count


class Raster:
    """z14 타일이 있는 곳만 여는 약 22 m 칸 격자. 경계선을 벽으로 그리고 이름 점에서 채운다."""

    LAT_STEP = 200  # 1e-6 도 ≈ 22 m
    LNG_STEP = 250  # ≈ 22 m (서울 위도)

    def __init__(self, tiles, zoom):
        n = 2**zoom

        def lng_e6(x):
            return round((x / n * 360.0 - 180.0) * 1e6)

        def lat_e6(y):
            return round(math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / n)))) * 1e6)

        boxes = [(lat_e6(y + 1), lng_e6(x), lat_e6(y), lng_e6(x + 1)) for x, y in tiles]  # 남, 서, 북, 동
        self.lat0 = min(b[0] for b in boxes)
        self.lng0 = min(b[1] for b in boxes)
        self.rows = (max(b[2] for b in boxes) - self.lat0) // self.LAT_STEP + 1
        self.cols = (max(b[3] for b in boxes) - self.lng0) // self.LNG_STEP + 1
        self.open = bytearray(self.rows * self.cols)
        for s, w, nn, e in boxes:
            r0, r1 = (s - self.lat0) // self.LAT_STEP, (nn - self.lat0) // self.LAT_STEP
            c0, c1 = (w - self.lng0) // self.LNG_STEP, (e - self.lng0) // self.LNG_STEP
            for r in range(r0, min(r1, self.rows - 1) + 1):
                row = r * self.cols
                self.open[row + c0 : row + min(c1, self.cols - 1) + 1] = b"\x01" * (min(c1, self.cols - 1) + 1 - c0)

    def rc(self, pt):
        return (pt[0] - self.lat0) // self.LAT_STEP, (pt[1] - self.lng0) // self.LNG_STEP

    def walls(self, segments, levels):
        """막힌 칸(타일 밖 + 경계선). 선은 8방향으로 이어 그려 4방향 채우기가 새지 않는다."""
        wall = bytearray(1 - v for v in self.open)
        for ay, ax, by, bx, level in segments:
            if level not in levels:
                continue
            r0, c0 = self.rc((ay, ax))
            r1, c1 = self.rc((by, bx))
            dr, dc = abs(r1 - r0), abs(c1 - c0)
            sr, sc = (1 if r1 > r0 else -1), (1 if c1 > c0 else -1)
            err = dc - dr
            r, c = r0, c0
            while True:
                if 0 <= r < self.rows and 0 <= c < self.cols:
                    wall[r * self.cols + c] = 1
                if r == r1 and c == c1:
                    break
                e2 = 2 * err
                if e2 > -dr:
                    err -= dr
                    c += sc
                if e2 < dc:
                    err += dc
                    r += sr
        return wall

    def flood(self, wall, seeds, labels=None):
        """[seeds] (점, 이름표)에서 동시에 채운다. 벽은 못 넘는다. 벽 칸은 끝에 옆 칸 이름표로 메운다."""
        cols = self.cols
        total = self.rows * cols
        if labels is None:
            labels = [-1] * total
        queue = []
        for pt, label in seeds:
            r, c = self.rc(pt)
            if not (0 <= r < self.rows and 0 <= c < cols):
                continue
            i = r * cols + c
            if labels[i] == -1 and not wall[i]:
                labels[i] = label
                queue.append(i)
        head = 0
        while head < len(queue):
            i = queue[head]
            head += 1
            label = labels[i]
            c = i % cols
            for j in (i - cols, i + cols, i - 1 if c > 0 else -1, i + 1 if c < cols - 1 else -1):
                if 0 <= j < total and labels[j] == -1 and not wall[j]:
                    labels[j] = label
                    queue.append(j)
        # 경계선 위 칸: 옆 칸 이름표(몇 번 되풀이 — 두꺼운 벽 대비)
        for _ in range(3):
            fill = []
            for i in range(total):
                if labels[i] == -1 and wall[i] and self.open[i]:
                    c = i % cols
                    for j in (i - cols, i + cols, i - 1 if c > 0 else -1, i + 1 if c < cols - 1 else -1):
                        if 0 <= j < total and labels[j] != -1:
                            fill.append((i, labels[j]))
                            break
            for i, label in fill:
                labels[i] = label
        return labels

    def label_at(self, labels, pt):
        r, c = self.rc(pt)
        if 0 <= r < self.rows and 0 <= c < self.cols:
            return labels[r * self.cols + c]
        return -1


PLACE_LEVEL = {"city": 0, "borough": 1, "suburb": 2, "quarter": 3, "neighbourhood": 4, "hamlet": 5, "village": 5, "town": 5, "island": 5}
GU_BOUNDARY = {4, 6, 7}  # 시·도 / 시·군·구 / 일반구
DONG_BOUNDARY = {8, 9, 10}  # 읍·면·동(행정·법정)


def short_city(name):
    if name.endswith("특별시") or name.endswith("광역시"):
        return name[:-3]
    if name.endswith("특별자치시"):
        return name[:-5]
    return name


class AddressBook:
    """점 → (구, 동, 주소).

    구: 시·구 경계선을 벽으로 한 격자를 구 이름 점에서 채운 결과(구 없는 시는 시 이름 점에서).
    동: 동 경계선까지 벽으로 해 동 이름 점에서 채운 결과 — 같은 구가 아니거나 못 채운 칸(이름 점 없는 행정동 조각)은
        같은 구 안에서 동 경계를 덜 넘고 가까운 동 이름 점.
    격자 밖이면 곧게 그은 선이 구 경계를 넘지 않는 가장 가까운 구 이름 점.
    """

    def __init__(self, places, boundaries, raster):
        self.places = places
        self.grid = boundaries
        self.raster = raster
        self.cities = [i for i, p in enumerate(places) if p["level"] == 0]
        self.boroughs = [i for i, p in enumerate(places) if p["level"] == 1]
        self.quarters = [i for i, p in enumerate(places) if p["level"] == 3]
        self.cache = {}
        self._quarter_grid = defaultdict(list)
        for i in self.quarters:
            p = places[i]["pt"]
            self._quarter_grid[(p[0] // 10000, p[1] // 10000)].append(i)
        started = time.time()
        gu_wall = raster.walls(boundaries.segments, GU_BOUNDARY)
        self.gu_labels = raster.flood(gu_wall, [(places[i]["pt"], i) for i in self.boroughs])
        # 구 없는 시(광명·과천 …): 아직 안 채운 곳을 시 이름 점에서
        self.gu_labels = raster.flood(gu_wall, [(places[i]["pt"], i) for i in self.cities], self.gu_labels)
        dong_wall = raster.walls(boundaries.segments, GU_BOUNDARY | DONG_BOUNDARY)
        self.dong_labels = raster.flood(dong_wall, [(places[i]["pt"], i) for i in self.quarters])
        print(f"구·동 격자 {raster.rows}×{raster.cols} 채움 ({time.time() - started:.0f}초)")

    def _nearest(self, pt, candidates, limit):
        return sorted(candidates, key=lambda i: meters(pt, self.places[i]["pt"]))[:limit]

    def borough_of(self, pt):
        """구 번호 — 구 없는 시면 None."""
        label = self.raster.label_at(self.gu_labels, pt)
        if label != -1:
            return label if self.places[label]["level"] == 1 else None
        for i in self._nearest(pt, self.boroughs, 4):
            if meters(pt, self.places[i]["pt"]) > 15_000:
                break
            if self.grid.crossings(pt, self.places[i]["pt"], GU_BOUNDARY) == 0:
                return i
        return None

    def city_of(self, pt, borough):
        start = self.places[borough]["pt"] if borough is not None else pt
        near = self._nearest(start, self.cities, 6)
        # 구가 있으면: 같은 시·도 안(시·도 경계를 안 넘는) 가장 가까운 시. 일반구(성남 수정구 등)는 시 경계도 안 넘는 시가 먼저.
        for levels in ({4, 6}, {4}):
            for i in near:
                if borough is None and levels == {4}:
                    break  # 구 없는 동은 시 경계를 넘지 않는 시만
                if self.grid.crossings(start, self.places[i]["pt"], levels) == 0:
                    return i
        return None

    def quarter_of(self, pt, borough):
        label = self.raster.label_at(self.dong_labels, pt)
        if label != -1 and self.places[label].get("borough") == borough:
            return label
        cy, cx = pt[0] // 10000, pt[1] // 10000
        near = []
        for dy in (-2, -1, 0, 1, 2):
            for dx in (-2, -1, 0, 1, 2):
                near.extend(self._quarter_grid.get((cy + dy, cx + dx), ()))
        near = [i for i in near if self.places[i].get("borough") == borough]
        near = self._nearest(pt, near, 3)
        if not near:
            return None
        # 동 경계를 덜 넘는 쪽, 같으면 가까운 쪽
        return min(near, key=lambda i: (self.grid.crossings(pt, self.places[i]["pt"], DONG_BOUNDARY), meters(pt, self.places[i]["pt"])))

    def resolve_places(self):
        for i, p in enumerate(self.places):
            if p["level"] == 0:
                p["address"] = ""
                continue
            b = i if p["level"] == 1 else self.borough_of(p["pt"])
            p["borough"] = b
        for i, p in enumerate(self.places):
            if p["level"] == 0:
                continue
            b = p["borough"]
            city = self.city_of(p["pt"], b)
            p["city"] = city
            parts = [short_city(self.places[city]["name"])] if city is not None else []
            if p["level"] == 1:
                p["address"] = " ".join(parts)
                continue
            if b is not None:
                parts.append(self.places[b]["name"])
            if p["level"] != 3:  # 홍대앞·신촌 같은 동네 이름은 어느 동인지까지(동 이름 점은 자기가 동)
                q = self.quarter_of(p["pt"], b)
                if q is not None:
                    parts.append(self.places[q]["name"])
            p["address"] = " ".join(parts)

    def area_of(self, pt):
        """장소가 속한 동(없으면 구) 번호. 30m 칸 단위로 기억해 둔다."""
        key = (pt[0] // 300, pt[1] // 300)
        if key in self.cache:
            return self.cache[key]
        b = self.borough_of(pt)
        q = self.quarter_of(pt, b)
        if q is None and b is None:
            # 구 없는 시(광명·과천 등) — 구 경계를 넘지 않는 가장 가까운 동
            q = self.quarter_of(pt, None)
        result = q if q is not None else b
        self.cache[key] = result
        return result


# ── 뽑기


ROAD_CLASS = {"motorway": 0, "trunk": 1, "primary": 2, "secondary": 3, "tertiary": 4, "minor": 5, "service": 6}


def _levels(value):
    """level 값("3", "-1;0", "-2-10" …) → 층 숫자들."""
    out = []
    for token in str(value).split(";"):
        token = token.strip()
        try:
            out.append(float(token))
            continue
        except ValueError:
            pass
        m = re.match(r"^(-?\d+(?:\.\d+)?)-(-?\d+(?:\.\d+)?)$", token)
        if m:
            out.extend([float(m.group(1)), float(m.group(2))])
    return out


def not_street_level(props):
    """길에서 안 보이는 곳 — 지하·실내, 또는 2층 이상만. 한국 OSM 은 1층(지상)을 level=1 로도 적어 0·1 을 지상으로 본다."""
    if props.get("indoor") in (1, True, "yes"):
        return True
    layer = props.get("layer")
    if isinstance(layer, (int, float)) and layer < 0:
        return True
    level = props.get("level")
    if level is None:
        return False
    levels = _levels(level)
    if not levels:
        return False
    return not (min(levels) <= 1.5 and max(levels) >= -0.5)


def extract(path):
    pois = []
    places = []
    roads = []
    boundaries = SegmentGrid()
    detail_tiles = []
    with open(path, "rb") as f:
        source = MmapSource(f)
        tiles = 0
        for (z, x, y), data in all_tiles(source):
            if z not in PLACE_ZOOMS:
                continue
            wanted = {"place"} if z != DETAIL_ZOOM else {"place", "poi", "transportation_name", "boundary"}
            layers = decode_tile(data, wanted)
            tiles += 1

            extent, feats = layers.get("place", (4096, []))
            proj = TileProjection(z, x, y, extent)
            for gtype, props, parts in feats:
                level = PLACE_LEVEL.get(props.get("class"))
                name = korean_name(props)
                if gtype != 1 or level is None or not name:
                    continue
                if z != DETAIL_ZOOM and level not in (0, 1):
                    continue  # 낮은 줌은 시·구만(동은 z14 에 다 있다)
                for part in parts:
                    for px, py in part:
                        if 0 <= px < extent and 0 <= py < extent:
                            places.append({"name": name, "level": level, "pt": proj.e6(px, py)})

            if z != DETAIL_ZOOM:
                continue
            detail_tiles.append((x, y))

            extent, feats = layers.get("poi", (4096, []))
            proj = TileProjection(z, x, y, extent)
            for gtype, props, parts in feats:
                name = korean_name(props)
                if gtype != 1 or not name:
                    continue
                cls = props.get("class") or ""
                sub = props.get("subclass") or ""
                if cls == "information":
                    continue  # 안내판·이정표
                shown = display_name(name, cls, sub)
                for part in parts:
                    for px, py in part:
                        if 0 <= px < extent and 0 <= py < extent:
                            pois.append({"name": shown, "class": cls, "subclass": sub, "pt": proj.e6(px, py), "flags": 1 if not_street_level(props) else 0})

            extent, feats = layers.get("transportation_name", (4096, []))
            proj = TileProjection(z, x, y, extent)
            for gtype, props, parts in feats:
                rc = ROAD_CLASS.get(props.get("class"))
                name = korean_name(props)
                if gtype != 2 or rc is None or not name or "지하" in name:
                    continue
                if not_street_level(props) or props.get("brunnel") == "tunnel":
                    continue
                for part in parts:
                    for piece in clip_polyline(part, 0, extent):
                        pts = []
                        for px, py in piece:
                            pt = proj.e6(px, py)
                            if not pts or pts[-1] != pt:
                                pts.append(pt)
                        if len(pts) >= 2:
                            roads.append({"name": name, "class": rc, "pts": pts})

            extent, feats = layers.get("boundary", (4096, []))
            proj = TileProjection(z, x, y, extent)
            for gtype, props, parts in feats:
                level = props.get("admin_level")
                if gtype != 2 or props.get("maritime") in (1, True) or level not in GU_BOUNDARY | DONG_BOUNDARY:
                    continue
                for part in parts:
                    for piece in clip_polyline(part, 0, extent):
                        boundaries.add([proj.e6(px, py) for px, py in piece], level)
    print(f"타일 {tiles}개 · 장소 {len(pois)} · 동네 점 {len(places)} · 도로 조각 {len(roads)} · 경계 조각 {len(boundaries.segments)}")
    return pois, places, roads, boundaries, Raster(detail_tiles, DETAIL_ZOOM)


def dedupe_places(places):
    """낮은 줌·높은 줌에 같은 시·구가 또 있으면 하나만(z14 쪽이 먼저 들어 있어 더 정확하지는 않으니 첫 것)."""
    out = []
    by_name = defaultdict(list)
    for p in places:
        same = [q for q in by_name[(p["name"], p["level"])] if meters(p["pt"], q["pt"]) < 3000]
        if same:
            continue
        by_name[(p["name"], p["level"])].append(p)
        out.append(p)
    return out


def dedupe_pois(pois):
    """같은 이름·거의 같은 위치(OSM 에 점·면으로 두 번 그린 것 등)는 하나로. 정류장은 길 건너편 것을 살리려 좁게."""
    out = []
    buckets = defaultdict(list)
    for p in pois:
        if p["class"] == "bus":
            radius = 8.0
        elif p["class"] == "railway":
            radius = 250.0  # 같은 역의 여러 노선 점
        else:
            radius = 30.0
        key = (name_key(p["name"]), p["class"] == "bus", p["class"] == "railway")
        cell = (p["pt"][0] // 3000, p["pt"][1] // 3000)
        dup = False
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                for q in buckets.get((key, cell[0] + dy, cell[1] + dx), ()):
                    if meters(p["pt"], q["pt"]) <= radius:
                        dup = True
                        q["flags"] &= p["flags"]  # 하나라도 길에서 보이면 보이는 것으로
                        break
                if dup:
                    break
            if dup:
                break
        if not dup:
            buckets[(key, cell[0], cell[1])].append(p)
            out.append(p)
    return out


def morton(lat, lng, base):
    y = max(0, (lat - base[0]) >> 6)
    x = max(0, (lng - base[1]) >> 6)
    code = 0
    for bit in range(20):
        code |= ((x >> bit) & 1) << (2 * bit) | ((y >> bit) & 1) << (2 * bit + 1)
    return code


# ── 쓰기


class Out:
    def __init__(self):
        self.buf = bytearray()

    def u(self, v):
        if v < 0:
            raise ValueError(v)
        while True:
            b = v & 0x7F
            v >>= 7
            if v:
                self.buf.append(b | 0x80)
            else:
                self.buf.append(b)
                return

    def s(self, v):
        self.u((v << 1) ^ (v >> 63) if v < 0 else v << 1)

    def raw(self, b):
        self.buf.extend(b)


def write_index(pois, places, roads, path):
    all_pts = [p["pt"] for p in pois] + [p["pt"] for p in places] + [pt for r in roads for pt in r["pts"]]
    base = (min(p[0] for p in all_pts), min(p[1] for p in all_pts))

    strings = {}
    table = []

    def sid(text):
        if text not in strings:
            strings[text] = len(table)
            table.append(text)
        return strings[text]

    kinds = {}
    kind_table = []
    for p in pois:
        k = (p["class"], p["subclass"])
        if k not in kinds:
            kinds[k] = len(kind_table)
            kind_table.append(k)

    order = sorted(range(len(places)), key=lambda i: morton(places[i]["pt"][0], places[i]["pt"][1], base))
    place_pos = {old: new for new, old in enumerate(order)}
    places = [places[i] for i in order]
    for p in pois:
        p["area"] = place_pos[p["area"]] if p.get("area") is not None else None
    pois = sorted(pois, key=lambda p: morton(p["pt"][0], p["pt"][1], base))
    roads = sorted(roads, key=lambda r: morton(r["pts"][0][0], r["pts"][0][1], base))

    # 문자열표는 쓰는 순서대로(가까운 장소 이름이 붙어 있어 gzip 이 잘 준다)
    for p in places:
        sid(p["name"])
        sid(p["address"])
    for p in pois:
        sid(p["name"])
    for r in roads:
        sid(r["name"])
    for c, s in kind_table:
        sid(c)
        sid(s)

    out = Out()
    out.raw(MAGIC)
    out.u(VERSION)
    out.u(base[0])
    out.u(base[1])
    out.u(len(table))
    for text in table:
        b = text.encode("utf-8")
        out.u(len(b))
        out.raw(b)
    out.u(len(kind_table))
    for c, s in kind_table:
        out.u(sid(c))
        out.u(sid(s))
    out.u(len(places))
    prev = base
    for p in places:
        out.u(sid(p["name"]))
        out.u(p["level"])
        out.u(sid(p["address"]))
        out.s(p["pt"][0] - prev[0])
        out.s(p["pt"][1] - prev[1])
        prev = p["pt"]
    out.u(len(pois))
    prev = base
    for p in pois:
        out.u(sid(p["name"]))
        out.u(kinds[(p["class"], p["subclass"])])
        out.u(p["area"] + 1 if p["area"] is not None else 0)
        out.u(p["flags"])
        out.s(p["pt"][0] - prev[0])
        out.s(p["pt"][1] - prev[1])
        prev = p["pt"]
    out.u(len(roads))
    prev = base
    vertices = 0
    for r in roads:
        out.u(sid(r["name"]))
        out.u(r["class"])
        out.u(len(r["pts"]))
        for pt in r["pts"]:
            out.s(pt[0] - prev[0])
            out.s(pt[1] - prev[1])
            prev = pt
        vertices += len(r["pts"])

    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = gzip.compress(bytes(out.buf), compresslevel=9, mtime=0)
    with open(path, "wb") as f:
        f.write(data)
    print(f"→ {path}")
    print(
        f"  풀면 {len(out.buf) / 1e6:.2f} MB · 압축 {len(data) / 1e6:.2f} MB · 문자열 {len(table)} · 종류 {len(kind_table)}"
        f" · 동네 {len(places)} · 장소 {len(pois)} · 도로 {len(roads)}(점 {vertices})"
    )


def main():
    started = time.time()
    if not os.path.exists(PMTILES):
        sys.exit(f"{PMTILES} 이 없다 — 먼저 build_offline_map.py")
    pois, places, roads, boundaries, raster = extract(PMTILES)

    places = dedupe_places(places)
    book = AddressBook(places, boundaries, raster)
    book.resolve_places()

    before = len(pois)
    pois = dedupe_pois(pois)
    print(f"장소 중복 합침 {before} → {len(pois)}")
    for p in pois:
        p["area"] = book.area_of(p["pt"])
    unknown = sum(1 for p in pois if p["area"] is None)
    print(f"주소 못 정한 장소 {unknown}개")

    vertices = sum(len(r["pts"]) for r in roads)
    for r in roads:
        r["pts"] = simplify(r["pts"], 1.0)
    print(f"도로 점 단순화 {vertices} → {sum(len(r['pts']) for r in roads)}")

    write_index(pois, places, roads, OUT)
    print(f"({time.time() - started:.0f}초)")


if __name__ == "__main__":
    main()
