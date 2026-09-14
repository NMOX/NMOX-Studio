# Tutoriales de NMOX Studio

<!-- languages -->
[English](README.md) · **Español** · [Français](README.fr.md) · [Deutsch](README.de.md) · [Русский](README.ru.md) · [Українська](README.uk.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt.md) · [Bahasa Indonesia](README.id.md) · [Filipino](README.tl.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh.md) · [हिन्दी](README.hi.md) · [עברית](README.he.md) · [العربية](README.ar.md)
<!-- /languages -->

Recorridos breves y prácticos por los sistemas que distinguen a NMOX
Studio de un IDE cualquiera. Cada uno se hace de una sentada: abres la
ventana, sigues los pasos y ya has usado la función de verdad.

Para la referencia completa (instalación, cada menú, cada red de
seguridad), consulta la [Guía del usuario](../user-guide.es.md). Para la
lista completa de dispositivos, [devices.md](../devices.md).

## Los sistemas

| Tutorial | Qué harás | Se abre con |
|----------|-----------|-------------|
| [El Rack de tareas](the-task-rack.es.md) | Cablear un patch de ejecución→monitor y verlo dispararse | ⌘9 / pestaña Rack de tareas |
| [Escribe tu propio dispositivo](your-own-device.es.md) | Añadir un dispositivo al rack con un editor de texto: sin Java, sin reiniciar | `~/.nmox/devices.d/` |
| [Banco de trabajo](workbench.es.md) | Usar la base para saltar entre proyectos y herramientas | ⌥⌘0 |
| [Estudio de proyecto](project-studio.es.md) | Generar un proyecto y ejecutarlo sin terminal | pestaña Estudio de proyecto |
| [Estudio de API](api-studio.es.md) | Enviar una petición, comprobarla y leer su nota de seguridad | ⌥⌘8 |
| [Estudio de bases de datos](db-studio.es.md) | Conectarte a SQLite y editar una fila en la rejilla | ⌥⌘7 |
| [Estudio de contratos](contract-studio.es.md) | Compilar, desplegar en una cadena local y llamar a un contrato | ⌥⌘6 (Web3) |
| [Diseñador de infraestructura](infra-designer.es.md) | Dibujar un droplet y un firewall y simular el despliegue | ⌥⌘9 |
| [Estudio de bloques](block-studio.es.md) | Construir un Web Component con bloques encajables | ⌥⌘5 |
| [Edición y depuración políglota](polyglot-editing-and-debugging.es.md) | Poner un punto de interrupción en una app Node y alcanzarlo | abre cualquier proyecto |
| [Del navegador al código fuente](browser-to-source.es.md) | Pulsar un elemento de la página, llegar a su código y cambiarle el estilo desde DevTools | ⌥⌘4 → DevTools → DOM |
| [El Agent Port (MCP)](agent-port.es.md) | Apuntar un agente de IA al estado vivo del IDE, de solo lectura por construcción | Herramientas ▸ Agent Port (MCP)… |
| [El Panel de Docker](docker-panel.es.md) | Inspeccionar contenedores y dockerizar un proyecto | pestaña Docker |
| [El Tablero de tareas y los sprints](task-board.es.md) | Llevar un kanban con reloj de tiempo, un standup de un clic y el burndown del sprint desde un único archivo versionado | ⌥⌘1 |
| [Enséñaselo a una sala](show-it-to-a-room.es.md) | Presentar, compartir y hacer capturas desde el propio IDE: de Modo presentación a Copiar árbol del proyecto como Markdown | Ver ▸ Modo presentación |
| [KVASIR](kvasir.es.md) | Preguntarle a la IA por qué falló una ejecución | Rack → KVASIR |
| [Explícalo todo](explain-anything.es.md) | Usar las cuatro caras de KVASIR: ejecuciones, código, respuestas de API, errores de base de datos | allí donde algo falle |
| [Migrar desde Postman](migrating-from-postman.es.md) | Importar tus colecciones, capturas HAR y más; los secretos van al llavero | ⌥⌘8 → Importar… |
| [Image Kit (Web)](image-kit.es.md) | Comprimir las imágenes de un proyecto: JPEG más ligeros, hermanos WebP y un informe honesto | Archivo ▸ Añadir al proyecto ▸ Image Kit (Web)… |
| [Espacios de aprendizaje](learning-spaces.es.md) | Levantar un entorno guiado con un REPL vivo | Nuevo espacio de aprendizaje… |
| [Asistentes y kits](wizards-and-kits.es.md) | Añadir una PWA, archivos de estándares o andamiajes de web clásica | Archivo ▸ Añadir al proyecto |

> **Una nota sobre los atajos.** En macOS los estudios viven en la
> familia `⌥⌘` (Opción-Comando) —`⌥⌘6`–`⌥⌘9`, `⌥⌘5`, `⌥⌘0`— porque
> la plataforma ya ocupa los atajos `⇧⌘`. En Linux y Windows el
> modificador es `Alt+`; los menús (Ventana ▸ …) funcionan siempre.

El primer arranque muestra tres pestañas —la Bienvenida, el Rack de
tareas y el Navegador web— con el Estudio de proyecto, el Banco de trabajo y
el Explorador de NPM acoplados a su lado. Cualquier otra ventana está a
un atajo de distancia y aparece en la columna HERRAMIENTAS de la
Bienvenida.
