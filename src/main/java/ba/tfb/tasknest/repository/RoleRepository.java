package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Role;
import ba.tfb.tasknest.entity.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);
}
