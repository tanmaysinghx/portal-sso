package com.tanmaysinghx.portalsso.user.repository;

import com.tanmaysinghx.portalsso.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Eagerly fetches roles: with {@code open-in-view: false}, callers outside a transaction
     * (e.g. the JWT claims customizer, which runs mid-token-generation with no session) would
     * otherwise hit a {@code LazyInitializationException} on {@link User#getRoles()}.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** {@code left join} so users with no roles yet are still listed; {@code distinct} avoids
     * row duplication from the roles join. */
    @Query("select distinct u from User u left join fetch u.roles order by u.email")
    List<User> findAllWithRoles();

    /** Same eager fetch as {@link #findByEmail}, for the paths that look a user up by id. */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    /**
     * Backs the "you cannot remove the last administrator" check. Counts only <em>enabled</em>
     * accounts, because a disabled administrator cannot sign in and so cannot recover the server —
     * counting them would let the last usable admin be demoted while the check still passed.
     */
    @Query("""
            select count(u) from User u join u.roles r
            where r.name = :roleName and u.enabled = true and u.id <> :excludedUserId
            """)
    long countOtherEnabledUsersWithRole(
            @Param("roleName") String roleName,
            @Param("excludedUserId") UUID excludedUserId);

    /**
     * Whether anyone can currently administer the server. Enabled specifically: a disabled
     * administrator cannot sign in, so counting one would hide exactly the locked-out state the
     * startup bootstrap exists to rescue an operator from.
     */
    @Query("select count(u) from User u join u.roles r where r.name = :roleName and u.enabled = true")
    long countEnabledUsersWithRole(@Param("roleName") String roleName);

    /** Every user holding a role, so the association can be cleared before the role is deleted. */
    @Query("select u from User u join u.roles r where r.id = :roleId")
    List<User> findAllByRoleId(@Param("roleId") UUID roleId);
}
