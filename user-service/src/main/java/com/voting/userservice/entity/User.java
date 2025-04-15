package com.voting.userservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Email
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean voted;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
