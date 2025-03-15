package com.voting.candidate_services.services;


import com.voting.candidate_services.dto.CandidateRequest;
import com.voting.candidate_services.dto.CandidateResponse;

import java.util.List;

public interface CandidateService {
    CandidateResponse createCandidate(CandidateRequest request);

    List<CandidateResponse> getAllCandidates();

    CandidateResponse getCandidateById(Long id);

    CandidateResponse updateCandidate(Long id, CandidateRequest request);

    String addVote(Long candidateId);

    void deleteCandidate(Long id);
}
