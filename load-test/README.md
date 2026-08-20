# FirstFolio k6 부하 테스트

k6는 Docker에서 실행하고, 로컬 Tomcat의 API에 요청을 보낸다. 실행 결과는 같은 Prometheus로 전송되어 Grafana의 `FirstFolio / Load Test Overview`에서 확인할 수 있다.

- [2026-08-20 로컬 관측·부하 테스트 결과](./TEST_RESULTS.md)

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

## 4. 테스트 계정 준비

FirstFolio 인증 API는 별도 액세스 토큰을 발급하지 않고 Firebase ID Token을 검증한다. 프론트엔드에서 로컬 테스트 계정으로 로그인해 얻은 Firebase ID Token을 `load-test/.env`에 넣는다.

```dotenv
AUTH_TOKEN=로컬-테스트-계정-Firebase-ID-Token
```

로컬 DB에 대단원·문항·강좌가 없다면 테스트 계정으로 회원가입한 뒤 최소 시드를 한 번 생성한다. `.env.local`의 DB와 로컬 콘텐츠 저장소 설정을 사용하며, localhost MySQL과 `CONTENT_STORAGE_TYPE=local`에서만 실행된다.

```shell
./load-test/seed-local.sh
```

다른 환경 파일을 사용할 때는 첫 번째 인자로 경로를 넘긴다.

```shell
./load-test/seed-local.sh /절대/경로/.env.local
```

시드는 기초 과정 1개, 자산 대단원 4개, 레벨 테스트 문항, 기초 강좌·퀴즈 및 퀴즈 보상 정책만 만든다. 기존의 다른 대단원 데이터가 있는 DB에서는 실행을 거부하며, 같은 시드 DB에서는 여러 번 실행해도 중복 생성하지 않는다. 실제 학습 내용이나 운영 초기 데이터로 사용하지 않는다.

온보딩 준비 시나리오는 계정의 현재 상태를 확인한 뒤 필요한 단계만 이어서 실행한다.

```text
백엔드 회원 미가입 → 닉네임·필수 약관 동의로 회원가입
레벨 테스트 미완료 → 레벨 테스트 응시·제출
커리큘럼 미확정 → 추천·후보 대단원 커리큘럼 확정
기초 과정 미완료 → 소단원 강좌·퀴즈·대단원 퀴즈 완료
기초 과정 완료 → 30,000,000원 포트폴리오 생성 확인
```

`TEST_NICKNAME`을 비우면 10자 이내의 임시 닉네임을 자동 생성한다. 원하는 닉네임이 있으면 로컬 설정에만 입력한다.

```dotenv
TEST_NICKNAME=k6테스트
```

레벨 테스트는 첫 선택지를 사용한다. 소단원 퀴즈는 점수와 무관하게 완료하고, 기초 대단원 퀴즈는 첫 응시 결과에서 정답을 수집한 뒤 재응시해 전체 정답으로 완료한다. 별도의 정답 파일을 저장하지 않는다.

계정 상태를 변경하므로 버려도 되는 로컬 테스트 계정인지 확인한 다음 실행한다.

```shell
docker compose \
  --env-file load-test/.env \
  -f docker-compose.load-test.yml \
  run --rm \
  -e ALLOW_STATE_CHANGES=true \
  k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/onboarding-setup.js
```

기본적으로 기초 과정 완료와 초기 모의투자금 생성까지만 수행한다. 첫 상품 매수도 필요하면 `load-test/.env`에 다음을 설정한다.

```dotenv
INITIAL_PORTFOLIO_BUY=true
INITIAL_PRODUCT_ID=
INITIAL_TRADE_AMOUNT=1000000
```

`INITIAL_PRODUCT_ID`를 비우면 공개 상품 목록의 첫 상품을 사용한다. 같은 계정과 상품으로 다시 실행해도 동일한 멱등 키를 사용한다.

## 5. 서비스 시나리오 실행

각 시나리오는 실행 전 테스트 계정이 `커리큘럼 확정 → 기초 과정 완료 → 활성 포트폴리오 생성` 상태인지 확인한다. 준비가 안 됐으면 부하를 발생시키지 않고 종료한다.

기본 `K6_PROFILE=smoke`에서는 1 VU가 사용자 여정을 한 번만 실행한다. 각 시나리오의 기능 검증이 성공한 뒤에만 `-e K6_PROFILE=load`를 붙여 약 90초·최대 10 VU 부하 테스트로 전환한다.

기본 p95 기준은 조회 API 500ms, Firebase 검증이 포함된 로그인 2,000ms이며 `performance_class` 태그로 각각 판정한다. 로컬 환경 성능 기준을 조정할 때는 `MAX_P95_MS`, `AUTH_MAX_P95_MS`를 변경한다.

### 로그인

```shell
docker compose --env-file load-test/.env -f docker-compose.load-test.yml \
  run --rm k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/auth-login.js
```

### 홈 대시보드

```shell
docker compose --env-file load-test/.env -f docker-compose.load-test.yml \
  run --rm k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/home-dashboard.js
```

### 금융상품 목록 → 상세

```shell
docker compose --env-file load-test/.env -f docker-compose.load-test.yml \
  run --rm k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/financial-products.js
```

### 커리큘럼 → 로드맵 → 강좌 → 진도

```shell
docker compose --env-file load-test/.env -f docker-compose.load-test.yml \
  run --rm k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/learning-read.js
```

### 핵심 조회 여정

홈, 학습, 상품, 포트폴리오, 포인트와 뉴스를 한 번의 사용자 여정으로 조회한다.

```shell
docker compose --env-file load-test/.env -f docker-compose.load-test.yml \
  run --rm k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/core-read-journey.js
```

예를 들어 핵심 조회 여정의 전체 부하 테스트는 다음과 같다.

```shell
docker compose --env-file load-test/.env -f docker-compose.load-test.yml \
  run --rm -e K6_PROFILE=load \
  k6 run --out experimental-prometheus-rw \
  /scripts/scenarios/core-read-journey.js
```

## 단일 GET API 사전 점검

`load-test/.env`에서 경로를 바꾼다. 인증 API라면 로컬 테스트 계정의 Firebase ID Token도 입력한다.

```dotenv
TARGET_PATH=/api/financial-products
AUTH_TOKEN=로컬-테스트-계정-Firebase-ID-Token
K6_TEST_ID=financial-products-local
```

쓰기 API는 데이터 중복 생성과 포인트·자산 변경 위험이 있으므로 이 기본 GET 시나리오에 넣지 않는다. 별도 스크립트와 전용 테스트 데이터로 분리한다.

## 안전장치

`BASE_URL`이 localhost, `127.0.0.1`, `host.docker.internal`이 아니면 테스트가 시작되지 않는다. 개발 또는 스테이징 서버를 대상으로 실행해야 할 때만 비용과 허용 요청량을 먼저 확인한 뒤 아래 값을 명시한다.

```dotenv
ALLOW_REMOTE_TARGET=true
```

운영 환경을 대상으로는 실행하지 않는다.

온보딩처럼 데이터를 변경하는 시나리오는 `ALLOW_STATE_CHANGES=true`가 없으면 시작되지 않는다. 일반 조회 시나리오는 이 값을 사용하지 않는다. 포인트·퀴즈 보상·거래 동시성처럼 반복 실행 시 상태가 누적되는 부하 테스트는 사용자별 전용 계정 풀과 초기화 방법을 마련한 뒤 별도 시나리오로 추가한다.
