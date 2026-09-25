package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.common.PagedResponse;
import ba.tfb.tasknest.dto.notification.NotificationResponse;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Notifikacije prijavljenog korisnika. Bez provjere role - notifikacije dobijaju
 * i klijenti i taskeri, samo iz razlicitih povoda.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Najnovije prvo, fiksno - redoslijed dolazi iz imena metode u repozitoriju.
     * <p>
     * Sort iz zahtjeva se odbacuje: ranije je ?sort=bilosta prolazio do Spring
     * Date i vracao 500. Isti problem su liste oglasa rijesile bijelom listom
     * polja; ovdje nema sta birati, pa se sort jednostavno ne prenosi.
     */
    @GetMapping
    public PagedResponse<NotificationResponse> myNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return PagedResponse.from(notificationService.getMyNotifications(principal.getId(),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())));
    }

    /** Broj nepročitanih, za znacku u navigaciji. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("count", notificationService.countUnread(principal.getId()));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable UUID id,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.markAsRead(id, principal.getId());
    }
}
