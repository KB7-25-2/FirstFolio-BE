-- FirstFolio service database baseline DDL
-- Source: Notion "ERD 2.1" (24 tables total)
-- Scope: 23 non-AI tables. AI/RAG metadata is defined in the AI service DDL.
-- Target: MySQL 8.0.16+ (CHECK constraints are enforced from 8.0.16).
-- Time policy: application code writes UTC values to DATETIME columns.

CREATE DATABASE IF NOT EXISTS firstfolio_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE firstfolio_db;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'FirstFolio 내부 사용자 식별자. 다른 도메인의 FK 기준',
    firebase_uid VARCHAR(128) NOT NULL COMMENT '검증된 Firebase ID Token의 uid',
    email VARCHAR(255) NULL COMMENT '검증된 Firebase 사용자 이메일 스냅샷',
    nickname VARCHAR(50) NOT NULL COMMENT '서비스 및 리더보드 표시 이름',
    role_code VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'MySQL을 권한 기준으로 사용하는 USER 또는 ADMIN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'FirstFolio 서비스 이용 상태. ACTIVE, SUSPENDED, WITHDRAWN',
    point_balance INT NOT NULL DEFAULT 0 COMMENT '현재 포인트 잔액. 모의투자금과 별도',
    last_attendance_date DATE NULL COMMENT '마지막 출석 인정 날짜',
    newsletter_opt_in BOOLEAN NOT NULL DEFAULT FALSE COMMENT '뉴스레터 수신 동의 현재 상태. 변경 이력은 user_consents에 기록',
    last_login_at DATETIME NULL COMMENT '마지막 로그인 인증 성공 시각',
    created_at DATETIME NOT NULL COMMENT '가입 일시',
    updated_at DATETIME NOT NULL COMMENT '마지막 수정 일시',
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_firebase_uid UNIQUE (firebase_uid),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_nickname UNIQUE (nickname),
    CONSTRAINT chk_users_role_code CHECK (role_code IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT chk_users_point_balance CHECK (point_balance >= 0),
    INDEX idx_users_role_status (role_code, status)
) ENGINE = InnoDB COMMENT = 'Firebase 사용자 연동, 공개 프로필, 권한, 서비스 상태와 사용자 현재값';

CREATE TABLE user_consents (
   consent_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '동의 이력 식별자',
   user_id BIGINT NOT NULL COMMENT '동의한 FirstFolio 사용자',
   consent_type VARCHAR(30) NOT NULL COMMENT 'TERMS_OF_SERVICE, PRIVACY, NEWSLETTER',
   policy_version VARCHAR(50) NOT NULL COMMENT '동의 또는 철회 대상 정책 버전',
   is_agreed BOOLEAN NOT NULL COMMENT 'TRUE는 동의, FALSE는 철회',
   occurred_at DATETIME NOT NULL COMMENT '동의 또는 철회 발생 시각',
   CONSTRAINT pk_user_consents PRIMARY KEY (consent_id),
   CONSTRAINT fk_user_consents_user
       FOREIGN KEY (user_id) REFERENCES users (user_id)
           ON UPDATE RESTRICT ON DELETE RESTRICT,
   CONSTRAINT chk_user_consents_type CHECK (
       consent_type IN ('TERMS_OF_SERVICE', 'PRIVACY', 'NEWSLETTER')
       ),
   INDEX idx_user_consents_latest (user_id, consent_type, occurred_at)
) ENGINE = InnoDB COMMENT = '약관과 선택 동의의 버전별 동의·철회 이벤트 이력';

CREATE TABLE system_policies (
    policy_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '정책 버전 식별자',
    policy_key VARCHAR(50) NOT NULL COMMENT 'QUIZ_REWARD, ATTENDANCE, LEADERBOARD, SIMULATION, TRADE, RESET, GIFTICON, AI_REVIEW 등',
    version_no INT NOT NULL COMMENT '정책별 버전 번호',
    config_json JSON NOT NULL COMMENT '정답 수별 포인트와 상품에 종속되지 않는 전역 운영 설정',
    effective_from DATETIME NOT NULL COMMENT '적용 시작 시각',
    effective_to DATETIME NULL COMMENT '적용 종료 시각',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '현재 활성 여부',
    created_by BIGINT NOT NULL COMMENT '정책 등록 관리자',
    created_at DATETIME NOT NULL COMMENT '등록 일시',
    CONSTRAINT pk_system_policies PRIMARY KEY (policy_id),
    CONSTRAINT uq_system_policies_key_version UNIQUE (policy_key, version_no),
    CONSTRAINT fk_system_policies_created_by
     FOREIGN KEY (created_by) REFERENCES users (user_id)
         ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_system_policies_version CHECK (version_no > 0),
    CONSTRAINT chk_system_policies_period CHECK (
     effective_to IS NULL OR effective_to > effective_from
     ),
    INDEX idx_system_policies_active_period (policy_key, is_active, effective_from, effective_to)
) ENGINE = InnoDB COMMENT = '버전형 보상·거래·리더보드·전역 운영 정책';

