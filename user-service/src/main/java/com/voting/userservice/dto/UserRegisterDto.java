package com.voting.userservice.dto;

import com.voting.userservice.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {
    private String name;
    private String email;
    private String password;
    private Role role;
}
