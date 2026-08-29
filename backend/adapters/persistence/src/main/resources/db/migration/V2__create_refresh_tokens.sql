-- Create the refresh_tokens table (rotating, hashed refresh tokens). Scope: Log In (sc-40).
--
-- Design notes:
--  * token_hash: SHA-256 hex (64 chars) of the raw refresh token — the plaintext
--    token is NEVER stored, so a leak of this column cannot be replayed.
--  * The hash is the natural primary key, so lookup/revoke are single-index
--    point operations keyed on the hash.
--  * account_id: FK to users(id), cascade-deleted with the owning account.
--  * Tokens are single-use and rotated: refresh deletes the old hash and
--    inserts a new one for the same account.
CREATE TABLE refresh_tokens (
    token_hash CHAR(64)    NOT NULL,
    account_id UUID        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (token_hash),
    CONSTRAINT fk_refresh_tokens_account FOREIGN KEY (account_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_account ON refresh_tokens (account_id);