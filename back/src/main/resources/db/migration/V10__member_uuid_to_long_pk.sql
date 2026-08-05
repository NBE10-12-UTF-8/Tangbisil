-- ============================================================
-- UUID PK -> Long PK 리팩토링 (이슈 #66) - member
--
-- V9에서 이 테이블을 참조하던 FK 제약조건을 이미 전부 걷어냈으므로,
-- 이 파일은 순수하게 PK 컬럼 교체만 담당한다.
--
-- 각 테이블 공통 패턴: (1) new_id 컬럼 추가 -> (2) ROW_NUMBER()로 명시적 채움
-- (MySQL의 "AUTO_INCREMENT 컬럼을 나중에 부여하면 NULL을 알아서 채워준다"는
--  트릭은 컬럼 추가와 AUTO_INCREMENT 부여가 분리된 이 스크립트 구조까지
--  보장한다는 문서 근거가 없고, H2는 애초에 그 백필을 안 해줘서 명시적으로 채움)
-- -> (3) 기존 id를 uuid로 rename하고 UNIQUE 부여 -> (4) new_id를 id로 rename하고
-- PRIMARY KEY 부여 -> (5) 그 다음에야 AUTO_INCREMENT 부여(이미 PK가 있으니 안전)
-- ============================================================

ALTER TABLE member ADD COLUMN new_id BIGINT NULL;

UPDATE member m
SET new_id = (SELECT ranked.rn FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM member) ranked WHERE ranked.id = m.id);
ALTER TABLE member MODIFY COLUMN new_id BIGINT NOT NULL;

ALTER TABLE member DROP PRIMARY KEY;
ALTER TABLE member CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE member ADD CONSTRAINT uk_member_uuid UNIQUE (uuid);
ALTER TABLE member CHANGE COLUMN new_id id BIGINT NOT NULL;
ALTER TABLE member ADD PRIMARY KEY (id);
ALTER TABLE member MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
