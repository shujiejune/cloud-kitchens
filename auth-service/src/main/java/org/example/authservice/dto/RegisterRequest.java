package org.example.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/auth/register.
 *
 * Java 17 record — all fields are final and set via the canonical constructor.
 * Bean Validation runs before the controller method body executes
 * (Spring calls @Valid on the @RequestBody parameter).
 */
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        /**
         * Raw password — BCrypt hashed in AuthService before persistence.
         * Never logged, never returned in any response.
         */
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        // 72 chars = BCrypt's effective input limit
        String password,

        @NotBlank(message = "Company name is required")
        @Size(max = 255, message = "Company name must be 255 characters or fewer")
        String companyName
) {}
