/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record UpdateEntityRequest(
    @NotBlank(message = "id is required") String id,
    @NotEmpty(message = "patch must not be empty") Map<String, Object> patch
) {}
