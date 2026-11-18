-- La CLABE de JUAN (1001) en las primeras copias de V3 tenía mal el dígito verificador.
-- En bases nuevas V3 ya trae la correcta y esto no cambia nada.
UPDATE fup_empleados
SET clabe = '002180700000000011'
WHERE employee_id = 1001 AND clabe = '002180700000000015';
