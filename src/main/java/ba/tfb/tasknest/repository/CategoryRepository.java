package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByActiveTrue();

    Optional<Category> findByName(String name);
}
