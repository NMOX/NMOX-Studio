# Si vienes de VS Code

<!-- languages -->
[English](coming-from-vscode.md) · **Español** · [Français](coming-from-vscode.fr.md) · [Deutsch](coming-from-vscode.de.md) · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · [Polski](coming-from-vscode.pl.md) · [Português (Brasil)](coming-from-vscode.pt.md) · [Bahasa Indonesia](coming-from-vscode.id.md) · [Filipino](coming-from-vscode.tl.md) · [Tiếng Việt](coming-from-vscode.vi.md) · [简体中文](coming-from-vscode.zh.md) · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

Tus manos ya saben dónde están las cosas. Esta página es el mapa que lleva
de esos hábitos a NMOX Studio: primero los atajos, luego dónde vive aquí
cada idea de VS Code, y después lo que, honestamente, es distinto.

Los cuatro primeros atajos que pulsa quien viene de VS Code hacen lo que
espera: **⇧⌘P** abre la paleta de comandos, **⇧⌘E** el árbol de archivos,
**⇧⌘X** los complementos y **⌃\`** la terminal. Están registrados en los
cinco perfiles de teclado que trae la plataforma, y una comprobación de la
compilación resuelve cada uno a través del teclado ya ensamblado en macOS,
Windows y Linux, para que nada más se dispare en su lugar.

<a id="the-chords"></a>
## Los atajos

Las columnas de macOS usan los glifos de la barra de menús (⌃ Control,
⌥ Opción, ⇧ Mayúsculas, ⌘ Comando); las de Windows y Linux son el mismo
atajo en un teclado de PC.

| Quieres | VS Code, macOS | NMOX, macOS | VS Code, Win/Linux | NMOX, Win/Linux |
|---|---|---|---|---|
| Paleta de comandos | ⇧⌘P | **⇧⌘P** (o ⌘I) — Búsqueda rápida | Ctrl+Shift+P | **Ctrl+Shift+P** (o Ctrl+I) |
| Abrir un archivo por su nombre | ⌘P | **⌘P** — Ir al archivo | Ctrl+P | **Ctrl+P** |
| El árbol de archivos | ⇧⌘E | **⇧⌘E** — Estudio de proyecto | Ctrl+Shift+E | **Ctrl+Shift+E** |
| Extensiones | ⇧⌘X | **⇧⌘X** — Herramientas ▸ Complementos | Ctrl+Shift+X | **Ctrl+Shift+X** |
| La terminal, en la carpeta del proyecto | ⌃\` | **⌃\`** | Ctrl+\` | **Ctrl+\`** |
| Abrir un proyecto reciente | ⌃R | **⌥⌘P** — Cambiar de proyecto… | Ctrl+R | **Ctrl+Alt+P** |
| Ir a un símbolo del proyecto | ⌘T | **⌥⇧⌘O** | Ctrl+T | **Ctrl+Alt+Shift+O** |
| Ir a la definición | F12 | **F12** o ⌘B | F12 | **F12** o Ctrl+B |
| Buscar las referencias | ⇧F12 | **⇧F12** — Buscar usos | Shift+F12 | **Shift+F12** |
| Renombrar un símbolo | F2 | **F2** o ⌃R | F2 | **F2** o Ctrl+R |
| Corrección rápida | ⌘. | **⌘.** o ⌃↩ | Ctrl+. | **Alt+Enter** |
| Ir a una línea | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Volver atrás / adelante | ⌃- / ⌃⇧- | **⌃- / ⌃⇧-** | Alt+← / Alt+→ | **Alt+← / Alt+→** |
| Comentar o descomentar la línea | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Mostrar sugerencias | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Añadir la siguiente aparición a la selección | ⌘D | **⌘D** o ⌘J | Ctrl+D | **Ctrl+D** o Ctrl+J |
| Seleccionar todas las apariciones | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Añadir un cursor arriba / abajo | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Subir / bajar la línea | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Copiar la línea abajo | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Borrar la línea | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Sangrar la línea | ⌘] | **⌘]** | Ctrl+] | **Alt+Shift+→** |
| Reemplazar | ⌥⌘F | **⌥⌘F** o ⌘R | Ctrl+H | **Ctrl+H** |
| Dar formato al documento | ⇧⌥F | **⇧⌥F** o ⌃⇧F | Shift+Alt+F | **Alt+Shift+F** |
| Cerrar la pestaña del editor | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| El panel de problemas | ⇧⌘M | **⌘6** — Elementos de acción (aquí ⇧⌘M pone o quita un marcador) | Ctrl+Shift+M | **Ctrl+6** |
| Poner o quitar un punto de interrupción | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| Empezar a depurar | F5 | **⇧⌘F5** — Depurar el archivo | F5 | **Ctrl+Shift+F5** |
| Ejecutar sin depurar | ⌃F5 | **F6** — Ejecutar Proyecto | Ctrl+F5 | **F6** |
| Ajustes | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | Herramientas ▸ Opciones (sin atajo) |

En macOS, ⌘, es el del propio menú de la app; cada uno de los demás atajos
de NMOX de la tabla se leyó del teclado que se distribuye, no de memoria.
Algunas cosas que la tabla no puede decir en una celda:

- **F5 está ocupada mientras depuras.** Aquí significa *Continuar*, como en
  todos los IDE de la familia NetBeans, así que una ejecución de depuración
  empieza con **⇧⌘F5** (Ctrl+Shift+F5) y se reanuda con F5.
- **⌃R aquí es Renombrar**, y por eso *Cambiar de proyecto* vive en ⌥⌘P en
  lugar del atajo de Abrir reciente de VS Code. Renombrar funciona donde lo
  admite el lenguaje del archivo.
- **Ctrl+, en Windows y Linux** retrocede por tu historial de edición, como
  siempre ha hecho en NetBeans; los ajustes están en
  Herramientas ▸ Opciones (en macOS, en **Settings…** del menú de la app, ⌘,).

**Ayuda ▸ Atajos de teclado…** enumera cada atajo de NMOX de tu perfil de
teclado activo, incluidos los cuatro de VS Code, leídos del teclado en
marcha, así que no pueden desviarse de lo que hacen las teclas.

### Cada atajo de edición, medido

Los atajos que buscan tus manos de VS Code mientras editas, cada uno
consultado en el teclado que se distribuye, con el perfil por omisión en
macOS. Donde el atajo de VS Code estaba libre aquí, ahora hace lo que hace
VS Code (las filas que dicen **Lo mismo:**); donde ya significaba algo de
lo que dependen los usuarios de NetBeans, conserva ese significado y la
fila dice dónde vive la acción de VS Code.

| VS Code, macOS | Lo que hace VS Code | En NMOX Studio |
|---|---|---|
| F12 | Go to Definition | **Lo mismo:** Ir a la declaración, como hace ⌘B |
| ⇧F12 | Go to References | **Lo mismo:** Buscar usos, como hace ⌃F7 |
| F2 | Rename Symbol | **Lo mismo:** Renombrar, como hace ⌃R |
| ⌘. | Quick Fix | **Lo mismo:** las correcciones de la línea, como las muestra ⌃↩ |
| ⌥↑ / ⌥↓ | Move Line Up / Down | Aparición marcada anterior / siguiente; subir o bajar la línea es ⌃⇧↑ / ⌃⇧↓ |
| ⇧⌥↑ / ⇧⌥↓ | Copy Line Up / Down | Lo mismo, como siempre |
| ⇧⌘K | Delete Line | Insertar la siguiente palabra coincidente (completa la palabra a partir del archivo); borrar la línea es ⌘E |
| ⌘L | Expand Line Selection | Seleccionar el identificador; seleccionar la línea no tiene atajo |
| ⇧⌘L | Select All Occurrences | Pegar como líneas en el editor; seleccionar todas las apariciones es ⌃⇧⌘J |
| ⌘/ | Toggle Line Comment | Lo mismo, como siempre |
| ⇧⌥A | Toggle Block Comment | Nada: no hay una acción aparte para comentarios de bloque, y ⌘/ pone o quita el comentario |
| ⌘] | Indent Line | **Lo mismo:** Desplazar a la derecha |
| ⌘[ | Outdent Line | Saltar a la llave correspondiente, como siempre; quitar sangría es ⇧Tab o ⌃⇧← |
| ⌘B | Toggle Sidebar | Ir a la declaración; ⇧⌘↩ muestra solo el editor, ⇧Esc maximiza la ventana en la que estás |
| ⌘J | Toggle Panel | Añade la siguiente aparición en el editor (como ⌘D); la ventana Output es ⌘4 |
| ⌘\ | Split Editor | Completar código en el editor; dividir el editor es ⌃⇧⌘V |
| ⇧⌘T | Reopen Closed Editor | Lo mismo, como siempre: el atajo de Abrir archivo reciente vuelve a abrir el último archivo cerrado |
| ⌃- / ⌃⇧- | Go Back / Go Forward | **Lo mismo:** Atrás y Adelante por los sitios donde has estado editando, como ⌃← / ⌃→ (atajos que macOS suele reservar para cambiar de escritorio) |
| ⌘G / ⇧⌘G | Find Next / Previous | Lo mismo, como siempre |
| ⌥⌘F | Replace | **Lo mismo:** Reemplazar, como hace ⌘R |
| ⇧⌘F | Find in Files | Lo mismo, como siempre: Buscar en los proyectos |
| ⇧⌘O | Go to Symbol in Editor | Abrir proyecto; los símbolos del archivo están en el Navegador (⌘7) |
| ⌘T | Go to Symbol in Workspace | En el editor intercambia las dos letras junto al cursor; los símbolos del proyecto son ⌥⇧⌘O |
| ⌃G | Go to Line | Lo mismo, como siempre |
| ⌘K ⌘S | Keyboard Shortcuts | ⌘K es Insertar la palabra coincidente anterior; la hoja es **Ayuda ▸ Atajos de teclado…** |
| ⌘, | Settings | Lo mismo, como siempre: NMOX Studio ▸ Settings… |
| ⇧⌥F | Format Document | **Lo mismo:** Formatear, como hace ⌃⇧F |

Los atajos marcados **Lo mismo:** están en todos los perfiles de teclado
que los dejan libres, y un perfil que da a alguno de ellos su propio
significado lo conserva: F12 en los perfiles Eclipse, Emacs y NetBeans
5.5, F2 en todos los perfiles menos el por omisión, ⇧F12 en Emacs y
NetBeans 5.5, ⌃- y ⌃⇧- en Emacs e IntelliJ, ⇧⌥F en IntelliJ. En Windows y
Linux, F12, ⇧F12 y F2 funcionan igual; los demás atajos de VS Code son
distintos allí, y la tabla de más arriba da los dos.


<a id="from-the-terminal"></a>
## Desde la terminal

`code .` es `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

