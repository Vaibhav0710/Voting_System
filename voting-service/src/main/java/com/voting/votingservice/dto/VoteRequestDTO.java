package com.voting.votingservice.dto;

import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class VoteRequestDTO {
    private Long userId;
    private Long candidateId;
}
