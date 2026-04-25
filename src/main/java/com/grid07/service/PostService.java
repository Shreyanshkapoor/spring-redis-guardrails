package com.grid07.service;

import com.grid07.dto.CreateCommentRequest;
import com.grid07.dto.CreatePostRequest;
import com.grid07.dto.LikeRequest;
import com.grid07.entity.*;
import com.grid07.exception.GuardrailException;
import com.grid07.exception.ResourceNotFoundException;
import com.grid07.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository     postRepo;
    private final CommentRepository  commentRepo;
    private final UserRepository     userRepo;
    private final BotRepository      botRepo;
    private final PostLikeRepository likeRepo;

    private final GuardrailService   guardrailService;
    private final ViralityService    viralityService;
    private final NotificationService notifService;


    @Transactional
    public Post createPost(CreatePostRequest req) {
        validateAuthor(req.getAuthorId(), req.getAuthorType());

        Post post = Post.builder()
                .authorId(req.getAuthorId())
                .authorType(req.getAuthorType())
                .content(req.getContent())
                .build();

        Post saved = postRepo.save(post);
        log.info("[PostService] Created post:{}", saved.getId());
        return saved;
    }



    @Transactional
    public Comment addComment(Long postId, CreateCommentRequest req) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        validateAuthor(req.getAuthorId(), req.getAuthorType());

        boolean isBotComment = req.getAuthorType() == AuthorType.BOT;

        if (isBotComment) {
            guardrailService.checkVerticalCap(req.getDepthLevel());

            if (req.getTargetUserId() == null) {
                throw new IllegalArgumentException("targetUserId is required for bot comments.");
            }
            guardrailService.checkCooldown(req.getAuthorId(), req.getTargetUserId());

            guardrailService.checkAndIncrementBotCount(postId);
        }

        Comment comment;
        try {
            comment = Comment.builder()
                    .postId(postId)
                    .authorId(req.getAuthorId())
                    .authorType(req.getAuthorType())
                    .content(req.getContent())
                    .depthLevel(req.getDepthLevel())
                    .build();
            comment = commentRepo.save(comment);
        } catch (Exception dbEx) {
            if (isBotComment) {
                guardrailService.rollbackBotCount(postId);
            }
            throw dbEx;
        }

        if (isBotComment) {
            viralityService.recordBotReply(postId);
        } else {
            viralityService.recordHumanComment(postId);
        }

        if (isBotComment) {
            String botName = botRepo.findById(req.getAuthorId())
                    .map(Bot::getName)
                    .orElse("Bot#" + req.getAuthorId());
            notifService.handleBotInteractionNotification(req.getTargetUserId(), botName, postId);
        }

        log.info("[PostService] Comment:{} added to post:{} by {}:{}",
                comment.getId(), postId, req.getAuthorType(), req.getAuthorId());
        return comment;
    }


    @Transactional
    public void likePost(Long postId, LikeRequest req) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        userRepo.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + req.getUserId()));

        if (likeRepo.existsByPostIdAndUserId(postId, req.getUserId())) {
            throw new IllegalArgumentException("User " + req.getUserId() + " already liked post " + postId);
        }

        PostLike like = PostLike.builder()
                .postId(postId)
                .userId(req.getUserId())
                .build();
        likeRepo.save(like);

        viralityService.recordHumanLike(postId);

        log.info("[PostService] User:{} liked post:{}", req.getUserId(), postId);
    }



    private void validateAuthor(Long authorId, AuthorType type) {
        if (type == AuthorType.USER) {
            userRepo.findById(authorId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authorId));
        } else {
            botRepo.findById(authorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bot not found: " + authorId));
        }
    }
}
