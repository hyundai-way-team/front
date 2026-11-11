#!/bin/sh
# 로컬 서버 https(시연용) 인증서 만들기.
# 휴대폰 사파리는 https 에서만 위치 공유를 허락한다 → 이 맥 안에서 인증 기관(CA)을 만들고, 그 CA 를 아이폰에 '신뢰'로 한 번 등록한다.
# 외부 서비스는 쓰지 않는다. 결과는 저장소 밖 ~/.way-dev-tls 에 둔다(개인 키 포함 — 커밋 금지).
#
#   tools/dev/make_local_https.sh [호스트 이름]     (기본: 이 맥의 이름.local)
#
# 만든 뒤: 서버를 WAY_TLS_DIR=~/.way-dev-tls 로 띄우고, 아이폰 사파리로 http://<호스트>:8080/dev/ca.mobileconfig 를 열어 허용 →
#         설정 > 일반 > VPN 및 기기 관리 에서 설치 → 설정 > 일반 > 정보 > 인증서 신뢰 설정 에서 'Way Dev CA' 켜기.
#         시연이 끝나면 프로파일을 지운다.
# CA 는 90일만 쓴다(오래 남지 않게).
set -eu
HOST="${1:-$(scutil --get LocalHostName).local}"
DIR="${WAY_TLS_DIR:-$HOME/.way-dev-tls}"
mkdir -p "$DIR"
chmod 700 "$DIR"
cd "$DIR"

if [ ! -f ca.key ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 90 \
    -keyout ca.key -out ca.pem \
    -subj "/CN=Way Dev CA ($HOST)" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null
fi

cat > server.ext <<EXT
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:$HOST,DNS:localhost
EXT
openssl req -newkey rsa:2048 -nodes -keyout server.key -out server.csr -subj "/CN=$HOST" 2>/dev/null
openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial -out server.pem -days 89 -sha256 -extfile server.ext 2>/dev/null

PASS="$(openssl rand -hex 16)"
openssl pkcs12 -export -name way -inkey server.key -in server.pem -certfile ca.pem -out keystore.p12 -passout "pass:$PASS"
printf %s "$PASS" > keystore.pass
openssl x509 -in ca.pem -outform der -out ca.cer
chmod 600 ca.key server.key keystore.p12 keystore.pass
rm -f server.csr server.ext

echo "만듦: $DIR (호스트 $HOST)"
openssl x509 -in server.pem -noout -subject -enddate -ext subjectAltName
