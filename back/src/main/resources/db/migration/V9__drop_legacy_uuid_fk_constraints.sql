-- ============================================================
-- UUID PK -> Long PK 리팩토링 (이슈 #66) - 0단계: 기존 FK 제약조건 전부 DROP
--
-- 이 리팩토링은 테이블 하나당 하나의 Flyway 버전으로 쪼갠다 (V9~V19).
-- MySQL/H2 둘 다 다른 테이블에서 아직 참조 중인 PRIMARY KEY는 DROP할 수 없다
-- (FK가 살아있는 동안은 참조 무결성 검증용 인덱스를 지울 수 없기 때문).
-- 그래서 각 부모 테이블(member, chat_room 등)의 PK를 BIGINT로 바꾸기 전에,
-- 그 테이블을 참조하는 모든 FK 제약조건을 스키마 전체에서 먼저 걷어내는
-- 이 준비 단계를 별도 버전으로 분리했다. 이후 V10~V19는 각 테이블을
-- 순수하게 "PK 교체" 또는 "PK 교체 + FK 재생성"만 하면 된다.
--
-- 이 파일은 순수 DROP FOREIGN KEY만 담고 있어 데이터 변형이 전혀 없고
-- 실패 가능성이 극히 낮다 (실패할 만한 게 거의 없음).
-- ============================================================

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
