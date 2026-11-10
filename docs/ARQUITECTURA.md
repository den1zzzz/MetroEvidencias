# Arquitectura

## Aplicación Android

Kotlin y Jetpack Compose con un ViewModel que expone el estado del formulario y el historial. El repositorio escribe registros mediante `AtomicFile` y propaga los errores de lectura para evitar sobrescribir datos dañados.

CameraX captura el original en el directorio privado de evidencias. `EvidenceStorage` produce una copia con la orientación corregida y una marca permanente calculada a partir del instante de captura. El registro de captura permite recuperar operaciones interrumpidas. La vista previa puede alternar entre original y copia etiquetada.

El usuario confirma el lote antes de enviarlo. WorkManager reintenta las evidencias pendientes cuando dispone de conexión. Cada fotografía tiene un identificador estable y una confirmación independiente del servidor. Los errores no eliminan los archivos locales.

## Servidor y panel

ASP.NET Core publica el panel web y las rutas de autenticación, recepción de evidencias, consulta, revisión y exportación. SQL Server conserva imágenes, metadatos, usuarios y sesiones. Las contraseñas usan PBKDF2 con sal y las sesiones se almacenan mediante el hash del token.

La recepción compara identificador, contenido y metadatos dentro de una transacción. Repetir una solicitud idéntica no crea otra foto; reutilizar el identificador con contenido diferente produce un conflicto. Se mantiene la lectura de los metadatos históricos para que las fotos pendientes de instalaciones anteriores puedan sincronizarse.

## Agrupación

La clave de carpeta combina el identificador de ubicación normalizado, turno y fecha de jornada. Se normalizan mayúsculas, acentos y espacios. El autor, las etiquetas, la referencia y los antiguos ambientes no intervienen. Para ubicaciones personalizadas se incorpora el lugar indicado por el usuario.

En horario de Lima, la jornada nocturna empieza a las 20:00 y termina a las 08:00 del día siguiente. La fecha propuesta antes de las 08:00 corresponde al día anterior. Puede corregirse desde el formulario. La fecha de jornada y el instante real de captura se conservan como datos diferentes.

Los campos históricos de ambiente permanecen en SQL Server para conservar los registros existentes y la compatibilidad de reintentos; no aparecen en el formulario ni separan carpetas. Las marcas de fotos ya capturadas no se modifican.

## Separación de código y datos

El código contiene las pantallas, reglas y esquema SQL. Los archivos del teléfono se guardan en el espacio privado de Android. Las imágenes sincronizadas se almacenan como datos binarios en SQL Server. Las exportaciones ZIP se preparan temporalmente fuera del código y se eliminan después de enviarlas.

El paquete de entrega excluye credenciales, fotos reales, cachés y resultados de compilación. El respaldo de la base de datos es una operación independiente de la entrega del código.
