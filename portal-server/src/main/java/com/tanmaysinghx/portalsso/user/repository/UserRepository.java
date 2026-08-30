package com.tanmaysinghx.portalsso.user.repository;

import com.tanmaysinghx.portalsso.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
