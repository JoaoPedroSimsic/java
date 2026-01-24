package com.example.message.infrastructure.adapters.output.db.jpa;

import com.example.message.infrastructure.adapters.output.db.entities.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaUserRepo extends JpaRepository<UserEntity, Long> {
  Optional<UserEntity> findByEmail(String email);
}
