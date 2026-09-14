/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.crud.controller;

import com.managemyopz.kernel.data.schema.web.BulkEntityRequest;
import com.managemyopz.kernel.data.schema.web.BulkResult;
import com.managemyopz.kernel.data.schema.web.CreateEntityRequest;
import com.managemyopz.kernel.data.schema.web.DeleteEntityRequest;
import com.managemyopz.kernel.data.schema.web.FilterEntityRequest;
import com.managemyopz.kernel.data.schema.web.PageEnvelope;
import com.managemyopz.kernel.data.schema.web.ReadEntityRequest;
import com.managemyopz.kernel.data.schema.web.UpdateEntityRequest;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import com.managemyopz.modules.crud.GenericCrudService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * One generic REST surface for every entity declared under a module's
 * {@code db/schema/*.yaml} — no per-entity controller, DTO, or SQL file exists
 * anywhere; {@code entity} in the path resolves against {@code SchemaRegistry}.
 */
@RestController
@RequestMapping("/api/v1/opzhub/db")
public class GenericCrudController {

    private final GenericCrudService crudService;

    public GenericCrudController(GenericCrudService crudService) {
        this.crudService = crudService;
    }

    @PostMapping("/{entity}/create")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> create(
            @PathVariable String entity,
            @Valid @RequestBody CreateEntityRequest request,
            HttpServletRequest httpRequest) {
        Map<String, Object> created = crudService.create(entity, request.row());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiEnvelope.ok(created, correlationId(httpRequest)));
    }

    @PostMapping("/{entity}/read")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> read(
            @PathVariable String entity,
            @Valid @RequestBody ReadEntityRequest request,
            HttpServletRequest httpRequest) {
        Map<String, Object> found = crudService.read(entity, request.id());
        return ResponseEntity.ok(ApiEnvelope.ok(found, correlationId(httpRequest)));
    }

    @PostMapping("/{entity}/filter")
    public ResponseEntity<ApiEnvelope<PageEnvelope>> filter(
            @PathVariable String entity,
            @RequestBody FilterEntityRequest request,
            HttpServletRequest httpRequest) {
        PageEnvelope page = crudService.filter(entity, request.filter());
        return ResponseEntity.ok(ApiEnvelope.ok(page, correlationId(httpRequest)));
    }

    @PostMapping("/{entity}/count")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> count(
            @PathVariable String entity,
            @RequestBody FilterEntityRequest request,
            HttpServletRequest httpRequest) {
        long total = crudService.count(entity, request.filter());
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("total", total), correlationId(httpRequest)));
    }

    @PutMapping("/{entity}/update")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> update(
            @PathVariable String entity,
            @Valid @RequestBody UpdateEntityRequest request,
            HttpServletRequest httpRequest) {
        Map<String, Object> updated = crudService.update(entity, request.id(), request.patch());
        return ResponseEntity.ok(ApiEnvelope.ok(updated, correlationId(httpRequest)));
    }

    @DeleteMapping("/{entity}/delete")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> delete(
            @PathVariable String entity,
            @Valid @RequestBody DeleteEntityRequest request,
            HttpServletRequest httpRequest) {
        crudService.delete(entity, request.id());
        return ResponseEntity.ok(ApiEnvelope.ok(
            Map.of("deleted", true, "id", request.id()),
            correlationId(httpRequest)
        ));
    }

    @PostMapping("/{entity}/bulk")
    public ResponseEntity<ApiEnvelope<BulkResult>> bulk(
            @PathVariable String entity,
            @RequestBody BulkEntityRequest request,
            HttpServletRequest httpRequest) {
        BulkResult result = crudService.bulk(entity, request);
        return ResponseEntity.ok(ApiEnvelope.ok(result, correlationId(httpRequest)));
    }

    private String correlationId(HttpServletRequest request) {
        Object correlationId = request.getAttribute(CorrelationFilter.MDC_KEY);
        return correlationId == null ? "" : correlationId.toString();
    }
}
