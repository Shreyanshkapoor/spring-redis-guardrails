package com.grid07.controller;

import com.grid07.entity.Bot;
import com.grid07.entity.User;
import com.grid07.repository.BotRepository;
import com.grid07.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SeedController {

    private final UserRepository userRepo;
    private final BotRepository  botRepo;

    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userRepo.save(user));
    }

    @PostMapping("/bots")
    public ResponseEntity<Bot> createBot(@RequestBody Bot bot) {
        return ResponseEntity.status(HttpStatus.CREATED).body(botRepo.save(bot));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        return ResponseEntity.ok(userRepo.findAll());
    }

    @GetMapping("/bots")
    public ResponseEntity<?> getBots() {
        return ResponseEntity.ok(botRepo.findAll());
    }
}
