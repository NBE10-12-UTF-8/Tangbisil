CREATE TABLE daily_cooccurrence_count (
    id BIGINT NOT NULL AUTO_INCREMENT,
    date DATE NOT NULL,
    keyword_a VARCHAR(255) NOT NULL,
    keyword_b VARCHAR(255) NOT NULL,
    frequency BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_cooccurrence_count_date_keywords UNIQUE (date, keyword_a, keyword_b)
);
