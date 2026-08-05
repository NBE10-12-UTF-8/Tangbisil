-- UUID PK -> Long PK 리팩토링 (이슈 #66) - chat_message
-- 패턴 설명은 V10__member_uuid_to_long_pk.sql 상단 주석 참고.
-- 부모(chat_room, chat_room_participant)는 V11~V12에서 이미 전환 완료.
-- 이 테이블을 참조하던 FK 제약조건은 V9에서 이미 걷어냈다.

ALTER TABLE chat_message ADD COLUMN new_id BIGINT NULL;
ALTER TABLE chat_message ADD COLUMN new_room_id BIGINT NULL;
ALTER TABLE chat_message ADD COLUMN new_participant_id BIGINT NULL;

UPDATE chat_message t
SET new_id = (SELECT ranked.rn FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM chat_message) ranked WHERE ranked.id = t.id);
ALTER TABLE chat_message MODIFY COLUMN new_id BIGINT NOT NULL;

UPDATE chat_message c
SET new_room_id = (SELECT r.id FROM chat_room r WHERE r.uuid = c.room_id),
    new_participant_id = (SELECT p.id FROM chat_room_participant p WHERE p.uuid = c.participant_id);

ALTER TABLE chat_message DROP PRIMARY KEY;
ALTER TABLE chat_message CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE chat_message ADD CONSTRAINT uk_chat_message_uuid UNIQUE (uuid);
ALTER TABLE chat_message CHANGE COLUMN new_id id BIGINT NOT NULL;
ALTER TABLE chat_message ADD PRIMARY KEY (id);
ALTER TABLE chat_message MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE chat_message DROP COLUMN room_id;
ALTER TABLE chat_message DROP COLUMN participant_id;
ALTER TABLE chat_message CHANGE COLUMN new_room_id room_id BIGINT NOT NULL;
ALTER TABLE chat_message CHANGE COLUMN new_participant_id participant_id BIGINT NOT NULL;
ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_room FOREIGN KEY (room_id) REFERENCES chat_room (id);
ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_participant FOREIGN KEY (participant_id) REFERENCES chat_room_participant (id);
