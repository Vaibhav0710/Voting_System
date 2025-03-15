package com.voting.candidate_services.repository;

import com.voting.candidate_services.entities.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CandidateRepository extends JpaRepository<Candidate, Long> {

}