package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.client.ClientHireResponse;
import ba.tfb.tasknest.dto.client.ClientProfileResponse;
import ba.tfb.tasknest.dto.common.PagedResponse;
import ba.tfb.tasknest.service.ClientProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}")
@RequiredArgsConstructor
public class ClientProfileController {

    private final ClientProfileService clientProfileService;

    @GetMapping("/client-profile")
    public ClientProfileResponse profile(@PathVariable UUID userId) {
        return clientProfileService.getProfile(userId);
    }

    @GetMapping("/hires")
    public PagedResponse<ClientHireResponse> hires(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PagedResponse.from(clientProfileService.getHires(userId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())));
    }
}
