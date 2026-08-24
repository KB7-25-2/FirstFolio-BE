ALTER TABLE gifticon_products
    DROP CHECK chk_gifticon_products_points,
    ADD CONSTRAINT chk_gifticon_products_points CHECK (required_points > 0),
    MODIFY COLUMN required_points INT NOT NULL
        COMMENT '교환에 필요한 포인트. 원화 액면가와 독립적으로 관리',
    COMMENT = '상품별 독립 포인트 가격으로 교환하는 기프티콘 상품';
