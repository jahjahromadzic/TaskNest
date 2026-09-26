package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.common.PagedResponse;
import ba.tfb.tasknest.dto.conversation.ConversationResponse;
import ba.tfb.tasknest.dto.conversation.MessageResponse;
import ba.tfb.tasknest.dto.conversation.SendMessageRequest;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public PagedResponse<ConversationResponse> myConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return PagedResponse.from(
                conversationService.getMyConversations(principal.getId(), unsorted(pageable)));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("count", conversationService.countUnread(principal.getId()));
    }

    @GetMapping("/{id}/messages")
    public PagedResponse<MessageResponse> messages(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 50) Pageable pageable) {
        return PagedResponse.from(
                conversationService.getMessages(id, principal.getId(), unsorted(pageable)));
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable UUID id,
                                @Valid @RequestBody SendMessageRequest request,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return conversationService.sendMessage(id, principal.getId(), request);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable UUID id,
                           @AuthenticationPrincipal UserPrincipal principal) {
        conversationService.markAsRead(id, principal.getId());
    }

    private static Pageable unsorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
