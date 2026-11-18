package mx.gob.seph.smp.demo.api;

public record ApiResponse<T>(boolean success, String message, T data, Integer count) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, null);
    }

    public static <T> ApiResponse<T> success(T data, int count) {
        return new ApiResponse<>(true, null, data, count);
    }

    /** Éxito con un mensaje que el front muestra en el aviso. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, message, null, null);
    }
}
