# FirstFolio-BE

고등학생을 위한 금융 학습 및 모의 자산 관리 서비스 FirstFolio의 백엔드입니다.

## 기술 구성

- Java 17
- Spring Framework 기반 Legacy 애플리케이션
- Spring MVC
- Firebase Authentication, Firebase Admin SDK
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

### Firebase Authentication

Firebase Console에서 프로젝트와 Web App을 생성하고 Authentication의 로그인 제공자를 활성화합니다. 백엔드는 Firebase Admin SDK로 클라이언트가 전달한 ID Token을 검증합니다.

Firebase Console의 `프로젝트 설정 > 서비스 계정`에서 로컬 개발용 서비스 계정 키를 발급하고 저장소 외부에 보관합니다. 서비스 계정 JSON 파일이나 Private Key는 Git에 커밋하지 않습니다.

`.env.local`에 Firebase 프로젝트 ID와 서비스 계정 JSON의 절대 경로를 작성합니다.

```text
FIREBASE_PROJECT_ID=firstfolio-local
GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/firebase-service-account.json
TERMS_OF_SERVICE_VERSION=2026-08-01
PRIVACY_POLICY_VERSION=2026-08-01
NEWSLETTER_POLICY_VERSION=2026-08-01
```

`GOOGLE_APPLICATION_CREDENTIALS`는 Google Application Default Credentials가 직접 읽습니다. `FIREBASE_PROJECT_ID`는 `application.properties`의 `firebase.project-id`로 연결됩니다.

`TERMS_OF_SERVICE_VERSION`, `PRIVACY_POLICY_VERSION`, `NEWSLETTER_POLICY_VERSION`에는 현재 서비스에 적용 중인 실제 문서 버전을 입력합니다. 회원가입과 뉴스레터 수신 동의 변경 시 서버가 이 버전과 동의·철회 시각을 `user_consents` 이력에 저장하므로 운영 환경에서도 반드시 설정해야 합니다. 위 날짜는 형식 예시이며 실제 정책 버전으로 교체합니다.

데이터베이스 환경변수와 동일하게 Tomcat을 실행하기 전에 `.env.local`을 현재 터미널에 불러오거나 IntelliJ EnvFile 설정으로 전달합니다. Firebase Bean은 실제 인증 기능에서 처음 사용할 때 초기화되므로 일반 단위 테스트에는 서비스 계정 파일이 필요하지 않습니다.

운영 환경에서는 서비스 계정 JSON을 Docker 이미지에 포함하지 않습니다. GitHub Actions의 production Environment Secret으로 관리하고 배포 단계에서 EC2의 제한된 경로에 파일을 생성한 뒤, 컨테이너의 `/run/secrets/firebase-admin.json`에 읽기 전용으로 마운트합니다.

```text
FIREBASE_PROJECT_ID=firstfolio-production
GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/firebase-admin.json
```

### CORS

백엔드는 `/api/**` 요청에 대해 `CORS_ALLOWED_ORIGINS`에 등록된 프론트엔드 출처만 허용합니다. 여러 주소는 쉼표로 구분하고 경로나 마지막 슬래시는 넣지 않습니다.

로컬 개발 환경:

```text
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

Vercel 운영 환경과 같이 프론트엔드와 백엔드 출처가 다르면 EC2 또는 Tomcat 실행 환경에 실제 프론트엔드 운영 주소를 등록합니다.

```text
CORS_ALLOWED_ORIGINS=https://first-folio-fe.vercel.app
```

위 Vercel 주소는 형식 예시이므로 Vercel 프로젝트의 실제 Production Domain으로 교체합니다. 전체 출처를 허용하는 `*`는 사용하지 않습니다. Firebase 인증은 `Authorization` 헤더를 사용하며 쿠키 기반 자격 증명은 허용하지 않습니다.

### JDBC 연결 테스트

JDBC 연결 테스트는 `.env.local` 또는 실행 환경변수의 `DB_DRIVER`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 사용해 실제 애플리케이션의 HikariCP `DataSource`로 MySQL에 연결하고 `SELECT 1`을 실행합니다.

```shell
./gradlew jdbcTest
```

외부 데이터베이스 상태에 따라 일반 단위 테스트가 실패하지 않도록 `./gradlew test`에서는 JDBC 연결 테스트를 제외합니다.

### 인증 API

프론트엔드는 Firebase Client SDK에서 인증한 뒤 발급받은 ID Token을 다음 형식으로 전달합니다.

```text
Authorization: Bearer {Firebase ID Token}
```

- `POST /api/auth/signup`: FirstFolio 사용자와 필수 약관 동의 이력을 생성합니다.
- `POST /api/auth/login`: 사용자 상태를 확인하고 마지막 로그인 시각과 다음 진입 단계를 반환합니다.
- `POST /api/auth/logout`: 토큰을 확인하고 204를 반환합니다. 성공 후 프론트엔드가 Firebase Client SDK의 `signOut`을 호출해야 합니다.

현재 로그아웃은 현재 기기 로그아웃만 지원하며 Firebase Refresh Token을 폐기하는 전체 기기 로그아웃은 수행하지 않습니다.

### 인증이 필요한 API에서 현재 사용자 조회

`/api/auth/**`, `/api/health`를 제외한 API 요청은 Firebase 인증 인터셉터가 `Authorization` 헤더의 ID Token을 검증합니다. 검증된 Firebase UID와 연결된 활성 FirstFolio 사용자를 조회한 뒤 Controller의 `@CurrentUser` 파라미터에 내부 사용자 정보를 주입합니다.

```java
@GetMapping("/portfolio")
public ApiResponse<PortfolioResponse> getPortfolio(
        @CurrentUser AuthenticatedUser currentUser
) {
    long userId = currentUser.userId();

    return ApiResponse.of(
            portfolioService.getPortfolio(userId)
    );
}
```

`AuthenticatedUser`에서는 FirstFolio 내부 `userId`, Firebase UID, 닉네임과 `roleCode`를 조회할 수 있습니다. 요청 본문이나 경로에서 받은 사용자 ID를 현재 로그인 사용자로 신뢰하지 않습니다.

### 사용자 프로필 API

인증된 사용자는 자신의 공개 프로필과 뉴스레터 수신 동의 상태를 조회·수정할 수 있습니다.

- `GET /api/users/me`: 현재 사용자 프로필 조회
- `PATCH /api/users/me`: 닉네임과 뉴스레터 수신 동의 상태 중 전달된 필드만 수정

닉네임은 2자 이상 10자 이하이며 중복을 허용하지 않습니다. 뉴스레터 동의 상태가 실제로 변경되면 `NEWSLETTER_POLICY_VERSION`과 변경 시각을 동의 이력에 기록합니다.

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
