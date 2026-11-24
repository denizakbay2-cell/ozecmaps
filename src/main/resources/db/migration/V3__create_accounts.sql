-- People who sign in to keep their places (home, workplace, saved places) on every device. The password itself is
-- never stored, only a salted PBKDF2 hash of it (see Passwords).
CREATE TABLE user_account (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email) AND email LIKE '_%@_%'),
    display_name  VARCHAR(60),
    password_hash VARCHAR(200) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0
);

-- The places an account keeps, in its order: at most one home and one workplace, and saved places.
CREATE TABLE saved_place (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT           NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    position   SMALLINT         NOT NULL CHECK (position >= 0),
    kind       VARCHAR(10)      NOT NULL CHECK (kind IN ('HOME', 'WORK', 'PLACE')),
    label      VARCHAR(200)     NOT NULL,
    detail     VARCHAR(200),
    lat        DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lon        DOUBLE PRECISION NOT NULL CHECK (lon BETWEEN -180 AND 180)
);
CREATE INDEX ix_saved_place_account ON saved_place (account_id);
