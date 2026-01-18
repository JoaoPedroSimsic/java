package com.example.message.infrastructure.adapters.output.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaUserRepo extends JpaRepository<UserEntity, Long> {}
