# FotoEvidenciaEquans

Registro fotográfico de mantenimiento y averías del sistema contraincendio de la Línea 2 del Metro de Lima. Incluye una aplicación Android para el personal y un panel web de supervisión.

## Uso

1. Iniciar sesión y crear un registro de mantenimiento o avería.
2. Confirmar ubicación, turno y fecha de jornada. El turno diurno abarca de 08:00 a 20:00; el nocturno, de 20:00 a 08:00 del día siguiente.
3. Seleccionar actividad y etiquetas, registrar la descripción y obtener GPS cuando esté disponible.
4. Tomar varias fotos y revisarlas en las miniaturas o en pantalla completa.
5. Confirmar el envío. Sin conexión, las fotos permanecen en el teléfono y el envío se reintenta cuando vuelve la red.
6. Desde el panel, revisar las carpetas, ampliar fotografías, comparar originales, cambiar su estado y descargar el conjunto en ZIP.

Las fotos de distintos trabajadores se reúnen automáticamente cuando coinciden **ubicación, turno y fecha de jornada**. La referencia opcional, las etiquetas y el autor no separan carpetas. Una jornada nocturna conserva su fecha de inicio después de medianoche.

La copia etiquetada incluye fecha y hora de captura fijas y líneas con `#` para ubicación, turno, trabajo, actividad y equipos. El original se conserva por separado.

## Abrir y ejecutar

- Abrir esta carpeta en Android Studio. Se requieren JDK 17, Android SDK 35 y un dispositivo Android 8 o posterior. Ejecutar el módulo `app`.
- Para el servidor se requieren .NET SDK 10 y SQL Server. Configurar `ConnectionStrings:Equans` en `server/appsettings.json` con una cuenta que pueda crear la base inicialmente.
- Ejecutar `server/INICIAR.cmd` y abrir `http://127.0.0.1:5080` en la computadora del supervisor. En el primer inicio se genera el archivo local `server/BootstrapCredentials.txt` con el acceso inicial, si no existen usuarios.
- En el emulador Android, configurar `http://10.0.2.2:5080` como dirección del servidor. En un celular físico, `localhost` corresponde al propio teléfono: se necesita una dirección del servidor accesible desde su red.

La configuración incluida corresponde al entorno local. Para acceder desde internet hace falta desplegar el servidor, configurar HTTPS y conectar la app a su dirección pública.

## Almacenamiento

Las fotos no se guardan dentro del código fuente. En Android se almacenan en el directorio privado de la aplicación, `files/evidence/`, con originales, copias etiquetadas y metadatos. Los registros se guardan en `files/registros.json`. Borrar los datos de la app o desinstalarla elimina los archivos locales.

Al sincronizar, los archivos se guardan en la base **FotoEvidenciaEquans**, tabla **dbo.Evidence**, columnas **Image** y **OriginalImage**. El servidor confirma cada foto antes de marcarla como enviada. El panel consulta esa misma base; sus carpetas son agrupaciones de registros y se materializan como directorios al descargar un ZIP. Las cuentas y sesiones se guardan en `dbo.AppUsers` y `dbo.Sessions`.

Los archivos de SQL Server y sus copias de seguridad pertenecen a la instancia de SQL Server, fuera del proyecto. Copiar este código a otra computadora no copia las evidencias: se debe respaldar y restaurar la base por separado.

## Estructura

| Carpeta | Contenido |
| --- | --- |
| `app/` | Aplicación Android, almacenamiento local, cámara y sincronización |
| `server/` | API, autenticación, acceso a SQL Server y esquema |
| `supervisor/` | Interfaz web del supervisor |
| `docs/` | Arquitectura y verificaciones |
| `gradle/` | Herramientas de compilación Android |

No se incluyen fotografías, bases de datos, contraseñas, historiales de entregas ni archivos de compilación en el paquete de código.
