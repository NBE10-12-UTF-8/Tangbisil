-- UUID PK -> Long PK 리팩토링 (이슈 #66) - email_verification_token
-- 이메일로만 조회되고 .getId()가 엔티티 바깥에서 참조된 적이 없어
-- 다른 테이블과 달리 uuid 보존 없이 기존 UUID id를 그대로 버린다.

ALTER TABLE email_verification_token ADD COLUMN new_id BIGINT NULL;

UPDATE email_verification_token t
SET new_id = (SELECT ranked.rn FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM email_verification_token) ranked WHERE ranked.id = t.id);
ALTER TABLE email_verification_token MODIFY COLUMN new_id BIGINT NOT NULL;

ALTER TABLE email_verification_token DROP PRIMARY KEY;
ALTER TABLE email_verification_token DROP COLUMN id;
ALTER TABLE email_verification_token CHANGE COLUMN new_id id BIGINT NOT NULL;
ALTER TABLE email_verification_token ADD PRIMARY KEY (id);
ALTER TABLE email_verification_token MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
