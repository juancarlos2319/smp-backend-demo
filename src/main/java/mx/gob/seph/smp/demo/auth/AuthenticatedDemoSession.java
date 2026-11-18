package mx.gob.seph.smp.demo.auth;

/** Usuario autenticado de la petición: el principal de Spring Security. */
public record AuthenticatedDemoSession(long userId, String username, String token) {
}
