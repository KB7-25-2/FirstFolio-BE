-- Redesign the not-yet-operated gifticon market around prepaid individual codes.
--
-- The old gifticon tables have no production data, so this migration deliberately
-- drops them instead of guessing how aggregate stock or delivery states map to
-- encrypted code inventory and completed in-service exchanges.

DROP TABLE gifticon_orders;
DROP TABLE gifticon_products;

CREATE TABLE gifticon_products (
    gifticon_product_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '기프티콘 상품 식별자',
    name VARCHAR(100) NOT NULL COMMENT '상품명',
    brand_name VARCHAR(100) NOT NULL COMMENT '사용자에게 표시할 브랜드명',
    category VARCHAR(50) NOT NULL COMMENT '카페, 편의점 등',
    face_value_krw INT NOT NULL COMMENT '기프티콘 원화 액면가',
    required_points INT NOT NULL COMMENT '정가 교환에 필요한 포인트. face_value_krw와 동일',
    status VARCHAR(20) NOT NULL COMMENT 'ON_SALE, STOPPED. 품절은 가용 코드 존재 여부로 계산',
    image_url VARCHAR(1000) NULL COMMENT '상품 이미지',
    created_at DATETIME NOT NULL COMMENT '등록 일시',
    updated_at DATETIME NOT NULL COMMENT '수정 일시',
    CONSTRAINT pk_gifticon_products PRIMARY KEY (gifticon_product_id),
    CONSTRAINT chk_gifticon_products_face_value CHECK (face_value_krw > 0),
    CONSTRAINT chk_gifticon_products_points CHECK (
        required_points > 0 AND required_points = face_value_krw
        ),
    CONSTRAINT chk_gifticon_products_status CHECK (
        status IN ('ON_SALE', 'STOPPED')
        ),
    INDEX idx_gifticon_products_market (status, category, required_points)
) ENGINE = InnoDB COMMENT = '포인트로 정가 교환하는 기프티콘 상품';

CREATE TABLE gifticon_codes (
    gifticon_code_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '선구매한 개별 기프티콘 코드 식별자',
    gifticon_product_id BIGINT NOT NULL COMMENT '코드가 속한 기프티콘 상품',
    code_ciphertext VARBINARY(1024) NOT NULL COMMENT '암호화한 실제 기프티콘 코드',
    barcode_ciphertext VARBINARY(1024) NULL COMMENT '코드와 다를 때 암호화해 저장하는 바코드 값',
    code_masked VARCHAR(100) NOT NULL COMMENT '목록과 관리자 조회용 마스킹 코드',
    code_fingerprint BINARY(32) NOT NULL COMMENT '상품 범위 코드 중복 검사용 HMAC-SHA-256 지문',
    encryption_key_version VARCHAR(50) NOT NULL COMMENT '코드 암호화에 사용한 키 버전',
    expires_at DATETIME NOT NULL COMMENT '코드 자체의 만료 시각. UTC 저장',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE, ASSIGNED, VOID',
    created_at DATETIME NOT NULL COMMENT '재고 등록 시각',
    CONSTRAINT pk_gifticon_codes PRIMARY KEY (gifticon_code_id),
    CONSTRAINT uq_gifticon_codes_product_fingerprint
        UNIQUE (gifticon_product_id, code_fingerprint),
    CONSTRAINT fk_gifticon_codes_product
        FOREIGN KEY (gifticon_product_id) REFERENCES gifticon_products (gifticon_product_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_gifticon_codes_status CHECK (
        status IN ('AVAILABLE', 'ASSIGNED', 'VOID')
        ),
    INDEX idx_gifticon_codes_allocation (
        gifticon_product_id,
        status,
        expires_at,
        gifticon_code_id
        )
) ENGINE = InnoDB COMMENT = '암호화한 선구매 기프티콘 개별 코드 재고';

CREATE TABLE gifticon_orders (
    gifticon_order_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '완료된 기프티콘 교환 주문 식별자',
    user_id BIGINT NOT NULL COMMENT '교환 사용자',
    gifticon_product_id BIGINT NOT NULL COMMENT '교환 상품',
    gifticon_code_id BIGINT NOT NULL COMMENT '주문에 한 번만 할당한 개별 코드',
    point_transaction_id BIGINT NOT NULL COMMENT '포인트 USE 차감 원장',
    spent_points INT NOT NULL COMMENT '교환 당시 차감한 포인트 스냅샷',
    product_snapshot_json JSON NOT NULL COMMENT '교환 당시 상품명·브랜드·분류·액면가·필요 포인트·이미지 스냅샷',
    idempotency_key VARCHAR(100) NOT NULL COMMENT '사용자 범위 중복 주문 방지 키',
    request_fingerprint BINARY(32) NOT NULL COMMENT '같은 멱등 키의 다른 요청 판별용 SHA-256 지문',
    first_disclosed_at DATETIME NULL COMMENT '코드와 바코드를 서버가 처음 공개한 시각',
    completed_at DATETIME NOT NULL COMMENT '포인트 차감·코드 할당·주문 생성 완료 시각',
    CONSTRAINT pk_gifticon_orders PRIMARY KEY (gifticon_order_id),
    CONSTRAINT uq_gifticon_orders_code UNIQUE (gifticon_code_id),
    CONSTRAINT uq_gifticon_orders_point_transaction UNIQUE (point_transaction_id),
    CONSTRAINT uq_gifticon_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_gifticon_orders_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_gifticon_orders_product
        FOREIGN KEY (gifticon_product_id) REFERENCES gifticon_products (gifticon_product_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_gifticon_orders_code
        FOREIGN KEY (gifticon_code_id) REFERENCES gifticon_codes (gifticon_code_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_gifticon_orders_point_transaction
        FOREIGN KEY (point_transaction_id) REFERENCES point_transactions (point_transaction_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_gifticon_orders_spent_points CHECK (spent_points > 0),
    CONSTRAINT chk_gifticon_orders_disclosed_at CHECK (
        first_disclosed_at IS NULL OR first_disclosed_at >= completed_at
        ),
    INDEX idx_gifticon_orders_user_time (user_id, completed_at),
    INDEX idx_gifticon_orders_product_time (gifticon_product_id, completed_at)
) ENGINE = InnoDB COMMENT = '포인트 차감과 개별 코드 할당이 완료된 기프티콘 교환 주문';

CREATE TABLE gifticon_code_access_logs (
    access_log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '기프티콘 코드 공개 이력 식별자',
    gifticon_order_id BIGINT NOT NULL COMMENT '코드를 공개한 교환 주문',
    actor_user_id BIGINT NOT NULL COMMENT '코드 공개를 요청한 사용자',
    access_type VARCHAR(20) NOT NULL COMMENT 'DISCLOSE',
    request_id VARCHAR(100) NOT NULL COMMENT '애플리케이션 요청 추적 식별자',
    occurred_at DATETIME NOT NULL COMMENT '코드 공개 시각',
    CONSTRAINT pk_gifticon_code_access_logs PRIMARY KEY (access_log_id),
    CONSTRAINT fk_gifticon_code_access_logs_order
        FOREIGN KEY (gifticon_order_id) REFERENCES gifticon_orders (gifticon_order_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_gifticon_code_access_logs_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (user_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_gifticon_code_access_logs_type CHECK (
        access_type = 'DISCLOSE'
        ),
    INDEX idx_gifticon_code_access_logs_order_time (gifticon_order_id, occurred_at),
    INDEX idx_gifticon_code_access_logs_actor_time (actor_user_id, occurred_at)
) ENGINE = InnoDB COMMENT = '평문을 남기지 않는 기프티콘 코드·바코드 공개 이력';
