-- UUID PK -> Long PK 리팩토링 (이슈 #66) - chat_room_participant
-- 패턴 설명은 V10__member_uuid_to_long_pk.sql 상단 주석 참고.
-- 부모(member, chat_room)는 V10~V11에서 이미 새 Long PK로 전환 완료된 상태이므로,
-- 부모의 uuid 컬럼(옛 id 값 그대로 보존됨)을 기준으로 조인해 새 Long id를 찾는다.
-- 이 테이블을 참조하던 FK 제약조건은 V9에서 이미 걷어냈다.

ALTER TABLE chat_room_participant ADD COLUMN new_id BIGINT NULL;
ALTER TABLE chat_room_participant ADD COLUMN new_room_id BIGINT NULL;
ALTER TABLE chat_room_participant ADD COLUMN new_member_id BIGINT NULL;

UPDATE chat_room_participant t
SET new_id = (SELECT ranked.rn FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM chat_room_participant) ranked WHERE ranked.id = t.id);
ALTER TABLE chat_room_participant MODIFY COLUMN new_id BIGINT NOT NULL;

UPDATE chat_room_participant p
SET new_room_id = (SELECT r.id FROM chat_room r WHERE r.uuid = p.room_id),
    new_member_id = (SELECT m.id FROM member m WHERE m.uuid = p.member_id);

ALTER TABLE chat_room_participant DROP PRIMARY KEY;
ALTER TABLE chat_room_participant CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE chat_room_participant ADD CONSTRAINT uk_chat_room_participant_uuid UNIQUE (uuid);
ALTER TABLE chat_room_participant CHANGE COLUMN new_id id BIGINT NOT NULL;
ALTER TABLE chat_room_participant ADD PRIMARY KEY (id);
ALTER TABLE chat_room_participant MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE chat_room_participant DROP COLUMN room_id;
ALTER TABLE chat_room_participant DROP COLUMN member_id;
ALTER TABLE chat_room_participant CHANGE COLUMN new_room_id room_id BIGINT NOT NULL;
ALTER TABLE chat_room_participant CHANGE COLUMN new_member_id member_id BIGINT NOT NULL;
ALTER TABLE chat_room_participant
    ADD CONSTRAINT fk_chat_room_participant_room FOREIGN KEY (room_id) REFERENCES chat_room (id);
ALTER TABLE chat_room_participant
    ADD CONSTRAINT fk_chat_room_participant_member FOREIGN KEY (member_id) REFERENCES member (id);
