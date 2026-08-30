package com.tanmaysinghx.portalsso.user.service;

import com.tanmaysinghx.portalsso.common.api.PageResponse;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.web.dto.UserResponse;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the users list: paging, search and filtering for the admin console. */
@Service
public class UserQueryService {

    static final int MAX_PAGE_SIZE = 200;
    static final int DEFAULT_PAGE_SIZE = 25;

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @param search matches email, first name or last name, case-insensitively
     * @param enabled null for "either"
     * @param role a role name, or null for any
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> find(String search, Boolean enabled, String role, int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "email"));

        // Step 1: select the page with no fetch join, so the database applies a real LIMIT.
        Page<User> pageOfUsers = userRepository.findAll(filter(search, enabled, role), pageRequest);
        List<UUID> ids = pageOfUsers.getContent().stream().map(User::getId).toList();
        if (ids.isEmpty()) {
            return PageResponse.of(pageOfUsers, List.of());
        }

        // Step 2: load roles for just those ids. Doing it in one fetch-joined, paged query would
        // make Hibernate page in memory over the whole result set — see findAllWithRolesByIds.
        Map<UUID, User> byId = new LinkedHashMap<>();
        userRepository.findAllWithRolesByIds(ids).forEach(u -> byId.put(u.getId(), u));

        // Re-ordered to the page's own ordering: the `in :ids` query gives no ordering guarantee,
        // so relying on its order would shuffle rows between renders.
        List<UserResponse> content = ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(UserResponse::from)
                .toList();

        return PageResponse.of(pageOfUsers, content);
    }

    private static Specification<User> filter(String search, Boolean enabled, String role) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(search)) {
                String needle = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), needle, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("firstName"), "")), needle, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("lastName"), "")), needle, '\\')));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (hasText(role)) {
                // A join here would multiply rows for multi-role users and corrupt the count, so the
                // membership test is a subquery instead.
                var sub = query.subquery(Long.class);
                var subRoot = sub.from(User.class);
                var joined = subRoot.join("roles");
                sub.select(cb.literal(1L))
                        .where(cb.and(
                                cb.equal(subRoot.get("id"), root.get("id")),
                                cb.equal(joined.get("name"), role.trim())));
                predicates.add(cb.exists(sub));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }

    /** Keeps a stray % or _ in a search term from behaving as a wildcard. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
