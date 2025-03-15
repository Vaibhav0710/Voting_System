package com.voting.candidate_services.services.impl;

import com.voting.candidate_services.dto.CandidateRequest;
import com.voting.candidate_services.dto.CandidateResponse;
import com.voting.candidate_services.entities.Candidate;
import com.voting.candidate_services.repository.CandidateRepository;
import com.voting.candidate_services.services.CandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateServiceImpl implements CandidateService {

    private final CandidateRepository candidateRepository;

    private CandidateResponse mapToResponse(Candidate candidate) {
        return new CandidateResponse(
                candidate.getId(),
                candidate.getName(),
                candidate.getParty(),
                candidate.getAge(),
                candidate.getDescription(),
                candidate.getSymbol(),
                candidate.getVotes()
        );
    }

    @Override
    public CandidateResponse createCandidate(CandidateRequest request) {
        Candidate candidate = new Candidate();
        candidate.setName(request.getName());
        candidate.setParty(request.getParty());
        candidate.setAge(request.getAge());
        candidate.setDescription(request.getDescription());
        candidate.setSymbol(request.getSymbol());

        Candidate savedCandidate = candidateRepository.save(candidate);
        return mapToResponse(savedCandidate);
    }

    @Override
    public List<CandidateResponse> getAllCandidates() {
        return candidateRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public CandidateResponse getCandidateById(Long id) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found with id: " + id));
        return mapToResponse(candidate);
    }

    @Override
    public CandidateResponse updateCandidate(Long id, CandidateRequest request) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found with id: " + id));

        candidate.setName(request.getName());
        candidate.setParty(request.getParty());
        candidate.setAge(request.getAge());
        candidate.setDescription(request.getDescription());
        candidate.setSymbol(request.getSymbol());

        Candidate updatedCandidate = candidateRepository.save(candidate);
        return mapToResponse(updatedCandidate);
    }

    @Override
    public String addVote(Long candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found with id: " + candidateId));

        candidate.setVotes(candidate.getVotes() + 1);
        candidateRepository.save(candidate);
        return "Vote added successfully to candidate: " + candidate.getName();
    }

    @Override
    public void deleteCandidate(Long id) {
        candidateRepository.deleteById(id);
    }
}
