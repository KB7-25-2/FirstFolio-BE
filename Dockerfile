# ── 빌드 스테이지 ───────────────────────────────
FROM gradle:8.8-jdk17 AS build

WORKDIR /src

COPY build.gradle settings.gradle ./
COPY src ./src

RUN gradle war --no-daemon


# ── 실행 스테이지 ───────────────────────────────
# Tomcat 9 고정. build.gradle 이 javax.servlet 을 쓰므로 Tomcat 10 이상에서는
# 배포는 성공하지만 모든 요청이 404 가 된다 (jakarta.servlet 으로 패키지가 바뀜).
FROM tomcat:9.0-jdk17-temurin

ENV TZ=UTC
ENV CATALINA_OPTS="-Duser.timezone=UTC -Xmx1g"

# ROOT.war 로 넣어야 컨텍스트 경로가 / 가 되어 /api/health 로 접근된다.
# 다른 이름이면 /firstfolio/api/health 가 되어 프론트 프록시와 어긋난다.
COPY --from=build /src/build/libs/firstfolio.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
