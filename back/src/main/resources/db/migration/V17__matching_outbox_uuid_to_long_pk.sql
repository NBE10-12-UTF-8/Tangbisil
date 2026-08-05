-- UUID PK -> Long PK 리팩토링 (이슈 #66) - matching_outbox
-- 패턴 설명은 V10__member_uuid_to_long_pk.sql 상단 주석 참고.
-- match_request_id는 FK가 없는 의도적인 UUID 참조라 이 마이그레이션에서 건드리지 않는다
-- (재시도 아웃박스 패턴 특성상 원본 MatchRequest가 사라져도 이 값은 유지되어야 함).

ALTER TABLE matching_outbox ADD COLUMN new_id BIGINT NULL;

UPDATE matching_outbox t
SET new_id = (SELECT ranked.rn FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM matching_outbox) ranked WHERE ranked.id = t.id);
ALTER TABLE matching_outbox MODIFY COLUMN new_id BIGINT NOT NULL;

ALTER TABLE matching_outbox DROP PRIMARY KEY;
ALTER TABLE matching_outbox CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE matching_outbox ADD CONSTRAINT uk_matching_outbox_uuid UNIQUE (uuid);
ALTER TABLE matching_outbox CHANGE COLUMN new_id id BIGINT NOT NULL;
ALTER TABLE matching_outbox ADD PRIMARY KEY (id);
ALTER TABLE matching_outbox MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
