package com.voting.candidate_services.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "candidates")
@Getter
@Setter
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
    private String party;
    @Column(nullable = false)
    private int age;
    @Column(length = 500)
    private String description;
    private String symbol;
    private int votes = 0;
}
