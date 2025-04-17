package com.voting.votingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class VotingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(VotingServiceApplication.class, args);
	}

}
