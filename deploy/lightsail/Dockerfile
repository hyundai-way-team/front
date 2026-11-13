# Way 임시 서버 — 포스크탑 Lightsail 에 함께 띄운다.
# 빌드는 맥에서(./gradlew :server:installDist) 끝내고 결과물만 넣는다 — 2GB 서버에서 Gradle 을 돌리지 않으려고.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY server/ /app/
# 동승자 웹 지도(앱과 같은 오프라인 지도)
COPY map/ /app/map/
ENV JAVA_OPTS="-Xmx256m -XX:+UseSerialGC" \
    WAY_HOST=0.0.0.0 \
    WAY_PORT=8080 \
    WAY_MAP_DIR=/app/map \
    WAY_DEV_ROUTES=0
EXPOSE 8080
HEALTHCHECK --interval=60s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/health >/dev/null || exit 1
CMD ["/app/bin/server"]
