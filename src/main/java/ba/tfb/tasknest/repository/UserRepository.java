package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.repository.projection.AdminUserRow;
import ba.tfb.tasknest.repository.projection.UserRoleRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Korisnici za admin listu, najnoviji prvo. Status je opcion; email se trazi
     * kao podniz, a prazan string znaci "bez filtera" - servis nikad ne salje
     * null, jer Postgres ne moze odrediti tip null parametra unutar concat().
     */
    @Query(value = """
            select new ba.tfb.tasknest.repository.projection.AdminUserRow(
                    u.id, u.email, u.firstName, u.lastName, u.accountStatus, u.createdAt)
            from User u
            where (:status is null or u.accountStatus = :status)
              and u.email like concat('%', :email, '%')
            order by u.createdAt desc
            """,
            countQuery = """
            select count(u) from User u
            where (:status is null or u.accountStatus = :status)
              and u.email like concat('%', :email, '%')
            """)
    Page<AdminUserRow> findForAdmin(@Param("status") AccountStatus status,
                                    @Param("email") String email,
                                    Pageable pageable);

    @Query("""
            select new ba.tfb.tasknest.repository.projection.UserRoleRow(u.id, r.name)
            from User u join u.roles r
            where u.id in :userIds
            """)
    List<UserRoleRow> findRolesFor(@Param("userIds") Collection<UUID> userIds);
}
