package mx.gob.seph.smp.demo.api;

/** Una regla de negocio impide la operación: responde 409 con el mensaje (como ReglaNegocioException del back real). */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String message) {
        super(message);
    }
}
