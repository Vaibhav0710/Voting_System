package com.voting.votingservice.feignClient;

import com.voting.votingservice.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "user-service")
public interface UserFeignClient {
    @GetMapping("/users/{id}")
    UserDTO getUserById(@PathVariable("id") Long id);

    @PutMapping("/users/{id}/voted")
    ResponseEntity<String> markUserAsVoted(@PathVariable("id") Long id);
}
