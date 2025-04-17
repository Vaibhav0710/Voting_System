package com.voting.votingservice.controller;


import com.voting.votingservice.dto.VoteRequestDTO;
import com.voting.votingservice.entity.Vote;
import com.voting.votingservice.repository.VoteRepository;
import com.voting.votingservice.service.VoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voting")
public class VoterController {
    
    private VoteService voteService;

    @PostMapping("/vote")
    public ResponseEntity<?> vote(@RequestBody VoteRequestDTO voteDto) {
        return ResponseEntity.ok(voteService.addVote(voteDto));
    }

    @GetMapping("/vote/count")
    public ResponseEntity<?> getVoteCount() {
        return ResponseEntity.ok(voteService.countVote());
    }

    @GetMapping("/vote/count/{candidateId}")
    public ResponseEntity<?> getVoteCount(@PathVariable Long candidateId) {
        return ResponseEntity.ok(voteService.countVoteByCandidateId(candidateId));
    }
}
