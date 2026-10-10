package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.geo.LocationResponse;
import ba.tfb.tasknest.service.LocationService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/lookup")
    public LocationResponse lookup(@RequestParam(required = false) @Size(max = 200) String address,
                                   @RequestParam UUID municipalityId) {
        return locationService.lookup(address, municipalityId);
    }
}
