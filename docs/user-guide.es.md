# NMOX Studio — Guía del usuario

<!-- languages -->
[English](user-guide.md) · **Español** · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Traducción parcial: los capítulos 1–3 están en español. Para el resto, consulta la [guía completa en inglés](user-guide.md).

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
