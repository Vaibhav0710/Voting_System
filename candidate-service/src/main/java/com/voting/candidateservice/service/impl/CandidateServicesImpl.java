package com.voting.candidateservice.service.impl;

import com.voting.candidateservice.dto.CandidateDTO;
import com.voting.candidateservice.entity.Candidate;
import com.voting.candidateservice.repository.CandidateRepository;
import com.voting.candidateservice.service.CandidateServices;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CandidateServicesImpl implements CandidateServices {

    private CandidateRepository candidateRepository;

    @Override
    public CandidateDTO updateCandidate(Long id, CandidateDTO candidateDTO) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found with ID: " + id));

        candidate.setName(candidateDTO.getName());
        candidate.setManifesto(candidateDTO.getManifesto());
        candidate.setAge(candidateDTO.getAge());
        candidate.setSymbol(candidateDTO.getSymbol());
        candidateRepository.save(candidate);
        return mapToDto(candidate);
    }

    @Override
    public CandidateDTO addCandidate(CandidateDTO dto) {
        Candidate saved = candidateRepository.save(mapToEntity(dto));
        return mapToDto(saved);
    }

    @Override
    public List<CandidateDTO> getAllCandidates() {
        return candidateRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public CandidateDTO getCandidateById(Long id) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        return mapToDto(candidate);
    }

    @Override
    public void deleteCandidate(Long id) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found with ID: " + id));

        candidateRepository.delete(candidate);
    }

    private CandidateDTO mapToDto(Candidate candidate) {
        CandidateDTO dto = new CandidateDTO();
        BeanUtils.copyProperties(candidate, dto);
        return dto;
    }

    private Candidate mapToEntity(CandidateDTO dto) {
        Candidate candidate = new Candidate();
        BeanUtils.copyProperties(dto, candidate);
        return candidate;
    }
}
