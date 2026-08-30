package com.tanmaysinghx.portalsso.user.web;

import com.tanmaysinghx.portalsso.user.service.RoleService;
import com.tanmaysinghx.portalsso.user.web.dto.CreateRoleRequest;
import com.tanmaysinghx.portalsso.user.web.dto.RoleResponse;
import com.tanmaysinghx.portalsso.user.web.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role registry. Until this existed the only thing that created a role was the dev-only test
 * seeder, so a production deployment had an empty {@code roles} table and assigning any role failed
 * — see migration {@code 011}, which now seeds the two the platform names.
 *
 * <p>There is no endpoint to rename a role: the name is the granted authority and travels in every
 * issued JWT. See {@link UpdateRoleRequest}.
 */
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {

    private final RoleService roleService;

    public AdminRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@Valid @RequestBody CreateRoleRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/{id}")
    public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        roleService.delete(id);
    }
}
