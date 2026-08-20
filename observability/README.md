# FirstFolio 관측 환경

백엔드 배포 구성과 분리된 Prometheus·Grafana 실행 환경이다. 이 Compose는 백엔드나 k6를 실행하지 않으며 `docker-compose.deploy.yml`을 변경하지 않는다.

```text
백엔드 :8080/internal/metrics
    ↑ X-Internal-Token, 15초마다 수집
Prometheus :9090
    ↑ 데이터 소스 조회
Grafana :3000
```

k6 테스트를 실행하면 결과 메트릭도 Prometheus로 전송되며 Grafana의 `FirstFolio / Load Test Overview`에서 확인할 수 있다. k6 자체는 이 Compose에 포함하지 않고 `docker-compose.load-test.yml`로 필요할 때만 실행한다.

## 준비

1. Docker를 실행한다.
2. 백엔드를 호스트의 `8080` 포트에서 실행한다.
3. 관측 환경 변수를 만든다.

```shell
cp observability/.env.example observability/.env
```

4. `observability/.env`의 `GRAFANA_ADMIN_PASSWORD`를 변경한다.
5. 백엔드의 `INTERNAL_CALL_TOKEN`과 **완전히 동일한 값**을 아래 파일에 저장한다. 마지막 개행 없이 저장하는 것을 권장한다.

```shell
printf '%s' '백엔드와-동일한-토큰' > observability/secrets/internal-call-token
chmod 600 observability/secrets/internal-call-token
```

토큰 파일과 `observability/.env`는 Git에 포함되지 않는다.

## 실행

저장소 루트에서 다음 명령을 실행한다.

```shell
docker compose \
  --env-file observability/.env \
  -f docker-compose.observability.yml \
  up -d
```

- Prometheus 대상 상태: <http://localhost:9090/targets>
- Grafana: <http://localhost:3000>
- Grafana의 `FirstFolio / Backend Overview`, `FirstFolio / Load Test Overview` 대시보드와 Prometheus 데이터 소스는 시작할 때 자동 등록된다.

Prometheus의 `firstfolio-be` 대상이 `DOWN`이면 백엔드 실행 여부, `8080` 포트, 토큰 파일 값과 백엔드 `INTERNAL_CALL_TOKEN`을 확인한다.

## 종료

```shell
docker compose \
  --env-file observability/.env \
  -f docker-compose.observability.yml \
  down
```

일반 종료에서는 Prometheus와 Grafana 데이터 볼륨을 유지한다. 데이터를 모두 지우고 초기화할 때만 명시적으로 `down -v`를 사용한다.

## AWS EC2에서 사용할 때

관측 Compose를 백엔드와 같은 EC2에서 실행해도 Prometheus는 호스트의 공개 포트 `8080`을 통해 백엔드를 수집한다. Prometheus와 Grafana 포트는 `127.0.0.1`에만 바인딩되므로 보안 그룹에서 `3000`, `9090`을 열지 않는다.

관리 화면은 SSH 터널로 접속한다.

```shell
ssh \
  -L 3000:127.0.0.1:3000 \
  -L 9090:127.0.0.1:9090 \
  ec2-user@EC2_HOST
```

장기 운영에서는 별도 인증 프록시, TLS, 백업과 알림 채널 구성을 추가한다.
