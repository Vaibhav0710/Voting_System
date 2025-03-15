package com.voting.candidate_services.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CandidateRequest {

    @NotBlank(message = "Candidate name cannot be empty")
    private String name;

    @NotBlank(message = "Party name cannot be empty")
    private String party;

    @Min(value = 25, message = "Candidate must be at least 25 years old")
    private int age;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private String symbol; // URL or base64 image
}
