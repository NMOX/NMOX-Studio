# NMOX Studio — Guía del usuario

<!-- languages -->
[English](user-guide.md) · **Español** · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Traducción parcial: los capítulos 1–4 están en español. Para el resto, consulta la [guía completa en inglés](user-guide.md).

Cómo usar el producto. Esta guía recorre las funciones en el orden en que las encontrarás: instalación, primer arranque, proyectos, el rack, los estudios, los asistentes y las redes de seguridad.

---

<a id="1-install"></a>
## 1. Instalación

**macOS (recomendado):**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

La línea `brew trust` es la confirmación única de Homebrew para cualquier tap de terceros: no se te volverá a preguntar en las actualizaciones. La aplicación está firmada ad-hoc pero no notarizada, así que una copia en cuarentena sería rechazada por Gatekeeper en el primer arranque: el cask elimina el atributo de cuarentena por sí mismo en un paso `postflight` y lo indica en la salida de la instalación. Nada silencioso.

**Todo lo demás:** descarga un archivo de la [última versión](https://github.com/NMOX/NMOX-Studio/releases/latest) — `.dmg` para macOS, `-setup.exe` para Windows, `.deb` para Debian/Ubuntu, `.tar.gz` genérico para Linux. Los cuatro incluyen su propio entorno de ejecución de Java; no hace falta instalar nada antes. El `-portable.zip` es el único artefacto que usa tu propio Java (necesita Java 21+ en el PATH, o arráncalo con `--jdkhome <ruta-al-jdk>`).

> **macOS, primer arranque:** la aplicación está firmada ad-hoc pero no notarizada, así que Gatekeeper pregunta antes de ejecutarla. **Haz clic derecho en la app → Abrir** la primera vez y confirma, o ejecuta
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`. Cualquiera de las dos lo resuelve para siempre.

### Actualizar

El IDE se actualiza solo: **Herramientas ▸ Complementos ▸ Actualizaciones** ofrece los módulos de cualquier versión más reciente. Instala, reinicia cuando se te pida y listo — sin volver a descargar la aplicación completa. Una advertencia honesta: el entorno de ejecución de Java incluido y el lanzador solo cambian con un instalador completo, así que para saltos importantes de plataforma sigue siendo correcto instalar de nuevo desde un archivo de la versión.

<a id="2-first-launch"></a>
## 2. Primer arranque

Desde una terminal, `nmoxstudio --open <carpeta>` arranca la aplicación con esa carpeta abierta como proyecto y el rack apuntando a ella — la misma puerta que abre «Abrir carpeta…» en la página de bienvenida.

El IDE se abre con todas las pestañas del conjunto junto al área del editor: **Bienvenida → Rack de tareas → Estudio de bases de datos → Estudio de contratos → Diseñador de infraestructura → Estudio de API → Panel de Docker** — cada superficie principal está a un clic desde el primer minuto. En el panel izquierdo: **Estudio de proyecto** (árbol de archivos y plantillas), la base **Banco de trabajo** y el **Explorador de NPM**. Se crea una carpeta `~/NMOX` como espacio de trabajo predeterminado; el rack apunta ahí hasta que abras un proyecto.

![Primer arranque — la página de bienvenida con todas las pestañas abiertas](images/welcome.png)

Atajos que conviene aprender el primer día (también aparecen todos en la pestaña de bienvenida):

| Atajo | Abre |
|---|---|
| **⌘I** | Búsqueda rápida — llega a todo |
| **⌘9** | Rack de tareas |
| **⌥⌘0** | Banco de trabajo |
| **⌥⌘3** | Cliente de chat IRC |
| **⌥⌘4** | Navegador (WebKit integrado, con DevTools) |
| **⌥⌘5** | Estudio de bloques |
| **⌥⌘6** | Estudio de contratos |
| **⌥⌘7** | Estudio de bases de datos |
| **⌥⌘8** | Estudio de API |
| **⌥⌘9** | Diseñador de infraestructura |
| **⌘8** | Panel de Docker |
| **⌘7** | Esquema del archivo actual |
| **⇧⌘N / ⌥⌘O** | Nuevo proyecto… / Abrir carpeta… |
| **⇧⌘E / ⇧⌘L** | Nuevo experimento… / Nuevo espacio de aprendizaje… |

<a id="3-projects"></a>
## 3. Proyectos

**Abrir:** cualquier carpeta con uno de los 60 manifiestos reconocidos se abre como un proyecto real — `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `foundry.toml`, `bower.json`, `Gruntfile.js` y compañía — incluidos los manifiestos de las cadenas de contratos: un repositorio Aiken (`aiken.toml`) o Clarinet (`Clarinet.toml`) se abre con sus carriles reales conectados. Una simple carpeta de HTML con etiquetas `<script>` y **sin** manifiesto también se abre, como proyecto STATIC: la web clásica es de primera clase, no un error.

**Crear:** *Nuevo proyecto…* ofrece plantillas reales — Angular, Vue, Svelte, JavaScript sin framework, Elixir/Phoenix, PHP Web (LEMP) y Web clásica (jQuery). Cada una llega con las configuraciones de lint, formato y pruebas ya conectadas y un repositorio git iniciado: un único commit de andamiaje que, cuando el asistente ejecuta la instalación por ti, incluye también el archivo de bloqueo, de modo que tu primer `git status` está limpio.

**Cambiar de proyecto es seguro:** si hay dispositivos en marcha (un servidor de desarrollo, un observador), el IDE pregunta antes de cambiar y los detiene con limpieza. Nada sigue ejecutándose a tus espaldas, nunca. Ni siquiera forzar el cierre del IDE puede dejar un proceso huérfano.

**Los experimentos** son la forma más rápida de probar una tecnología. **Archivo ▸ Nuevo experimento…** (⇧⌘E) elige una plantilla y genera un proyecto desechable en `~/.nmox/experiments`: sin git, sin recientes, ya confiado y con las dependencias instaladas, para que la **primera ejecución funcione**. Se abre con su propio recorrido `EXPERIMENT.md`, que te dice qué pulsar, qué archivo cambiar y dónde vive la inteligencia del IDE para esa tecnología. Conserva lo que prospere: **Archivo ▸ Experimentos…** ▸ **Promover** lo saca e inicia git, **Duplicar** crea una copia para probar otro enfoque y **Descartar** elimina el resto. El estante muestra la edad de cada uno y su coste real en disco. ¿Prefieres el camino guiado? El diálogo presenta los 93 espacios de aprendizaje.

![El estante de espacios de aprendizaje — cantidad, coste en disco, edad y todo el ciclo de vida](images/spaces-shelf.png)

![Un experimento Express recién creado: el recorrido abierto, las dependencias instaladas y la API ya sirviendo](images/experiment-walkthrough.png)

**Ejecutar, construir, probar — y detener:** el ▶ de la barra (F6) ejecuta el proyecto como lo hace su cadena de herramientas: un script `start` si package.json lo tiene, `cargo run`, `go run`, `dotnet run`, y para una carpeta de HTML un pequeño servidor estático en el primer puerto libre a partir de 8080. Construir, Probar y Limpiar están al lado y en el menú Ejecutar. Un servidor de desarrollo que anuncia su dirección enciende el indicador ⇄ de la barra de estado y abre la página en el navegador integrado. Todo se ejecuta tras la confirmación de confianza del espacio de trabajo la primera vez. Una ejecución que no pudo arrancar lo dice y ofrece abrir el Doctor del entorno. Para detener: el ■ a la derecha de Depurar (⌥⌘.) detiene todos los comandos en marcha a la vez y dice qué detuvo; **Ejecutar ▸ Detener** detiene uno y luego ofrece **Repetir**. El ■ ve todo lo que el producto ejecuta por ti, incluidas las instalaciones; al pasar el cursor, el mensaje nombra exactamente qué se detendría y desde cuándo lleva cada cosa en marcha.

**`.env` en todas partes:** si tu proyecto tiene un `.env`, los dispositivos lanzados desde el rack reciben esas variables. Edítalo y la barra de estado avisa de que los reinicios lo recogerán — los procesos en marcha conservan honestamente su entorno anterior.

<a id="4-the-task-rack"></a>
## 4. El rack de tareas

![El rack de tareas](images/tabs/the-task-rack.png)

El rack es el corazón del producto. Cada herramienta de tu flujo de trabajo — npm, el empaquetador, el ejecutor de pruebas, el servidor de desarrollo, el linter, git, el despliegue — es un dispositivo físico en un rack: los mandos eligen la tarea, GO la ejecuta, los LED muestran el estado y una pantalla LCD te cuenta con palabras qué ocurrió.

![El rack apuntando a un sitio jQuery clásico — el preajuste Classic Web Bench: MAESTRO, CRATE, DYNAMO (su mando TASK analizó el Gruntfile real), IGNITION sirviendo estático y VITALS vigilando la calidad](images/task-rack.png)

**Lo básico:**

- **Añade dispositivos** arrastrándolos desde la paleta (tiene categorías y un filtro de búsqueda). Cada dispositivo trae su ficha *Cómo se usa*.
- **Ejecuta algo** pulsando el botón GO de un dispositivo. Pasa antes el cursor por encima: el mensaje muestra la línea de comandos exacta que se ejecutará. Sin magia.
- **Cablea una tubería:** pulsa **Tab** para girar el rack y ver su parte trasera. Arrastra un cable de conexión desde el jack **OK** de un dispositivo hasta el jack **GO** del siguiente. Ahora `instalar → construir → probar` es una sola pulsación: la cadena se ejecuta sola y se detiene en el primer fallo. La salida corre por la pantalla de fósforo del dispositivo MONITOR.
- **Deshaz cualquier cambio estructural** con **⌘Z** — añadir, quitar y recablear dispositivos. Quitar un dispositivo en marcha detiene antes su proceso.
- **Los preajustes** te dan un rack entero ya cableado con un clic — Ship Gate, Dev Intelligence, Monorepo Lanes, E2E Loop, LAMP Bench, Web3 Bench, Uptime Watch. Los montajes se guardan por proyecto automáticamente.

![Tab gira el rack — los cables conectan MAESTRO a través de CRATE, DYNAMO e IGNITION hasta VITALS](images/rack-rear.png)

**Coordinación, cuando tu tubería crece:**

- **QUORUM** une carriles: solo se dispara cuando *todas* sus entradas cableadas han tenido éxito — el clásico «espera a lint Y pruebas Y comprobación de tipos».
- **Las compuertas ENABLE** en los procesos largos: la entrada ENABLE de un servidor de desarrollo significa «no arranques hasta que esto se dispare».
- **REFLEX** vigila archivos y enruta por patrón — `src/**/*.css` a una cadena, `**/*.ts` a otra, por carril en un monorepo.
- **ROSETTA** elige el carril de herramientas en repositorios mixtos (el rack detecta Node/Rust/Go/PHP/… por directorio y apunta cada dispositivo en consecuencia).

**Carriles nativos de tu cadena de herramientas.** En AUTO, los dispositivos de lint y formato (PURITY, GLOSS) hablan la cadena del propio proyecto en vez de recurrir a herramientas de Node en todas partes: un espacio de trabajo Deno usa `deno lint` y `deno fmt`, un proyecto Cargo usa `cargo clippy` y `cargo fmt`, y un módulo Go usa `go vet` (o `golangci-lint` si el proyecto lleva su configuración) y `gofmt`. Un `biome.json` cambia los carriles de Node a Biome, y las posiciones explícitas del mando siempre ganan a AUTO.

**Tus propios dispositivos.** El estante se amplía con un editor de texto: cualquier `*.json` en `~/.nmox/devices.d/` se convierte en un dispositivo real — mandos, botones, LED, puertos y cables, guardado en el montaje y accesible desde ⌘I. Declara un comando como un arreglo de argumentos, nombra un mando y `{{mando}}` se sustituye al pulsar el botón. Las leyes las mantiene el anfitrión, no tu archivo: **la confianza del espacio de trabajo controla el primer arranque igual que en un dispositivo integrado**.

**Las compuertas de calidad** convierten el «parece terminado» en «está terminado»:

- **VITALS** ejecuta Lighthouse contra tu servidor vivo y exige un mínimo de rendimiento, accesibilidad, buenas prácticas o SEO.
- **VERITAS** impone un mínimo de cobertura y vuelve a ejecutar exactamente las pruebas que fallaron, por su nombre.
- **GAUNTLET** mide la carga de un extremo y exige un mínimo de rendimiento. **PRISM** vigila el tamaño del paquete, **BEACON** el certificado y la disponibilidad de una URL, y **PREFLIGHT** es la lista de comprobación previa al envío — cablea su OK a tu dispositivo de despliegue y los despliegues no podrán ejecutarse hasta que todo esté en verde.
- **GOVERNOR** vigila las regresiones de gas en el trabajo con Solidity (`.gas-snapshot`).

**Cualquier otra cosa:** **SOLDER** envuelve cualquier comando de consola como un dispositivo de pleno derecho — y el rack entero **se exporta a GitHub Actions** (tu tubería local y tu integración continua son el mismo cableado). **HELM** ejecuta comandos en un servidor remoto por ssh, **TAIL** sigue cualquier archivo de registro y **PHOSPHOR** es una terminal dentro del rack. Si el comando imprime una dirección local, se enciende el indicador ⇄ como en cualquier dispositivo que sirva, y se apaga cuando la ejecución termina.

**El rack se mantiene sincronizado solo.** Edita `package.json` y el mando de scripts de NPM-9000 se actualiza en su sitio. Edita un `Gruntfile` y DYNAMO vuelve a analizar sus tareas. Añade una dependencia y la pantalla de CRATE se refresca. Sin volver a apuntar, sin botones de recargar.

### KVASIR — explica el último fallo

![KVASIR explicando una ejecución fallida real: el diagnóstico autorizado en el frontal y los pasos completos de solución en el visor](images/kvasir-explain.png)

**KVASIR** es asistencia de IA a la manera del rack: un dispositivo que explica el error que hay ahora mismo en el bus MONITOR, no una barra lateral de chat. Cuando una ejecución falla, pulsa **EXPLAIN** y KVASIR le pregunta a tu IA qué salió mal y cuál es el siguiente paso concreto. Un veredicto breve aparece en la pantalla; **VIEW** abre la respuesta completa. **MODEL** elige entre **FAST** (rápido y barato, el valor por omisión) y **DEEP** (más potente). EXPLAIN es azul: lee y pregunta, nunca toca tu proyecto.

**Elige tu IA y pon tu clave.** KVASIR funciona con **Claude (Anthropic)**, **ChatGPT (OpenAI)** o **Gemini (Google)** — tu clave, tu elección. Pulsa **KEY…** para escoger el proveedor y pegar su clave; la elección se recuerda y la clave vive solo en el llavero del sistema operativo. También se leen las variables de entorno habituales de cada proveedor, y una clave guardada gana a una del entorno.

**Qué envía KVASIR, y todo lo que envía.** La primera vez que pulsas EXPLAIN, un diálogo enumera exactamente lo que saldrá de tu máquina y lo que no; nada se envía sin ese consentimiento, y el consentimiento es por proveedor. Tras un EXPLAIN correcto, el botón **VIEW** abre la respuesta como una conversación, así que puedes seguir preguntando sobre el mismo fallo.

**Pregúntale a KVASIR sobre tu código.** El mismo asistente llega al editor: selecciona código y elige **Preguntar a KVASIR sobre la selección…**, o **Editar con KVASIR…** para decir qué cambiar y ver un antes y un después antes de aplicar nada. **⌥⌘G** completa en el cursor con texto fantasma que solo se inserta si pulsas Tab, y el indicador de rama de git puede redactar tu mensaje de commit.

**Apunta un agente a tu IDE.** Herramientas ▸ Agent Port (MCP)… abre un extremo MCP que un asistente externo puede consultar: es **de solo lectura por construcción**, está apagado hasta que tú lo enciendes, escucha únicamente en la interfaz local y exige el token que se genera al arrancarlo.

El rack es ampliable: los complementos de terceros pueden añadir dispositivos (instala su NBM desde Herramientas ▸ Complementos). Para escribir uno, consulta [device-spi.md](device-spi.md).
