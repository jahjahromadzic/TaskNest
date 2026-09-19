package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.reference.CategoryResponse;
import ba.tfb.tasknest.dto.reference.MunicipalityResponse;
import ba.tfb.tasknest.service.ReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceService referenceService;

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return referenceService.getActiveCategories();
    }

    @GetMapping("/municipalities")
    public List<MunicipalityResponse> municipalities() {
        return referenceService.getAllMunicipalities();
    }
}