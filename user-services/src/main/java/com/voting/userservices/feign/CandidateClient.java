package com.voting.userservices.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "CANDIDATE-SERVICE")
public interface CandidateClient {
    @PutMapping("/voting/candidates/vote/{candidateId}")
    String addVote(@PathVariable Long candidateId);
}