CREATE TABLE main_chapters (
    main_chapter_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '대단원 식별자',
    chapter_type VARCHAR(20) NOT NULL COMMENT 'FOUNDATION 또는 ASSET',
    asset_type VARCHAR(30) NULL COMMENT 'DEPOSIT_SAVINGS, BOND, STOCK, FUND. 기초 과정은 NULL',
    title VARCHAR(100) NOT NULL COMMENT '대단원명',
    description TEXT NULL COMMENT '대단원 설명',
    display_order INT NOT NULL COMMENT '노출 순서',
    is_required BOOLEAN NOT NULL DEFAULT FALSE COMMENT '필수 과정 여부',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '공개·사용 여부',
    created_at DATETIME NOT NULL COMMENT '생성 일시',
    updated_at DATETIME NOT NULL COMMENT '수정 일시',
    CONSTRAINT pk_main_chapters PRIMARY KEY (main_chapter_id),
    CONSTRAINT chk_main_chapters_type CHECK (
       (chapter_type = 'FOUNDATION' AND asset_type IS NULL)
           OR
       (
           chapter_type = 'ASSET'
               AND asset_type IN ('DEPOSIT_SAVINGS', 'BOND', 'STOCK', 'FUND')
           )
       ),
    CONSTRAINT chk_main_chapters_display_order CHECK (display_order > 0),
    INDEX idx_main_chapters_active_order (is_active, display_order),
    INDEX idx_main_chapters_type (chapter_type, asset_type)
) ENGINE = InnoDB COMMENT = '포트폴리오 기초 과정과 자산 대단원';

