-- ConfirmIQ schema. Monetary values and rates are stored as TEXT decimal strings
-- to avoid floating-point rounding in SQLite (see BigDecimalStringConverter).

CREATE TABLE counterparty (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    short_code              VARCHAR(20)  NOT NULL UNIQUE,
    legal_name              VARCHAR(200) NOT NULL,
    lei                     VARCHAR(20)  NOT NULL UNIQUE,
    bic                     VARCHAR(11),
    country                 VARCHAR(2)   NOT NULL,
    client_type             VARCHAR(30)  NOT NULL,
    delivery_channel        VARCHAR(20)  NOT NULL,
    confirmation_sla_hours  INTEGER      NOT NULL DEFAULT 24,
    master_agreement        VARCHAR(100),
    active                  BOOLEAN      NOT NULL DEFAULT 1,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP    NOT NULL
);

CREATE TABLE counterparty_contact (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    counterparty_id  INTEGER      NOT NULL REFERENCES counterparty (id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    email            VARCHAR(200) NOT NULL,
    phone            VARCHAR(40),
    role             VARCHAR(40)  NOT NULL,
    is_primary       BOOLEAN      NOT NULL DEFAULT 0
);

-- counterparty_id NULL = house (Meridian Capital) settlement instruction
CREATE TABLE settlement_instruction (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    counterparty_id    INTEGER REFERENCES counterparty (id) ON DELETE CASCADE,
    currency           VARCHAR(3)   NOT NULL,
    beneficiary_name   VARCHAR(200) NOT NULL,
    bank_name          VARCHAR(200) NOT NULL,
    bank_bic           VARCHAR(11)  NOT NULL,
    account_number     VARCHAR(40)  NOT NULL,
    intermediary_bic   VARCHAR(11),
    effective_from     DATE         NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT 1
);
CREATE INDEX ix_ssi_cpty_ccy ON settlement_instruction (counterparty_id, currency);

CREATE TABLE app_user (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    username         VARCHAR(60)  NOT NULL UNIQUE,
    full_name        VARCHAR(120) NOT NULL,
    email            VARCHAR(200) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    counterparty_id  INTEGER REFERENCES counterparty (id),
    active           BOOLEAN      NOT NULL DEFAULT 1,
    last_login_at    TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL
);

CREATE TABLE trade (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    trade_ref         VARCHAR(30)  UNIQUE,
    external_ref      VARCHAR(60)  NOT NULL UNIQUE,
    source_system     VARCHAR(40)  NOT NULL,
    ingestion_channel VARCHAR(20)  NOT NULL,
    product_type      VARCHAR(20)  NOT NULL,
    counterparty_id   INTEGER      NOT NULL REFERENCES counterparty (id),
    trade_date        DATE         NOT NULL,
    execution_time    TIMESTAMP    NOT NULL,
    value_date        DATE         NOT NULL,
    buy_currency      VARCHAR(3)   NOT NULL,
    buy_amount        TEXT         NOT NULL,
    sell_currency     VARCHAR(3)   NOT NULL,
    sell_amount       TEXT         NOT NULL,
    rate              TEXT         NOT NULL,
    far_value_date    DATE,
    far_buy_amount    TEXT,
    far_sell_amount   TEXT,
    far_rate          TEXT,
    settlement_currency VARCHAR(3),
    fixing_date       DATE,
    fixing_source     VARCHAR(60),
    book              VARCHAR(40)  NOT NULL,
    trader            VARCHAR(60)  NOT NULL,
    economic_version  INTEGER      NOT NULL DEFAULT 1,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    lock_version      INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX ix_trade_cpty ON trade (counterparty_id);
CREATE INDEX ix_trade_date ON trade (trade_date);

CREATE TABLE confirmation (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    trade_id         INTEGER     NOT NULL UNIQUE REFERENCES trade (id),
    status           VARCHAR(30) NOT NULL,
    prepared_by      VARCHAR(60),
    approved_by      VARCHAR(60),
    sent_at          TIMESTAMP,
    sla_due_at       TIMESTAMP,
    matched_at       TIMESTAMP,
    confirmed_at     TIMESTAMP,
    chaser_count     INTEGER     NOT NULL DEFAULT 0,
    last_chased_at   TIMESTAMP,
    escalated        BOOLEAN     NOT NULL DEFAULT 0,
    dispute_reason   TEXT,
    status_changed_at TIMESTAMP  NOT NULL,
    created_at       TIMESTAMP   NOT NULL,
    lock_version     INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX ix_conf_status ON confirmation (status);

CREATE TABLE confirmation_template (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    code             VARCHAR(60)  NOT NULL UNIQUE,
    name             VARCHAR(120) NOT NULL,
    product_type     VARCHAR(20)  NOT NULL,
    counterparty_id  INTEGER REFERENCES counterparty (id),
    created_at       TIMESTAMP    NOT NULL
);

CREATE TABLE template_version (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    template_id   INTEGER      NOT NULL REFERENCES confirmation_template (id) ON DELETE CASCADE,
    version       INTEGER      NOT NULL,
    title         VARCHAR(200) NOT NULL,
    preamble      TEXT         NOT NULL,
    clauses       TEXT         NOT NULL,
    closing       TEXT         NOT NULL,
    change_note   VARCHAR(300),
    active        BOOLEAN      NOT NULL DEFAULT 0,
    created_by    VARCHAR(60)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    UNIQUE (template_id, version)
);

CREATE TABLE document (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    trade_id             INTEGER      REFERENCES trade (id),
    doc_type             VARCHAR(30)  NOT NULL,
    direction            VARCHAR(10)  NOT NULL,
    stage                VARCHAR(10)  NOT NULL,
    version              INTEGER      NOT NULL,
    file_name            VARCHAR(200) NOT NULL,
    storage_key          VARCHAR(300) NOT NULL,
    sha256               VARCHAR(64)  NOT NULL,
    size_bytes           INTEGER      NOT NULL,
    mime_type            VARCHAR(60)  NOT NULL,
    terms_hash           VARCHAR(64),
    template_version_id  INTEGER REFERENCES template_version (id),
    source               VARCHAR(30)  NOT NULL,
    processing_status    VARCHAR(30),
    processing_note      TEXT,
    created_by           VARCHAR(60)  NOT NULL,
    created_at           TIMESTAMP    NOT NULL
);
CREATE INDEX ix_doc_trade ON document (trade_id);

CREATE TABLE match_result (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    trade_id             INTEGER     NOT NULL REFERENCES trade (id),
    inbound_document_id  INTEGER     NOT NULL REFERENCES document (id),
    outcome              VARCHAR(20) NOT NULL,
    identified_by        VARCHAR(20) NOT NULL,
    qr_verified          BOOLEAN     NOT NULL DEFAULT 0,
    fields_json          TEXT        NOT NULL,
    created_at           TIMESTAMP   NOT NULL
);
CREATE INDEX ix_match_trade ON match_result (trade_id);

CREATE TABLE outbox_message (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    trade_id      INTEGER REFERENCES trade (id),
    message_type  VARCHAR(30)  NOT NULL,
    recipients    VARCHAR(500) NOT NULL,
    cc            VARCHAR(500),
    subject       VARCHAR(300) NOT NULL,
    body          TEXT         NOT NULL,
    document_id   INTEGER REFERENCES document (id),
    status        VARCHAR(20)  NOT NULL,
    created_by    VARCHAR(60)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);
CREATE INDEX ix_outbox_trade ON outbox_message (trade_id);

CREATE TABLE audit_event (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    trade_id      INTEGER,
    entity_type   VARCHAR(30)  NOT NULL,
    entity_id     INTEGER,
    action        VARCHAR(40)  NOT NULL,
    from_status   VARCHAR(30),
    to_status     VARCHAR(30),
    actor         VARCHAR(60)  NOT NULL,
    actor_role    VARCHAR(20),
    details       TEXT,
    comment       TEXT,
    occurred_at   TIMESTAMP    NOT NULL
);
CREATE INDEX ix_audit_trade ON audit_event (trade_id);
CREATE INDEX ix_audit_time ON audit_event (occurred_at);
