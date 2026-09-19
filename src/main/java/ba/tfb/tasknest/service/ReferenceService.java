package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.reference.CategoryResponse;
import ba.tfb.tasknest.dto.reference.MunicipalityResponse;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferenceService {

    private final CategoryRepository categoryRepository;
    private final MunicipalityRepository municipalityRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByActiveTrue().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MunicipalityResponse> getAllMunicipalities() {
        return municipalityRepository.findAllByOrderByNameAsc().stream()
                .map(MunicipalityResponse::from)
                .toList();
    }
}