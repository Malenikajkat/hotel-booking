package org.example.hotelmanagement.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter that authenticates internal service calls using a shared secret token.
 * Used by Booking Service to call confirm-availability and release endpoints.
 * This filter runs BEFORE the JWT filter so that internal-token requests
 * are authenticated as ADMIN without requiring a valid JWT.
 */
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthFilter.class);
    private static final String INTERNAL_TOKEN = "Bearer internal-token";
    private static final String AUTH_HEADER = "Authorization";
    private static final String INTERNAL_PATH_PREFIX = "/api/rooms/";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String authHeader = request.getHeader(AUTH_HEADER);

        // Only apply to internal room endpoints (confirm-availability and release)
        if (path.startsWith(INTERNAL_PATH_PREFIX) && INTERNAL_TOKEN.equals(authHeader)) {
            // Authenticate as internal service with ADMIN role
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "internal-service",
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Internal service authenticated for path: {}", path);
        } else if (path.startsWith(INTERNAL_PATH_PREFIX) && authHeader != null) {
            // Internal endpoint called with wrong token — reject early with 401 JSON
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid internal token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
