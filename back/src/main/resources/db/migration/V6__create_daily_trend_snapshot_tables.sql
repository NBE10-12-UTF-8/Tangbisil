CREATE TABLE daily_keyword_count (
    id BIGINT NOT NULL AUTO_INCREMENT,
    date DATE NOT NULL,
    keyword VARCHAR(255) NOT NULL,
    frequency BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_keyword_count_date_keyword UNIQUE (date, keyword)
);

CREATE TABLE daily_message_count (
    id BIGINT NOT NULL AUTO_INCREMENT,
    date DATE NOT NULL,
    total_messages BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_message_count_date UNIQUE (date)
);
