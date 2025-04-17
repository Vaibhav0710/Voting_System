package com.voting.votingservice.service;

import com.voting.votingservice.dto.VoteCountResponseDTO;
import com.voting.votingservice.dto.VoteRequestDTO;
import com.voting.votingservice.dto.VoteResponseDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface VoteService {
    VoteResponseDTO addVote(VoteRequestDTO voteDto);

    Long countVoteByCandidateId(Long candidateId);

    List<VoteCountResponseDTO> countVote();
}
