package mx.gob.seph.smp.demo.modules;

import java.util.Map;

/** Elemento de `GET /users/moduleByUser`. */
public record UserModule(
        int moduleId,
        String moduleName,
        String description,
        String config,
        String path,
        Integer parentId,
        String parentName,
        String icon,
        String materialIcon,
        boolean vista,
        String codigo,
        Map<String, Boolean> permisos,
        Integer orden) {
}
