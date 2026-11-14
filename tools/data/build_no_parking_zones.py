#!/usr/bin/env python3
"""
지정 주정차 금지구역 데이터 만들기 — 서버 /api/stop-zones 가 카카오 로컬 API 키가 있을 때 쓴다.

출처
  1) 전국주정차금지(지정)구역표준데이터(공공데이터포털 15034542, 행정안전부 표준) — 도로명·상세위치 글·시간대.
     좌표가 없다. 상세위치는 '○○약국 앞 ∼ ○○사거리' 같은 글뿐이라 끝점은 서버가 그때그때 카카오 로컬 API 로 찾는다.
     (카카오 정책: 응답 결과를 저장해 다시 쓰면 안 된다 — 그래서 여기서는 카카오를 부르지 않는다.)
  2) OpenStreetMap 도로 모양(© OpenStreetMap contributors, ODbL) — Overpass API 로 금지구역에 나오는 도로 이름만 받는다.
     서버는 이걸로 '화면 안에 있는 도로의 금지구역'만 골라 카카오 호출 수를 줄이고, 두 끝점 사이를 실제 도로를 따라 자른다.

수도권(서울·인천·경기)만 남겨 다음 둘을 쓴다.
  server/src/main/resources/data/no_parking_capital.csv.gz        금지구역(원본 칸 그대로 + 도로 이름 정리)
  server/src/main/resources/data/no_parking_roads_capital.txt.gz  도로 이름별 선(같은 이름 길을 끝점끼리 이어 붙임)

실행: python3 tools/data/build_no_parking_zones.py [--cache 폴더]
  --cache: 받은 표준데이터·Overpass 응답을 둘 폴더(다시 돌릴 때 다시 받지 않음). 기본 /tmp/way-no-parking
필요: python3 만(외부 패키지 없음)
"""

import argparse
import csv
import datetime
import gzip
import io
import json
import math
import os
import re
import ssl
import sys
import time
import urllib.parse
import urllib.request

# macOS python.org 판은 인증서 묶음이 없을 수 있어 certifi 가 있으면 쓴다(tools/map/build_offline_map.py 와 같음)
try:
    import certifi

    SSL = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL = None

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DATA = os.path.join(ROOT, "server", "src", "main", "resources", "data")
OUT_ROWS = os.path.join(DATA, "no_parking_capital.csv.gz")
OUT_ROADS = os.path.join(DATA, "no_parking_roads_capital.txt.gz")

UA = "Way-zone-builder/1.0 (hyundai-way-team)"
PK = "15034542"
PORTAL = "https://www.data.go.kr"
OVERPASS = "https://overpass-api.de/api/interpreter"
CAPITAL = ("서울특별시", "경기도", "인천광역시")
# 수도권 대략 경계(남·서·북·동) — build_bus_stops.py 와 같다
BBOX = (36.85, 126.30, 38.30, 127.90)
# 차가 다니는 길만(고속도로·보도·자전거길 빼고)
HIGHWAYS = "trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|service|road"
CHUNK = 250
# 같은 이름 길 조각을 이을 때 이보다 많이 꺾이면 잇지 않는다 — 상·하행이 따로 그려진 길이 끝에서 만나 U자 한 선이 되는 것 막기
MAX_TURN_DEG = 100.0
SIMPLIFY_M = 2.0

ROW_FIELDS = [
    ("CTPRVN_NM", "sido"),
    ("SIGNGU_NM", "sigungu"),
    ("SIGNGU_CODE", "sigungu_code"),
    ("RDNMADR", "road_raw"),
    ("DESC_LC", "desc"),
    ("PRHIB_TOT_ET", "length"),
    ("WEEKDAY_PRHIBT_OPEN_HHMM", "wk_open"),
    ("WEEKDAY_PRHIBT_COLSE_HHMM", "wk_close"),
    ("WEEKDAY_PRHIBT_PERMS_HHMM", "wk_allow_from"),
    ("WEEKDAY_PRHIBT_PERME_HHMM", "wk_allow_to"),
    ("SAT_HHMM_OPEN_HHMM", "sat_open"),
    ("SAT_HHMM_COLSE_HHMM", "sat_close"),
    ("SAT_PRHIBT_PERMS_HHMM", "sat_allow_from"),
    ("SAT_PRHIBT_PERME_HHMM", "sat_allow_to"),
    ("HOLIDAY_PRHIBT_OPEN_HHMM", "hol_open"),
    ("HOLIDAY_PRHIBT_COLSE_HHMM", "hol_close"),
    ("HOLIDAY_PRHIBT_PERMS_HHMM", "hol_allow_from"),
    ("HOLIDAY_PRHIBT_PERME_HHMM", "hol_allow_to"),
]


