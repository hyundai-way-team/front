# Way 임시 서버 — 포스크탑 Lightsail 에 함께 올리기

Way 서버(:server, Ktor)를 포스크탑 운영 서버(Lightsail 2GB, `13.124.198.66`)에 컨테이너 하나로 함께 띄운다.
HTTPS 는 포스크탑의 Caddy 가 붙여 준다(인증서 자동). 포스크탑 서버·DB 는 건드리지 않는다.

```
인터넷 ─443→ poketdesktop-caddy ─┬→ poketdesktop-server:8080   (posktop.duckdns.org)
                                  └→ way-server:8080            (way.posktop.duckdns.org)
```

- Way 서버는 맥에서 빌드해 결과물만 올린다(서버에서 Gradle 을 돌리지 않음).
- 컨테이너 메모리 상한 420MB(JVM 힙 256MB). 띄우기 전에 서버 남은 메모리가 600MB 아래면 멈춘다.
- 운영에선 개발용 경로(`/dev/sms` 문자 사본 — 동승자 이름·링크가 있음)를 끈다(`WAY_DEV_ROUTES=0`). 상태 확인은 `/api/health`.

## 처음 한 번

1. **주소**: `way.posktop.duckdns.org` — DuckDNS 는 이름의 하위 이름도 같은 IP 로 답해서, 포스크탑 주소(`posktop.duckdns.org` → `13.124.198.66`) 아래에 계정 일 없이 쓴다. DuckDNS 는 공용 도메인 목록(PSL)에 있어 인증서 발급 한도를 남과 나눠 쓰지 않는다(11/13 Let's Encrypt 로 받음, Caddy 가 갱신). 계정 없는 sslip.io 는 한도를 여럿이 나눠 써서 쓰지 않기로.
2. **포스크탑 Caddy 가 저장소 밖 사이트 폴더를 읽게 하기**(11/13 끝남 — 포스크탑 main 1d0f646) — 포스크탑 저장소 두 줄(커밋해 둬야 포스크탑 `update.sh` 가 되돌리지 않는다):
   - `deploy/Caddyfile` 맨 끝에 `import /etc/caddy/sites/*.caddy`
   - `deploy/docker-compose.prod.yml` 의 `caddy.volumes` 에 `- /home/ubuntu/caddy-sites:/etc/caddy/sites:ro`
   - 서버에서는 **이 두 파일만** 받아 Caddy 만 다시 만든다(포스크탑 서버는 그대로, Caddy 몇 초 끊김):
     ```bash
     mkdir -p ~/caddy-sites && cd ~/poketdesktop && git fetch -q origin \
       && git checkout origin/main -- deploy/Caddyfile deploy/docker-compose.prod.yml \
       && docker compose -f deploy/docker-compose.prod.yml up -d --no-deps caddy
     ```
     `update.sh` 는 쓰지 않는다 — main 에 아직 안 낸 포스크탑 코드까지 같이 배포된다.

## 올리기(고칠 때마다)

```bash
WAY_DOMAIN=way.posktop.duckdns.org bash deploy/lightsail/deploy.sh
```

끝나면 앱 `WayConfig.SERVER_URL` 을 `https://<WAY_DOMAIN>` 으로.

## 내리기

```bash
ssh -i ~/.ssh/LightsailDefaultKey-ap-northeast-2.pem ubuntu@13.124.198.66 \
  'rm -f ~/caddy-sites/way.caddy && docker exec poketdesktop-caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile && cd ~/way && docker compose down'
```