-- current_content_version_id is added after content_versions because the two
-- tables form a deliberate circular reference.
CREATE TABLE sub_chapters (
    sub_chapter_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '소단원 식별자',
    main_chapter_id BIGINT NOT NULL COMMENT '소속 대단원',
    title VARCHAR(100) NOT NULL COMMENT '소단원명',
    description TEXT NULL COMMENT '소단원 설명',
    display_order INT NOT NULL COMMENT '대단원 내 순서',
    current_content_version_id BIGINT NULL COMMENT '현재 공개 중인 소단원 JSON 버전',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '공개·사용 여부',
    created_at DATETIME NOT NULL COMMENT '생성 일시',
    updated_at DATETIME NOT NULL COMMENT '수정 일시',
    CONSTRAINT pk_sub_chapters PRIMARY KEY (sub_chapter_id),
    CONSTRAINT uq_sub_chapters_chapter_order UNIQUE (main_chapter_id, display_order),
    CONSTRAINT fk_sub_chapters_main_chapter
      FOREIGN KEY (main_chapter_id) REFERENCES main_chapters (main_chapter_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_sub_chapters_display_order CHECK (display_order > 0),
    INDEX idx_sub_chapters_active (main_chapter_id, is_active, display_order)
) ENGINE = InnoDB COMMENT = '대단원 아래 소단원 강좌 메타데이터';

CREATE TABLE content_versions (
    content_version_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '학습 콘텐츠 버전 식별자',
    sub_chapter_id BIGINT NOT NULL COMMENT '대상 소단원',
    version_no INT NOT NULL COMMENT '소단원별 버전 번호',
    schema_version VARCHAR(20) NOT NULL COMMENT '학습 JSON 스키마 버전',
    storage_object_key VARCHAR(500) NOT NULL COMMENT '정적 콘텐츠 저장소 객체 키',
    storage_version_id VARCHAR(1024) NOT NULL COMMENT '정적 콘텐츠 저장소 객체 버전 식별자',
    status VARCHAR(20) NOT NULL COMMENT 'DRAFT, REVIEW, PUBLISHED, RETIRED',
    published_at DATETIME NULL COMMENT '게시 일시',
    created_by BIGINT NOT NULL COMMENT '업로드 관리자',
    created_at DATETIME NOT NULL COMMENT '버전 생성 일시',
    CONSTRAINT pk_content_versions PRIMARY KEY (content_version_id),
    CONSTRAINT uq_content_versions_sub_version UNIQUE (sub_chapter_id, version_no),
    CONSTRAINT fk_content_versions_sub_chapter
      FOREIGN KEY (sub_chapter_id) REFERENCES sub_chapters (sub_chapter_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_content_versions_created_by
      FOREIGN KEY (created_by) REFERENCES users (user_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_content_versions_version CHECK (version_no > 0),
    CONSTRAINT chk_content_versions_status CHECK (
      status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'RETIRED')
      ),
    INDEX idx_content_versions_status (sub_chapter_id, status, published_at)
) ENGINE = InnoDB COMMENT = '정적 콘텐츠 저장소의 소단원 JSON 불변 버전 메타데이터';

ALTER TABLE sub_chapters
    ADD CONSTRAINT fk_sub_chapters_current_content
        FOREIGN KEY (current_content_version_id)
            REFERENCES content_versions (content_version_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE TABLE user_curriculum_items (
    curriculum_item_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '개인 커리큘럼 항목 식별자',
    user_id BIGINT NOT NULL COMMENT '커리큘럼 소유 사용자',
    main_chapter_id BIGINT NOT NULL COMMENT '선택된 대단원',
    display_order INT NOT NULL COMMENT '사용자가 확정한 학습 순서',
    source_type VARCHAR(30) NOT NULL COMMENT 'REQUIRED, LEVEL_TEST_WRONG, CART',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 또는 REMOVED',
    confirmed_at DATETIME NOT NULL COMMENT '커리큘럼 확정 일시',
    completed_at DATETIME NULL COMMENT '대단원 완료 일시',
    CONSTRAINT pk_user_curriculum_items PRIMARY KEY (curriculum_item_id),
    CONSTRAINT uq_user_curriculum_user_chapter UNIQUE (user_id, main_chapter_id),
    CONSTRAINT fk_user_curriculum_user
       FOREIGN KEY (user_id) REFERENCES users (user_id)
           ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_user_curriculum_main_chapter
       FOREIGN KEY (main_chapter_id) REFERENCES main_chapters (main_chapter_id)
           ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_user_curriculum_order CHECK (display_order > 0),
    CONSTRAINT chk_user_curriculum_source CHECK (
       source_type IN ('REQUIRED', 'LEVEL_TEST_WRONG', 'CART')
       ),
    CONSTRAINT chk_user_curriculum_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    INDEX idx_user_curriculum_active_order (user_id, status, display_order)
) ENGINE = InnoDB COMMENT = '사용자가 확정한 개인 커리큘럼 구성과 순서';

CREATE TABLE user_learning_progress (
    progress_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '학습 진도 식별자',
    user_id BIGINT NOT NULL COMMENT '학습 사용자',
    sub_chapter_id BIGINT NOT NULL COMMENT '진행 중인 소단원',
    content_version_id BIGINT NOT NULL COMMENT '사용자가 학습한 소단원 JSON 버전',
    last_page_id VARCHAR(100) NULL COMMENT '소단원 JSON 내부 마지막 학습 페이지 ID',
    status VARCHAR(20) NOT NULL COMMENT 'NOT_STARTED, IN_PROGRESS, COMPLETED',
    started_at DATETIME NULL COMMENT '학습 시작 일시',
    completed_at DATETIME NULL COMMENT '소단원 완료 일시',
    updated_at DATETIME NOT NULL COMMENT '진도 갱신 일시',
    CONSTRAINT pk_user_learning_progress PRIMARY KEY (progress_id),
    CONSTRAINT uq_user_learning_progress_user_sub UNIQUE (user_id, sub_chapter_id),
    CONSTRAINT fk_user_learning_progress_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_user_learning_progress_sub
        FOREIGN KEY (sub_chapter_id) REFERENCES sub_chapters (sub_chapter_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_user_learning_progress_content
        FOREIGN KEY (content_version_id) REFERENCES content_versions (content_version_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_user_learning_progress_status CHECK (
        status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')
        ),
    INDEX idx_user_learning_progress_continue (user_id, status, updated_at)
) ENGINE = InnoDB COMMENT = '사용자별 소단원 진행 위치와 최초 완료 상태';

CREATE TABLE quiz_questions (
    question_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '특정 문항 버전 행의 식별자',
    question_key VARCHAR(100) NOT NULL COMMENT '버전 간 동일 문항을 묶는 논리 키',
    version_no INT NOT NULL COMMENT '문항 버전 번호',
    usage_type VARCHAR(30) NOT NULL COMMENT 'LEVEL_TEST, SUB_CHAPTER, MAIN_CHAPTER, DAILY_GENERAL, DAILY_NEWS',
    main_chapter_id BIGINT NULL COMMENT '소속 대단원',
    sub_chapter_id BIGINT NULL COMMENT '소속 소단원. 대단원 퀴즈 문항은 NULL',
    display_order INT NULL COMMENT 'DB 범위 내 기본 문항 순서',
    question_type VARCHAR(30) NOT NULL COMMENT 'SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE, SCENARIO',
    difficulty VARCHAR(20) NULL COMMENT 'EASY, MEDIUM, HARD',
    prompt TEXT NOT NULL COMMENT '모든 문항에 공통으로 사용하는 질문 문장',
    scenario_json JSON NULL COMMENT '상황판단형의 캐릭터 상황, 금융시장 상황과 제약 조건',
    options_json JSON NULL COMMENT '선택지 배열',
    correct_answer_json JSON NOT NULL COMMENT '정답 데이터',
    explanation TEXT NOT NULL COMMENT '정답 해설',
    source_refs_json JSON NULL COMMENT 'AI DB knowledge_contents ID, 근거 출처와 기준 시점. DB 간 FK는 애플리케이션에서 검증',
    status VARCHAR(20) NOT NULL COMMENT 'DRAFT, REVIEW, PUBLISHED, RETIRED',
    created_by BIGINT NOT NULL COMMENT '작성자 또는 생성 작업 관리자',
    published_at DATETIME NULL COMMENT '게시 일시',
    created_at DATETIME NOT NULL COMMENT '버전 생성 일시',
    CONSTRAINT pk_quiz_questions PRIMARY KEY (question_id),
    CONSTRAINT uq_quiz_questions_key_version UNIQUE (question_key, version_no),
    CONSTRAINT fk_quiz_questions_main_chapter
        FOREIGN KEY (main_chapter_id) REFERENCES main_chapters (main_chapter_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_questions_sub_chapter
        FOREIGN KEY (sub_chapter_id) REFERENCES sub_chapters (sub_chapter_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_questions_created_by
        FOREIGN KEY (created_by) REFERENCES users (user_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_questions_version CHECK (version_no > 0),
    CONSTRAINT chk_quiz_questions_usage CHECK (
        usage_type IN (
                       'LEVEL_TEST',
                       'SUB_CHAPTER',
                       'MAIN_CHAPTER',
                       'DAILY_GENERAL',
                       'DAILY_NEWS'
            )
        ),
    CONSTRAINT chk_quiz_questions_type CHECK (
        question_type IN (
                          'SINGLE_CHOICE',
                          'MULTIPLE_CHOICE',
                          'TRUE_FALSE',
                          'SCENARIO'
            )
        ),
    CONSTRAINT chk_quiz_questions_difficulty CHECK (
        difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')
        ),
    CONSTRAINT chk_quiz_questions_scenario CHECK (
        (question_type = 'SCENARIO' AND scenario_json IS NOT NULL)
            OR
        (question_type <> 'SCENARIO' AND scenario_json IS NULL)
        ),
    CONSTRAINT chk_quiz_questions_status CHECK (
        status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'RETIRED')
        ),
    CONSTRAINT chk_quiz_questions_scope CHECK (
        (usage_type = 'SUB_CHAPTER' AND sub_chapter_id IS NOT NULL)
            OR
        (usage_type IN ('LEVEL_TEST', 'MAIN_CHAPTER')
            AND main_chapter_id IS NOT NULL
            AND sub_chapter_id IS NULL)
            OR
        (usage_type IN ('DAILY_GENERAL', 'DAILY_NEWS'))
        ),
    INDEX idx_quiz_questions_usage_status (usage_type, status, main_chapter_id),
    INDEX idx_quiz_questions_sub_status (sub_chapter_id, status)
) ENGINE = InnoDB COMMENT = '레벨 테스트·소단원·대단원·일일 퀘스트 문항 버전 원본';

CREATE TABLE quiz_attempts (
    attempt_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '퀴즈 응시 식별자',
    user_id BIGINT NOT NULL COMMENT '응시 사용자',
    quiz_type VARCHAR(30) NOT NULL COMMENT 'LEVEL_TEST, SUB_CHAPTER, MAIN_CHAPTER',
    main_chapter_id BIGINT NULL COMMENT '대단원 또는 레벨 테스트 대상',
    sub_chapter_id BIGINT NULL COMMENT '소단원 퀴즈 대상',
    content_version_id BIGINT NULL COMMENT '소단원 흐름에서 응시한 학습 JSON 버전',
    attempt_no INT NOT NULL DEFAULT 1 COMMENT '같은 퀴즈의 응시 순번',
    status VARCHAR(20) NOT NULL COMMENT 'IN_PROGRESS, SUBMITTED, GRADED',
    total_count INT NOT NULL DEFAULT 0 COMMENT '전체 문제 수',
    correct_count INT NOT NULL DEFAULT 0 COMMENT '정답 수',
    score INT NOT NULL DEFAULT 0 COMMENT '채점 점수',
    started_at DATETIME NOT NULL COMMENT '응시 시작 일시',
    submitted_at DATETIME NULL COMMENT '최종 제출 일시',
    CONSTRAINT pk_quiz_attempts PRIMARY KEY (attempt_id),
    CONSTRAINT fk_quiz_attempts_user
       FOREIGN KEY (user_id) REFERENCES users (user_id)
           ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_attempts_main_chapter
       FOREIGN KEY (main_chapter_id) REFERENCES main_chapters (main_chapter_id)
           ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_attempts_sub_chapter
       FOREIGN KEY (sub_chapter_id) REFERENCES sub_chapters (sub_chapter_id)
           ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_attempts_content_version
       FOREIGN KEY (content_version_id) REFERENCES content_versions (content_version_id)
           ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_attempts_type CHECK (
       quiz_type IN ('LEVEL_TEST', 'SUB_CHAPTER', 'MAIN_CHAPTER')
       ),
    CONSTRAINT chk_quiz_attempts_status CHECK (
       status IN ('IN_PROGRESS', 'SUBMITTED', 'GRADED')
       ),
    CONSTRAINT chk_quiz_attempts_counts CHECK (
       attempt_no > 0
           AND total_count >= 0
           AND correct_count >= 0
           AND correct_count <= total_count
           AND score >= 0
       ),
    CONSTRAINT chk_quiz_attempts_scope CHECK (
       (quiz_type = 'SUB_CHAPTER' AND sub_chapter_id IS NOT NULL)
           OR
       (quiz_type IN ('LEVEL_TEST', 'MAIN_CHAPTER')
           AND main_chapter_id IS NOT NULL
           AND sub_chapter_id IS NULL)
       ),
    INDEX idx_quiz_attempts_user_status (user_id, quiz_type, status, started_at),
    INDEX idx_quiz_attempts_main_chapter (main_chapter_id, user_id, attempt_no),
    INDEX idx_quiz_attempts_sub_chapter (sub_chapter_id, user_id, attempt_no)
) ENGINE = InnoDB COMMENT = '레벨·소단원·대단원 퀴즈 응시';

CREATE TABLE quiz_answers (
    quiz_answer_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '문항별 답안 식별자',
    attempt_id BIGINT NOT NULL COMMENT '소속 응시',
    question_id BIGINT NOT NULL COMMENT '출제한 특정 문항 버전 행',
    display_order INT NOT NULL COMMENT '응시 내 문항 순서',
    question_snapshot_json JSON NOT NULL COMMENT '출제 당시 본문·시나리오·선택지·정답·해설 스냅샷',
    user_answer_json JSON NULL COMMENT '사용자가 제출한 답안',
    is_correct BOOLEAN NULL COMMENT '채점 전에는 NULL',
    answered_at DATETIME NULL COMMENT '답안 제출 시각',
    created_at DATETIME NOT NULL COMMENT '출제 이력 생성 시각',
    CONSTRAINT pk_quiz_answers PRIMARY KEY (quiz_answer_id),
    CONSTRAINT uq_quiz_answers_attempt_question UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_quiz_answers_attempt
      FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (attempt_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_answers_question
      FOREIGN KEY (question_id) REFERENCES quiz_questions (question_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_quiz_answers_display_order CHECK (display_order > 0),
    INDEX idx_quiz_answers_attempt_order (attempt_id, display_order)
) ENGINE = InnoDB COMMENT = '퀴즈 문항별 사용자 답안·채점·문항 스냅샷';

CREATE TABLE point_transactions (
    point_transaction_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '포인트 원장 식별자',
    user_id BIGINT NOT NULL COMMENT '대상 사용자',
    transaction_type VARCHAR(20) NOT NULL COMMENT 'EARN, USE, REFUND, EXPIRE',
    amount INT NOT NULL COMMENT '증감 포인트. 적립은 양수, 사용은 음수',
    reason_type VARCHAR(30) NOT NULL COMMENT 'QUIZ, DAILY_QUEST, ATTENDANCE, GIFTICON, ADMIN 등',
    reason_id BIGINT NULL COMMENT '응시·퀘스트·주문 등 근거 식별자',
    balance_after INT NOT NULL COMMENT '반영 후 포인트 잔액',
    idempotency_key VARCHAR(120) NOT NULL COMMENT '중복 지급·차감 방지 키',
    occurred_at DATETIME NOT NULL COMMENT '포인트 발생 시각',
    created_at DATETIME NOT NULL COMMENT '원장 기록 시각',
    CONSTRAINT pk_point_transactions PRIMARY KEY (point_transaction_id),
    CONSTRAINT uq_point_transactions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_point_transactions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_point_transactions_type CHECK (
        transaction_type IN ('EARN', 'USE', 'REFUND', 'EXPIRE')
        ),
    CONSTRAINT chk_point_transactions_amount CHECK (amount <> 0),
    CONSTRAINT chk_point_transactions_balance CHECK (balance_after >= 0),
    INDEX idx_point_transactions_user_time (user_id, occurred_at),
    INDEX idx_point_transactions_reason (reason_type, reason_id)
) ENGINE = InnoDB COMMENT = '포인트 적립·사용·취소·만료 원장';

CREATE TABLE daily_quests (
    daily_quest_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '일일 퀘스트 식별자',
    user_id BIGINT NOT NULL COMMENT '대상 사용자',
    quest_date DATE NOT NULL COMMENT '서비스 기준 퀘스트 날짜',
    status VARCHAR(20) NOT NULL COMMENT 'ASSIGNED, IN_PROGRESS, COMPLETED',
    total_count INT NOT NULL DEFAULT 5 COMMENT '문항 수. 초기 정책은 5',
    correct_count INT NOT NULL DEFAULT 0 COMMENT '정답 수',
    score INT NOT NULL DEFAULT 0 COMMENT '리더보드 반영 점수',
    point_transaction_id BIGINT NULL COMMENT '완료 보상 원장',
    completed_at DATETIME NULL COMMENT '완료 일시',
    CONSTRAINT pk_daily_quests PRIMARY KEY (daily_quest_id),
    CONSTRAINT uq_daily_quests_user_date UNIQUE (user_id, quest_date),
    CONSTRAINT fk_daily_quests_user
      FOREIGN KEY (user_id) REFERENCES users (user_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_daily_quests_point_transaction
      FOREIGN KEY (point_transaction_id) REFERENCES point_transactions (point_transaction_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_daily_quests_status CHECK (
      status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED')
      ),
    CONSTRAINT chk_daily_quests_counts CHECK (
      total_count = 5
          AND correct_count BETWEEN 0 AND 5
          AND score >= 0
      ),
    INDEX idx_daily_quests_date_status (quest_date, status),
    INDEX idx_daily_quests_user_status (user_id, status, quest_date)
) ENGINE = InnoDB COMMENT = '사용자별 매일 5문제 일일 퀘스트';

CREATE TABLE daily_quest_items (
    daily_quest_item_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '일일 문항 배정 식별자',
    daily_quest_id BIGINT NOT NULL COMMENT '소속 일일 퀘스트',
    question_id BIGINT NOT NULL COMMENT '배정한 특정 문항 버전 행',
    source_type VARCHAR(20) NOT NULL COMMENT 'GENERAL, WRONG_RETRY, NEWS',
    display_order INT NOT NULL COMMENT '1~5 노출 순서',
    question_snapshot_json JSON NOT NULL COMMENT '배정 당시 시나리오를 포함한 문항 전체 스냅샷',
    user_answer_json JSON NULL COMMENT '사용자 답안',
    is_correct BOOLEAN NULL COMMENT '채점 결과',
    answered_at DATETIME NULL COMMENT '답안 제출 시각',
    created_at DATETIME NOT NULL COMMENT '배정 일시',
    CONSTRAINT pk_daily_quest_items PRIMARY KEY (daily_quest_item_id),
    CONSTRAINT uq_daily_quest_items_order UNIQUE (daily_quest_id, display_order),
    CONSTRAINT fk_daily_quest_items_quest
       FOREIGN KEY (daily_quest_id) REFERENCES daily_quests (daily_quest_id)
           ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_daily_quest_items_question
       FOREIGN KEY (question_id) REFERENCES quiz_questions (question_id)
           ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_daily_quest_items_source CHECK (
       source_type IN ('GENERAL', 'WRONG_RETRY', 'NEWS')
       ),
    CONSTRAINT chk_daily_quest_items_order CHECK (display_order BETWEEN 1 AND 5),
    INDEX idx_daily_quest_items_question (question_id)
) ENGINE = InnoDB COMMENT = '일일 퀘스트 문항 배정·답안·채점 이력';

CREATE TABLE leaderboard_rankings (
    leaderboard_ranking_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '순위 스냅샷 행 식별자',
    ranking_type VARCHAR(30) NOT NULL COMMENT '초기값 WEEKLY_DAILY_QUEST',
    period_start DATE NOT NULL COMMENT '주간 집계 시작일',
    period_end DATE NOT NULL COMMENT '주간 집계 종료일',
    snapshot_at DATETIME NOT NULL COMMENT '순위 생성 시각',
    user_id BIGINT NOT NULL COMMENT '순위 사용자',
    score INT NOT NULL COMMENT '기간 누적 점수',
    rank_no INT NOT NULL COMMENT '확정 순위',
    tie_breaker_json JSON NULL COMMENT '동점 처리 근거',
    created_at DATETIME NOT NULL COMMENT '저장 일시',
    CONSTRAINT pk_leaderboard_rankings PRIMARY KEY (leaderboard_ranking_id),
    CONSTRAINT uq_leaderboard_snapshot_user UNIQUE (ranking_type, snapshot_at, user_id),
    CONSTRAINT fk_leaderboard_rankings_user
      FOREIGN KEY (user_id) REFERENCES users (user_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_leaderboard_type CHECK (ranking_type = 'WEEKLY_DAILY_QUEST'),
    CONSTRAINT chk_leaderboard_period CHECK (period_end >= period_start),
    CONSTRAINT chk_leaderboard_values CHECK (score >= 0 AND rank_no > 0),
    INDEX idx_leaderboard_lookup (ranking_type, period_start, period_end, snapshot_at, rank_no),
    INDEX idx_leaderboard_user (user_id, snapshot_at)
) ENGINE = InnoDB COMMENT = '매일 생성하는 주간 일일 퀘스트 순위 스냅샷';

CREATE TABLE financial_products (
    product_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '모의 상품 식별자',
    asset_type VARCHAR(30) NOT NULL COMMENT 'DEPOSIT_SAVINGS, BOND, STOCK, FUND',
    display_name VARCHAR(150) NOT NULL COMMENT '사용자 노출 가명 상품명',
    description TEXT NULL COMMENT '교육용 상품 설명',
    source_provider VARCHAR(150) NOT NULL COMMENT '실제 데이터 제공처',
    source_product_code VARCHAR(150) NOT NULL COMMENT '내부용 원상품 식별 코드',
    source_product_name VARCHAR(200) NOT NULL COMMENT '내부용 실제 상품명',
    source_reference_at DATETIME NOT NULL COMMENT '실제 데이터 기준 시점',
    real_terms_json JSON NOT NULL COMMENT '실제 금리·수익률·만기·주기·위험도',
    simulation_terms_json JSON NOT NULL COMMENT '상품별 압축 배율과 모의 계산 조건의 유일한 저장 위치',
    risk_level VARCHAR(30) NULL COMMENT '위험도',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '선택 가능 여부',
    created_at DATETIME NOT NULL COMMENT '등록 일시',
    updated_at DATETIME NOT NULL COMMENT '수정 일시',
    CONSTRAINT pk_financial_products PRIMARY KEY (product_id),
    CONSTRAINT chk_financial_products_asset_type CHECK (
        asset_type IN ('DEPOSIT_SAVINGS', 'BOND', 'STOCK', 'FUND')
        ),
    INDEX idx_financial_products_active_type (is_active, asset_type),
    INDEX idx_financial_products_source (source_provider, source_product_code)
) ENGINE = InnoDB COMMENT = '실제 원천 정보와 가명 모의 금융상품';

CREATE TABLE product_prices (
    product_price_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '상품 가격 이력 식별자',
    product_id BIGINT NOT NULL COMMENT '주식·펀드 모의 상품',
    price DECIMAL(19, 4) NOT NULL COMMENT '모의 기준 가격',
    reference_at DATETIME NOT NULL COMMENT '가격 기준 시각',
    source_type VARCHAR(20) NOT NULL COMMENT 'REAL_DATA 또는 SIMULATION',
    generation_key VARCHAR(100) NOT NULL COMMENT '동일 시점 가격 중복 생성 방지 키',
    created_at DATETIME NOT NULL COMMENT '저장 일시',
    CONSTRAINT pk_product_prices PRIMARY KEY (product_price_id),
    CONSTRAINT uq_product_prices_generation_key UNIQUE (generation_key),
    CONSTRAINT uq_product_prices_product_time UNIQUE (product_id, reference_at),
    CONSTRAINT fk_product_prices_product
        FOREIGN KEY (product_id) REFERENCES financial_products (product_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_product_prices_price CHECK (price >= 0),
    CONSTRAINT chk_product_prices_source CHECK (
        source_type IN ('REAL_DATA', 'SIMULATION')
        ),
    INDEX idx_product_prices_latest (product_id, reference_at DESC)
) ENGINE = InnoDB COMMENT = '주식·펀드 모의 가격의 시점별 이력';

CREATE TABLE portfolios (
    portfolio_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '모의 포트폴리오 식별자',
    user_id BIGINT NOT NULL COMMENT '포트폴리오 소유 사용자',
    generation_no INT NOT NULL DEFAULT 1 COMMENT '파산 신청 후 증가하는 포트폴리오 세대',
    status VARCHAR(20) NOT NULL COMMENT 'ACTIVE 또는 CLOSED',
    initial_amount DECIMAL(19, 2) NOT NULL DEFAULT 30000000 COMMENT '최초·재설정 모의투자금',
    cash_balance DECIMAL(19, 2) NOT NULL COMMENT '현재 모의 현금',
    opened_at DATETIME NOT NULL COMMENT '세대 시작 일시',
    closed_at DATETIME NULL COMMENT '세대 종료 일시',
    created_at DATETIME NOT NULL COMMENT '생성 일시',
    updated_at DATETIME NOT NULL COMMENT '수정 일시',
    CONSTRAINT pk_portfolios PRIMARY KEY (portfolio_id),
    CONSTRAINT uq_portfolios_user_generation UNIQUE (user_id, generation_no),
    CONSTRAINT fk_portfolios_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_portfolios_generation CHECK (generation_no > 0),
    CONSTRAINT chk_portfolios_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_portfolios_amounts CHECK (
        initial_amount > 0 AND cash_balance >= 0
        ),
    INDEX idx_portfolios_user_status (user_id, status)
) ENGINE = InnoDB COMMENT = '사용자별 모의 포트폴리오와 현재 모의 현금';

CREATE TABLE portfolio_holdings (
    holding_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '보유 상품 식별자',
    portfolio_id BIGINT NOT NULL COMMENT '소속 포트폴리오',
    product_id BIGINT NOT NULL COMMENT '보유 모의 상품',
    quantity DECIMAL(19, 6) NOT NULL DEFAULT 0 COMMENT '보유 수량',
    principal_amount DECIMAL(19, 2) NOT NULL DEFAULT 0 COMMENT '투입 원금',
    average_cost DECIMAL(19, 4) NULL COMMENT '평균 매입 단가',
    terms_snapshot_json JSON NOT NULL COMMENT '가입·매수 당시 상품·시뮬레이션 조건',
    status VARCHAR(20) NOT NULL COMMENT 'ACTIVE, MATURED, SOLD',
    created_at DATETIME NOT NULL COMMENT '최초 보유 일시',
    updated_at DATETIME NOT NULL COMMENT '마지막 변경 일시',
    CONSTRAINT pk_portfolio_holdings PRIMARY KEY (holding_id),
    CONSTRAINT uq_portfolio_holdings_product UNIQUE (portfolio_id, product_id),
    CONSTRAINT fk_portfolio_holdings_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES portfolios (portfolio_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_portfolio_holdings_product
        FOREIGN KEY (product_id) REFERENCES financial_products (product_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_portfolio_holdings_values CHECK (
        quantity >= 0
            AND principal_amount >= 0
            AND (average_cost IS NULL OR average_cost >= 0)
        ),
    CONSTRAINT chk_portfolio_holdings_status CHECK (
        status IN ('ACTIVE', 'MATURED', 'SOLD')
        ),
    INDEX idx_portfolio_holdings_status (portfolio_id, status)
) ENGINE = InnoDB COMMENT = '포트폴리오 현재 보유 상품과 조건 스냅샷';

CREATE TABLE portfolio_transactions (
    portfolio_transaction_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '모의 자산 거래·이벤트 식별자',
    portfolio_id BIGINT NOT NULL COMMENT '대상 포트폴리오',
    holding_id BIGINT NULL COMMENT '대상 보유 상품',
    product_id BIGINT NULL COMMENT '대상 모의 상품',
    transaction_type VARCHAR(30) NOT NULL COMMENT 'INITIAL_GRANT, BUY, SELL, INTEREST, DIVIDEND, MATURITY, RESET',
    amount DECIMAL(19, 2) NOT NULL COMMENT '현금 증감 또는 거래 금액',
    quantity DECIMAL(19, 6) NULL COMMENT '매수·매도 수량',
    unit_price DECIMAL(19, 4) NULL COMMENT '거래 단가',
    status VARCHAR(20) NOT NULL COMMENT 'SCHEDULED, COMPLETED, FAILED, CANCELLED',
    scheduled_at DATETIME NULL COMMENT '이자·배당·만기 예정 시각',
    processed_at DATETIME NULL COMMENT '실제 반영 시각',
    event_key VARCHAR(120) NULL COMMENT '자산 이벤트 이중 반영 방지 키',
    idempotency_key VARCHAR(120) NOT NULL COMMENT '동일 요청 이중 처리 방지 키',
    detail_json JSON NULL COMMENT '수수료·세금·계산 근거·초기화 전후 정보',
    created_at DATETIME NOT NULL COMMENT '기록 생성 일시',
    CONSTRAINT pk_portfolio_transactions PRIMARY KEY (portfolio_transaction_id),
    CONSTRAINT uq_portfolio_transactions_event_key UNIQUE (event_key),
    CONSTRAINT uq_portfolio_transactions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_portfolio_transactions_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES portfolios (portfolio_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_portfolio_transactions_holding
        FOREIGN KEY (holding_id) REFERENCES portfolio_holdings (holding_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_portfolio_transactions_product
        FOREIGN KEY (product_id) REFERENCES financial_products (product_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_portfolio_transactions_type CHECK (
        transaction_type IN (
                             'INITIAL_GRANT',
                             'BUY',
                             'SELL',
                             'INTEREST',
                             'DIVIDEND',
                             'MATURITY',
                             'RESET'
            )
        ),
    CONSTRAINT chk_portfolio_transactions_status CHECK (
        status IN ('SCHEDULED', 'COMPLETED', 'FAILED', 'CANCELLED')
        ),
    CONSTRAINT chk_portfolio_transactions_values CHECK (
        (quantity IS NULL OR quantity >= 0)
            AND (unit_price IS NULL OR unit_price >= 0)
        ),
    INDEX idx_portfolio_transactions_portfolio_time (portfolio_id, created_at),
    INDEX idx_portfolio_transactions_schedule (status, scheduled_at),
    INDEX idx_portfolio_transactions_product (product_id, created_at)
) ENGINE = InnoDB COMMENT = '매수·매도·자산 이벤트·지급·초기화 이력';

CREATE TABLE gifticon_products (
    gifticon_product_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '기프티콘 상품 식별자',
    name VARCHAR(100) NOT NULL COMMENT '상품명',
    category VARCHAR(50) NOT NULL COMMENT '카페, 편의점 등',
    required_points INT NOT NULL COMMENT '교환 필요 포인트',
    stock_quantity INT NOT NULL DEFAULT 0 COMMENT '현재 재고',
    status VARCHAR(20) NOT NULL COMMENT 'ON_SALE, SOLD_OUT, STOPPED',
    image_url VARCHAR(1000) NULL COMMENT '상품 이미지',
    valid_until DATE NULL COMMENT '판매·사용 유효일',
    created_at DATETIME NOT NULL COMMENT '등록 일시',
    updated_at DATETIME NOT NULL COMMENT '수정 일시',
    CONSTRAINT pk_gifticon_products PRIMARY KEY (gifticon_product_id),
    CONSTRAINT chk_gifticon_products_points CHECK (required_points > 0),
    CONSTRAINT chk_gifticon_products_stock CHECK (stock_quantity >= 0),
    CONSTRAINT chk_gifticon_products_status CHECK (
       status IN ('ON_SALE', 'SOLD_OUT', 'STOPPED')
       ),
    INDEX idx_gifticon_products_market (status, category, required_points)
) ENGINE = InnoDB COMMENT = '교환 가능한 기프티콘 상품과 재고';

CREATE TABLE gifticon_orders (
     gifticon_order_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '기프티콘 교환 주문 식별자',
     user_id BIGINT NOT NULL COMMENT '교환 사용자',
     gifticon_product_id BIGINT NOT NULL COMMENT '교환 상품',
     point_transaction_id BIGINT NOT NULL COMMENT '포인트 차감 원장',
     status VARCHAR(20) NOT NULL COMMENT 'REQUESTED, SENT, COMPLETED',
     delivery_info VARCHAR(255) NULL COMMENT '발송에 필요한 최소 정보',
     provider_reference VARCHAR(255) NULL COMMENT '공급사 처리 식별자',
     idempotency_key VARCHAR(100) NOT NULL COMMENT '중복 주문 방지 키',
     requested_at DATETIME NOT NULL COMMENT '교환 신청 일시',
     completed_at DATETIME NULL COMMENT '발송·완료 일시',
     CONSTRAINT pk_gifticon_orders PRIMARY KEY (gifticon_order_id),
     CONSTRAINT uq_gifticon_orders_point_transaction UNIQUE (point_transaction_id),
     CONSTRAINT uq_gifticon_orders_idempotency UNIQUE (idempotency_key),
     CONSTRAINT fk_gifticon_orders_user
         FOREIGN KEY (user_id) REFERENCES users (user_id)
             ON UPDATE RESTRICT ON DELETE RESTRICT,
     CONSTRAINT fk_gifticon_orders_product
         FOREIGN KEY (gifticon_product_id) REFERENCES gifticon_products (gifticon_product_id)
             ON UPDATE RESTRICT ON DELETE RESTRICT,
     CONSTRAINT fk_gifticon_orders_point_transaction
         FOREIGN KEY (point_transaction_id) REFERENCES point_transactions (point_transaction_id)
             ON UPDATE RESTRICT ON DELETE RESTRICT,
     CONSTRAINT chk_gifticon_orders_status CHECK (
         status IN ('REQUESTED', 'SENT', 'COMPLETED')
         ),
     INDEX idx_gifticon_orders_user_time (user_id, requested_at),
     INDEX idx_gifticon_orders_status_time (status, requested_at)
) ENGINE = InnoDB COMMENT = '기프티콘 교환 신청·포인트 차감·발송 상태';

CREATE TABLE admin_audit_logs (
    audit_log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '관리자·중요 작업 이력 식별자',
    actor_user_id BIGINT NULL COMMENT '작업 관리자 또는 시스템 사용자',
    action_type VARCHAR(50) NOT NULL COMMENT 'CREATE, UPDATE, PUBLISH, RETIRE, REWARD, RESET, REVIEW 등',
    entity_type VARCHAR(50) NOT NULL COMMENT '대상 테이블·도메인 유형',
    entity_id BIGINT NULL COMMENT '대상 레코드 식별자',
    before_json JSON NULL COMMENT '변경 전 값',
    after_json JSON NULL COMMENT '변경 후 값',
    request_id VARCHAR(100) NULL COMMENT '요청·배치 추적 식별자',
    result_status VARCHAR(20) NOT NULL COMMENT 'SUCCESS 또는 FAILED',
    created_at DATETIME NOT NULL COMMENT '작업 발생 일시',
    CONSTRAINT pk_admin_audit_logs PRIMARY KEY (audit_log_id),
    CONSTRAINT fk_admin_audit_logs_actor
      FOREIGN KEY (actor_user_id) REFERENCES users (user_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_admin_audit_logs_result CHECK (
      result_status IN ('SUCCESS', 'FAILED')
      ),
    INDEX idx_admin_audit_logs_entity (entity_type, entity_id, created_at),
    INDEX idx_admin_audit_logs_actor (actor_user_id, created_at),
    INDEX idx_admin_audit_logs_request (request_id)
) ENGINE = InnoDB COMMENT = '관리자와 중요 시스템 작업의 변경 감사 이력';

-- MySQL cannot express these ERD rules with ordinary cross-row constraints.
-- They must be enforced transactionally by the Spring service:
-- 1. Exactly one active FOUNDATION main chapter.
-- 2. Every confirmed curriculum includes FOUNDATION at display_order = 1.
-- 3. A user has at most one ACTIVE portfolio.
-- 4. LEVEL_TEST questions reference only ASSET chapters.
-- 5. A content version assigned to sub_chapters.current_content_version_id
--    belongs to the same sub_chapter.
-- 6. COMPLETED learning progress is not modified by later re-learning.
-- 7. FOUNDATION completion creates exactly one INITIAL_GRANT using a
--    deterministic user_id + curriculum_item_id idempotency key.
-- 8. Published questions and content versions are not updated or deleted.
