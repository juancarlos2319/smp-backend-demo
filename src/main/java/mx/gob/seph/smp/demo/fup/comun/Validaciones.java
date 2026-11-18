package mx.gob.seph.smp.demo.fup.comun;

import java.util.regex.Pattern;

import mx.gob.seph.smp.demo.api.ReglaNegocioException;

/** Mismas reglas que `nuevo-ingreso.validators.ts` del front; aquí fallan con 409 y el motivo. */
public final class Validaciones {
    public static final Pattern RFC = Pattern.compile("^[A-ZÑ&]{4}\\d{6}[A-Z0-9]{3}$");
    public static final Pattern CURP = Pattern.compile("^[A-Z][AEIOUX][A-Z]{2}\\d{6}[HMX][A-Z]{5}[0-9A-Z]\\d$");
    public static final String CURP_GENERICA = "XXXX000000XXXXXX00";
    public static final Pattern QNA = Pattern.compile("^\\d{4}(0[1-9]|1\\d|2[0-4])$");
    public static final Pattern CP = Pattern.compile("^\\d{5}$");
    public static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** Inicio del RFC para buscar: letras, números y espacios. */
    public static final Pattern TEXTO_RFC = Pattern.compile("^[A-Za-zÑñ&0-9\\s]*$");
    /** Texto de la clave de plaza: letras, números, puntos y espacios. */
    public static final Pattern TEXTO_PLAZA = Pattern.compile("^[A-Za-z0-9.\\s]*$");

    private static final int[] PESOS_CLABE = {3, 7, 1};

    private Validaciones() {
    }

    public static void rfc(String rfc) {
        if (rfc == null || !RFC.matcher(rfc).matches()) {
            throw new ReglaNegocioException("El RFC no tiene un formato válido.");
        }
    }

    public static void curp(String curp) {
        if (curp == null || !CURP.matcher(curp).matches()) {
            throw new ReglaNegocioException("La CURP no tiene un formato válido.");
        }
        if (CURP_GENERICA.equals(curp)) {
            throw new ReglaNegocioException("No se acepta la CURP genérica.");
        }
    }

    public static void curpCoincideConRfc(String curp, String rfc) {
        if (!curp.substring(0, 10).equals(rfc.substring(0, 10))) {
            throw new ReglaNegocioException("Los primeros 10 caracteres de la CURP deben coincidir con los del RFC.");
        }
    }

    public static boolean clabeValida(String clabe) {
        if (clabe == null || !clabe.matches("\\d{18}")) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 17; i++) {
            suma += (Character.getNumericValue(clabe.charAt(i)) * PESOS_CLABE[i % 3]) % 10;
        }
        return (10 - suma % 10) % 10 == Character.getNumericValue(clabe.charAt(17));
    }

    public static void quincena(Integer qna, String campo) {
        if (qna == null || !QNA.matcher(String.valueOf(qna)).matches()) {
            throw new ReglaNegocioException("La " + campo + " debe ser AAAAQQ con QQ de 01 a 24.");
        }
    }

    /** Mínimo de caracteres sin contar espacios, como el front. */
    public static String filtroTexto(String value, int minimo, Pattern permitido, String campo) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!permitido.matcher(value).matches()) {
            throw new ReglaNegocioException("El " + campo + " tiene caracteres no válidos.");
        }
        String limpio = value.replaceAll("\\s", "").toUpperCase();
        if (limpio.length() < minimo) {
            throw new ReglaNegocioException("Escribe al menos " + minimo + " caracteres del " + campo + ".");
        }
        return limpio;
    }

    public static String upperTrim(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
