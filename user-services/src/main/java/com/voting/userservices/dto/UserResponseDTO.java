package com.voting.userservices.dto;

import com.voting.userservices.entities.Role;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserResponseDTO {
    private Long id;
    private String name;
    private String email;
    private Role role;
    private boolean hasVoted;
}
