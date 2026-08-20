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

COMMIT;
