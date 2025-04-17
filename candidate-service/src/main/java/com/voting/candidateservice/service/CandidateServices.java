package com.voting.candidateservice.service;


import com.voting.candidateservice.dto.CandidateDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface CandidateServices {
    List<CandidateDTO> getAllCandidates();
    CandidateDTO addCandidate(CandidateDTO candidateDTO);
    CandidateDTO updateCandidate(Long id, CandidateDTO candidateDTO);
    void deleteCandidate(Long id);
    CandidateDTO getCandidateById(Long id);
}
