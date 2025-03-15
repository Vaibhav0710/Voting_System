package com.voting.userservices.repository;

import com.voting.userservices.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByHasVoted(boolean hasVoted);
}
