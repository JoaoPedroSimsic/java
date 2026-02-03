package com.example.message.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.example.message.core.domain.User;
import com.example.message.core.exceptions.infrastructure.DatabaseUnavailableException;
import com.example.message.core.ports.output.UserRepositoryPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.persistence.QueryTimeoutException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseFailureIT extends BaseIntegrationTest {

  @Autowired private UserRepositoryPort userRepository;

  @Autowired private CacheManager cacheManager;

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @MockitoSpyBean private UserRepositoryPort spyUserRepository;

  @BeforeEach
  void setUp() {
    circuitBreakerRegistry.circuitBreaker("userRepository").reset();
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  @Test
  void testFindByIdRecoveryFlow() {
    User user =
        User.builder().name("Test User").email("test@example.com").password("password").build();

    User saved = userRepository.save(user);

    doThrow(new QueryTimeoutException("DB down")).when(spyUserRepository).find(saved.getId());

    User found = userRepository.find(saved.getId());
    assertNotNull(found);
    assertEquals("Test User", found.getName());
  }

  @Test
  void testSaveFailureThrowsCustomException() {
    doThrow(new QueryTimeoutException("DB down")).when(spyUserRepository).save(any());

    User user =
        User.builder().name("New User").email("new@example.com").password("password").build();

    assertThrows(DatabaseUnavailableException.class, () -> userRepository.save(user));
  }

  @Test
  void testFindAllEmptyFallback() {
    doThrow(new QueryTimeoutException("DB down")).when(spyUserRepository).findAll();

    List<User> users = userRepository.findAll();
    assertNotNull(users);
    assertTrue(users.isEmpty());
  }

  @Test
  void testCircuitBreakerStateTransition() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("userRepository");
    assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

    doThrow(new QueryTimeoutException("DB down")).when(spyUserRepository).find(any());

    for (int i = 0; i < 20; i++) {
      try {
        userRepository.find(1L);
      } catch (Exception e) {
      }
    }

    assertNotEquals(CircuitBreaker.State.CLOSED, cb.getState());
  }
}
