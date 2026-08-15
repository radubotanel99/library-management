-- Seed the settings the application needs to run.
-- updated_at stays NULL until a setting is actually changed.

INSERT INTO parameter (key, value) VALUES ('DAYS_TO_KEEP_A_BOOK', '14');
INSERT INTO parameter (key, value) VALUES ('MAX_BOOKS_PER_MEMBER', '3');
