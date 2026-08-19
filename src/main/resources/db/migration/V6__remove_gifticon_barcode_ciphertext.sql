-- Applied V5 migrations must remain immutable.
-- Move the barcode removal and comment changes into a new migration.

ALTER TABLE gifticon_codes
    DROP COLUMN barcode_ciphertext;

ALTER TABLE gifticon_orders
    MODIFY COLUMN first_disclosed_at DATETIME NULL
        COMMENT '코드를 서버가 처음 공개한 시각. 바코드는 프론트에서 렌더링';

ALTER TABLE gifticon_code_access_logs
    COMMENT = '평문을 남기지 않는 기프티콘 코드 공개 이력';
