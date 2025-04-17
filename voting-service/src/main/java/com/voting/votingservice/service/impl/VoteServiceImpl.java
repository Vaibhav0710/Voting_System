package com.voting.votingservice.service.impl;

import com.voting.votingservice.dto.UserDTO;
import com.voting.votingservice.dto.VoteCountResponseDTO;
import com.voting.votingservice.dto.VoteRequestDTO;
import com.voting.votingservice.dto.VoteResponseDTO;
import com.voting.votingservice.entity.Vote;
import com.voting.votingservice.feignClient.UserFeignClient;
import com.voting.votingservice.repository.VoteRepository;
import com.voting.votingservice.service.VoteService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VoteServiceImpl implements VoteService {

    private VoteRepository voteRepository;

    private UserFeignClient userFeignClient;

    @Override
    public VoteResponseDTO addVote(VoteRequestDTO voteDto) {
        UserDTO user = userFeignClient.getUserById(voteDto.getUserId());
        if (user.getVoted()) {
            throw new RuntimeException("User has already voted.");
        }
        Vote vote = new Vote();
        vote.setCandidateId(voteDto.getCandidateId());
        vote.setVoteTime(LocalDateTime.now());
        voteRepository.save(vote);
        userFeignClient.markUserAsVoted(voteDto.getUserId());
        return new VoteResponseDTO(vote.getId(), vote.getCandidateId(), vote.getVoteTime());
    }

    @Override
    public Long countVoteByCandidateId(Long candidateId) {
        return voteRepository.countByCandidateId(candidateId);
    }

    @Override
    public List<VoteCountResponseDTO> countVote() {
        List<Vote> allVotes = voteRepository.findAll();
        Map<Long, Long> voteCountMap = new HashMap<>();
        for (Vote vote : allVotes) {
            Long candidateId = vote.getCandidateId();
            if (voteCountMap.containsKey(candidateId)) {
                voteCountMap.put(candidateId, voteCountMap.get(candidateId) + 1);
            } else {
                voteCountMap.put(candidateId, 1L);
            }
        }
        List<VoteCountResponseDTO> responseList = new ArrayList<VoteCountResponseDTO>();
        for (Map.Entry<Long, Long> entry : voteCountMap.entrySet()) {
            VoteCountResponseDTO dto = new VoteCountResponseDTO(entry.getKey(), entry.getValue());
            responseList.add(dto);
        }
        return responseList;
    }
}
