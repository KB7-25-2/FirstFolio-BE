# FirstFolio-BE

고등학생을 위한 금융 학습 및 모의 자산 관리 서비스 FirstFolio의 백엔드입니다.

## 기술 구성

- Java 17
- Spring Framework 기반 Legacy 애플리케이션
- Spring MVC
- MyBatis
- MySQL, HikariCP
- Gradle WAR
- 외부 Tomcat

## 시작하기

### 사전 요구사항

- JDK 17
- MySQL
- Git

### 데이터베이스

공유 가능한 환경 변수 양식을 복사해 로컬 전용 파일을 만듭니다.

```shell
cp .env.example .env.local
```

`.env.local`에 현재 개발 환경의 접속 정보를 작성합니다.

```text
DB_DRIVER=net.sf.log4jdbc.sql.jdbcapi.DriverSpy
DB_URL=jdbc:log4jdbc:mysql://localhost:3306/firstfolio_db?serverTimezone=UTC&characterEncoding=UTF-8
DB_USERNAME=firstfolio
DB_PASSWORD=
```

Railway MySQL에 외부 접속할 때는 `localhost:3306`을 Railway TCP Proxy의 실제 도메인과 포트로 변경합니다.

Spring Legacy와 외부 Tomcat은 `.env.local`을 자동으로 읽지 않으므로 실행 전에 파일 내용을 환경변수로 불러옵니다.

macOS/Linux 예시:

```shell
set -a
source .env.local
set +a
```

같은 터미널에서 Tomcat을 실행해야 환경변수가 전달됩니다.

IntelliJ에서 실행할 때는 EnvFile 플러그인을 설치한 뒤 `Run/Debug Configurations`의 `EnvFile` 항목에서 `.env.local`을 선택합니다.

실제 운영 환경에서는 `.env.local`을 서버에 배포하지 않습니다. Railway, AWS EC2 또는 Tomcat 실행 환경에 `DB_DRIVER`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 환경변수로 등록합니다.

`.env.local`과 비밀번호는 저장소에 커밋하지 않고, 변수 이름과 안전한 기본값만 `.env.example`로 공유합니다.

### JDBC 연결 테스트

JDBC 연결 테스트는 `.env.local` 또는 실행 환경변수의 `DB_DRIVER`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 사용해 실제 애플리케이션의 HikariCP `DataSource`로 MySQL에 연결하고 `SELECT 1`을 실행합니다.

```shell
./gradlew jdbcTest
```

외부 데이터베이스 상태에 따라 일반 단위 테스트가 실패하지 않도록 `./gradlew test`에서는 JDBC 연결 테스트를 제외합니다.

### 빌드와 테스트

macOS/Linux:

```shell
./gradlew clean test
./gradlew war
```

Windows:

```powershell
.\gradlew.bat clean test
.\gradlew.bat war
```

WAR 결과물은 `build/libs/firstfolio.war`입니다.

### 상태 확인

Tomcat 배포 후 아래 API로 애플리케이션 상태를 확인합니다.

```text
GET /<context-path>/api/health
```

응답:

```json
{
  "status": "UP"
}
```

## 기본 패키지 규칙

도메인을 먼저 나누고 각 도메인 내부에서 계층을 구분합니다.

```text
org.firstfolio
├── config
├── user
├── curriculum
├── dashboard
├── learning
├── quiz
├── quest
├── reward
├── leaderboard
├── portfolio
├── simulation
├── news
├── content
└── admin
    ├── controller
    ├── domain
    ├── dto
    ├── mapper
    └── service
```

- 모든 도메인은 `controller`, `domain`, `dto`, `mapper`, `service` 패키지를 기본으로 사용합니다.
- `dto` 안의 요청·응답 모델은 구현 시 `request`, `response` 하위 패키지로 나눌 수 있습니다.
- MyBatis 매퍼 인터페이스에는 `@Mapper`를 붙입니다.
- 매퍼 XML은 `src/main/resources/mappers/<domain>` 아래에 둡니다.
- `news`는 검수·발행된 뉴스 및 뉴스레터 조회 등 백엔드 책임만 담당합니다.
- 뉴스 수집, 생성, RAG 등 AI 파이프라인은 별도 프로젝트에서 관리하며 `ai` 도메인은 이 저장소에 만들지 않습니다.
- 비밀값과 개인별 설정은 Git에 커밋하지 않습니다.

## 협업 확인 사항

Pull Request를 올리기 전에 다음을 확인합니다.

- `./gradlew clean test` 통과
- 새 동작에 대한 테스트 추가
- 민감 정보가 코드·설정·로그에 포함되지 않음
- Spring Boot와 JPA를 도입하지 않음
