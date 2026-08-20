# FirstFolio k6 부하 테스트

k6는 Docker에서 실행하고, 로컬 Tomcat의 API에 요청을 보낸다. 실행 결과는 같은 Prometheus로 전송되어 Grafana의 `FirstFolio / Load Test Overview`에서 확인할 수 있다.

```text
k6 컨테이너 → 로컬 Tomcat :8080
       └── 테스트 결과 → Prometheus → Grafana
```

## 1. 준비

1. 로컬 Tomcat을 실행하고 `http://localhost:8080/api/health`가 `UP`인지 확인한다.
2. Prometheus·Grafana 관측 환경을 실행한다.
3. 최초 한 번만 로컬 설정 파일을 만든다.

```shell
cp load-test/.env.example load-test/.env
```

기본 대상은 `http://host.docker.internal:8080/api/health`다. 운영 서버 오실행을 막기 위해 localhost 계열이 아닌 주소는 기본적으로 거부한다.

## 2. smoke 테스트

먼저 1명의 가상 사용자가 3번 요청하는 smoke 테스트로 연결과 기준값을 확인한다.

```shell
docker compose \
  --env-file load-test/.env \
  -f docker-compose.load-test.yml \
  run --rm k6
```

`checks`, `http_req_failed`, `http_req_duration`의 모든 기준이 통과하면 다음 단계로 진행한다.

## 3. load 테스트

기본 설정은 약 90초 동안 0명에서 최대 10 VU까지 올렸다가 다시 0명으로 내린다. 각 VU는 요청 후 1초 동안 대기한다.

```shell
docker compose \
  --env-file load-test/.env \
  -f docker-compose.load-test.yml \
  run --rm k6 \
  run --out experimental-prometheus-rw /scripts/load-test.js
```

실행 중에는 다음 두 대시보드를 함께 본다.

- `FirstFolio / Load Test Overview`: k6 요청량, 실패율, 응답 시간, VU
- `FirstFolio / Backend Overview`: 서버 요청률, 5xx, JVM, HikariCP

## 다른 API 테스트

`load-test/.env`에서 경로를 바꾼다. 인증 API라면 로컬 테스트 계정의 액세스 토큰도 입력한다.

```dotenv
TARGET_PATH=/api/financial-products
AUTH_TOKEN=로컬-테스트-계정-access-token
K6_TEST_ID=financial-products-local
```

쓰기 API는 데이터 중복 생성과 포인트·자산 변경 위험이 있으므로 이 기본 GET 시나리오에 넣지 않는다. 별도 스크립트와 전용 테스트 데이터로 분리한다.

## 원격 환경 안전장치

`BASE_URL`이 localhost, `127.0.0.1`, `host.docker.internal`이 아니면 테스트가 시작되지 않는다. 개발 또는 스테이징 서버를 대상으로 실행해야 할 때만 비용과 허용 요청량을 먼저 확인한 뒤 아래 값을 명시한다.

```dotenv
ALLOW_REMOTE_TARGET=true
```

운영 환경을 대상으로는 실행하지 않는다.
