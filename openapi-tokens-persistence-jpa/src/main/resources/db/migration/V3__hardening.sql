-- Hardening migration:
--   * optimistic locking for api_token;
--   * tenant-aware uniqueness that actually de-duplicates NULL tenants;
--   * removal of redundant indexes and one index for the scope mapping lookup.

ALTER TABLE api_token ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- uq_scope_mapping did not deduplicate rows with tenant_id IS NULL (NULLs are distinct in SQL),
-- so logical duplicates must be removed BEFORE the stricter constraint is added.
DELETE FROM scope_mapping a
    USING scope_mapping b
    WHERE a.id > b.id
      AND a.token_scope = b.token_scope
      AND a.project_scope = b.project_scope
      AND a.tenant_id IS NOT DISTINCT FROM b.tenant_id;

-- PostgreSQL 15+ supports NULLS NOT DISTINCT.
ALTER TABLE scope_mapping DROP CONSTRAINT uq_scope_mapping;
ALTER TABLE scope_mapping
    ADD CONSTRAINT uq_scope_mapping UNIQUE NULLS NOT DISTINCT (token_scope, project_scope, tenant_id);

-- idx_api_token_prefix duplicates the index behind uq_api_token_prefix.
DROP INDEX IF EXISTS idx_api_token_prefix;

-- No query filters by tenant alone.
DROP INDEX IF EXISTS idx_api_token_tenant_id;

-- Scope mapping lookup used on every authentication.
CREATE INDEX IF NOT EXISTS idx_scope_mapping_token_scope_tenant
    ON scope_mapping (token_scope, tenant_id);
