package io.github.joaosimsic.infrastructure.adapters.output.db.repositories;

import io.github.joaosimsic.core.domain.User;
import io.github.joaosimsic.core.exceptions.infrastructure.DatabaseUnavailableException;
import io.github.joaosimsic.core.ports.output.UserPort;
import io.github.joaosimsic.infrastructure.adapters.output.db.entities.UserEntity;
import io.github.joaosimsic.infrastructure.adapters.output.db.jpa.JpaUserRepo;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JpaUserRepository implements UserPort {
  private final JpaUserRepo repository;
  private final CacheManager cacheManager;

  public JpaUserRepository(JpaUserRepo repository, CacheManager cacheManager) {
    this.repository = repository;
    this.cacheManager = cacheManager;
  }

  @Override
  @CircuitBreaker(name = "userRepository")
  @Retryable(
      retryFor = {DataAccessException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  @CachePut(value = "userById", key = "#result.id", condition = "#result != null")
  @CacheEvict(
      value = {"users", "userByEmail", "userByExternalId"},
      allEntries = true)
  public User save(User user) {
    UserEntity entity = new UserEntity();

    entity.setId(user.getId());
    entity.setExternalId(user.getExternalId());
    entity.setName(user.getName());
    entity.setEmail(user.getEmail());
    entity.setPassword(user.getPassword());

    UserEntity saved = repository.save(entity);

    return User.builder()
        .id(saved.getId())
        .externalId(saved.getExternalId())
        .name(saved.getName())
        .email(saved.getEmail())
        .build();
  }

  @Recover
  public User recoverSave(DataAccessException e, User user) {
    log.error(
        "Failed to save user after retries due to database unavailability: {}", e.getMessage());
    throw new DatabaseUnavailableException(
        "Unable to save user. Database is temporarily unavailable.");
  }

  @Override
  @CircuitBreaker(name = "userRepository")
  @Retryable(
      retryFor = {DataAccessException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  @Cacheable(value = "users")
  public List<User> findAll() {
    return repository.findAll().stream()
        .map(
            e ->
                User.builder()
                    .id(e.getId())
                    .externalId(e.getExternalId())
                    .name(e.getName())
                    .email(e.getEmail())
                    .build())
        .collect(Collectors.toList());
  }

  @Recover
  public List<User> recoverFindAll(DataAccessException e) {
    log.error("Database failed, checking cache. Error: {}", e.getMessage());

    Cache cache = cacheManager.getCache("users");

    if (cache == null) {
      log.warn("No cache found for users, returning empty list");
      return Collections.emptyList();
    }

    Object cachedValue = cache.get("users", Object.class);

    if (cachedValue instanceof List<?> rawList) {
      return rawList.stream().filter(User.class::isInstance).map(User.class::cast).toList();
    }

    return Collections.emptyList();
  }

  @Override
  @CircuitBreaker(name = "userRepository")
  @Retryable(
      retryFor = {DataAccessException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  @Cacheable(value = "userById", key = "#id")
  public User find(Long id) {
    return repository
        .findById(id)
        .map(
            e ->
                User.builder()
                    .id(e.getId())
                    .externalId(e.getExternalId())
                    .name(e.getName())
                    .email(e.getEmail())
                    .build())
        .orElse(null);
  }

  @Recover
  public User recoverFind(DataAccessException e, Long id) {
    log.warn("DB Failure. Falling back to cache for ID: {}", id);
    return getFromCache("userById", id, User.class);
  }

  @Override
  @CircuitBreaker(name = "userRepository")
  @Retryable(
      retryFor = {DataAccessException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  @Cacheable(value = "userByEmail", key = "#email", unless = "#result == null")
  public User findByEmail(String email) {
    return repository
        .findByEmail(email)
        .map(
            e ->
                User.builder()
                    .id(e.getId())
                    .externalId(e.getExternalId())
                    .name(e.getName())
                    .email(e.getEmail())
                    .build())
        .orElse(null);
  }

  @Recover
  public User recoverFindByEmail(DataAccessException e, String email) {
    log.warn("DB Failure. Falling back to cache for Email: {}", email);
    return getFromCache("userByEmail", email, User.class);
  }

  @Override
  @CircuitBreaker(name = "userRepository")
  @Retryable(
      retryFor = {DataAccessException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  @Cacheable(value = "userByExternalId", key = "#externalId", unless = "#result == null")
  public User findByExternalId(String externalId) {
    return repository
        .findByExternalId(externalId)
        .map(
            e ->
                User.builder()
                    .id(e.getId())
                    .externalId(e.getExternalId())
                    .name(e.getName())
                    .email(e.getEmail())
                    .build())
        .orElse(null);
  }

  @Recover
  public User recoverFindByExternalId(DataAccessException e, String externalId) {
    log.warn("DB Failure. Falling back to cache for ExternalId: {}", externalId);
    return getFromCache("userByExternalId", externalId, User.class);
  }

  @Override
  @CircuitBreaker(name = "userRepository")
  @Retryable(
      retryFor = {DataAccessException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
  @CacheEvict(
      value = {"users", "userById", "userByEmail", "userByExternalId"},
      allEntries = true)
  public void delete(Long id) {
    repository.deleteById(id);
  }

  @Recover
  public void recoverDelete(DataAccessException e, Long id) {
    log.error("Failed to delete user {} after retries: {}", id, e.getMessage());
    throw new DatabaseUnavailableException(
        "Unable to delete user. Database is temporarily unavailable.");
  }

  private <T> T getFromCache(String cacheName, Object key, Class<T> type) {
    Cache cache = cacheManager.getCache(cacheName);

    if (cache == null) {
      log.warn("No cache found for {}, returning null", cacheName);
      return null;
    }

    return cache.get(key, type);
  }
}
