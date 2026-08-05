-- ============================================================
-- UUID PK -> Long PK 리팩토링 (이슈 #66)
-- 내부 PK/FK는 BIGINT AUTO_INCREMENT로 바꾸고, 기존 UUID 값은
-- 외부 노출용 uuid 컬럼(UNIQUE)으로 그대로 보존한다.
--
-- matching_outbox.match_request_id / report.reported_message_id /
-- reported_message.sender_member_id 세 컬럼은 원본 로우가 하드 삭제된 뒤에도
-- 값을 보존해야 하는, 의도적으로 FK가 없는 참조라 이 마이그레이션에서 건드리지 않는다.
--
-- 순서: (A) 신규 BIGINT 컬럼 추가 -> (B) 부모 신규 PK 값으로 FK 컬럼 채우기 ->
--       (C) 기존 FK 제약조건 DROP -> (D) 각 테이블 PK를 BIGINT로 교체 ->
--       (E) 기존 UUID FK 컬럼을 신규 BIGINT FK 컬럼으로 교체하고 FK 재생성
-- FK 제약조건(C)을 부모 PK 교체(D)보다 먼저 걷어내야, 자식 테이블이 참조 중인
-- 부모의 PRIMARY KEY를 안전하게 DROP할 수 있다.
-- ============================================================

-- ---------- Phase A: 신규 BIGINT id 컬럼 + FK용 신규 BIGINT 컬럼 추가 ----------
-- (AUTO_INCREMENT는 "컬럼 추가"와 "키 추가"를 별도 문장으로 분리해도 된다 -
--  MySQL은 그 컬럼에 AUTO_INCREMENT 속성을 "부여하는" 시점에만 키가 있으면 되므로,
--  컬럼을 먼저 NULL 허용으로 추가한 뒤 UNIQUE 키를 걸고 나서 AUTO_INCREMENT로 전환한다)

ALTER TABLE member ADD COLUMN new_id BIGINT NULL;
ALTER TABLE chat_room ADD COLUMN new_id BIGINT NULL;

ALTER TABLE chat_room_participant ADD COLUMN new_id BIGINT NULL;
ALTER TABLE chat_room_participant ADD COLUMN new_room_id BIGINT NULL;
ALTER TABLE chat_room_participant ADD COLUMN new_member_id BIGINT NULL;

ALTER TABLE chat_message ADD COLUMN new_id BIGINT NULL;
ALTER TABLE chat_message ADD COLUMN new_room_id BIGINT NULL;
ALTER TABLE chat_message ADD COLUMN new_participant_id BIGINT NULL;

ALTER TABLE match_request ADD COLUMN new_id BIGINT NULL;
ALTER TABLE match_request ADD COLUMN new_member_id BIGINT NULL;
ALTER TABLE match_request ADD COLUMN new_room_id BIGINT NULL;

ALTER TABLE report ADD COLUMN new_id BIGINT NULL;
ALTER TABLE report ADD COLUMN new_reporter_id BIGINT NULL;
ALTER TABLE report ADD COLUMN new_reported_id BIGINT NULL;
ALTER TABLE report ADD COLUMN new_room_id BIGINT NULL;

ALTER TABLE reported_message ADD COLUMN new_id BIGINT NULL;
ALTER TABLE reported_message ADD COLUMN new_report_id BIGINT NULL;

ALTER TABLE matching_outbox ADD COLUMN new_id BIGINT NULL;
ALTER TABLE password_reset_token ADD COLUMN new_id BIGINT NULL;
ALTER TABLE email_verification_token ADD COLUMN new_id BIGINT NULL;

ALTER TABLE member ADD CONSTRAINT uk_member_new_id UNIQUE (new_id);
ALTER TABLE chat_room ADD CONSTRAINT uk_chat_room_new_id UNIQUE (new_id);
ALTER TABLE chat_room_participant ADD CONSTRAINT uk_chat_room_participant_new_id UNIQUE (new_id);
ALTER TABLE chat_message ADD CONSTRAINT uk_chat_message_new_id UNIQUE (new_id);
ALTER TABLE match_request ADD CONSTRAINT uk_match_request_new_id UNIQUE (new_id);
ALTER TABLE report ADD CONSTRAINT uk_report_new_id UNIQUE (new_id);
ALTER TABLE reported_message ADD CONSTRAINT uk_reported_message_new_id UNIQUE (new_id);
ALTER TABLE matching_outbox ADD CONSTRAINT uk_matching_outbox_new_id UNIQUE (new_id);
ALTER TABLE password_reset_token ADD CONSTRAINT uk_password_reset_token_new_id UNIQUE (new_id);
ALTER TABLE email_verification_token ADD CONSTRAINT uk_email_verification_token_new_id UNIQUE (new_id);

ALTER TABLE member MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE chat_room MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE chat_room_participant MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE chat_message MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE match_request MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE report MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE reported_message MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE matching_outbox MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE password_reset_token MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE email_verification_token MODIFY COLUMN new_id BIGINT NOT NULL AUTO_INCREMENT;

-- ---------- Phase B: 신규 FK 컬럼에 부모의 신규 BIGINT id 값 채우기 ----------

-- 상관 서브쿼리 형태로 작성한다 (MySQL 멀티테이블 UPDATE...JOIN 문법은 H2가 지원하지 않아,
-- 테스트 프로파일의 H2(MODE=MySQL)와 운영 MySQL 양쪽에서 동일하게 동작하는 표준 서브쿼리로 통일)

UPDATE chat_room_participant p
SET new_room_id = (SELECT r.new_id FROM chat_room r WHERE r.id = p.room_id),
    new_member_id = (SELECT m.new_id FROM member m WHERE m.id = p.member_id);

UPDATE chat_message c
SET new_room_id = (SELECT r.new_id FROM chat_room r WHERE r.id = c.room_id),
    new_participant_id = (SELECT p.new_id FROM chat_room_participant p WHERE p.id = c.participant_id);

UPDATE match_request mr
SET new_member_id = (SELECT m.new_id FROM member m WHERE m.id = mr.member_id),
    new_room_id = (SELECT r.new_id FROM chat_room r WHERE r.id = mr.room_id);

UPDATE report rp
SET new_reporter_id = (SELECT m.new_id FROM member m WHERE m.id = rp.reporter_id),
    new_reported_id = (SELECT m.new_id FROM member m WHERE m.id = rp.reported_id),
    new_room_id = (SELECT r.new_id FROM chat_room r WHERE r.id = rp.room_id);

UPDATE reported_message rm
SET new_report_id = (SELECT rp.new_id FROM report rp WHERE rp.id = rm.report_id);

-- ---------- Phase C: 기존 FK 제약조건 및 영향받는 인덱스 DROP ----------

ALTER TABLE chat_room_participant DROP FOREIGN KEY fk_chat_room_participant_room;
ALTER TABLE chat_room_participant DROP FOREIGN KEY fk_chat_room_participant_member;
ALTER TABLE chat_message DROP FOREIGN KEY fk_chat_message_room;
ALTER TABLE chat_message DROP FOREIGN KEY fk_chat_message_participant;
ALTER TABLE match_request DROP FOREIGN KEY fk_match_request_member;
ALTER TABLE match_request DROP FOREIGN KEY fk_match_request_room;
ALTER TABLE report DROP FOREIGN KEY fk_report_reporter;
ALTER TABLE report DROP FOREIGN KEY fk_report_reported;
ALTER TABLE report DROP FOREIGN KEY fk_report_room;
ALTER TABLE reported_message DROP FOREIGN KEY fk_reported_message_report;

DROP INDEX idx_match_request_status_room_id ON match_request;

-- ---------- Phase D: 각 테이블 PK를 BIGINT로 교체 (id -> uuid, new_id -> id) ----------
-- 매 테이블마다: PK DROP -> id를 uuid로 rename+UNIQUE 추가 -> new_id를 id로 rename
-- (이 시점엔 Phase A에서 만든 UNIQUE 인덱스가 auto_increment 요구조건을 충족시켜줌)
-- -> id에 PRIMARY KEY 추가 -> 이제 중복이 된 임시 UNIQUE 인덱스 DROP

ALTER TABLE member DROP PRIMARY KEY;
ALTER TABLE member CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE member ADD CONSTRAINT uk_member_uuid UNIQUE (uuid);
ALTER TABLE member CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE member ADD PRIMARY KEY (id);
ALTER TABLE member DROP INDEX uk_member_new_id;

ALTER TABLE chat_room DROP PRIMARY KEY;
ALTER TABLE chat_room CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE chat_room ADD CONSTRAINT uk_chat_room_uuid UNIQUE (uuid);
ALTER TABLE chat_room CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE chat_room ADD PRIMARY KEY (id);
ALTER TABLE chat_room DROP INDEX uk_chat_room_new_id;

ALTER TABLE chat_room_participant DROP PRIMARY KEY;
ALTER TABLE chat_room_participant CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE chat_room_participant ADD CONSTRAINT uk_chat_room_participant_uuid UNIQUE (uuid);
ALTER TABLE chat_room_participant CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE chat_room_participant ADD PRIMARY KEY (id);
ALTER TABLE chat_room_participant DROP INDEX uk_chat_room_participant_new_id;

ALTER TABLE chat_message DROP PRIMARY KEY;
ALTER TABLE chat_message CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE chat_message ADD CONSTRAINT uk_chat_message_uuid UNIQUE (uuid);
ALTER TABLE chat_message CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE chat_message ADD PRIMARY KEY (id);
ALTER TABLE chat_message DROP INDEX uk_chat_message_new_id;

ALTER TABLE match_request DROP PRIMARY KEY;
ALTER TABLE match_request CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE match_request ADD CONSTRAINT uk_match_request_uuid UNIQUE (uuid);
ALTER TABLE match_request CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE match_request ADD PRIMARY KEY (id);
ALTER TABLE match_request DROP INDEX uk_match_request_new_id;

ALTER TABLE report DROP PRIMARY KEY;
ALTER TABLE report CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE report ADD CONSTRAINT uk_report_uuid UNIQUE (uuid);
ALTER TABLE report CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE report ADD PRIMARY KEY (id);
ALTER TABLE report DROP INDEX uk_report_new_id;

ALTER TABLE reported_message DROP PRIMARY KEY;
ALTER TABLE reported_message CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE reported_message ADD CONSTRAINT uk_reported_message_uuid UNIQUE (uuid);
ALTER TABLE reported_message CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE reported_message ADD PRIMARY KEY (id);
ALTER TABLE reported_message DROP INDEX uk_reported_message_new_id;

ALTER TABLE matching_outbox DROP PRIMARY KEY;
ALTER TABLE matching_outbox CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE matching_outbox ADD CONSTRAINT uk_matching_outbox_uuid UNIQUE (uuid);
ALTER TABLE matching_outbox CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE matching_outbox ADD PRIMARY KEY (id);
ALTER TABLE matching_outbox DROP INDEX uk_matching_outbox_new_id;

-- password_reset_token/email_verification_token은 엔티티에 공개용 uuid 필드가 없다
-- (이메일로만 조회되고 .getId()가 엔티티 바깥에서 참조된 적이 없어 uuid 보존이 불필요) -
-- 그래서 다른 테이블과 달리 기존 UUID id를 uuid로 보존하지 않고 그대로 DROP한다.
ALTER TABLE password_reset_token DROP PRIMARY KEY;
ALTER TABLE password_reset_token DROP COLUMN id;
ALTER TABLE password_reset_token CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE password_reset_token ADD PRIMARY KEY (id);
ALTER TABLE password_reset_token DROP INDEX uk_password_reset_token_new_id;

ALTER TABLE email_verification_token DROP PRIMARY KEY;
ALTER TABLE email_verification_token DROP COLUMN id;
ALTER TABLE email_verification_token CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE email_verification_token ADD PRIMARY KEY (id);
ALTER TABLE email_verification_token DROP INDEX uk_email_verification_token_new_id;

-- ---------- Phase E: 기존 UUID FK 컬럼 제거, 신규 BIGINT FK 컬럼을 원래 이름으로 교체 + FK 재생성 ----------

ALTER TABLE chat_room_participant DROP COLUMN room_id;
ALTER TABLE chat_room_participant DROP COLUMN member_id;
ALTER TABLE chat_room_participant CHANGE COLUMN new_room_id room_id BIGINT NOT NULL;
ALTER TABLE chat_room_participant CHANGE COLUMN new_member_id member_id BIGINT NOT NULL;
ALTER TABLE chat_room_participant
    ADD CONSTRAINT fk_chat_room_participant_room FOREIGN KEY (room_id) REFERENCES chat_room (id);
ALTER TABLE chat_room_participant
    ADD CONSTRAINT fk_chat_room_participant_member FOREIGN KEY (member_id) REFERENCES member (id);

ALTER TABLE chat_message DROP COLUMN room_id;
ALTER TABLE chat_message DROP COLUMN participant_id;
ALTER TABLE chat_message CHANGE COLUMN new_room_id room_id BIGINT NOT NULL;
ALTER TABLE chat_message CHANGE COLUMN new_participant_id participant_id BIGINT NOT NULL;
ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_room FOREIGN KEY (room_id) REFERENCES chat_room (id);
ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_participant FOREIGN KEY (participant_id) REFERENCES chat_room_participant (id);

ALTER TABLE match_request DROP COLUMN member_id;
ALTER TABLE match_request DROP COLUMN room_id;
ALTER TABLE match_request CHANGE COLUMN new_member_id member_id BIGINT NULL;
ALTER TABLE match_request CHANGE COLUMN new_room_id room_id BIGINT NULL;
ALTER TABLE match_request
    ADD CONSTRAINT fk_match_request_member FOREIGN KEY (member_id) REFERENCES member (id);
ALTER TABLE match_request
    ADD CONSTRAINT fk_match_request_room FOREIGN KEY (room_id) REFERENCES chat_room (id);

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

ALTER TABLE reported_message DROP COLUMN report_id;
ALTER TABLE reported_message CHANGE COLUMN new_report_id report_id BIGINT NULL;
ALTER TABLE reported_message
    ADD CONSTRAINT fk_reported_message_report FOREIGN KEY (report_id) REFERENCES report (id);

CREATE INDEX idx_match_request_status_room_id ON match_request (status, room_id);