Vuelve al instante, y un segundo `nmox` le pasa su carpeta al IDE que ya
está en marcha. Se admite una columna (`src/app.ts:42:7`) y el editor se
abre al principio de la línea; un nombre que no existe se rechaza en la
terminal en lugar de arrancar nada. Se admite `-r`, `-n` abre en la única
ventana, y `-a` y `-v` se rechazan nombrándolas.

`-w` (`--wait`) abre un archivo y espera hasta que cierres su pestaña, y `-d`
(`--diff`) compara dos archivos lado a lado, así que NMOX Studio puede ser el
editor y el difftool de git, igual que `code --wait`:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

`git commit` abre entonces el mensaje en el IDE; guárdalo, cierra la pestaña
y git sigue su camino. Salir del IDE con un archivo todavía abierto también
lo devuelve, con lo que se haya guardado.
**Equipo ▸ Usar NMOX Studio con Git…** configura esas mismas líneas por ti,
después de mostrarte qué valor tiene ahora cada una.
Homebrew, el instalador de Windows
(*Añadir «nmox» al PATH*) y los paquetes de Linux lo ponen en tu PATH; para
una instalación desde el DMG, la [guía del usuario](user-guide.es.md#2-first-launch)
muestra el enlace de una línea.

<a id="where-each-vs-code-idea-lives"></a>
## Dónde vive cada idea de VS Code

| En VS Code | En NMOX Studio |
|---|---|
| **Explorer** | El **Estudio de proyecto** (⇧⌘E): el árbol de archivos (clic derecho en un archivo para Copiar ruta, Copiar ruta relativa y Mostrar en Finder), las plantillas y el editor del `package.json` del proyecto. El **Banco de trabajo** (⌥⌘0) es la base: archivos abiertos, archivos recientes, proyectos recientes y todo lo que está en marcha. |
| **Command Palette** | La **Búsqueda rápida** (⇧⌘P o ⌘I): acciones, archivos, proyectos recientes, dispositivos del rack, servidores activos, peticiones del Estudio de API, símbolos. Los nombres de los comandos del propio VS Code también funcionan: *Format Document*, *Toggle Terminal*, *Git: Commit* u *Open Settings* enumeran la acción que hace lo mismo aquí, bajo **Comandos de VS Code**, con su propio nombre y su atajo. |
| **Extensions** | **Herramientas ▸ Complementos** instala y actualiza módulos, incluidas las propias actualizaciones de NMOX. Mucho de lo que una extensión añade en VS Code es aquí un **dispositivo del rack**, y puedes escribir uno como un archivo JSON en `~/.nmox/devices.d` ([archivos de dispositivo](device-files.md)). |
| **`tasks.json`** | Se lee el `.vscode/tasks.json` de tu repositorio: escribe el nombre de una tarea en la Búsqueda rápida (⇧⌘P o ⌘I) y Entrar sobre *Ejecutar tarea: build — make all* la ejecuta, con la confianza del espacio de trabajo preguntando antes en un proyecto en el que no has confiado, su salida en la ventana Output y el ■ de la barra de herramientas para detenerla. A su lado, los propios scripts del proyecto se ejecutan tal como están escritos: Ejecutar / Compilar / Probar de la barra de herramientas (F6, F11, ⌃F6), **Ejecutar script** en una línea de `scripts` del `package.json`, el **Explorador de NPM** y el **Rack de tareas** (⌘9), donde las tareas son dispositivos que cableas entre sí. |
| **`launch.json`** | Se lee el `.vscode/launch.json` de tu repositorio: escribe el nombre de una configuración en la Búsqueda rápida (⇧⌘P o ⌘I) y Entrar sobre *Depurar: Launch Program — ${workspaceFolder}/server.js* arranca el depurador de puntos de interrupción sobre ese programa, con la confianza del espacio de trabajo preguntando antes. Las configuraciones de Node (`node`, `pwa-node`) y de Python (`python`, `debugpy`) depuran su `program` en su `cwd`, con sus `args` y su `env`; las de Chrome (`chrome`, `pwa-chrome`) abren su `url` (o su `file`) con su `webRoot`. Sin un `launch.json`, **Depurar el archivo** (⇧⌘F5) y el botón de depurar de la barra de herramientas deducen qué lanzar a partir del propio proyecto —la entrada del script `start`, `main`, `index.js`—, y el dispositivo del rack **INSPECTOR** lanza un depurador como un paso de una tubería. |
| **Integrated terminal** | La ventana **Terminal** (⌃\`): la primera pulsación arranca una consola en la carpeta del proyecto y las siguientes la traen de vuelta. |
| **`settings.json`** | Herramientas ▸ Opciones (en macOS, NMOX Studio ▸ Settings…). El `.vscode/settings.json` de un repositorio también se lee: `editor.tabSize`, `editor.insertSpaces` y `editor.indentSize` fijan su sangría mientras escribes, `files.trimTrailingWhitespace` y `files.insertFinalNewline` (cuando valen `true`) se aplican al guardar, y un bloque de lenguaje como `"[typescript]"` los sustituye para su lenguaje. Donde el repositorio tiene además un `.editorconfig`, el `.editorconfig` gana dondequiera que ambos digan algo. |
| **Problems panel** | **Elementos de acción** (⌘6), o haz clic en el recuento **✕ ⚠** de la barra de estado: los errores y avisos de los servidores de lenguaje, y lo que encuentran los dispositivos PURITY y TYPEGUARD del rack en lint y tipos. Como en VS Code, algunos servidores solo informan de los archivos que tienes abiertos; gopls informa de todo el paquete. |
| **Outline** | El **Navegador** (⌘7). |
| **Source Control** | El indicador de git de la barra de estado (rama y cambios, un clic hasta el historial) y el menú **Equipo**. |
| **Workspace Trust** | La misma idea, aplicada antes de ejecutar nada que haya elegido un repositorio: abrir un proyecto clonado no ejecuta nada hasta que confías en él (**Confianza del espacio de trabajo**). |
| **Keyboard Shortcuts editor** | Herramientas ▸ Opciones ▸ Atajos de teclado (en macOS, Settings… ▸ Atajos de teclado): cambia cualquier atajo, o pasa el perfil entero a Eclipse, Emacs o IntelliJ. |

La primera vez que abres un repositorio que trae `.vscode/tasks.json`,
`launch.json` o `settings.json`, un aviso dice qué se encontró y dónde
vive; haz clic en él para abrir la Búsqueda rápida. Lo dice una vez por
proyecto.

<a id="what-is-honestly-different"></a>
## Lo que, honestamente, es distinto

- **⌘D añade la siguiente aparición en el perfil de teclado por omisión,
  no en todos los perfiles.** El perfil Eclipse conserva ⌘D como el
  *Delete Line* de Eclipse, el perfil NetBeans 5.5 como *Shift Line Left*
  y el perfil Emacs como *kill word* (y Ctrl+D como *delete character* en
  Windows y Linux); el perfil IntelliJ tiene ⌘D en macOS y conserva Ctrl+D
  como *Duplicate Line* en Windows y Linux. El otro atajo del gesto
  también cambia según el perfil: ⌘J (Ctrl+J) en el por omisión, ⌃J
  (Alt+J) en Eclipse e IntelliJ, y ninguno en Emacs y NetBeans 5.5, donde
  Atajos de teclado puede darle uno.
- **⌃\` abre la Terminal y le da el foco; no la oculta.** Y mientras la
  Terminal tiene el foco, las teclas son de tu consola, así que la segunda
  pulsación llega a la consola en vez de devolverte al editor.
- **`launch.json` se lee, y lo que el depurador no puede respetar se
  rechaza.** Aquí el depurador pasa un programa, su carpeta de trabajo,
  sus `args` (una lista de cadenas) y su `env` (cadenas que se añaden al
  entorno heredado), así que una configuración que fija `envFile`,
  `runtimeExecutable`, `runtimeArgs`, `preLaunchTask` o cualquier otro
  campo que no se le ha enseñado aparece en la lista pero no arranca:
  Entrar nombra los campos en la barra de estado. Arrancar el programa sin
  ellos depuraría algo distinto de lo que dice el archivo. Unos `args`
  escritos como una sola cadena (VS Code se la pasa a una consola) y un
  valor de `env` que sea `null` (que elimina una variable) se rechazan del
  mismo modo, y también `"request": "attach"`, una entrada de
  `compounds`, un tipo sin adaptador aquí (`go`, `msedge`, `cppdbg` y los
  demás), un valor que solo VS Code puede proporcionar (`${file}`,
  `${input:…}`) y una ruta fuera del proyecto. Los campos que solo dan
  forma a lo que muestra el depurador —`skipFiles`, `outFiles`,
  `sourceMaps`, `console`, `justMyCode`, `presentation`— se aceptan y no
  se aplican; la salida del programa va a la ventana Output.
- **`tasks.json` se lee, y lo que no puede ejecutarse tal como está
  escrito se rechaza.** Una tarea que usa un valor
  que solo VS Code puede proporcionar (`${input:…}`, `${file}`,
  `${config:…}`, `${command:…}`) o que tiene un `dependsOn` hacia otra
  tarea aparece en la lista pero no se ejecuta: Entrar dice qué variable o
  qué tarea en la barra de estado. Ejecutarla con el valor en blanco, o
  sin la tarea de la que depende, ejecutaría algo distinto de lo que dice
  el archivo. Lo mismo haría un tipo de tarea que aporta una extensión
  (`gulp`, `typescript`) y una carpeta de trabajo fuera del proyecto.
- **Una tarea `"type": "shell"` se ejecuta en la consola que usaría VS
  Code.** En macOS y Linux es tu `$SHELL` con `-c` (un zsh, bash o fish
  de macOS arranca como consola de inicio de sesión, `-l`, igual que los
  perfiles por omisión de VS Code); en Windows es PowerShell, `pwsh` si
  está instalado. `options.shell` se respeta a la manera de VS Code: nombra
  un `executable` y se ejecuta exactamente con los `args` que des, así que
  un bash necesita `"args": ["-c"]`. En Windows solo se ejecutan
  PowerShell (con args que terminan en `-Command`) y `cmd.exe` (con args
  que terminan en `/c`); cualquier otra consola allí se rechaza por su
  nombre en lugar de recibir una línea de órdenes entrecomillada a ojo.
- **No hay un perfil de teclado «VS Code».** Los atajos de arriba viajan en
  el perfil por omisión y en los otros cuatro. Una excepción deliberada: en
  el perfil **Eclipse**, ⇧⌘E sigue siendo el *Switch to Editor* del propio
  Eclipse, y dentro del editor ⇧⌘P y ⇧⌘X conservan los significados de
  Eclipse (llave correspondiente, mayúsculas): quien eligió Eclipse espera
  Eclipse.
- **En Linux, Ctrl+\` abre la Terminal, no un selector de ventanas.** El
  selector está en Ctrl+Tab. En un escritorio que se queda Ctrl+Tab para
  sí (KDE, por ejemplo), **Ventana ▸ Documentos…** enumera en su lugar los
  archivos abiertos.
- **Los atajos con Ctrl+Alt pueden chocar con AltGr.** En Windows, las
  distribuciones de teclado que escriben caracteres con AltGr (la polaca,
  por ejemplo) envían Ctrl+Alt para ello. Si Ctrl+Alt+P o Ctrl+Alt+K te
  escribe un carácter, mueve *Cambiar de proyecto* o los atajos de
  experimentos en Atajos de teclado.
- **Las extensiones de VS Code no se instalan aquí.** La inteligencia de
  lenguaje viene de los servidores de lenguaje que NMOX conoce (el Doctor
  del entorno enumera lo que falta y cómo instalarlo), de las gramáticas
  propias del editor y de los complementos hechos para la plataforma
  NetBeans.
