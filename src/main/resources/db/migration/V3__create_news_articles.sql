CREATE TABLE news_articles (
    financial_news_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '금융 뉴스 식별자',
    title VARCHAR(255) NOT NULL COMMENT '뉴스 제목',
    summary TEXT NOT NULL COMMENT '요약',
    image_url VARCHAR(1024) NULL COMMENT '썸네일 이미지 URL',
    source_name VARCHAR(100) NOT NULL COMMENT '원문 언론사명',
    source_url VARCHAR(1024) NOT NULL COMMENT '원문 기사 URL',
    source_published_at DATETIME NOT NULL COMMENT '원문 기사 발행 시각',
    published_at DATETIME NOT NULL COMMENT '서비스 노출 시각',
    created_at DATETIME NOT NULL COMMENT '등록 일시',
    updated_at DATETIME NOT NULL COMMENT '마지막 수정 일시',
    CONSTRAINT pk_news_articles PRIMARY KEY (financial_news_id),
    INDEX idx_news_articles_published_at (published_at)
) ENGINE = InnoDB COMMENT = '금융 뉴스 스크랩 기사';
