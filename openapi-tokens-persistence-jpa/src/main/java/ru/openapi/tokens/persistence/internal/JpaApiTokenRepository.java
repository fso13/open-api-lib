package ru.openapi.tokens.persistence.internal;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.openapi.tokens.persistence.internal.ApiTokenEntity;
import ru.openapi.tokens.persistence.internal.ApiTokenEntityMapper;
import ru.openapi.tokens.persistence.internal.ApiTokenJpaRepository;
import ru.openapi.tokens.token.ApiToken;
import ru.openapi.tokens.token.spi.ApiTokenRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaApiTokenRepository implements ApiTokenRepository {

    private final ApiTokenJpaRepository jpaRepository;
    private final ApiTokenEntityMapper mapper;

    public JpaApiTokenRepository(ApiTokenJpaRepository jpaRepository, ApiTokenEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ApiToken save(ApiToken token) {
        Objects.requireNonNull(token, "token");
        final ApiTokenEntity saved = jpaRepository.save(mapper.toEntity(token));
        // scopes are LAZY — reload with graph via findByPrefix / findById after flush
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ApiToken> findById(UUID id) {
        return jpaRepository.findById(id).map(entity -> {
            entity.getScopes().size(); // initialize LAZY within transaction
            return mapper.toDomain(entity);
        });
    }

    @Override
    public Optional<ApiToken> findByPrefix(String prefix) {
        return jpaRepository.findByPrefix(prefix).map(mapper::toDomain);
    }

    @Override
    public List<ApiToken> findByOwnerId(String ownerId) {
        return jpaRepository.findByOwnerId(ownerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ApiToken> findAll() {
        return jpaRepository.findAll().stream()
                .peek(entity -> entity.getScopes().size())
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByOwnerId(String ownerId) {
        return jpaRepository.countByOwnerId(ownerId);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
