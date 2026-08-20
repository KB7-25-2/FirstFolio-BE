SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;

SET @seed_actor_user_id = (
    SELECT MIN(user_id)
    FROM users
    WHERE status = 'ACTIVE'
);

INSERT INTO main_chapters (
    main_chapter_id,
    chapter_type,
    asset_type,
    title,
    description,
    display_order,
    is_required,
    is_active,
    created_at,
    updated_at
) VALUES
    (9100001, 'FOUNDATION', NULL, '테스트용 포트폴리오 기초', 'k6 로컬 테스트용 필수 과정', 1, TRUE, TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (9100002, 'ASSET', 'DEPOSIT_SAVINGS', '테스트용 예·적금', 'k6 로컬 테스트용 자산 대단원', 2, FALSE, TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (9100003, 'ASSET', 'BOND', '테스트용 채권', 'k6 로컬 테스트용 자산 대단원', 3, FALSE, TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (9100004, 'ASSET', 'STOCK', '테스트용 주식', 'k6 로컬 테스트용 자산 대단원', 4, FALSE, TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (9100005, 'ASSET', 'FUND', '테스트용 펀드', 'k6 로컬 테스트용 자산 대단원', 5, FALSE, TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP())
ON DUPLICATE KEY UPDATE
    chapter_type = VALUES(chapter_type),
    asset_type = VALUES(asset_type),
    title = VALUES(title),
    description = VALUES(description),
    display_order = VALUES(display_order),
    is_required = VALUES(is_required),
    is_active = VALUES(is_active),
    updated_at = UTC_TIMESTAMP();

INSERT INTO sub_chapters (
    sub_chapter_id,
    main_chapter_id,
    title,
    description,
    display_order,
    current_content_version_id,
    is_active,
    created_at,
    updated_at
) VALUES (
    9200001,
    9100001,
    '테스트용 기초 강좌',
    'k6 로컬 테스트용 최소 소단원',
    1,
    NULL,
    TRUE,
    UTC_TIMESTAMP(),
    UTC_TIMESTAMP()
)
ON DUPLICATE KEY UPDATE
    main_chapter_id = VALUES(main_chapter_id),
    title = VALUES(title),
    description = VALUES(description),
    display_order = VALUES(display_order),
    is_active = VALUES(is_active),
    updated_at = UTC_TIMESTAMP();

INSERT INTO quiz_questions (
    question_id,
    question_key,
    version_no,
    usage_type,
    main_chapter_id,
    sub_chapter_id,
    display_order,
    question_type,
    difficulty,
    prompt,
    scenario_json,
    options_json,
    correct_answer_json,
    explanation,
    generation_type,
    source_refs_json,
    status,
    created_by,
    published_at,
    created_at
) VALUES
    (9300001, 'loadtest-level-deposit', 1, 'LEVEL_TEST', 9100002, NULL, 1, 'SINGLE_CHOICE', 'EASY', '예금은 금융상품입니까?', NULL, JSON_ARRAY(JSON_OBJECT('key', 'A', 'label', '예'), JSON_OBJECT('key', 'B', 'label', '아니요')), JSON_OBJECT('key', 'A'), '로컬 테스트용 정답입니다.', 'HUMAN', NULL, 'PUBLISHED', @seed_actor_user_id, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (9300002, 'loadtest-level-bond', 1, 'LEVEL_TEST', 9100003, NULL, 1, 'SINGLE_CHOICE', 'EASY', '채권은 금융상품입니까?', NULL, JSON_ARRAY(JSON_OBJECT('key', 'A', 'label', '예'), JSON_OBJECT('key', 'B', 'label', '아니요')), JSON_OBJECT('key', 'A'), '로컬 테스트용 정답입니다.', 'HUMAN', NULL, 'PUBLISHED', @seed_actor_user_id, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (9300003, 'loadtest-level-stock', 1, 'LEVEL_TEST', 9100004, NULL, 1, 'SINGLE_CHOICE', 'EASY', '주식은 금융상품입니까?', NULL, JSON_ARRAY(JSON_OBJECT('key', 'A', 'label', '예'), JSON_OBJECT('key', 'B', 'label', '아니요')), JSON_OBJECT('key', 'A'), '로컬 테스트용 정답입니다.', 'HUMAN', NULL, 'PUBLISHED', @seed_actor_user_id, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (9300004, 'loadtest-level-fund', 1, 'LEVEL_TEST', 9100005, NULL, 1, 'SINGLE_CHOICE', 'EASY', '펀드는 금융상품입니까?', NULL, JSON_ARRAY(JSON_OBJECT('key', 'A', 'label', '예'), JSON_OBJECT('key', 'B', 'label', '아니요')), JSON_OBJECT('key', 'A'), '로컬 테스트용 정답입니다.', 'HUMAN', NULL, 'PUBLISHED', @seed_actor_user_id, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (9300011, 'loadtest-foundation-sub', 1, 'SUB_CHAPTER', 9100001, 9200001, NULL, 'SINGLE_CHOICE', 'EASY', '분산 투자는 위험 관리에 도움을 줍니까?', NULL, JSON_ARRAY(JSON_OBJECT('key', 'A', 'label', '예'), JSON_OBJECT('key', 'B', 'label', '아니요')), JSON_OBJECT('key', 'A'), '로컬 테스트용 정답입니다.', 'HUMAN', NULL, 'PUBLISHED', @seed_actor_user_id, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (9300012, 'loadtest-foundation-main', 1, 'MAIN_CHAPTER', 9100001, NULL, 1, 'SINGLE_CHOICE', 'EASY', '포트폴리오는 여러 자산으로 구성할 수 있습니까?', NULL, JSON_ARRAY(JSON_OBJECT('key', 'A', 'label', '예'), JSON_OBJECT('key', 'B', 'label', '아니요')), JSON_OBJECT('key', 'A'), '로컬 테스트용 정답입니다.', 'HUMAN', NULL, 'PUBLISHED', @seed_actor_user_id, UTC_TIMESTAMP(), UTC_TIMESTAMP())
ON DUPLICATE KEY UPDATE
    question_key = VALUES(question_key),
    version_no = VALUES(version_no),
    usage_type = VALUES(usage_type),
    main_chapter_id = VALUES(main_chapter_id),
    sub_chapter_id = VALUES(sub_chapter_id),
    display_order = VALUES(display_order),
    question_type = VALUES(question_type),
    difficulty = VALUES(difficulty),
    prompt = VALUES(prompt),
    scenario_json = VALUES(scenario_json),
    options_json = VALUES(options_json),
    correct_answer_json = VALUES(correct_answer_json),
    explanation = VALUES(explanation),
    generation_type = VALUES(generation_type),
    source_refs_json = VALUES(source_refs_json),
    status = VALUES(status),
    created_by = VALUES(created_by),
    published_at = VALUES(published_at);

INSERT INTO content_versions (
    content_version_id,
    sub_chapter_id,
    version_no,
    schema_version,
    storage_object_key,
    storage_version_id,
    status,
    published_at,
    created_by,
    created_at
) VALUES (
    9400001,
    9200001,
    1,
    '1.0',
    'learning/sub-chapters/9200001/lesson.json',
    'load-test-v1',
    'PUBLISHED',
    UTC_TIMESTAMP(),
    @seed_actor_user_id,
    UTC_TIMESTAMP()
)
ON DUPLICATE KEY UPDATE
    sub_chapter_id = VALUES(sub_chapter_id),
    version_no = VALUES(version_no),
    schema_version = VALUES(schema_version),
    storage_object_key = VALUES(storage_object_key),
    storage_version_id = VALUES(storage_version_id),
    status = VALUES(status),
    published_at = VALUES(published_at),
    created_by = VALUES(created_by);

UPDATE sub_chapters
SET current_content_version_id = 9400001,
    updated_at = UTC_TIMESTAMP()
WHERE sub_chapter_id = 9200001;

INSERT INTO system_policies (
    policy_id,
    policy_key,
    version_no,
    config_json,
    effective_from,
    effective_to,
    is_active,
    created_by,
    created_at
) VALUES (
    9500001,
    'QUIZ_REWARD',
    1,
    JSON_OBJECT('points_per_correct', 100),
    '2020-01-01 00:00:00',
    NULL,
    TRUE,
    @seed_actor_user_id,
    UTC_TIMESTAMP()
)
ON DUPLICATE KEY UPDATE
    config_json = VALUES(config_json),
    effective_from = VALUES(effective_from),
    effective_to = VALUES(effective_to),
    is_active = VALUES(is_active),
    created_by = VALUES(created_by);

INSERT INTO financial_products (
    product_id,
    asset_type,
    display_name,
    description,
    source_provider,
    source_product_code,
    source_product_name,
    source_reference_at,
    real_terms_json,
    simulation_terms_json,
    risk_level,
    is_active,
    created_at,
    updated_at
) VALUES
    (
        9600001,
        'DEPOSIT_SAVINGS',
        '테스트용 새싹 정기예금',
        '금융상품 조회 부하 테스트용 가명 예금',
        'FSS_FINLIFE',
        'LOAD-TEST-DEPOSIT-01',
        '로컬 테스트 원본 예금 01',
        '2026-08-20 00:00:00',
        JSON_OBJECT(
            'interest_rate', 3.20,
            'maturity_months', 6,
            'interest_interval', 'MATURITY',
            'interest_interval_source', 'ASSUMED',
            'interest_rate_type', 'SIMPLE',
            'reserve_type', NULL
        ),
        JSON_OBJECT(
            'service_maturity_hours', 144,
            'service_interest_interval_hours', 144,
            'compression_hours_per_month', 24,
            'compressed_at', '2026-08-20T00:00:00'
        ),
        'LOW',
        TRUE,
        UTC_TIMESTAMP(),
        UTC_TIMESTAMP()
    ),
    (
        9600002,
        'DEPOSIT_SAVINGS',
        '테스트용 나무 정기적금',
        '금융상품 조회 부하 테스트용 가명 적금',
        'FSS_FINLIFE',
        'LOAD-TEST-SAVING-01',
        '로컬 테스트 원본 적금 01',
        '2026-08-20 00:00:00',
        JSON_OBJECT(
            'interest_rate', 3.60,
            'maturity_months', 12,
            'interest_interval', 'MATURITY',
            'interest_interval_source', 'ASSUMED',
            'interest_rate_type', 'SIMPLE',
            'reserve_type', 'FIXED'
        ),
        JSON_OBJECT(
            'service_maturity_hours', 288,
            'service_interest_interval_hours', 288,
            'compression_hours_per_month', 24,
            'compressed_at', '2026-08-20T00:00:00'
        ),
        'LOW',
        TRUE,
        UTC_TIMESTAMP(),
        UTC_TIMESTAMP()
    ),
    (
        9600003,
        'BOND',
        '테스트용 푸른 국채',
        '금융상품 조회 부하 테스트용 가명 국채',
        'DATA_GO_KR_BOND',
        'LOAD-TEST-BOND-01',
        '로컬 테스트 원본 국채 01',
        '2026-08-20 00:00:00',
        JSON_OBJECT(
            'coupon_rate', 3.10,
            'maturity_months', 12,
            'interest_interval_months', 6,
            'interest_type', '이표채',
            'bond_category', '국채',
            'credit_rating', NULL
        ),
        JSON_OBJECT(
            'service_maturity_hours', 288,
            'service_interest_interval_hours', 144,
            'compression_hours_per_month', 24,
            'compressed_at', '2026-08-20T00:00:00'
        ),
        'LOW',
        TRUE,
        UTC_TIMESTAMP(),
        UTC_TIMESTAMP()
    ),
    (
        9600004,
        'BOND',
        '테스트용 튼튼 회사채',
        '금융상품 조회 부하 테스트용 가명 회사채',
        'DATA_GO_KR_BOND',
        'LOAD-TEST-BOND-02',
        '로컬 테스트 원본 회사채 01',
        '2026-08-20 00:00:00',
        JSON_OBJECT(
            'coupon_rate', 4.20,
            'maturity_months', 9,
            'interest_interval_months', 3,
            'interest_type', '이표채',
            'bond_category', '일반회사채',
            'credit_rating', 'AA0'
        ),
        JSON_OBJECT(
            'service_maturity_hours', 216,
            'service_interest_interval_hours', 72,
            'compression_hours_per_month', 24,
            'compressed_at', '2026-08-20T00:00:00'
        ),
        'MEDIUM',
        TRUE,
        UTC_TIMESTAMP(),
        UTC_TIMESTAMP()
    ),
    (
        9600005,
        'STOCK',
        '테스트용 햇살 성장주',
        '금융상품 조회 부하 테스트용 가명 주식',
        'TOSSINVEST',
        'LOAD-TEST-STOCK-01',
        '로컬 테스트 원본 주식 01',
        '2026-08-20 00:00:00',
        JSON_OBJECT('market', 'KOSPI', 'sector', '교육서비스'),
        JSON_OBJECT(
            'time_compressed', FALSE,
            'reason', 'STOCK_REALTIME_PRICE',
            'registered_at', '2026-08-20T00:00:00'
        ),
        'HIGH',
        TRUE,
        UTC_TIMESTAMP(),
        UTC_TIMESTAMP()
    ),
    (
        9600006,
        'STOCK',
        '테스트용 별빛 가치주',
        '금융상품 조회 부하 테스트용 가명 주식',
        'TOSSINVEST',
        'LOAD-TEST-STOCK-02',
        '로컬 테스트 원본 주식 02',
        '2026-08-20 00:00:00',
        JSON_OBJECT('market', 'KOSDAQ', 'sector', '정보기술'),
        JSON_OBJECT(
            'time_compressed', FALSE,
            'reason', 'STOCK_REALTIME_PRICE',
            'registered_at', '2026-08-20T00:00:00'
        ),
        'HIGH',
        TRUE,
        UTC_TIMESTAMP(),
        UTC_TIMESTAMP()
    ),
    (
        9600007,
        'FUND',
        '테스트용 균형 지수펀드',
        '금융상품 조회 부하 테스트용 가명 ETF',
        'DATA_GO_KR_ETF',
        'LOAD-TEST-FUND-01',
        '로컬 테스트 원본 ETF 01',
        '2026-08-20 00:00:00',
        JSON_OBJECT('fund_type', 'MIXED'),
        JSON_OBJECT(
            'time_compressed', FALSE,
            'reason', 'ETF_REALTIME_PRICE',
            'registered_at', '2026-08-20T00:00:00'
        ),
        'MEDIUM',
        TRUE,
        UTC_TIMESTAMP(),
        UTC_TIMESTAMP()
    ),
    (
        9600008,
        'FUND',
        '테스트용 미래 주식펀드',
        '금융상품 조회 부하 테스트용 가명 ETF',
        'DATA_GO_KR_ETF',
        'LOAD-TEST-FUND-02',
        '로컬 테스트 원본 ETF 02',
        '2026-08-20 00:00:00',
        JSON_OBJECT('fund_type', 'EQUITY'),
        JSON_OBJECT(
            'time_compressed', FALSE,
            'reason', 'ETF_REALTIME_PRICE',
            'registered_at', '2026-08-20T00:00:00'
        ),
        'HIGH',
        TRUE,
        UTC_TIMESTAMP(),
        UTC_TIMESTAMP()
    )
ON DUPLICATE KEY UPDATE
    asset_type = VALUES(asset_type),
    display_name = VALUES(display_name),
    description = VALUES(description),
    source_provider = VALUES(source_provider),
    source_product_code = VALUES(source_product_code),
    source_product_name = VALUES(source_product_name),
    source_reference_at = VALUES(source_reference_at),
    real_terms_json = VALUES(real_terms_json),
    simulation_terms_json = VALUES(simulation_terms_json),
    risk_level = VALUES(risk_level),
    is_active = VALUES(is_active),
    updated_at = UTC_TIMESTAMP();

INSERT INTO product_prices (
    product_price_id,
    product_id,
    price,
    reference_at,
    source_type,
    generation_key,
    created_at
) VALUES
    (9700001, 9600005, 72500.0000, '2026-08-20 00:00:00', 'REAL_DATA', 'load-test-price-9600005', UTC_TIMESTAMP()),
    (9700002, 9600006, 41800.0000, '2026-08-20 00:00:00', 'REAL_DATA', 'load-test-price-9600006', UTC_TIMESTAMP()),
    (9700003, 9600007, 12500.0000, '2026-08-20 00:00:00', 'REAL_DATA', 'load-test-price-9600007', UTC_TIMESTAMP()),
    (9700004, 9600008, 18300.0000, '2026-08-20 00:00:00', 'REAL_DATA', 'load-test-price-9600008', UTC_TIMESTAMP())
ON DUPLICATE KEY UPDATE
    product_id = VALUES(product_id),
    price = VALUES(price),
    reference_at = VALUES(reference_at),
    source_type = VALUES(source_type),
    generation_key = VALUES(generation_key);

COMMIT;
