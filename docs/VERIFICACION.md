# Verificación

Entorno comprobado el 10 de noviembre de 2026: Android SDK 35, JDK 17, .NET 10 y SQL Server local.

- Aplicación Android compilada con `assembleDebug`.
- Nueve pruebas unitarias aprobadas, incluidas las transiciones de turno a las 08:00 y 20:00, la jornada nocturna después de medianoche y la fecha real de captura en la marca.
- Análisis Android Lint sin errores. Persisten avisos de actualizaciones disponibles de dependencias; no se modificaron esas versiones en esta entrega.
- Servidor compilado sin errores ni advertencias.
- Prueba de integración ejecutada en una base SQL independiente: autenticación, permisos, recepción repetida sin duplicados, rechazo de conflictos, almacenamiento de originales, revisión y persistencia tras reinicio.
- Agrupación comprobada entre dos trabajadores y con diferentes valores históricos de ambiente: misma ubicación, turno y jornada producen la misma carpeta. Otra ubicación, otro turno u otra jornada producen carpetas distintas.
- Exportación ZIP comprobada: reúne las fotos de la carpeta, conserva los originales y excluye las de otros lugares.
- Panel del supervisor abierto y verificado con las nueve fotos existentes, sin modificar sus imágenes ni estados.
- APK instalado en el celular conectado mediante actualización, sin desinstalación. Actividad principal iniciada correctamente. La captura física, GPS y envío desde ese celular requieren una prueba de campo.

## Repetir las comprobaciones

Android:

```text
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Servidor, desde la raíz del proyecto:

```text
dotnet build server/FotoEvidenciaEquans.Api.csproj
node server/integration-test.cjs
```

La integración requiere SQL Server y permisos para crear una base cuyo nombre comienza por `FotoEvidenciaEquansTests_`. No utiliza la base de evidencias del usuario. Las pruebas instrumentadas de Android, si se ejecutan, deben utilizar `connectedVerificationAndroidTest`: ese tipo de compilación usa un paquete separado para proteger los datos de la aplicación instalada.

## Prueba por USB

Con depuración USB autorizada y el servidor ejecutándose, `adb reverse tcp:5080 tcp:5080` permite que el celular acceda al servidor de la computadora mediante `http://127.0.0.1:5080`. Esta conexión es temporal; para trabajar sin USB se necesita un servidor accesible desde la red del teléfono.
