-- UUID PK -> Long PK 리팩토링 (이슈 #66) - reported_message
-- 패턴 설명은 V10__member_uuid_to_long_pk.sql 상단 주석 참고.
-- sender_member_id는 메시지 작성자의 실제 회원 UUID를 백업용으로 복사해둔 값이라
-- FK 없이 그대로 유지한다 (탈퇴 등으로 원본 회원이 사라져도 신고 증거는 남아야 함).
-- 이 테이블을 참조하던 FK 제약조건은 V9에서 이미 걷어냈다.

ALTER TABLE reported_message ADD COLUMN new_id BIGINT NULL;
ALTER TABLE reported_message ADD COLUMN new_report_id BIGINT NULL;

UPDATE reported_message t
SET new_id = (SELECT ranked.rn FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM reported_message) ranked WHERE ranked.id = t.id);
ALTER TABLE reported_message MODIFY COLUMN new_id BIGINT NOT NULL;

UPDATE reported_message rm
SET new_report_id = (SELECT rp.id FROM report rp WHERE rp.uuid = rm.report_id);

ALTER TABLE reported_message DROP PRIMARY KEY;
ALTER TABLE reported_message CHANGE COLUMN id uuid BINARY(16) NOT NULL;
ALTER TABLE reported_message ADD CONSTRAINT uk_reported_message_uuid UNIQUE (uuid);
ALTER TABLE reported_message CHANGE COLUMN new_id id BIGINT NOT NULL;
ALTER TABLE reported_message ADD PRIMARY KEY (id);
ALTER TABLE reported_message MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE reported_message DROP COLUMN report_id;
ALTER TABLE reported_message CHANGE COLUMN new_report_id report_id BIGINT NULL;
ALTER TABLE reported_message
    ADD CONSTRAINT fk_reported_message_report FOREIGN KEY (report_id) REFERENCES report (id);
