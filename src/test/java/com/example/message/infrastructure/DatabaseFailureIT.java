package com.example.message.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.message.core.domain.User;
import com.example.message.core.exceptions.infrastructure.DatabaseUnavailableException;
import com.example.message.core.ports.output.UserRepositoryPort;
import com.example.message.infrastructure.adapters.output.db.entities.UserEntity;
import com.example.message.infrastructure.adapters.output.db.jpa.JpaUserRepo;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class DatabaseFailureIT extends BaseIntegrationTest {

  @Autowired private UserRepositoryPort userRepository;

  @Autowired private CacheManager cacheManager;

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @MockitoBean private JpaUserRepo mockJpaUserRepo;

  @BeforeEach
  void setUp() {
    circuitBreakerRegistry.circuitBreaker("userRepository").reset();
    cacheManager
        .getCacheNames()
        .forEach(
            name -> {
              var cache = cacheManager.getCache(name);
              if (cache != null) cache.clear();
            });
  }

  @Test
  void testFindByIdRecoveryFlow() {
    UserEntity savedEntity = new UserEntity();
    savedEntity.setId(1L);
    savedEntity.setName("Test User");
    savedEntity.setEmail("test@example.com");
    savedEntity.setPassword("password");

    when(mockJpaUserRepo.save(any())).thenReturn(savedEntity);

    User user =
        User.builder().name("Test User").email("test@example.com").password("password").build();

    User saved = userRepository.save(user);
    assertNotNull(saved);

    when(mockJpaUserRepo.findById(saved.getId()))
        .thenThrow(new DataAccessResourceFailureException("DB down"));

    User found = userRepository.find(saved.getId());
    assertNotNull(found);
    assertEquals("Test User", found.getName());
  }

  @Test
  void testSaveFailureThrowsCustomException() {
    when(mockJpaUserRepo.save(any())).thenThrow(new DataAccessResourceFailureException("DB down"));

    User user =
        User.builder().name("New User").email("new@example.com").password("password").build();

    Exception exception = assertThrows(Exception.class, () -> userRepository.save(user));

    Throwable rootCause = exception;
    while (rootCause.getCause() != null && !(rootCause instanceof DatabaseUnavailableException)) {
      rootCause = rootCause.getCause();
    }

    assertTrue(
        rootCause instanceof DatabaseUnavailableException
            || exception instanceof ExhaustedRetryException);
  }

  @Test
  void testFindAllEmptyFallback() {
    when(mockJpaUserRepo.findAll()).thenThrow(new DataAccessResourceFailureException("DB down"));

    List<User> users = userRepository.findAll();
    assertNotNull(users);
    assertTrue(users.isEmpty());
  }

  @Test
  void testCircuitBreakerStateTransition() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("userRepository");
    assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

    when(mockJpaUserRepo.save(any())).thenThrow(new DataAccessResourceFailureException("DB down"));

    User user = User.builder().name("CB User").email("cb@example.com").password("password").build();

    for (int i = 0; i < 15; i++) {
      try {
        userRepository.save(user);
      } catch (Exception ignored) {
      }
    }

    assertNotEquals(CircuitBreaker.State.CLOSED, cb.getState());
  }
}
