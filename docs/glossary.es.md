# Glosario

<!-- languages -->
[English](glossary.md) · **Español** · [Français](glossary.fr.md) · [Deutsch](glossary.de.md) · [Русский](glossary.ru.md) · [Українська](glossary.uk.md) · [Polski](glossary.pl.md) · [Português (Brasil)](glossary.pt.md) · [Bahasa Indonesia](glossary.id.md) · [Filipino](glossary.tl.md) · [Tiếng Việt](glossary.vi.md) · [简体中文](glossary.zh.md) · [हिन्दी](glossary.hi.md) · [עברית](glossary.he.md) · [العربية](glossary.ar.md)
<!-- /languages -->

Las palabras que usa NMOX Studio y que otro IDE no usa, más los términos de
NetBeans que se dejan ver. Cada entrada dice qué significa la palabra aquí y
dónde leer más.

<a id="the-rack"></a>
## El rack

**Rack de tareas** (⌘9) — La ventana donde se ejecutan tus herramientas.
Cada tarea (instalar, compilar, probar, servir, analizar, desplegar) es un
*dispositivo* montado en un rack, como el equipo físico de un estudio de
grabación. [Guía del usuario §4](user-guide.es.md#4-the-task-rack).

**Dispositivo** — Una herramienta del rack, por ejemplo VELOCITY (Vite),
VERITAS (pruebas) o PURITY (lint). Un dispositivo tiene un panel frontal (la
*carátula*) con mandos, botones, luces y una pequeña pantalla, y un panel
trasero con *jacks*. Hay 53 dispositivos incorporados, enumerados en
[la referencia de dispositivos](devices.md). Puedes añadir los tuyos como
un archivo JSON en `~/.nmox/devices.d/`
([archivos de dispositivo](device-files.md)).

**Carátula** — El panel frontal de un dispositivo. Pulsa **Tab** en el rack
para girarlo y ver el panel trasero.

**Jack** — Un conector del panel trasero de un dispositivo. Los jacks de
salida envían señales y los de entrada las reciben. Hay tres clases de
señal:
- Un **disparo** (*trigger*) es un solo pulso: «la compilación terminó»,
  «OK», «FAIL».
- Una **compuerta** (*gate*) se queda encendida o apagada: «el servidor está
  levantado».
- Los **datos** llevan texto, como una URL o una línea de salida.

**Cable** — Una conexión de un jack de salida a un jack de entrada. Conecta
el jack OK de una compilación al jack RUN del ejecutor de pruebas, y las
pruebas se ejecutan cada vez que una compilación pasa. Para conectar dos
jacks, arrastra de uno al otro, o pulsa uno y después el otro.

**Patch** — Un rack entero: sus dispositivos, sus ajustes y sus cables. Se
guarda junto al proyecto como `.nmoxrack.json` (con **Guardar patch**), así
que merece la pena versionarlo.

**Preajuste** — Un patch ya hecho que puedes cargar desde el menú
**Preajustes** del rack, por ejemplo *Ship Gate* o *E2E Loop*. Guarda
cualquier patch en `~/.nmox/presets.d/` y aparecerá también en el menú.

**Rack inicial** — El patch que recibe un proyecto la primera vez que lo
abres, elegido según el tipo de proyecto: una consola de Vite para una app
Vite, carriles de ejecución, depuración y pruebas para un crate de Cargo, y
así con cada tipo.

**Carril** — Dos significados, los dos sobre ejecutar cosas:
- Una **tubería**: una cadena de dispositivos unidos por cables, como
  instalar → compilar → probar. Varios carriles pueden correr a la vez, y
  QUORUM espera a que terminen todos.
- El **carril AUTO** de un dispositivo: la orden que elige para este
  proyecto. En AUTO, el dispositivo de pruebas ejecuta `npm test` en un
  proyecto Node y `cargo test` en uno de Rust.

**Compartir… / Importar…** — Guarda un rack en un archivo para otra persona,
o carga uno que te hayan enviado. Antes de montar nada, Importar muestra
todo lo que contiene el archivo, y cada dispositivo llega apagado.

**Galería de racks** — **Herramientas ▸ Galería de racks…** enumera los
racks de la comunidad, los preajustes, los racks iniciales y tus propios
racks guardados. Cada entrada muestra para qué sirve y qué herramientas
necesita. [Racks de la comunidad](racks.md).

<a id="projects-and-running"></a>
## Proyectos y ejecución

**Apuntar** / **proyecto apuntado** — El proyecto con el que el IDE está
trabajando ahora. Abrir un proyecto lo apunta: el rack, los estudios, la
barra de estado y Ejecutar siguen al proyecto apuntado. Apuntar a otro los
cambia todos, y lo que siga en marcha se detiene antes, después de
preguntar.

**Confianza del espacio de trabajo** — La pregunta que hace NMOX Studio
antes de ejecutar por primera vez el propio código de un proyecto (scripts,
compilaciones, pruebas). Si respondes **Mantener seguro**, no se ejecuta
nada del proyecto; **Confiar en el espacio de trabajo** lo permite. Tu
respuesta se recuerda por carpeta.

**▶ y ■** — Ejecutar y Detener en la barra de herramientas. ▶ (F6) ejecuta
el proyecto apuntado. ■ (⌥⌘.) detiene todos los comandos que NMOX Studio
arrancó por ti.

**Indicador ⇄** / **sirviendo** — Cuando algo que ejecutas imprime una
dirección local, como `http://localhost:5173/`, la dirección aparece en la
barra de estado tras un símbolo ⇄. Ese servidor en marcha *está sirviendo*.
Pulsa la dirección para abrirla en el Navegador web. La Búsqueda rápida
enumera lo que sirve bajo *Servidores activos*.

**Experimento** — Un proyecto desechable hecho a partir de una plantilla en
`~/.nmox/experiments`. Sus dependencias ya están instaladas y ya es de
confianza. **Promocionar…** para conservarlo, o **Descartar…** para
eliminarlo. **Archivo ▸ Nuevo experimento…**.

**Espacio de aprendizaje** — Un tutorial guiado de un lenguaje o un
framework. Crea un proyecto real, un recorrido y un rack preparado con un
REPL vivo, y **Archivo ▸ Comprobar mi trabajo** revisa tus ejercicios. Hay
93. **Archivo ▸ Nuevo espacio de aprendizaje…**.

**PREFLIGHT** — El dispositivo de comprobación antes de publicar. Ejecuta
las comprobaciones que tu proyecto define (lint, tipos, pruebas,
compilación) como un único aprobado o suspenso.

**PRIMEROS PASOS** — La lista de comprobación de la pestaña Bienvenida. Los
pasos se marcan solos a medida que los haces y nunca se desmarcan.

<a id="the-windows"></a>
## Las ventanas

**Estudio** — Una ventana con su propia herramienta para un tipo de
trabajo. Hay cinco: **Estudio de API** (⌥⌘8), **Estudio de bases de datos**
(⌥⌘7), **Estudio de contratos** (⌥⌘6, contratos inteligentes), **Estudio de
bloques** (⌥⌘5, componentes web construidos con bloques) y el **Diseñador de
infraestructura** (⌥⌘9, infraestructura en la nube). Cada uno guarda su
trabajo junto al proyecto en un archivo `.nmox*.json`. El **Estudio de
proyecto** comparte el nombre, pero es el árbol de archivos y las
plantillas de proyecto.

**Banco de trabajo** (⌥⌘0) — La base: lo que está en marcha, lo que está
abierto, y tus proyectos y archivos recientes.

**Tablero de tareas** (⌥⌘1) — Un tablero kanban por proyecto, con sprints y
un reloj de tiempo, guardado como `.nmoxtasks.json`.

**Bienvenida** — La pestaña de inicio: acciones para empezar, proyectos
recientes, la columna *HERRAMIENTAS* que enumera todas las ventanas, y
PRIMEROS PASOS.

<a id="ai"></a>
## IA

**KVASIR** — El nombre de las funciones de IA de NMOX Studio: Preguntar,
Editar, Completar, Explicar y Redactar el mensaje de commit. Funciona con
Claude, ChatGPT o Gemini usando tu propia clave de API, que se guarda en el
llavero del sistema operativo. Cada función te pide consentimiento una vez
y nombra exactamente lo que enviará. No se envía nada hasta que usas una
función. Las versiones anteriores lo llamaban ORACLE.

**Agent Port** — **Herramientas ▸ Agent Port (MCP)…** da a un agente de IA
que corre en tu máquina, como un asistente de programación, acceso de solo
lectura al estado del IDE por MCP: archivos abiertos, diagnósticos,
ejecuciones, símbolos. Es de solo lectura por diseño y solo escucha en tu
propia máquina.
[Tutorial](tutorials/agent-port.md).

<a id="netbeans-terms-you-may-see"></a>
## Términos de NetBeans que puedes ver

NMOX Studio está construido sobre la plataforma NetBeans, y algunas de sus
palabras se dejan ver.

**Módulo** / **NBM** — Una parte de la aplicación. Un *NBM* es el archivo
en el que se entrega un módulo. **Herramientas ▸ Complementos** instala las
actualizaciones módulo a módulo.

**Centro de actualizaciones** — De donde
**Herramientas ▸ Complementos ▸ Actualizaciones** saca las versiones nuevas de los módulos de NMOX Studio.
Lee un catálogo que se publica con cada versión en GitHub.

**userdir** — La carpeta donde NMOX Studio guarda sus ajustes, la
disposición de las ventanas, los registros y las actualizaciones
instaladas. El diálogo «Acerca de» muestra dónde está (en el menú Ayuda en
Windows y Linux, en el menú NMOX Studio en macOS). Su registro está en
`var/log/messages.log`. Para empezar con ajustes limpios, arranca con
`--userdir <una carpeta vacía>`.

**Opciones** / **Settings…** — El diálogo de preferencias. Es
**Herramientas ▸ Opciones** en Windows y Linux, y
**NMOX Studio ▸ Settings…** en macOS.

**Elementos de acción** — La ventana que enumera los problemas encontrados
en el proyecto: los errores y avisos de los servidores de lenguaje, y los
resultados de lint y de comprobación de tipos del rack. Pulsa un problema
para ir a esa línea.
