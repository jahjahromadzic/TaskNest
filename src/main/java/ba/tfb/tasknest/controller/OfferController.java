package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.offer.OfferResponse;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.OfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    @PostMapping("/tasks/{taskId}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse submit(@PathVariable UUID taskId,
                                @Valid @RequestBody CreateOfferRequest request,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.submitOffer(taskId, principal.getId(), request);
    }

    @GetMapping("/tasks/{taskId}/offers")
    public List<OfferResponse> forTask(@PathVariable UUID taskId,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.getOffersForTask(taskId, principal.getId());
    }

    @PostMapping("/offers/{offerId}/accept")
    public OfferResponse accept(@PathVariable UUID offerId,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.acceptOffer(offerId, principal.getId());
    }

    @PostMapping("/offers/{offerId}/withdraw")
    public OfferResponse withdraw(@PathVariable UUID offerId,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.withdrawOffer(offerId, principal.getId());
    }

    @GetMapping("/offers/mine")
    public List<OfferResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return offerService.getMyOffers(principal.getId());
    }
}