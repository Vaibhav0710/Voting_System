package com.voting.votingservice.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class VoteResponseDTO {
    private Long voteId;
    private Long candidateId;
    private LocalDateTime voteTime;
}
