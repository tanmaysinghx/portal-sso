package com.tanmaysinghx.portalsso.client.service;

import com.tanmaysinghx.portalsso.client.entity.OAuthClient;
import com.tanmaysinghx.portalsso.client.repository.OAuthClientRepository;
import com.tanmaysinghx.portalsso.client.web.dto.OAuthClientResponse;
import com.tanmaysinghx.portalsso.common.api.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the OAuth client registry.
 *
 * <p>Simpler than the users equivalent: a client's redirect URIs and scopes are comma-delimited
 * columns rather than an association, so there is nothing to fetch-join and the page can be
 * selected in a single query.
 */
@Service
public class OAuthClientQueryService {

    static final int MAX_PAGE_SIZE = 200;

    private final OAuthClientRepository repository;

    public OAuthClientQueryService(OAuthClientRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OAuthClientResponse> find(String search, Boolean enabled, int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "clientId"));

        return PageResponse.from(
                repository.findAll(filter(search, enabled), pageRequest), OAuthClientResponse::from);
    }

    private static Specification<OAuthClient> filter(String search, Boolean enabled) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String needle = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("clientId")), needle, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("clientName"), "")), needle, '\\')));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
