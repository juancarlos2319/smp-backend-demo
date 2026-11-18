package mx.gob.seph.smp.demo.seguridad;

/** Banderas de un rol en un módulo (`core_role_modules.can_*`), con la llave que usa el front. */
public enum Permiso {
    AGREGAR("agregar", "can_agregar"),
    EDITAR("editar", "can_editar"),
    ELIMINAR("eliminar", "can_eliminar"),
    AUTORIZAR("autorizar", "can_autorizar"),
    VER_DETALLE_TECNICO("verDetalleTecnico", "can_ver_detalle_tecnico");

    private final String key;
    private final String column;

    Permiso(String key, String column) {
        this.key = key;
        this.column = column;
    }

    /** Llave en JSON (`permisos.agregar`, ...). */
    public String key() {
        return key;
    }

    /** Columna de `core_role_modules`; constante, nunca viene del usuario. */
    String column() {
        return column;
    }

    public String etiqueta() {
        return switch (this) {
            case AGREGAR -> "AGREGAR";
            case EDITAR -> "EDITAR";
            case ELIMINAR -> "ELIMINAR";
            case AUTORIZAR -> "AUTORIZAR";
            case VER_DETALLE_TECNICO -> "VER DETALLE TÉCNICO";
        };
    }
}
