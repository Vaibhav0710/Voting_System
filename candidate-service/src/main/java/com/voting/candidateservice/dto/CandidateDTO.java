package com.voting.candidateservice.dto;

import jakarta.persistence.Column;
import lombok.*;

@Setter
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class CandidateDTO {
    private Long id;
    private Long age;
    private String name;
    private String party;
    private String manifesto;
    private String constituency;
    private String symbol;
}
