package mx.gob.seph.smp.demo.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /** Atributo de la petición con el motivo del 401, para el mensaje del entry point. */
    public static final String AUTH_ERROR_ATTRIBUTE = "smp.authError";

    private final JwtSessionService jwtSessionService;

    public JwtAuthenticationFilter(JwtSessionService jwtSessionService) {
        this.jwtSessionService = jwtSessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            JwtSessionService.Validation validation = jwtSessionService.validate(token);
            if (validation instanceof JwtSessionService.Valid valid) {
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(valid.session(), null, List.of()));
            } else if (validation instanceof JwtSessionService.Revoked) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Sesión cerrada");
            }
        }
        filterChain.doFilter(request, response);
    }
}
