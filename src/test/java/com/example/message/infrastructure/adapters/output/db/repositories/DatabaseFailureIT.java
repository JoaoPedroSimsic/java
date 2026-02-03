package com.example.message.infrastructure.adapters.output.db.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.message.core.domain.User;
import com.example.message.core.exceptions.infrastructure.DatabaseUnavailableException;
import com.example.message.core.ports.output.UserRepositoryPort;
import com.example.message.infrastructure.BaseIntegrationTest;
import com.example.message.infrastructure.adapters.output.db.entities.UserEntity;
import com.example.message.infrastructure.adapters.output.db.jpa.JpaUserRepo;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class DatabaseFailureIT extends BaseIntegrationTest {

  @Autowired private UserRepositoryPort userRepository;
  @Autowired private CacheManager cacheManager;
  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;
  @MockitoBean private JpaUserRepo mockJpaUserRepo;

  private User testUser;
  private UserEntity testEntity;

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

    testUser =
        User.builder().name("Test User").email("test@example.com").password("password").build();

    testEntity = new UserEntity();
    testEntity.setId(1L);
    testEntity.setName("Test User");
    testEntity.setEmail("test@example.com");
  }

  @Test
  void testFindByIdRecoveryFlow() {
    when(mockJpaUserRepo.save(any())).thenReturn(testEntity);
    User saved = userRepository.save(testUser);

    when(mockJpaUserRepo.findById(saved.getId()))
        .thenThrow(new DataAccessResourceFailureException("DB down"));

    User found = userRepository.find(saved.getId());

    assertThat(found).isNotNull();
    assertThat(found.getName()).isEqualTo("Test User");
    verify(mockJpaUserRepo, times(0)).findById(saved.getId());
  }

  @Test
  void testSaveFailureThrowsCustomException() {
    when(mockJpaUserRepo.save(any())).thenThrow(new DataAccessResourceFailureException("DB down"));

    assertThatThrownBy(() -> userRepository.save(testUser))
        .isInstanceOf(DatabaseUnavailableException.class);
  }

  @Test
  void testFindAllEmptyFallback() {
    when(mockJpaUserRepo.findAll()).thenThrow(new DataAccessResourceFailureException("DB down"));

    List<User> users = userRepository.findAll();

    assertThat(users).isEmpty();
  }

  @Test
  void testCircuitBreakerStateTransition() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("userRepository");
    int callsToMake =
        Math.max(
            cb.getCircuitBreakerConfig().getSlidingWindowSize(),
            cb.getCircuitBreakerConfig().getMinimumNumberOfCalls());

    when(mockJpaUserRepo.save(any())).thenThrow(new DataAccessResourceFailureException("DB down"));

    for (int i = 0; i < callsToMake; i++) {
      assertThrows(Exception.class, () -> userRepository.save(testUser));
    }

    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThatThrownBy(() -> userRepository.save(testUser))
        .isInstanceOf(CallNotPermittedException.class);
  }
}
