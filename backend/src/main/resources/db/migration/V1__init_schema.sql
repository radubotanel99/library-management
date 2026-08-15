-- Initial schema: category, member, parameter, book, loan.
--
-- Auditing note: created_at / updated_at deliberately have NO database defaults and
-- NO triggers. They are set exclusively by JPA auditing in the application layer
-- (see AuditableEntity + JpaConfig). A raw INSERT that bypasses the application will
-- therefore fail on created_at NOT NULL -- that is intentional, not an oversight:
-- the application is the only supported writer.
--
-- All timestamps are TIMESTAMPTZ and stored in UTC.

CREATE TABLE category (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE member (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    email        VARCHAR(255),
    address      VARCHAR(500),
    phone_number VARCHAR(50),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Key/value settings store. No created_at by design: a parameter row is seeded once
-- and only ever updated, so its creation time carries no information.
CREATE TABLE parameter (
    key        VARCHAR(100) PRIMARY KEY,
    value      VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ
);

CREATE TABLE book (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255)  NOT NULL,
    author       VARCHAR(255)  NOT NULL,
    book_number  INTEGER       NOT NULL,
    category_id  BIGINT        NOT NULL,
    publisher    VARCHAR(255),
    price        NUMERIC(10, 2),
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    removal_note VARCHAR(500),
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ,
    CONSTRAINT fk_book_category FOREIGN KEY (category_id) REFERENCES category (id)
);

-- No ON DELETE clause anywhere: categories, members and books are only ever
-- soft-deleted (deleted flag / status enum). A hard DELETE while still referenced
-- must be rejected (NO ACTION), never cascaded and never nulled out.
CREATE TABLE loan (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT      NOT NULL,
    member_id   BIGINT      NOT NULL,
    state       VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT fk_loan_book FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT fk_loan_member FOREIGN KEY (member_id) REFERENCES member (id)
);

COMMENT ON COLUMN loan.created_at IS
    'The borrow date of the loan.';

-- There is deliberately NO due_at column. The due date is derived at read time as
-- created_at + parameter(DAYS_TO_KEEP_A_BOOK), so that changing the lending period
-- retroactively re-evaluates every open loan. Storing it would freeze that. Do not
-- "fix" this by adding a column.
COMMENT ON TABLE loan IS
    'Loans have no due_at column by design: due date = created_at + DAYS_TO_KEEP_A_BOOK, computed at read time.';

-- Enum value guards. Enums are persisted as strings, so the database enforces the
-- allowed set. loan.state stores ACTIVE | LATE | FINISHED only -- the API-level OPEN
-- filter is an alias for (ACTIVE, LATE) and is never a stored value.
ALTER TABLE book ADD CONSTRAINT ck_book_status
    CHECK (status IN ('ACTIVE', 'LOST', 'DAMAGED', 'WITHDRAWN'));

ALTER TABLE loan ADD CONSTRAINT ck_loan_state
    CHECK (state IN ('ACTIVE', 'LATE', 'FINISHED'));

-- Partial unique indexes: uniqueness applies only to live rows, so a soft-deleted
-- category/member name and a removed book's number become reusable.
CREATE UNIQUE INDEX ux_category_name_active ON category (lower(name)) WHERE deleted = false;
CREATE UNIQUE INDEX ux_member_name_active ON member (lower(name)) WHERE deleted = false;
CREATE UNIQUE INDEX ux_book_number_active ON book (book_number) WHERE status = 'ACTIVE';

-- A book can have at most one loan that is not finished.
CREATE UNIQUE INDEX ux_loan_one_open_per_book ON loan (book_id) WHERE state IN ('ACTIVE', 'LATE');

CREATE INDEX ix_book_category ON book (category_id);
CREATE INDEX ix_book_status ON book (status);
CREATE INDEX ix_book_title ON book (lower(title));
CREATE INDEX ix_book_author ON book (lower(author));
CREATE INDEX ix_loan_member ON loan (member_id);
CREATE INDEX ix_loan_state ON loan (state);
CREATE INDEX ix_loan_book ON loan (book_id);
