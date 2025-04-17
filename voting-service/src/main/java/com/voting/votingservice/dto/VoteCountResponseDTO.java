package com.voting.votingservice.dto;


import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VoteCountResponseDTO {
    private Long candidateId;
    private Long voteCount;
}
