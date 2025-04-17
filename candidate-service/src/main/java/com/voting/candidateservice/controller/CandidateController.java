package com.voting.candidateservice.controller;

import com.voting.candidateservice.dto.CandidateDTO;
import com.voting.candidateservice.service.CandidateServices;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/candidate")
public class CandidateController {

    private CandidateServices candidateService;

    @PutMapping("/{id}")
    public ResponseEntity<CandidateDTO> updateCandidate(@PathVariable Long id, @RequestBody CandidateDTO candidateDTO) {
        return ResponseEntity.ok(candidateService.updateCandidate(id, candidateDTO));
    }

    @PostMapping("/createCandidate")
    public ResponseEntity<CandidateDTO> create(@RequestBody CandidateDTO dto) {
        return ResponseEntity.ok(candidateService.addCandidate(dto));
    }

    @GetMapping
    public ResponseEntity<List<CandidateDTO>> getAll() {
        return ResponseEntity.ok(candidateService.getAllCandidates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CandidateDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(candidateService.getCandidateById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        candidateService.deleteCandidate(id);
        return ResponseEntity.noContent().build();
    }
}
