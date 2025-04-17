package com.voting.candidateservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Candidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long age;

    @Column(nullable = false)
    private String party;

    @Column(nullable = false)
    private String manifesto;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String constituency;
}