def http(url, data=None, referer=None):
    req = urllib.request.Request(url, data=data, headers={"User-Agent": UA})
    if referer:
        req.add_header("Referer", referer)
        req.add_header("X-Requested-With", "XMLHttpRequest")
    with urllib.request.urlopen(req, timeout=300, context=SSL) as r:
        return r.read()


def fetch_rows(cache):
    """공공데이터포털 표준데이터 '전체 내려받기'와 같은 요청(페이지마다 1만 줄)."""
    path = os.path.join(cache, "standard.json")
    if os.path.exists(path):
        return json.load(open(path, encoding="utf-8"))
    page_url = f"{PORTAL}/data/{PK}/standard.do"
    head = json.loads(http(f"{PORTAL}/download/columList.json?pk={PK}&ext=CSV", referer=page_url))
    cols = head["tableVO"]["colNmList"]
    total = int(head["totalCount"])
    rows, page = [], 1
    while len(rows) < total:
        q = [("publicDataPk", PK)] + [("colNmList", c) for c in cols]
        q += [("totalCount", str(total)), ("svcTableNm", head["tableVO"]["svcTableNm"]), ("perPage", "10000"), ("page", str(page))]
        got = json.loads(http(f"{PORTAL}/download/standard.json?" + urllib.parse.urlencode(q), referer=page_url))
        if not got:
            break
        rows += got
        page += 1
    json.dump(rows, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    return rows


ROAD_END = re.compile(r"^(.*?(?:번길|대로|로|길))[\d\-]+$")
ROAD_OK = re.compile(r"^[가-힣0-9.]+(?:번길|대로|로|길)$")


def norm_road(s):
    """'성저로112' → '성저로', '구운로45번길59-2' → '구운로45번길', 공백 없앰."""
    s = re.sub(r"\s+", "", s or "")
    m = ROAD_END.match(s)
    return m.group(1) if m else s


def roads_of(raw):
    """도로명 칸 → 도로 이름 여럿. '황무로,마소로' · '향동로+향기로' · '증신로153번길,156번길' · '설봉로(망현산로)'."""
    raw = raw or ""
    inner = re.findall(r"\(([^)]*)\)", raw)
    outer = re.sub(r"\([^)]*\)", "", raw)
    out, base = [], None
    for part in re.split(r"[,+·/]|및", outer) + inner:
        name = norm_road(part)
        if base and re.fullmatch(r"\d+번길", name):
            name = base + name
        if ROAD_OK.match(name) and "미표시" not in name:
            out.append(name)
            m = re.match(r"^(.*?(?:대로|로|길))\d+번길$", name)
            base = m.group(1) if m else name
    return list(dict.fromkeys(out))


def length_units(rows):
    """기관(시군구 코드)마다 총연장 단위를 정한다 — 값의 가운데가 10 을 넘으면 m 로 적는 기관(화성·의정부·안산·부천 …)."""
    values = {}
    for r in rows:
        try:
            values.setdefault((r.get("SIGNGU_CODE") or "").strip(), []).append(float(r.get("PRHIB_TOT_ET") or ""))
        except ValueError:
            pass
    return {code: sorted(vs)[len(vs) // 2] > 10 for code, vs in values.items() if vs}


def length_m(raw, metres):
    """총연장 → m. km 로 적는 기관은 10 이하를 km 로(0.8 → 800), m 로 적는 기관의 3 미만은 자리채움으로 보고 비운다(김포 '1' 159줄)."""
    try:
        v = float(raw)
    except (TypeError, ValueError):
        return ""
    if v <= 0:
        return ""
    if metres:
        return "" if v < 3 else f"{v:g}"
    return f"{v * 1000:g}" if v <= 10 else f"{v:g}"


def live_capital_rows(rows):
    today = datetime.date.today().isoformat()
    metres = length_units([r for r in rows if r.get("CTPRVN_NM") in CAPITAL])
    kept, seq = [], {}
    for r in rows:
        if r.get("CTPRVN_NM") not in CAPITAL:
            continue
        end = (r.get("RELIS_DATE") or "").strip()
        if end and end <= today:
            continue
        roads = roads_of(r.get("RDNMADR"))
        if not roads or not (r.get("DESC_LC") or "").strip():
            continue
        code = (r.get("SIGNGU_CODE") or "").strip()
        seq[code] = seq.get(code, 0) + 1
        out = {"id": f"np-{code}-{seq[code]}", "roads": ";".join(roads)}
        for src, dst in ROW_FIELDS:
            out[dst] = re.sub(r"\s+", " ", (r.get(src) or "").strip())
        out["length_m"] = length_m(r.get("PRHIB_TOT_ET"), metres.get(code, False))
        kept.append(out)
    return kept


def name_pattern(name):
    """Overpass 정규식 한 칸 — 'OO로12번길' 이 'OO로 12번길' 로 적혀 있어도 찾는다."""
    esc = re.sub(r"([.\\^$|?*+()\[\]{}])", r"\\\1", name)
    return re.sub(r"(로|길)(\d)", r"\1 ?\2", esc)


def fetch_ways(names, cache):
    ways = {}
    names = sorted(names)
    chunks = [names[i : i + CHUNK] for i in range(0, len(names), CHUNK)]
    for i, chunk in enumerate(chunks):
        path = os.path.join(cache, f"overpass_{i:03d}.json")
        if not os.path.exists(path):
            pattern = "|".join(name_pattern(n) for n in chunk)
            s, w, n, e = BBOX
            query = f'[out:json][timeout:300];way["highway"~"^({HIGHWAYS})$"]["name"~"^({pattern})$"]({s},{w},{n},{e});out geom;'
            for attempt in range(4):
                try:
                    body = http(OVERPASS, data=urllib.parse.urlencode({"data": query}).encode())
                    json.loads(body)
                    open(path, "wb").write(body)
                    break
                except Exception as ex:  # 바쁘면(429·504) 잠시 뒤 다시
                    print(f"  Overpass {i + 1}/{len(chunks)} 다시({ex})", file=sys.stderr)
                    time.sleep(20 * (attempt + 1))
            else:
                sys.exit(f"Overpass {i + 1} 번째 묶음을 못 받았어요")
            time.sleep(3)
        for el in json.load(open(path, encoding="utf-8")).get("elements", []):
            if el.get("type") == "way" and el.get("geometry"):
                ways[el["id"]] = el
        print(f"  도로 {i + 1}/{len(chunks)} 묶음 · 지금까지 {len(ways)} 갈래", file=sys.stderr)
    return ways.values()


def merge_chains(ways):
    """같은 이름 길 조각을 끝 노드끼리 이어 긴 선으로(OSM 은 교차로마다 길을 자른다). 반환: [(이름, 도로 종류, [(lat,lng)…])]"""
    rank = {k: i for i, k in enumerate(["trunk", "primary", "secondary", "tertiary"])}
    by_name = {}
    for w in ways:
        name = norm_road(w["tags"].get("name"))
        nodes = w.get("nodes") or []
        pts = [(p["lat"], p["lon"]) for p in w["geometry"]]
        if len(nodes) != len(pts) or len(pts) < 2:
            continue
        kind = w["tags"].get("highway", "").replace("_link", "")
        by_name.setdefault(name, []).append([nodes, pts, kind])
    out = []
    for name, parts in by_name.items():
        unused = set(range(len(parts)))
        ends = {}
        for i, (nodes, _, _) in enumerate(parts):
            ends.setdefault(nodes[0], []).append(i)
            ends.setdefault(nodes[-1], []).append(i)
        while unused:
            i = unused.pop()
            nodes, pts, kind = list(parts[i][0]), list(parts[i][1]), parts[i][2]
            for at_tail in (True, False):
                while True:
                    tip = nodes[-1] if at_tail else nodes[0]
                    # 끝에서 바깥으로 향하는 방향과 이어 붙일 조각이 나가는 방향
                    out_dir = heading(pts[-2], pts[-1]) if at_tail else heading(pts[1], pts[0])
                    best, best_turn = None, MAX_TURN_DEG
                    for j in ends.get(tip, []):
                        if j not in unused:
                            continue
                        n2, p2, _ = parts[j]
                        nxt_dir = heading(p2[0], p2[1]) if n2[0] == tip else heading(p2[-1], p2[-2])
                        turn = turn_deg(out_dir, nxt_dir)
                        if turn < best_turn:
                            best, best_turn = j, turn
                    nxt = best
                    if nxt is None:
                        break
                    unused.discard(nxt)
                    n2, p2, k2 = parts[nxt]
                    if at_tail:
                        if n2[0] != tip:
                            n2, p2 = n2[::-1], p2[::-1]
                        nodes += n2[1:]
                        pts += p2[1:]
                    else:
                        if n2[-1] != tip:
                            n2, p2 = n2[::-1], p2[::-1]
                        nodes = n2[:-1] + nodes
                        pts = p2[:-1] + pts
                    if rank.get(k2, 9) < rank.get(kind, 9):
                        kind = k2
            out.append((name, kind, simplify(pts)))
    return out


def heading(a, b):
    """두 점 방향(평면, 동·북 m)."""
    return ((b[1] - a[1]) * math.cos(math.radians(a[0])), b[0] - a[0])


def turn_deg(u, v):
    """u 로 오다가 v 로 나갈 때 꺾이는 각도(0 = 곧게, 180 = 되돌아감)."""
    nu, nv = math.hypot(*u), math.hypot(*v)
    if nu == 0 or nv == 0:
        return 0.0
    c = max(-1.0, min(1.0, (u[0] * v[0] + u[1] * v[1]) / (nu * nv)))
    return math.degrees(math.acos(c))


def simplify(pts):
    """더글러스-포이커(약 2 m) — 파일과 서버 메모리를 줄인다."""
    if len(pts) < 3:
        return pts
    lat0 = math.radians(pts[0][0])
    xy = [(p[1] * 111_320 * math.cos(lat0), p[0] * 111_320) for p in pts]
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        (ax, ay), (bx, by) = xy[a], xy[b]
        dx, dy = bx - ax, by - ay
        seg = dx * dx + dy * dy
        far, far_d = -1, SIMPLIFY_M
        for k in range(a + 1, b):
            px, py = xy[k]
            t = 0 if seg == 0 else max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / seg))
            d = math.hypot(px - (ax + t * dx), py - (ay + t * dy))
            if d > far_d:
                far, far_d = k, d
        if far >= 0:
            keep[far] = True
            stack += [(a, far), (far, b)]
    return [p for p, k in zip(pts, keep) if k]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default="/tmp/way-no-parking")
    args = ap.parse_args()
    os.makedirs(args.cache, exist_ok=True)

    rows = live_capital_rows(fetch_rows(args.cache))
    names = {n for r in rows for n in r["roads"].split(";")}
    print(f"금지구역 {len(rows)} 곳 · 도로 이름 {len(names)} 개", file=sys.stderr)

    chains = merge_chains(fetch_ways(names, args.cache))
    found = {c[0] for c in chains}
    with_road = sum(1 for r in rows if any(n in found for n in r["roads"].split(";")))
    print(f"도로 선 {len(chains)} 개 · 지도에서 도로를 찾은 금지구역 {with_road}/{len(rows)}", file=sys.stderr)

    cols = ["id", "roads"] + [dst for _, dst in ROW_FIELDS] + ["length_m"]
    buf = io.StringIO()
    w = csv.DictWriter(buf, fieldnames=cols, lineterminator="\n")
    w.writeheader()
    w.writerows(rows)
    with gzip.open(OUT_ROWS, "wt", encoding="utf-8") as f:
        f.write(buf.getvalue())

    with gzip.open(OUT_ROADS, "wt", encoding="utf-8") as f:
        f.write("# 이름\t도로 종류\t위도,경도 위도,경도 … — © OpenStreetMap contributors (ODbL)\n")
        for name, kind, pts in sorted(chains):
            f.write(f"{name}\t{kind}\t" + " ".join(f"{la:.5f},{lo:.5f}" for la, lo in pts) + "\n")
    print(f"썼어요: {OUT_ROWS} ({os.path.getsize(OUT_ROWS) // 1024} KB), {OUT_ROADS} ({os.path.getsize(OUT_ROADS) // 1024} KB)", file=sys.stderr)


if __name__ == "__main__":
    main()
