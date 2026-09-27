package ba.tfb.tasknest.realtime;

import ba.tfb.tasknest.security.CustomUserDetailsService;
import ba.tfb.tasknest.security.JwtService;
import ba.tfb.tasknest.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String OWN_QUEUES = "/user/queue/";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserDetailsChecker accountStatusChecker;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader(HEADER))
                    .orElseThrow(() -> new MessageDeliveryException("A valid access token is required")));
            case SUBSCRIBE -> {
                String destination = accessor.getDestination();
                if (accessor.getUser() == null || destination == null || !destination.startsWith(OWN_QUEUES)) {
                    throw new MessageDeliveryException("You can only subscribe to your own queues");
                }
            }
            case SEND -> throw new MessageDeliveryException("This connection only receives updates");
            default -> {
            }
        }

        return message;
    }

    private Optional<RealtimeUser> authenticate(String header) {
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }

        return jwtService.parseAccessToken(header.substring(PREFIX.length()).trim())
                .flatMap(jwtService::extractUserId)
                .flatMap(userDetailsService::loadUserById)
                .filter(this::isActive)
                .map(principal -> new RealtimeUser(principal.getId()));
    }

    private boolean isActive(UserPrincipal principal) {
        try {
            accountStatusChecker.check(principal);
            return true;
        } catch (AccountStatusException e) {
            log.debug("Realtime connection rejected for user {}: {}", principal.getId(), e.getMessage());
            return false;
        }
    }
}
