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
| Comentar o descomentar en bloque | ⇧⌥A | **⇧⌥A** | Shift+Alt+A (Ctrl+Shift+A en Linux) | **Shift+Alt+A** (y Ctrl+Shift+A en Linux) |
| Activar o desactivar el ajuste de línea | ⌥Z | **⌥Z** — Ver ▸ Ajuste de línea | Alt+Z | **Alt+Z** |
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
| Copiar la ruta del archivo que estás editando | ⌥⌘C | **⌥⌘C** — Edición ▸ Copiar ruta | Shift+Alt+C | **Ctrl+Alt+C** |
| Copiar su ruta relativa | ⇧⌥⌘C | Edición ▸ Copiar ruta relativa (sin atajo) | Ctrl+K Ctrl+Shift+Alt+C | Edición ▸ Copiar ruta relativa (sin atajo) |
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
- **Copiar ruta relativa no tiene atajo.** El ⇧⌥⌘C de VS Code es
  Ctrl+Alt+Shift+C en un PC, que es el *Clear Split* de la plataforma en
  todos los perfiles de teclado; la fila está en el menú Edición, y ⇧⌘P
  la encuentra por el propio título de VS Code, *File: Copy Relative
  Path of Active File*.
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
| ⌘L | Expand Line Selection | Seleccionar el identificador, que conserva el atajo en el perfil de teclado por omisión; Expandir la selección de línea está en la Búsqueda rápida con ese nombre, y en el propio ⌘L en los perfiles de teclado IntelliJ y Emacs, que dejan libre el atajo |
| ⇧⌘L | Select All Occurrences | Pegar como líneas en el editor; seleccionar todas las apariciones es ⌃⇧⌘J |
| ⌘/ | Toggle Line Comment | Lo mismo, como siempre |
| ⇧⌥A | Toggle Block Comment | **Lo mismo:** envuelve la selección, o la línea del cursor, en los delimitadores de bloque del lenguaje, y los vuelve a quitar; una selección que ya contiene un delimitador se rechaza en lugar de romperla |
| ⌘] | Indent Line | **Lo mismo:** Desplazar a la derecha |
| ⌘[ | Outdent Line | Saltar a la llave correspondiente, como siempre; quitar sangría es ⇧Tab o ⌃⇧← |
| ⌥Z | Toggle Word Wrap | **Lo mismo:** Ver ▸ Ajuste de línea. Cambia el ajuste para todos los editores del lenguaje del archivo, no para una sola pestaña, y la elección se guarda |
| ⌘B | Toggle Sidebar | Ir a la declaración; ⇧⌘↩ muestra solo el editor, ⇧Esc maximiza la ventana en la que estás |
| ⌘J | Toggle Panel | Añade la siguiente aparición en el editor (como ⌘D); la ventana Output es ⌘4 |
| ⌘\ | Split Editor | Completar código en el editor; dividir el editor es ⌃⇧⌘V |
| ⇧⌘T | Reopen Closed Editor | Lo mismo, como siempre: el atajo de Abrir archivo reciente vuelve a abrir el último archivo cerrado |
| ⌃- / ⌃⇧- | Go Back / Go Forward | **Lo mismo:** Atrás y Adelante por los sitios donde has estado editando, como ⌃← / ⌃→ (atajos que macOS suele reservar para cambiar de escritorio) |
| ⌘G / ⇧⌘G | Find Next / Previous | Lo mismo, como siempre |
| ⌥⌘F | Replace | **Lo mismo:** Reemplazar, como hace ⌘R |
| ⇧⌘F | Find in Files | Lo mismo, como siempre: Buscar en los proyectos |
| ⇧⌘O | Go to Symbol in Editor | Abrir proyecto; los símbolos del archivo están en la Búsqueda rápida: ⌘I y luego `@name`, como en VS Code (Navegar ▸ Ir al símbolo de este archivo… escribe la `@` por ti), y como árbol en el Navegador (⌘7) |
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

Alternar el comentario de bloque y Alternar el ajuste de línea llevan sus
atajos de VS Code también en Windows y Linux. El ajuste de línea es Alt+Z
en todos los perfiles. Alternar el comentario de bloque es Shift+Alt+A en
los perfiles por omisión, Emacs e IntelliJ (los perfiles Eclipse y
NetBeans 5.5 conservan ahí su propio Alt+Shift+A), y en Linux es además
Ctrl+Shift+A, el atajo de VS Code en Linux, en los cinco. Expandir la
selección de línea es Ctrl+L en Windows y Linux solo en el perfil
IntelliJ; en el perfil Emacs, Ctrl+L sigue siendo allí el *recenter* del
propio Emacs.

Un lenguaje sin comentarios de bloque, como Python, lo dice en la barra
de estado. En un archivo HTML, Vue o Svelte, un bloque `<script>` o
`<style>` se comenta como su propio lenguaje. Sin selección, el atajo
comenta o descomenta la línea; no busca un comentario que simplemente
rodee el cursor, así que, para quitar un comentario de varias líneas,
selecciónalo.

En la Búsqueda rápida, los símbolos del archivo son la categoría
**Símbolos de este archivo**. Escribir solo `@` los enumera desde el
principio del archivo; `m name` (la letra, un espacio y luego el nombre)
busca en esa categoría y en ninguna otra.


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
editor, el difftool y el mergetool de git, igual que `code --wait`:

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
`git mergetool` abre cada archivo con conflictos de la misma manera. Donde
VS Code pone *Accept Current Change | Accept Incoming Change | Accept Both
Changes* encima de un conflicto, NMOX Studio tiñe los dos lados y pone una
advertencia en la línea `<<<<<<<`; la bombilla del margen, o la corrección
rápida con el cursor en esa línea (⌘. en un Mac, Alt+Enter en los demás),
ofrece las mismas tres, cada una una sola edición que se puede deshacer.
Guarda, cierra la pestaña y git pasa al siguiente archivo. Los tintes y las
tres opciones están en cualquier archivo con marcadores de conflicto, con o
sin `git mergetool`.
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
| **Extensions** | **Herramientas ▸ Complementos** instala y actualiza módulos, incluidas las propias actualizaciones de NMOX. Las extensiones de VS Code no se instalan aquí, así que **Herramientas ▸ Extensiones de VS Code recomendadas…** responde a la pregunta que plantea el `.vscode/extensions.json` de un repositorio: para cada extensión que recomienda, qué hace ese trabajo en NMOX Studio —una función integrada, una ventana que puede abrir, un dispositivo del rack, un servidor de lenguaje (y si ese servidor está instalado) o nada—, y de una extensión que no conoce dice que es desconocida en lugar de adivinar. Mucho de lo que una extensión añade en VS Code es aquí un **dispositivo del rack**, y puedes escribir uno como un archivo JSON en `~/.nmox/devices.d` ([archivos de dispositivo](device-files.md)). |
| **`tasks.json`** | Se lee el `.vscode/tasks.json` de tu repositorio: escribe el nombre de una tarea en la Búsqueda rápida (⇧⌘P o ⌘I) y Entrar sobre *Ejecutar tarea: build — make all* la ejecuta —o elígela de la lista que muestra **Ejecutar ▸ Ejecutar tarea…**—, con la confianza del espacio de trabajo preguntando antes en un proyecto en el que no has confiado, su salida en la ventana Output y el ■ de la barra de herramientas para detenerla. Las tareas de su `dependsOn` se ejecutan primero, `${file}` es el archivo abierto en el editor y `${input:…}` te pregunta antes de que arranque nada. A su lado, los propios scripts del proyecto se ejecutan tal como están escritos: Ejecutar / Compilar / Probar de la barra de herramientas (F6, F11, ⌃F6), **Ejecutar script** en una línea de `scripts` del `package.json`, el **Explorador de NPM** y el **Rack de tareas** (⌘9), donde las tareas son dispositivos que cableas entre sí. |
| **`launch.json`** | Se lee el `.vscode/launch.json` de tu repositorio: escribe el nombre de una configuración en la Búsqueda rápida (⇧⌘P o ⌘I) y Entrar sobre *Depurar: Launch Program — ${workspaceFolder}/server.js* arranca el depurador de puntos de interrupción sobre ese programa —**Depurar ▸ Iniciar depuración…** enumera las mismas configuraciones—, con la confianza del espacio de trabajo preguntando antes. Las configuraciones de Node (`node`, `pwa-node`) depuran su `program` en su `cwd`, con sus `args`, su `env` y su `envFile`, bajo su `runtimeExecutable` y sus `runtimeArgs` —así que una configuración de `npm run dev`, de `tsx` o de `--experimental-strip-types` arranca tal como está escrita—, y un `"request": "attach"` de Node se conecta a un proceso `node --inspect` de esta máquina. Las configuraciones de Python (`python`, `debugpy`) depuran su `program` con `args`, `env`, `envFile` y el intérprete que nombra su `python`; las de Chrome (`chrome`, `pwa-chrome`) abren su `url` (o su `file`) con su `webRoot`. `"program": "${file}"` depura el archivo que muestra tu editor, y una `preLaunchTask` que nombra una tarea de tu `tasks.json` se ejecuta primero: el depurador arranca cuando la tarea ha terminado bien. Sin un `launch.json`, **Depurar el archivo** (⇧⌘F5) y el botón de depurar de la barra de herramientas deducen qué lanzar a partir del propio proyecto —la entrada del script `start`, `main`, `index.js`—, y el dispositivo del rack **INSPECTOR** lanza un depurador como un paso de una tubería. |
| **Integrated terminal** | La ventana **Terminal** (⌃\`): la primera pulsación arranca una consola en la carpeta del proyecto y las siguientes la traen de vuelta. |
| **`settings.json`** | Herramientas ▸ Opciones (en macOS, NMOX Studio ▸ Settings…). El `.vscode/settings.json` de un repositorio también se lee: `editor.tabSize`, `editor.insertSpaces` y `editor.indentSize` fijan su sangría mientras escribes, `files.trimTrailingWhitespace` y `files.insertFinalNewline` (cuando valen `true`) se aplican al guardar, `files.eol` es el final de línea con el que se escriben los archivos, `"editor.formatOnSave": false` impide que guardar reformatee el archivo, y un bloque de lenguaje como `"[typescript]"` los sustituye para su lenguaje. Donde el repositorio tiene además un `.editorconfig`, el `.editorconfig` gana dondequiera que ambos digan algo. |
| **Problems panel** | **Elementos de acción** (⌘6), o haz clic en el recuento **✕ ⚠** de la barra de estado: los errores y avisos de los servidores de lenguaje, y lo que encuentran los dispositivos PURITY y TYPEGUARD del rack en lint y tipos. Como en VS Code, algunos servidores solo informan de los archivos que tienes abiertos; gopls informa de todo el paquete. |
| **Search view** (`search.useIgnoreFiles`) | **Buscar en los proyectos** (⇧⌘F). Como en VS Code, se salta lo que ignoran los archivos `.gitignore` del repositorio y `.git/info/exclude`, así que `node_modules` y `dist/` quedan fuera de los resultados cuando el `.gitignore` los incluye; fuera de un repositorio se salta por su nombre `node_modules`, `dist`, `build` y las demás carpetas de compilación. Marca **Buscar en fuentes generadas** en su diálogo para buscar también en ellas. Tu archivo global de exclusiones de git no se lee. |
| **Outline** | El **Navegador** (⌘7). |
| **Snippets** (`.vscode/*.code-snippets`) | Los archivos de fragmentos que tu equipo ha confirmado en el repositorio se leen tal como están: escribe un prefijo, pulsa ⌃Space y el fragmento aparece como *prefijo — Nombre (descripción)* con su archivo al lado, limitado a los lenguajes que nombra su `scope`. Al aceptarlo se inserta el cuerpo con sus paradas de tabulación, sus espejos, sus variables (`TM_FILENAME`, `CURRENT_YEAR`, `UUID` y las demás) y sus transformaciones `/regex/format/`; Tab recorre las paradas, y Entrar avanza y, tras la última, deja el cursor en `$0`. |
| **Auto Save** (`files.autoSave`) | **Archivo ▸ Guardado automático** lo activa y lo desactiva; cada cuánto guarda, y si guarda también cuando un archivo pierde el foco, se decide en la pestaña Guardado automático de la categoría Editor de los ajustes. Es una preferencia tuya: el `files.autoSave` de un repositorio no se lee. |
| **Markdown preview** | La pestaña **Vista previa** en la parte superior de un editor de Markdown, junto a **Código fuente**. |
| **Timeline** (historial local) | La pestaña **Historial** en la parte superior de cada editor: las versiones del archivo que el IDE conservó a medida que guardabas, cada una comparable con el archivo tal como está ahora y restaurable. |
| **Breadcrumbs** | **Ver ▸ Mostrar la ruta de navegación**. |
| **Source Control** | El indicador de git de la barra de estado (rama y cambios, un clic hasta el historial) y el menú **Equipo**. |
| **Workspace Trust** | La misma idea, aplicada antes de ejecutar nada que haya elegido un repositorio: abrir un proyecto clonado no ejecuta nada hasta que confías en él (**Confianza del espacio de trabajo**). |
| **Keyboard Shortcuts editor** | Herramientas ▸ Opciones ▸ Atajos de teclado (en macOS, Settings… ▸ Atajos de teclado): cambia cualquier atajo, o pasa el perfil entero a Eclipse, Emacs o IntelliJ. |

La primera vez que abres un repositorio que trae `.vscode/tasks.json`,
`launch.json`, `settings.json` o `extensions.json`, un aviso dice qué se
encontró y dónde vive; haz clic en él para abrir la Búsqueda rápida (o,
en el caso de las extensiones, la hoja que dice qué cubre cada una). Lo
dice una vez por proyecto.

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
  sus `args` (una lista de cadenas), su `env` y su `envFile`, y el entorno
  de ejecución que lo arranca (`runtimeExecutable` y `runtimeArgs` para
  Node, `python` para Python); en Node, además, se conecta a un proceso
  que ya está en marcha. Una configuración que fija `postDebugTask`,
  `restart` o cualquier otro campo que no se le ha enseñado aparece en la
  lista pero no arranca: Entrar nombra los campos en la barra de estado.
  Arrancar el programa sin ellos depuraría algo distinto de lo que dice el
  archivo. Unos `args` o unos `runtimeArgs` escritos como una sola cadena
  (VS Code se la pasa a una consola) y un valor de `env` que sea `null`
  (que elimina una variable) se rechazan del mismo modo, y también una
  entrada de `compounds`, un tipo sin adaptador aquí (`go`, `msedge`,
  `cppdbg` y los demás), una conexión (attach) a cualquier cosa que no sea
  Node, un valor que solo VS Code puede proporcionar (`${input:…}`,
  `${command:…}`) y un programa, una carpeta de trabajo o un `envFile`
  fuera del proyecto. Los campos que solo dan forma a lo que muestra el
  depurador —`skipFiles`, `outFiles`, `sourceMaps`, `console`,
  `justMyCode`, `presentation`— se aceptan y no se aplican; la salida del
  programa va a la ventana Output. Conviene saber cinco cosas antes de
  pulsar Entrar:
  - **Una `preLaunchTask` se ejecuta primero, y tiene que terminar bien.**
    La tarea se ejecuta como la ejecutaría Entrar sobre ella en la
    Búsqueda rápida —las tareas de su `dependsOn`, sus preguntas, su
    propia pestaña de Output— y el depurador arranca cuando ha terminado
    con el código de salida 0; donde VS Code pregunta *Debug Anyway?* tras
    una tarea fallida, aquí la barra de estado dice que la configuración
    no se inició. Un `program` que la tarea compila (`dist/server.js`) se
    busca después de la tarea, no antes. Una etiqueta que `tasks.json` no
    define, una etiqueta que comparten dos tareas, la forma de objeto
    (`{"type": "npm", "script": "build"}`) y una tarea en segundo plano
    (`"isBackground": true`, un vigilante que nunca termina) se rechazan
    por su nombre antes de que se ejecute nada.
  - **Un `envFile` que no existe se rechaza**, donde VS Code arranca el
    programa sin él. Sus variables se añaden al entorno y una entrada de
    `env` gana al archivo, como en VS Code. El archivo se lee como simples
    líneas `NAME=value` (las líneas de comentario, `export` y un par de
    comillas alrededor de un valor se admiten); una línea que VS Code
    leería de otra manera —un escape dentro de comillas dobles, un `#`
    después de un valor, un acento grave y, en una configuración de
    Python, un `export` o un `${NAME}`— se rechaza nombrando el archivo y
    el número de línea, nunca el valor.
  - **`${file}`, `${relativeFile}`, `${fileBasename}`,
    `${fileBasenameNoExtension}`, `${fileExtname}`, `${fileDirname}` y las
    demás variables de archivo significan el archivo del editor activo**:
    la pestaña que tiene el foco cuando es un editor y, si no, la pestaña
    que se muestra en el área del editor; el mismo archivo que significa
    el `${file}` de una tarea. Sin ningún archivo abierto, la
    configuración se rechaza nombrando la variable, y un `${file}` fuera
    del proyecto se rechaza como cualquier otro programa que esté ahí.
  - **Un `runtimeExecutable` es un nombre o una ruta absoluta.** Un nombre
    (`npm`, `tsx`, `nodemon`) se busca en tu PATH y luego en el
    `node_modules/.bin` del proyecto; `${workspaceFolder}/node_modules/.bin/tsx`
    tiene que existir; una ruta relativa se rechaza, porque VS Code la
    buscaría como un nombre. Cuando el entorno de ejecución es la orden
    entera (`npm run dev` sin `program`), el script que arranca se depura
    como una sesión propia, que aparece en la ventana Sessions.
  - **Una conexión (attach) es a esta máquina.** `port` (9229 si no se
    escribe) y un `address` que sea `localhost`, `127.0.0.1` o `::1`;
    cualquier otra dirección se rechaza, porque esto no es desarrollo
    remoto. Si no hay nada escuchando, la barra de estado lo dice, y
    terminar la sesión deja tu programa en marcha.
- **`tasks.json` se lee, y lo que no puede ejecutarse tal como está
  escrito se rechaza.** Una tarea se ejecuta con las tareas de su
  `dependsOn` por delante (todas a la vez, o una tras otra con
  `"dependsOrder": "sequence"`), con `${file}`, `${relativeFile}`,
  `${lineNumber}`, `${selectedText}` y el resto de esa familia rellenados
  a partir del archivo abierto en el editor, y con las preguntas de sus
  `${input:…}` (`promptString`, `pickString`) hechas antes de que arranque
  nada. Toda la ejecución se decide primero: si una de sus tareas no puede
  ejecutarse tal como está escrita, no se ejecuta nada, y Entrar dice qué
  tarea y por qué en la barra de estado. Eso abarca una etiqueta de
  `dependsOn` que el archivo no define, una dependencia que es una tarea
  en segundo plano (los *problem matchers* no se leen, así que nada dice
  cuándo está lista), `${file}` sin ningún archivo abierto y una pregunta
  que cancelas. Una dependencia que falla detiene la ejecución ahí. Se
  siguen rechazando por su nombre: un valor que solo VS Code puede
  proporcionar (`${config:…}`, `${command:…}`, un input de
  `"type": "command"`), una dependencia escrita como un objeto
  (`{"type": "npm", …}`) en lugar de una etiqueta, un tipo de tarea que
  aporta una extensión (`gulp`, `typescript`) y una carpeta de trabajo
  fuera del proyecto.
- **Los fragmentos (snippets) son los de tu repositorio, y tres cosas son
  distintas.** Una elección (`${1|a,b|}`) empieza con su primera opción,
  sin lista en la que elegir; un marcador anidado dentro del valor por
  omisión de otro pasa a formar parte de ese valor; y un fragmento cuya
  transformación no se puede ejecutar con seguridad (una expresión regular
  que Java no puede compilar, o una que podría no terminar nunca) se deja
  fuera y se nombra en el registro en lugar de insertarse a medias.
  Escribir un prefijo no abre la lista por sí solo: lo hace ⌃Space. Los
  fragmentos sin `prefix`, los fragmentos de usuario y los
  `isFileTemplate` no se leen.
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
