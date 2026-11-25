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

## Ollama y conversación en línea

1. Instala y ejecuta Ollama en un ordenador o servidor de confianza e instala un modelo, por ejemplo `ollama pull llama3.2`. La aplicación **no incluye un servidor ni un modelo Ollama**.
2. El servidor debe ser accesible desde el teléfono. En la misma red, configura Ollama para escuchar en la interfaz adecuada (por ejemplo, mediante `OLLAMA_HOST=0.0.0.0:11434`) y limita el acceso con el firewall. No expongas el puerto sin protección a Internet: Ollama no proporciona autenticación por defecto. Para acceso remoto, usa un túnel privado (por ejemplo VPN) o un proxy HTTPS protegido a nivel de red. Esta aplicación no incluye soporte para claves/tokens ni autenticación HTTP.
3. En Jarvis, pulsa **CONECTAR OLLAMA** e introduce la URL completa del servidor y el nombre exacto del modelo; acepta el envío de preguntas. Por ejemplo, `http://192.168.1.10:11434` solo es un ejemplo: utiliza la IP real de tu equipo. `localhost` en Android se refiere al teléfono, no a tu ordenador.
4. Las preguntas que el intérprete clasifica como conversación se envían al servidor; los comandos del dispositivo siguen ejecutándose mediante el intérprete local. Si falla la conexión, Jarvis informa del error y usa una respuesta local. La respuesta del modelo nunca se ejecuta como comando. Ollama no tiene navegación autónoma. Para consultar información en línea di «investiga en internet [tema]» o «busca en Wikipedia [tema]»: Jarvis consulta la API de Wikipedia en español y muestra la fuente; si Ollama está configurado, envía el extracto para resumirlo. Es una búsqueda limitada a Wikipedia, no a toda la web y no garantiza información de última hora. Otras búsquedas existentes abren el navegador.

La app permite HTTP para servidores en la red local, pero las conversaciones viajan **sin cifrar**. Usa solo redes de confianza y, de ser posible, HTTPS. No introduzcas contraseñas ni claves en la URL. La dirección y el modelo se guardan en preferencias de la app, que pueden incluirse en copias de seguridad del dispositivo.

## Cámara y secuencias

«Abre la cámara» abre la aplicación de cámara; «toma una foto» abre una cámara propia visible, solicita permiso y guarda una captura en la galería. En Android 8 y anteriores se solicita también permiso de almacenamiento. Si se deniega el permiso o la cámara está ocupada, no se toma la foto. No se permiten capturas mediante reglas o tareas programadas. La confirmación final aparece en la pantalla de cámara, no al iniciar la orden.

Se pueden encadenar comandos, por ejemplo: «enciende la linterna, pon el volumen al 70%, baja el brillo al 35% y toma una foto». Jarvis informa los resultados de cada paso. Si una secuencia requiere abrir varias aplicaciones/pantallas, solo se inicia la primera y se informa que las demás deben solicitarse por separado. Algunas acciones necesitan permisos especiales o interacción adicional del usuario; Android no permite acceso total al dispositivo.
