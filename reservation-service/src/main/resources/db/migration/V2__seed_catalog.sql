INSERT INTO branch (name) VALUES ('제주공항점'), ('서울역점');

-- Flyway가 빈 테이블에 지점을 순서대로 넣으므로 지점 ID는 1, 2가 된다.
-- 정비 중인 투싼은 검색 결과에서 제외되는지 확인하는 데이터다.
INSERT INTO vehicle (branch_id, name, seats, daily_price, status) VALUES
    (1, '아반떼', 5, 60000, 'AVAILABLE'),
    (1, '쏘나타', 5, 80000, 'AVAILABLE'),
    (1, '투싼', 5, 90000, 'MAINTENANCE'),
    (2, 'K5', 5, 75000, 'AVAILABLE');