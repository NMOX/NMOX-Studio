# Inicio rápido: cinco minutos hasta tener tu proyecto en marcha

<!-- languages -->
[English](quickstart.md) · **Español** · [Français](quickstart.fr.md) · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · [Português (Brasil)](quickstart.pt.md) · [Bahasa Indonesia](quickstart.id.md) · [Filipino](quickstart.tl.md) · [Tiếng Việt](quickstart.vi.md) · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · [العربية](quickstart.ar.md)
<!-- /languages -->

Esta página pone en marcha uno de tus propios proyectos dentro de NMOX
Studio. Solo cubre lo que necesitas para eso. [La guía del
usuario](user-guide.es.md) es el manual completo. Si usas VS Code, lee
después [si vienes de VS Code](coming-from-vscode.es.md).

<a id="1-install-one-minute"></a>
## 1. Instalar (un minuto)

**macOS, con Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Homebrew te pide ejecutar `brew trust` una sola vez para cualquier tap de
terceros. No vuelve a preguntar cuando actualizas.

**macOS, Windows y Linux, sin Homebrew:** descarga la última versión para
tu sistema desde
[la página de versiones](https://github.com/NMOX/NMOX-Studio/releases/latest):

| Sistema | Archivo | Después |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | Arrastra la app a Aplicaciones. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | Ejecuta el instalador. |
| Debian, Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Otro Linux | `NMOX-Studio-<version>-linux.tar.gz` | Descomprímelo y ejecuta `bin/nmoxstudio`. |

Cada uno de estos archivos trae su propio entorno de ejecución de Java, así
que no tienes nada más que instalar. Solo el zip portátil necesita Java 21
o posterior ya instalado en la máquina.

En macOS la app está notarizada por Apple. La primera vez que la abres,
macOS pregunta si quieres abrir una aplicación descargada de internet:
pulsa **Abrir**.

<a id="2-open-your-project-one-minute"></a>
## 2. Abrir tu proyecto (un minuto)

Arranca **NMOX Studio**. Se abre con tres pestañas: **Bienvenida**,
**Rack de tareas** y **Navegador web**.

Para abrir tu proyecto, elige **Archivo ▸ Abrir carpeta…** (⌥⌘O en macOS,
Ctrl+Alt+O en Windows y Linux) y selecciona su carpeta. También puedes
hacerlo desde una terminal, como harías con `code .`:

```bash
cd ~/code/my-app
nmox .
```

El comando vuelve al instante. Si NMOX Studio ya está en marcha, recibe la
carpeta; si no, arranca. Homebrew, el instalador de Windows y los paquetes
de Linux ponen `nmox` en tu PATH. Si instalaste desde el DMG, consulta
[cómo poner `nmox` en tu PATH](user-guide.es.md#2-first-launch).

Una carpeta cuenta como proyecto si tiene un `package.json`, `Cargo.toml`,
`go.mod`, `pom.xml`, `composer.json`, `pyproject.toml` o alguno de otros 57
archivos de proyecto. Una carpeta de simples archivos HTML también cuenta.

Al abrir un proyecto pasan tres cosas:

- El **Estudio de proyecto**, a la izquierda, muestra tus archivos.
- La barra de estado, abajo, muestra tu rama de git y cuántos archivos has
  cambiado.
- El **Rack de tareas** se prepara para el tipo de proyecto que es. Un
  proyecto Vite recibe una consola de Vite, un proyecto Cargo recibe
  carriles de ejecución, depuración y pruebas, y así con cada tipo.

<a id="3-run-it-one-minute"></a>
## 3. Ejecutarlo (un minuto)

Pulsa **▶** en la barra de herramientas, o F6. Ejecuta tu proyecto como lo
ejecutan sus herramientas: el script `dev`, `start` o `serve` de
`package.json`, `cargo run`, `go run`. Usa el propio gestor de paquetes de
tu proyecto: npm, pnpm o yarn, o bun en un proyecto Bun.

La primera vez que ejecutas algo en un proyecto, NMOX Studio pregunta si
confías en la carpeta. Un proyecto en el que no confías no ejecuta nada de
su propio código: ni scripts, ni compilaciones, ni pruebas. Pulsa
**Confiar en el espacio de trabajo** para tu propio código.

Si tu proyecto es un servidor de desarrollo, su dirección aparece en la
barra de estado junto a un símbolo **⇄**, y la página se abre en la pestaña
**Navegador web**. Edita un archivo y guárdalo, y la página se recarga.

Para detener todo lo que está en marcha, pulsa **■** junto a ▶, o ⌥⌘.
(Opción, Comando y punto).

Si no pasa nada, mira la pestaña **Output** de abajo. Explica por qué no
pudo arrancar la ejecución —por ejemplo, que falta una herramienta o que
las dependencias aún no están instaladas— y ofrece arreglarlo.
**Herramientas ▸ Doctor del entorno…** enumera todas las herramientas que
NMOX Studio puede usar y muestra cuáles están instaladas.

<a id="4-find-anything-thirty-seconds"></a>
## 4. Encontrar cualquier cosa (treinta segundos)

Pulsa **⌘I** (Ctrl+I en Windows y Linux) y escribe. La Búsqueda rápida
encuentra archivos, acciones de los menús, símbolos, dispositivos del
rack, servidores y comandos en marcha, y los scripts de tu `package.json`.
Pulsa Entrar para abrir o ejecutar el resultado.

Pulsa **⌘P** para abrir un archivo por su nombre (Ir al archivo).

<a id="5-test-it-thirty-seconds"></a>
## 5. Probarlo (treinta segundos)

Pulsa **⌃F6** (Ctrl+F6) para ejecutar las pruebas de tu proyecto. Para ver
todas las pruebas del proyecto antes de ejecutar ninguna, abre la ventana
**Pruebas** con ⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## Si no tienes un proyecto a mano

- **Archivo ▸ Nuevo proyecto…** crea un proyecto real a partir de una
  plantilla (Angular, Vue, Svelte, React con Vite, JavaScript sin
  framework, PHP, Phoenix y más). Crea los archivos, inicia git e instala
  las dependencias.
- **Archivo ▸ Nuevo espacio de aprendizaje…** abre un tutorial guiado.
  *Tu primera página web* es el primero de la lista.

<a id="where-to-go-next"></a>
## Por dónde seguir

- **[El rack de tareas](user-guide.es.md#4-the-task-rack)**. Cada
  herramienta que ejecutas es un dispositivo del rack, y los cables entre
  dispositivos los encadenan: por ejemplo, ejecutar las pruebas cada vez
  que la compilación pasa.
- **[El editor](user-guide.es.md#5-the-editor)**. Incluye Emmet, muestras
  de color, depuración con puntos de interrupción para Node y Chrome, y
  plantillas de Angular.
- **[Los estudios](user-guide.es.md#6-the-studios)**. Los estudios de API,
  de bases de datos, de contratos y de bloques, y el Tablero de tareas.
- **[El glosario](glossary.es.md)** explica las palabras propias del
  producto: rack, patch, jack, carril, apuntar, KVASIR.
