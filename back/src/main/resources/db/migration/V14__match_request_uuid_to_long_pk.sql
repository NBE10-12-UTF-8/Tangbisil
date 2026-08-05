-- UUID PK -> Long PK 리팩토링 (이슈 #66) - match_request
-- 패턴 설명은 V10__member_uuid_to_long_pk.sql 상단 주석 참고.
-- member_id/room_id는 매칭 대기 중엔 room이 비어있을 수 있어 nullable이다.
-- 이 테이블을 참조하던 FK 제약조건은 V9에서 이미 걷어냈다.

ALTER TABLE match_request ADD COLUMN new_id BIGINT NULL;
ALTER TABLE match_request ADD COLUMN new_member_id BIGINT NULL;
ALTER TABLE match_request ADD COLUMN new_room_id BIGINT NULL;

UPDATE match_request t
SET new_id = (SELECT ranked.rn FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM match_request) ranked WHERE ranked.id = t.id);
ALTER TABLE match_request MODIFY COLUMN new_id BIGINT NOT NULL;

UPDATE match_request mr
SET new_member_id = (SELECT m.id FROM member m WHERE m.uuid = mr.member_id),
    new_room_id = (SELECT r.id FROM chat_room r WHERE r.uuid = mr.room_id);

DROP INDEX idx_match_request_status_room_id ON match_request;

ALTER TABLE match_request DROP PRIMARY KEY;
ALTER TABLE match_request CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE match_request ADD CONSTRAINT uk_match_request_uuid UNIQUE (uuid);
ALTER TABLE match_request CHANGE COLUMN new_id id BIGINT NOT NULL;
ALTER TABLE match_request ADD PRIMARY KEY (id);
ALTER TABLE match_request MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE match_request DROP COLUMN member_id;
ALTER TABLE match_request DROP COLUMN room_id;
ALTER TABLE match_request CHANGE COLUMN new_member_id member_id BIGINT NULL;
ALTER TABLE match_request CHANGE COLUMN new_room_id room_id BIGINT NULL;
ALTER TABLE match_request
    ADD CONSTRAINT fk_match_request_member FOREIGN KEY (member_id) REFERENCES member (id);
ALTER TABLE match_request
    ADD CONSTRAINT fk_match_request_room FOREIGN KEY (room_id) REFERENCES chat_room (id);

CREATE INDEX idx_match_request_status_room_id ON match_request (status, room_id);
