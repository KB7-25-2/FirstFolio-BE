CREATE TABLE newsletters (
    newsletter_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '뉴스레터 식별자',
    week_start_date DATE NOT NULL COMMENT '해당 주 월요일',
    headline VARCHAR(200) NOT NULL COMMENT '대제목',
    financial_words_json JSON NOT NULL COMMENT '한주의 금융 단어 3개',
    issues_json JSON NOT NULL COMMENT '이번 주 이슈 3개',
    stats_json JSON NOT NULL COMMENT '숫자로 보는 이번 주 3개',
    status VARCHAR(20) NOT NULL COMMENT 'REVIEW, PUBLISHED, RETIRED',
    generation_type VARCHAR(10) NOT NULL COMMENT 'AI, HUMAN',
    published_at DATETIME NULL COMMENT '게시 일시',
    created_by BIGINT NOT NULL COMMENT '작성자(AI 배치는 시스템 계정)',
    created_at DATETIME NOT NULL COMMENT '등록 일시',
    CONSTRAINT pk_newsletters PRIMARY KEY (newsletter_id),
    CONSTRAINT fk_newsletters_created_by
      FOREIGN KEY (created_by) REFERENCES users (user_id)
          ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_newsletters_status CHECK (
      status IN ('REVIEW', 'PUBLISHED', 'RETIRED')
      ),
    CONSTRAINT chk_newsletters_generation_type CHECK (
      generation_type IN ('AI', 'HUMAN')
      ),
    INDEX idx_newsletters_status (status, published_at),
    INDEX idx_newsletters_week_start_date (week_start_date)
) ENGINE = InnoDB COMMENT = '주간 금융 레터';
