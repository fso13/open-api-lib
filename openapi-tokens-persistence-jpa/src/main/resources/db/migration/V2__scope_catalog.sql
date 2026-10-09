CREATE TABLE scope_catalog (
    scope       VARCHAR(128) PRIMARY KEY,
    description VARCHAR(512)
);

COMMENT ON TABLE scope_catalog IS 'Human readable descriptions of token scopes; overrides openapi.tokens.scopes.catalog';
