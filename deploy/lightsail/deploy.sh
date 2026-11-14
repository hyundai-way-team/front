#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Way 임시 서버를 포스크탑 Lightsail 에 올린다(맥에서 실행).
#
#   WAY_DOMAIN=way.posktop.duckdns.org bash deploy/lightsail/deploy.sh
#
# 하는 일
#   1. 맥에서 서버를 빌드(:server:installDist) — 2GB 서버에서 Gradle 을 돌리지 않는다
#   2. 결과물 + 지도 + Dockerfile 을 묶어 서버 ~/way 로 보낸다
#   3. 서버 메모리가 넉넉한지 보고, way-server 컨테이너를 포스크탑 Caddy 망에 띄운다
#   4. ~/caddy-sites/way.caddy 를 쓰고 Caddy 를 다시 읽힌다(재시작 아님 — 포스크탑은 안 끊긴다)
#   5. https://$WAY_DOMAIN/api/health 확인
#
# 먼저 한 번 필요한 것(README.md): 포스크탑 Caddy 가 ~/caddy-sites 를 읽게 하기(11/13 끝남)
# ---------------------------------------------------------------------------
set -euo pipefail

DOMAIN="${WAY_DOMAIN:?예: WAY_DOMAIN=way.posktop.duckdns.org bash deploy/lightsail/deploy.sh}"
HOST="${WAY_SSH_HOST:-ubuntu@13.124.198.66}"
KEY="${WAY_SSH_KEY:-$HOME/.ssh/LightsailDefaultKey-ap-northeast-2.pem}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

say()  { printf '\n\033[1;33m== %s\033[0m\n' "$*"; }
ok()   { printf '   \033[32mOK\033[0m  %s\n' "$*"; }
warn() { printf '   \033[31m!!\033[0m  %s\n' "$*"; }

[ -f "$KEY" ] || { warn "키가 없습니다: $KEY"; exit 1; }

say "1. 서버 빌드(맥)"
(cd "$ROOT" && ./gradlew -q --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process :server:installDist)
ok "$ROOT/server/build/install/server"

say "2. 묶어서 보내기"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp "$ROOT/deploy/lightsail/Dockerfile" "$ROOT/deploy/lightsail/docker-compose.yml" "$ROOT/deploy/lightsail/way.caddy.template" "$STAGE/"
cp -R "$ROOT/server/build/install/server" "$STAGE/server"
cp -R "$ROOT/app/src/main/assets/map" "$STAGE/map"
# 맥 파일 속성은 빼고 묶는다(서버 tar 가 모르는 머리글 경고)
COPYFILE_DISABLE=1 tar --no-mac-metadata -czf "$STAGE.tgz" -C "$STAGE" .
ok "$(du -h "$STAGE.tgz" | cut -f1) 묶음"
scp -q -i "$KEY" -o StrictHostKeyChecking=accept-new "$STAGE.tgz" "$HOST:way-deploy.tgz"
rm -f "$STAGE.tgz"
ok "보냄"

say "3~4. 서버에서 띄우기"
ssh -i "$KEY" -o StrictHostKeyChecking=accept-new "$HOST" "WAY_DOMAIN='$DOMAIN' bash -s" <<'REMOTE'
set -euo pipefail
ok()   { printf '   \033[32mOK\033[0m  %s\n' "$*"; }
warn() { printf '   \033[31m!!\033[0m  %s\n' "$*"; }

CADDY=poketdesktop-caddy
docker inspect "$CADDY" >/dev/null 2>&1 || { warn "포스크탑 Caddy 컨테이너가 없습니다"; exit 1; }
if ! docker exec "$CADDY" sh -c 'grep -q "import /etc/caddy/sites/" /etc/caddy/Caddyfile && test -d /etc/caddy/sites'; then
  warn "포스크탑 Caddy 가 ~/caddy-sites 를 아직 읽지 않습니다 — README.md '처음 한 번' 을 먼저 해 주세요"
  exit 1
fi

AVAIL_KB=$(awk '/MemAvailable/ {print $2}' /proc/meminfo)
if [ "$AVAIL_KB" -lt 600000 ]; then
  warn "남은 메모리가 $((AVAIL_KB / 1024))MB 라 띄우지 않습니다(600MB 필요)"
  exit 1
fi
ok "남은 메모리 $((AVAIL_KB / 1024))MB"

mkdir -p ~/way
rm -rf ~/way/server ~/way/map
tar xzf ~/way-deploy.tgz -C ~/way
rm -f ~/way-deploy.tgz
NET=$(docker inspect "$CADDY" -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' | awk '{print $1}')
printf 'WAY_DOMAIN=%s\nCADDY_NETWORK=%s\n' "$WAY_DOMAIN" "$NET" > ~/way/.env
ok "망 $NET"
# 카카오 키 파일은 처음 한 번 빈 파일로만 만든다(키는 사람이 직접 넣는다 — README '카카오 로컬 API 키')
if [ ! -f ~/way/kakao.env ]; then (umask 077 && : > ~/way/kakao.env); fi
if grep -q '^KAKAO_REST_KEY=.' ~/way/kakao.env; then ok "카카오 키 있음 — 지정 주정차 금지구역 켬"; else warn "카카오 키 없음 — 버스정류장 10m 만"; fi

cd ~/way
docker compose up -d --build 2>&1 | tail -3
for _i in $(seq 1 30); do
  sleep 2
  if docker exec "$CADDY" wget -qO- http://way-server:8080/api/health >/dev/null 2>&1; then break; fi
done
docker exec "$CADDY" wget -qO- http://way-server:8080/api/health >/dev/null 2>&1 || { warn "way-server 가 응답하지 않습니다"; docker logs --tail 30 way-server; exit 1; }
ok "way-server 응답"

mkdir -p ~/caddy-sites
sed "s/__WAY_DOMAIN__/$WAY_DOMAIN/" ~/way/way.caddy.template > ~/caddy-sites/way.caddy
docker exec "$CADDY" caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
ok "Caddy 에 $WAY_DOMAIN 추가(다시 읽기)"
docker ps --format '   {{.Names}}  {{.Status}}'
REMOTE

say "5. 밖에서 확인"
for _i in $(seq 1 30); do
  if curl -fsS --max-time 5 "https://$DOMAIN/api/health" >/dev/null 2>&1; then
    ok "https://$DOMAIN/api/health"
    echo "   앱 WayConfig.SERVER_URL = \"https://$DOMAIN\""
    exit 0
  fi
  sleep 2 # 처음엔 인증서를 받느라 잠깐 걸린다
done
warn "https://$DOMAIN 이 아직 안 열립니다 — 주소가 13.124.198.66 을 가리키는지, Caddy 로그(docker logs poketdesktop-caddy)를 확인해 주세요"
exit 1
