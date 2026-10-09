CREATE TABLE api_token (
    id                      UUID PRIMARY KEY,
    prefix                  VARCHAR(16)  NOT NULL,
    token_hash              VARCHAR(255) NOT NULL,
    name                    VARCHAR(128) NOT NULL,
    description             VARCHAR(512),
    owner_id                VARCHAR(128) NOT NULL,
    tenant_id               VARCHAR(128),
    status                  VARCHAR(32)  NOT NULL,
    expires_at              TIMESTAMPTZ,
    sliding_ttl_seconds     INT,
    last_used_at            TIMESTAMPTZ,
    rate_limit_requests     INT,
    rate_limit_window_seconds INT,
    created_at              TIMESTAMPTZ  NOT NULL,
    revoked_at              TIMESTAMPTZ,
    created_by              VARCHAR(128),
    CONSTRAINT uq_api_token_prefix UNIQUE (prefix)
);

CREATE INDEX idx_api_token_owner_id ON api_token (owner_id);
CREATE INDEX idx_api_token_tenant_id ON api_token (tenant_id);
CREATE INDEX idx_api_token_prefix ON api_token (prefix);

CREATE TABLE api_token_scope (
    token_id UUID         NOT NULL,
    scope    VARCHAR(128) NOT NULL,
    PRIMARY KEY (token_id, scope),
    CONSTRAINT fk_api_token_scope_token
        FOREIGN KEY (token_id) REFERENCES api_token (id) ON DELETE CASCADE
);

CREATE TABLE scope_mapping (
    id             UUID PRIMARY KEY,
    token_scope    VARCHAR(128) NOT NULL,
    project_scope  VARCHAR(128) NOT NULL,
    tenant_id      VARCHAR(128),
    CONSTRAINT uq_scope_mapping UNIQUE (token_scope, project_scope, tenant_id)
);

CREATE TABLE api_token_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    token_id        UUID,
    owner_id        VARCHAR(128),
    tenant_id       VARCHAR(128),
    http_method     VARCHAR(16),
    endpoint        VARCHAR(512),
    action          VARCHAR(128),
    success         BOOLEAN      NOT NULL,
    response_status INT,
    ip              VARCHAR(64),
    user_agent      VARCHAR(512),
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_api_token_audit_log_token_created
    ON api_token_audit_log (token_id, created_at);
CREATE INDEX idx_api_token_audit_log_created
    ON api_token_audit_log (created_at);
