package com.voting.userservices.dto;

import com.voting.userservices.entities.Role;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserRequestDTO {
    private String name;
    private String email;
    private String password;
    private Role role;
}
