package com.example.ai

import com.example.model.JarvisIntentType

/**
 * Curated bilingual training corpus used to train Jarvis's on-device Multi-Layer Perceptron.
 * Covers natural paraphrases, colloquial phrasings, and device-control variations.
 */
object JarvisTrainingCorpus {

    val samples: List<Pair<String, JarvisIntentType>> = listOf(
        // TOGGLE_FLASHLIGHT
        "enciende la linterna" to JarvisIntentType.TOGGLE_FLASHLIGHT,
        "apaga la linterna" to JarvisIntentType.TOGGLE_FLASHLIGHT,
        "prende el flash" to JarvisIntentType.TOGGLE_FLASHLIGHT,
        "necesito luz esta muy oscuro aqui" to JarvisIntentType.TOGGLE_FLASHLIGHT,
        "activa la luz trasera del telefono" to JarvisIntentType.TOGGLE_FLASHLIGHT,
        "apaga la luz del flash" to JarvisIntentType.TOGGLE_FLASHLIGHT,
        "ilumina con la antorcha" to JarvisIntentType.TOGGLE_FLASHLIGHT,
        "corta la linterna por favor" to JarvisIntentType.TOGGLE_FLASHLIGHT,

        // SET_VOLUME
        "sube el volumen" to JarvisIntentType.SET_VOLUME,
        "baja el volumen" to JarvisIntentType.SET_VOLUME,
        "pon el volumen al 70 por ciento" to JarvisIntentType.SET_VOLUME,
        "mas fuerte la musica por favor" to JarvisIntentType.SET_VOLUME,
        "bajale al sonido esta muy alto" to JarvisIntentType.SET_VOLUME,
        "silencia el audio multimedia" to JarvisIntentType.SET_VOLUME,
        "volumen al maximo" to JarvisIntentType.SET_VOLUME,
        "deja el volumen de timbre al 40%" to JarvisIntentType.SET_VOLUME,

        // SET_RINGER_MODE
        "pon el telefono en silencio" to JarvisIntentType.SET_RINGER_MODE,
        "activa el modo vibracion" to JarvisIntentType.SET_RINGER_MODE,
        "restaura el sonido normal del timbre" to JarvisIntentType.SET_RINGER_MODE,
        "no quiero que suene el telefono" to JarvisIntentType.SET_RINGER_MODE,
        "cambia a modo no molestar" to JarvisIntentType.SET_RINGER_MODE,
        "deja el celular vibrando" to JarvisIntentType.SET_RINGER_MODE,

        // SET_BRIGHTNESS
        "sube el brillo de la pantalla" to JarvisIntentType.SET_BRIGHTNESS,
        "baja el brillo" to JarvisIntentType.SET_BRIGHTNESS,
        "pon el brillo al 80 por ciento" to JarvisIntentType.SET_BRIGHTNESS,
        "la pantalla esta muy oscura dale mas luz" to JarvisIntentType.SET_BRIGHTNESS,
        "brillo automatico" to JarvisIntentType.SET_BRIGHTNESS,
        "iluminacion al minimo" to JarvisIntentType.SET_BRIGHTNESS,

        // SET_ALARM
        "pon una alarma a las 7 de la manana" to JarvisIntentType.SET_ALARM,
        "despiertame a las 6:30" to JarvisIntentType.SET_ALARM,
        "crea una alarma para las 8 pm" to JarvisIntentType.SET_ALARM,
        "configura el despertador a las 9:15" to JarvisIntentType.SET_ALARM,
        "avisame con una alarma a las 14:00" to JarvisIntentType.SET_ALARM,

        // SET_TIMER
        "pon un temporizador de 5 minutos" to JarvisIntentType.SET_TIMER,
        "inicia una cuenta regresiva de 30 segundos" to JarvisIntentType.SET_TIMER,
        "cronometro de 10 minutos para cocinar" to JarvisIntentType.SET_TIMER,
        "cuenta atras de 2 horas" to JarvisIntentType.SET_TIMER,
        "timer de 45 segundos" to JarvisIntentType.SET_TIMER,

        // OPEN_SYSTEM_PANEL
        "abre el panel de wifi" to JarvisIntentType.OPEN_SYSTEM_PANEL,
        "muestra la configuracion de bluetooth" to JarvisIntentType.OPEN_SYSTEM_PANEL,
        "quiero conectar una red wifi" to JarvisIntentType.OPEN_SYSTEM_PANEL,
        "ve a los ajustes del sistema" to JarvisIntentType.OPEN_SYSTEM_PANEL,
        "abre las opciones de ubicacion gps" to JarvisIntentType.OPEN_SYSTEM_PANEL,
        "muestrame el uso de bateria" to JarvisIntentType.OPEN_SYSTEM_PANEL,

        // OPEN_APP_ACTION
        "abre la camara para tomar una foto" to JarvisIntentType.OPEN_APP_ACTION,
        "inicia la calculadora" to JarvisIntentType.OPEN_APP_ACTION,
        "muestra mi calendario" to JarvisIntentType.OPEN_APP_ACTION,
        "abre las alarmas del reloj" to JarvisIntentType.OPEN_APP_ACTION,

        // QUERY_DEVICE_STATUS
        "cual es el estado del sistema" to JarvisIntentType.QUERY_DEVICE_STATUS,
        "haz un diagnostico completo del telefono" to JarvisIntentType.QUERY_DEVICE_STATUS,
        "cuanta bateria me queda" to JarvisIntentType.QUERY_DEVICE_STATUS,
        "cuanto almacenamiento libre tengo" to JarvisIntentType.QUERY_DEVICE_STATUS,
        "cuanta memoria ram hay disponible" to JarvisIntentType.QUERY_DEVICE_STATUS,
        "como esta mi conexion a internet" to JarvisIntentType.QUERY_DEVICE_STATUS,
        "dame un reporte de telemetria" to JarvisIntentType.QUERY_DEVICE_STATUS,

        // SET_TTS_VOICE
        "silencia tu voz jarvis" to JarvisIntentType.SET_TTS_VOICE,
        "no hables en voz alta" to JarvisIntentType.SET_TTS_VOICE,
        "reactiva tu voz para escucharte" to JarvisIntentType.SET_TTS_VOICE,
        "vuelve a hablar jarvis" to JarvisIntentType.SET_TTS_VOICE,

        // SEND_MESSAGE
        "envia un whatsapp a Carlos diciendo llego tarde" to JarvisIntentType.SEND_MESSAGE,
        "manda un mensaje por telegram a Ana diciendo hola" to JarvisIntentType.SEND_MESSAGE,
        "escribele por wasap a mama que ya sali" to JarvisIntentType.SEND_MESSAGE,
        "manda un sms a Juan diciendo llamame" to JarvisIntentType.SEND_MESSAGE,
        "avisale a Pedro por whatsapp que voy en camino" to JarvisIntentType.SEND_MESSAGE,
        "dile a Laura por telegram que nos vemos a las 5" to JarvisIntentType.SEND_MESSAGE,
        "enviar mensaje de texto" to JarvisIntentType.SEND_MESSAGE,

        // MAKE_CALL
        "llama a mama" to JarvisIntentType.MAKE_CALL,
        "marca a Carlos por telefono" to JarvisIntentType.MAKE_CALL,
        "haz una llamada al 5512345678" to JarvisIntentType.MAKE_CALL,
        "quiero hablar por telefono con Pedro" to JarvisIntentType.MAKE_CALL,
        "comunicame con Ana" to JarvisIntentType.MAKE_CALL,

        // WEB_BROWSE
        "busca en internet noticias de ciencia" to JarvisIntentType.WEB_BROWSE,
        "abre wikipedia.org en el navegador" to JarvisIntentType.WEB_BROWSE,
        "busca en youtube musica relajante" to JarvisIntentType.WEB_BROWSE,
        "investiga sobre energia nuclear en la web" to JarvisIntentType.WEB_BROWSE,
        "navega a github.com" to JarvisIntentType.WEB_BROWSE,
        "quiero buscar recetas de pasta en internet" to JarvisIntentType.WEB_BROWSE,

        // NAVIGATE_MAPS
        "llevame al aeropuerto" to JarvisIntentType.NAVIGATE_MAPS,
        "como llegar al centro comercial" to JarvisIntentType.NAVIGATE_MAPS,
        "inicia ruta en mapas hacia mi casa" to JarvisIntentType.NAVIGATE_MAPS,
        "donde queda la farmacia mas cercana" to JarvisIntentType.NAVIGATE_MAPS,
        "navega hacia el hospital" to JarvisIntentType.NAVIGATE_MAPS,

        // SEND_EMAIL
        "envia un correo a Juan" to JarvisIntentType.SEND_EMAIL,
        "manda un email a soporte@ejemplo.com sobre el proyecto" to JarvisIntentType.SEND_EMAIL,
        "redacta un correo electronico" to JarvisIntentType.SEND_EMAIL,
        "escribe un mail para Ana" to JarvisIntentType.SEND_EMAIL,

        // MEDIA_CONTROL
        "pausa la musica" to JarvisIntentType.MEDIA_CONTROL,
        "reanuda la reproduccion" to JarvisIntentType.MEDIA_CONTROL,
        "siguiente cancion" to JarvisIntentType.MEDIA_CONTROL,
        "cancion anterior" to JarvisIntentType.MEDIA_CONTROL,
        "deten el audio que esta sonando" to JarvisIntentType.MEDIA_CONTROL,
        "pon play a la musica" to JarvisIntentType.MEDIA_CONTROL,

        // OPEN_APP_BY_NAME
        "abre whatsapp" to JarvisIntentType.OPEN_APP_BY_NAME,
        "inicia spotify" to JarvisIntentType.OPEN_APP_BY_NAME,
        "abre telegram por favor" to JarvisIntentType.OPEN_APP_BY_NAME,
        "ejecuta youtube" to JarvisIntentType.OPEN_APP_BY_NAME,
        "quiero abrir instagram" to JarvisIntentType.OPEN_APP_BY_NAME,
        "abre google maps" to JarvisIntentType.OPEN_APP_BY_NAME,
        "lanza gmail" to JarvisIntentType.OPEN_APP_BY_NAME,

        // RUN_ROUTINE
        "activa el modo noche" to JarvisIntentType.RUN_ROUTINE,
        "ejecuta la rutina modo cine" to JarvisIntentType.RUN_ROUTINE,
        "inicia modo ahorro de bateria" to JarvisIntentType.RUN_ROUTINE,
        "pon modo trabajo" to JarvisIntentType.RUN_ROUTINE,
        "corre la rutina de la manana" to JarvisIntentType.RUN_ROUTINE,

        // CREATE_AUTOMATION
        "crea una rutina llamada descanso que apague la linterna y baje el brillo" to JarvisIntentType.CREATE_AUTOMATION,
        "automatiza que si la bateria baja del 20% active modo ahorro" to JarvisIntentType.CREATE_AUTOMATION,
        "cuando conecte el cargador di bateria cargando" to JarvisIntentType.CREATE_AUTOMATION,
        "configura una automatizacion cuando se conecte el wifi" to JarvisIntentType.CREATE_AUTOMATION,
        "nueva rutina llamada ejercicio que suba el volumen al 90" to JarvisIntentType.CREATE_AUTOMATION,

        // LIST_AUTOMATIONS
        "lista mis rutinas" to JarvisIntentType.LIST_AUTOMATIONS,
        "que automatizaciones tengo activas" to JarvisIntentType.LIST_AUTOMATIONS,
        "muestra las reglas programadas" to JarvisIntentType.LIST_AUTOMATIONS,
        "cuales son mis rutinas guardadas" to JarvisIntentType.LIST_AUTOMATIONS,

        // DELETE_AUTOMATION
        "elimina la rutina modo noche" to JarvisIntentType.DELETE_AUTOMATION,
        "borra la automatizacion de bateria" to JarvisIntentType.DELETE_AUTOMATION,
        "desactiva la rutina de ejercicio" to JarvisIntentType.DELETE_AUTOMATION,

        // SCHEDULE_COMMAND
        "en 5 minutos apaga la linterna" to JarvisIntentType.SCHEDULE_COMMAND,
        "en 30 segundos pon modo vibracion" to JarvisIntentType.SCHEDULE_COMMAND,
        "programa dentro de 10 minutos pausar la musica" to JarvisIntentType.SCHEDULE_COMMAND,
        "en 1 hora baja el brillo al 20 por ciento" to JarvisIntentType.SCHEDULE_COMMAND,

        // SAVE_NOTE
        "guarda una nota comprar cafe y leche" to JarvisIntentType.SAVE_NOTE,
        "recuerda que mi reunion es el viernes" to JarvisIntentType.SAVE_NOTE,
        "apunta esto por favor llamar al dentista" to JarvisIntentType.SAVE_NOTE,
        "memoriza el codigo 4829" to JarvisIntentType.SAVE_NOTE,

        // READ_NOTES
        "lee mis notas guardadas" to JarvisIntentType.READ_NOTES,
        "que recuerdas de mis apuntes" to JarvisIntentType.READ_NOTES,
        "muestra mis recordatorios y notas" to JarvisIntentType.READ_NOTES,
        "borra todas mis notas" to JarvisIntentType.READ_NOTES,

        // TEACH_NEURAL_NET
        "aprende que a dormir significa activar modo noche" to JarvisIntentType.TEACH_NEURAL_NET,
        "cuando diga luz enciende la linterna" to JarvisIntentType.TEACH_NEURAL_NET,
        "ensena a tu red neuronal que silencio total es modo silencio" to JarvisIntentType.TEACH_NEURAL_NET,

        // OPEN_ASSISTANT_SETTINGS
        "ponte como asistente predeterminado" to JarvisIntentType.OPEN_ASSISTANT_SETTINGS,
        "configura a jarvis como asistente por defecto" to JarvisIntentType.OPEN_ASSISTANT_SETTINGS,

        // CONVERSATION
        "hola jarvis buenos dias" to JarvisIntentType.CONVERSATION,
        "que hora es ahorita" to JarvisIntentType.CONVERSATION,
        "que dia es hoy" to JarvisIntentType.CONVERSATION,
        "cuanto es 25 por 4" to JarvisIntentType.CONVERSATION,
        "convierte 10 kilometros a millas" to JarvisIntentType.CONVERSATION,
        "quien eres y que puedes hacer" to JarvisIntentType.CONVERSATION,
        "muchas gracias jarvis" to JarvisIntentType.CONVERSATION,
        "cuentame algo sobre inteligencia artificial" to JarvisIntentType.CONVERSATION,
        "que es la gravedad" to JarvisIntentType.CONVERSATION
    )
}
