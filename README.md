# Jarvis para Android

Asistente de voz en español con interfaz Compose, comandos para el dispositivo, rutinas y un clasificador neuronal local. Es un proyecto en desarrollo: sus funciones dependen del dispositivo y de los permisos concedidos.

## Privacidad y límites

- La interpretación de comandos, los alias y las rutinas se procesan en el dispositivo. Esto no garantiza que toda la aplicación funcione sin Internet: el clima consulta Open-Meteo y los servicios de reconocimiento de voz o síntesis del dispositivo pueden utilizar la red. Las búsquedas y aplicaciones externas también pueden transmitir datos.
- El micrófono se usa para el dictado y, si se activa, para la escucha de la frase de activación mediante un servicio con notificación. El proveedor de reconocimiento puede procesar audio fuera del dispositivo. La escucha puede consumir batería, no es continua garantizada y Android puede impedir abrir la aplicación desde segundo plano. Al detectar la frase, la escucha en segundo plano se desactiva; hay que activarla de nuevo manualmente.
- Contactos se usa para buscar destinatarios. Los mensajes se preparan en una aplicación externa y deben ser revisados y enviados por el usuario; las llamadas abren el marcador para que el usuario las confirme. Las automatizaciones pueden abrir aplicaciones y cambiar ajustes: configura solo acciones en las que confíes.
- El servicio de accesibilidad es opcional y puede acceder al contenido de la pantalla cuando está habilitado. No es necesario para preparar mensajes. Las reglas de copia de seguridad de Android pueden incluir datos de la aplicación según la configuración del dispositivo.
- Para consultar el clima hay que indicar una ciudad, por ejemplo «clima en Caracas». Si no se encuentra o falla la conexión, se informa del error sin inventar datos. La búsqueda usa el primer resultado, por lo que ciudades con nombres repetidos pueden ser ambiguas.

## Compilar y probar

Requisitos: JDK 21 (necesario para las pruebas Robolectric con Android 16), Gradle 9.3.1 y Android SDK con la plataforma 36.1 y build-tools 36.0.0. Configura `ANDROID_HOME` o `local.properties` para localizar el SDK. Este repositorio no incluye los ejecutables ni el JAR de Gradle Wrapper.

La configuración de depuración espera `debug.keystore` en la raíz; para generar una clave **solo para desarrollo**:

```sh
keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
gradle :app:testDebugUnitTest :app:assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/`. Para publicación se requiere una clave de lanzamiento propia y la configuración correspondiente; no uses la clave de depuración para distribuir una versión de producción.

## APK con GitHub Actions

El flujo **Android APK** se ejecuta en pushes, solicitudes de cambios y manualmente. Ejecuta las pruebas y compila un APK firmado con una clave de depuración temporal, disponible como artefacto `jarvis-debug-apk` en la ejecución exitosa de Actions. Esa clave cambia en cada ejecución: puede ser necesario desinstalar el APK anterior antes de instalar otro. No es un APK de producción.
