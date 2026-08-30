package com.tanmaysinghx.portalsso.user.repository;

import com.tanmaysinghx.portalsso.user.entity.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    List<Role> findAllByOrderByNameAsc();

    /**
     * How many users hold each role, as {@code [roleId, count]} pairs.
     *
     * <p>One query rather than a count per role: the roles screen shows the number beside every row,
     * and the obvious loop turns a page render into N+1 round trips. Roles nobody holds are absent
     * from the result and default to zero at the call site.
     */
    @Query("select r.id, count(u) from User u join u.roles r group by r.id")
    List<Object[]> countUsersPerRole();
}
