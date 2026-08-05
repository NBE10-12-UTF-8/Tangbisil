-- UUID PK -> Long PK 리팩토링 (이슈 #66) - report
-- 패턴 설명은 V10__member_uuid_to_long_pk.sql 상단 주석 참고.
-- reported_message_id는 원본 메시지가 24시간 후 하드 삭제되는데도 값을 보존해야 하는
-- 의도적으로 FK 없는 UUID 참조라 이 마이그레이션에서 건드리지 않는다.
-- 이 테이블을 참조하던 FK 제약조건은 V9에서 이미 걷어냈다.

ALTER TABLE report ADD COLUMN new_id BIGINT NULL;
ALTER TABLE report ADD COLUMN new_reporter_id BIGINT NULL;
ALTER TABLE report ADD COLUMN new_reported_id BIGINT NULL;
ALTER TABLE report ADD COLUMN new_room_id BIGINT NULL;

UPDATE report t
SET new_id = (SELECT ranked.rn FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM report) ranked WHERE ranked.id = t.id);
ALTER TABLE report MODIFY COLUMN new_id BIGINT NOT NULL;

UPDATE report rp
SET new_reporter_id = (SELECT m.id FROM member m WHERE m.uuid = rp.reporter_id),
    new_reported_id = (SELECT m.id FROM member m WHERE m.uuid = rp.reported_id),
    new_room_id = (SELECT r.id FROM chat_room r WHERE r.uuid = rp.room_id);

ALTER TABLE report DROP PRIMARY KEY;
ALTER TABLE report CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE report ADD CONSTRAINT uk_report_uuid UNIQUE (uuid);
ALTER TABLE report CHANGE COLUMN new_id id BIGINT NOT NULL;
ALTER TABLE report ADD PRIMARY KEY (id);
ALTER TABLE report MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE report DROP COLUMN reporter_id;
ALTER TABLE report DROP COLUMN reported_id;
ALTER TABLE report DROP COLUMN room_id;
ALTER TABLE report CHANGE COLUMN new_reporter_id reporter_id BIGINT NULL;
ALTER TABLE report CHANGE COLUMN new_reported_id reported_id BIGINT NULL;
ALTER TABLE report CHANGE COLUMN new_room_id room_id BIGINT NULL;
ALTER TABLE report
    ADD CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id) ON DELETE SET NULL;
ALTER TABLE report
    ADD CONSTRAINT fk_report_reported FOREIGN KEY (reported_id) REFERENCES member (id) ON DELETE SET NULL;
ALTER TABLE report
    ADD CONSTRAINT fk_report_room FOREIGN KEY (room_id) REFERENCES chat_room (id);
