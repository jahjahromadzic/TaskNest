package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MunicipalityRepository extends JpaRepository<Municipality, UUID> {

    List<Municipality> findAllByOrderByNameAsc();
}
