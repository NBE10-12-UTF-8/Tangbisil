-- UUID PK -> Long PK 리팩토링 (이슈 #66) - chat_room
-- 패턴 설명은 V10__member_uuid_to_long_pk.sql 상단 주석 참고.

ALTER TABLE chat_room ADD COLUMN new_id BIGINT NULL;

UPDATE chat_room t
SET new_id = (SELECT ranked.rn FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM chat_room) ranked WHERE ranked.id = t.id);
ALTER TABLE chat_room MODIFY COLUMN new_id BIGINT NOT NULL;

ALTER TABLE chat_room DROP PRIMARY KEY;
ALTER TABLE chat_room CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE chat_room ADD CONSTRAINT uk_chat_room_uuid UNIQUE (uuid);
ALTER TABLE chat_room CHANGE COLUMN new_id id BIGINT NOT NULL;
ALTER TABLE chat_room ADD PRIMARY KEY (id);
ALTER TABLE chat_room MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
