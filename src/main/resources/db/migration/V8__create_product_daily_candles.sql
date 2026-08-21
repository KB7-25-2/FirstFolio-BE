CREATE TABLE product_daily_candles (
    product_daily_candle_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '상품 일봉 식별자',
    product_id BIGINT NOT NULL COMMENT '가명 모의 상품 식별자',
    trade_date DATE NOT NULL COMMENT '한국 시장 기준 거래일',
    open_price DECIMAL(19, 4) NOT NULL COMMENT '시가',
    high_price DECIMAL(19, 4) NOT NULL COMMENT '고가',
    low_price DECIMAL(19, 4) NOT NULL COMMENT '저가',
    close_price DECIMAL(19, 4) NOT NULL COMMENT '종가',
    volume DECIMAL(30, 8) NOT NULL COMMENT '거래량',
    currency VARCHAR(3) NOT NULL COMMENT '통화 코드',
    adjusted BOOLEAN NOT NULL DEFAULT TRUE COMMENT '수정주가 적용 여부',
    source_type VARCHAR(30) NOT NULL COMMENT '데이터 생성 방식',
    source_reference_at DATETIME NOT NULL COMMENT '원천 캔들 기준 시각(UTC)',
    created_at DATETIME NOT NULL COMMENT '최초 저장 시각(UTC)',
    updated_at DATETIME NOT NULL COMMENT '마지막 교정 시각(UTC)',
    CONSTRAINT pk_product_daily_candles PRIMARY KEY (product_daily_candle_id),
    CONSTRAINT uq_product_daily_candles_product_date UNIQUE (product_id, trade_date),
    CONSTRAINT fk_product_daily_candles_product
        FOREIGN KEY (product_id) REFERENCES financial_products (product_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_product_daily_candles_prices CHECK (
        open_price > 0
            AND high_price > 0
            AND low_price > 0
            AND close_price > 0
            AND high_price >= open_price
            AND high_price >= close_price
            AND low_price <= open_price
            AND low_price <= close_price
    ),
    CONSTRAINT chk_product_daily_candles_volume CHECK (volume >= 0),
    CONSTRAINT chk_product_daily_candles_source CHECK (source_type = 'TOSS_INVEST'),
    INDEX idx_product_daily_candles_latest (product_id, trade_date DESC)
) ENGINE = InnoDB COMMENT = '주식·ETF의 확정 일봉 OHLCV';
