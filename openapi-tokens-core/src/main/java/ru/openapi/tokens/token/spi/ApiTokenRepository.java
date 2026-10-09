package ru.openapi.tokens.token.spi;

import ru.openapi.tokens.token.ApiToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for API tokens.
 */
public interface ApiTokenRepository {

    ApiToken save(ApiToken token);

    Optional<ApiToken> findById(UUID id);

    Optional<ApiToken> findByPrefix(String prefix);

    List<ApiToken> findByOwnerId(String ownerId);

    List<ApiToken> findAll();

    long countByOwnerId(String ownerId);

    void deleteById(UUID id);
}
