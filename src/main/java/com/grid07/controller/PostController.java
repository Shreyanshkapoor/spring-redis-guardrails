package com.grid07.controller;

import com.grid07.dto.CreateCommentRequest;
import com.grid07.dto.CreatePostRequest;
import com.grid07.dto.LikeRequest;
import com.grid07.entity.Comment;
import com.grid07.entity.Post;
import com.grid07.service.GuardrailService;
import com.grid07.service.PostService;
import com.grid07.service.ViralityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService       postService;
    private final ViralityService   viralityService;
    private final GuardrailService  guardrailService;



    @PostMapping
    public ResponseEntity<Post> createPost(@Valid @RequestBody CreatePostRequest req) {
        Post post = postService.createPost(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }


    @PostMapping("/{postId}/comments")
    public ResponseEntity<Comment> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest req) {
        Comment comment = postService.addComment(postId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }


    @PostMapping("/{postId}/like")
    public ResponseEntity<Map<String, Object>> likePost(
            @PathVariable Long postId,
            @Valid @RequestBody LikeRequest req) {
        postService.likePost(postId, req);
        return ResponseEntity.ok(Map.of(
                "message", "Post liked successfully.",
                "postId", postId,
                "userId", req.getUserId()
        ));
    }


    @GetMapping("/{postId}/virality")
    public ResponseEntity<Map<String, Object>> getViralityScore(@PathVariable Long postId) {
        Long score = viralityService.getScore(postId);
        return ResponseEntity.ok(Map.of(
                "postId", postId,
                "viralityScore", score
        ));
    }


    @GetMapping("/{postId}/bot-count")
    public ResponseEntity<Map<String, Object>> getBotCount(@PathVariable Long postId) {
        Long count = guardrailService.getBotCount(postId);
        return ResponseEntity.ok(Map.of(
                "postId", postId,
                "botReplyCount", count,
                "horizontalCap", GuardrailService.HORIZONTAL_CAP
        ));
    }
}
