package com.voting.candidate_services.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CandidateResponse {
    private Long id;
    private String name;
    private String party;
    private int age;
    private String description;
    private String symbol;
    private int votes;
}
