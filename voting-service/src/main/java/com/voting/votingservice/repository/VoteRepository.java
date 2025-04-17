package com.voting.votingservice.repository;

import com.voting.votingservice.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    Long countByCandidateId(Long candidateId);
}
