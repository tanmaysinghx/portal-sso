package com.tanmaysinghx.portalsso.user.repository;

import com.tanmaysinghx.portalsso.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

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

    /**
     * Loads roles for one already-selected page of users.
     *
     * <p>This exists because of a specific trap. Combining a fetch join with {@code Pageable} makes
     * Hibernate log {@code HHH90003004} and paginate <em>in memory</em>: it fetches every matching
     * row and slices afterwards, which is exactly the behaviour paging was added to remove. So the
     * page is selected first without a fetch join (a real SQL {@code LIMIT}), and the roles for that
     * page's ids come from this second query — two queries with a fixed row count, rather than one
     * that reads the table.
     */
    @Query("select distinct u from User u left join fetch u.roles where u.id in :ids")
    List<User> findAllWithRolesByIds(@Param("ids") List<UUID> ids);

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

    /** Everyone with a stored TOTP secret — the population the startup key check has to account for. */
    @Query("select u from User u where u.mfaSecret is not null")
    List<User> findAllWithMfaSecret();

    /** Every user holding a role, so the association can be cleared before the role is deleted. */
    @Query("select u from User u join u.roles r where r.id = :roleId")
    List<User> findAllByRoleId(@Param("roleId") UUID roleId);
}
