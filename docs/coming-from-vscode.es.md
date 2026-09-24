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
| Ir a la definición | F12 | **⌘B** | F12 | **Ctrl+B** |
| Renombrar un símbolo | F2 | **⌃R** | F2 | **Ctrl+R** |
| Ir a una línea | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Comentar o descomentar la línea | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Mostrar sugerencias | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Añadir la siguiente aparición a la selección | ⌘D | **⌘J** | Ctrl+D | **Ctrl+J** |
| Seleccionar todas las apariciones | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Añadir un cursor arriba / abajo | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Subir / bajar la línea | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Copiar la línea abajo | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Borrar la línea | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Dar formato al documento | ⇧⌥F | **⌃⇧F** | Shift+Alt+F | **Alt+Shift+F** |
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


<a id="from-the-terminal"></a>
## Desde la terminal

`code .` es `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox            # just start the IDE
```

Vuelve al instante, y un segundo `nmox` le pasa su carpeta al IDE que ya
está en marcha. Homebrew, el instalador de Windows (*Añadir «nmox» al
PATH*) y los paquetes de Linux lo ponen en tu PATH; para una instalación
desde el DMG, la [guía del usuario](user-guide.es.md#2-first-launch)
muestra el enlace de una línea.

<a id="where-each-vs-code-idea-lives"></a>
## Dónde vive cada idea de VS Code

| En VS Code | En NMOX Studio |
|---|---|
| **Explorer** | El **Estudio de proyecto** (⇧⌘E): el árbol de archivos, las plantillas y el editor del `package.json` del proyecto. El **Banco de trabajo** (⌥⌘0) es la base: archivos abiertos, archivos recientes, proyectos recientes y todo lo que está en marcha. |
| **Command Palette** | La **Búsqueda rápida** (⇧⌘P o ⌘I): acciones, archivos, proyectos recientes, dispositivos del rack, servidores activos, peticiones del Estudio de API, símbolos. |
| **Extensions** | **Herramientas ▸ Complementos** instala y actualiza módulos, incluidas las propias actualizaciones de NMOX. Mucho de lo que una extensión añade en VS Code es aquí un **dispositivo del rack**, y puedes escribir uno como un archivo JSON en `~/.nmox/devices.d` ([archivos de dispositivo](device-files.md)). |
| **`tasks.json`** | Los propios scripts de tu proyecto, ejecutados tal como están escritos: Ejecutar / Compilar / Probar de la barra de herramientas (F6, F11, ⌃F6), **Ejecutar script** en una línea de `scripts` del `package.json`, el **Explorador de NPM** y el **Rack de tareas** (⌘9), donde las tareas son dispositivos que cableas entre sí. |
| **`launch.json`** | **Depurar el archivo** (⇧⌘F5) y el botón de depurar de la barra de herramientas deducen qué lanzar a partir del propio proyecto —la entrada del script `start`, `main`, `index.js`—, y el dispositivo del rack **INSPECTOR** lanza un depurador como un paso de una tubería. |
| **Integrated terminal** | La ventana **Terminal** (⌃\`): la primera pulsación arranca una consola en la carpeta del proyecto y las siguientes la traen de vuelta. |
| **`settings.json`** | Herramientas ▸ Opciones (en macOS, NMOX Studio ▸ Settings…). El `.editorconfig` de tu proyecto se aplica mientras escribes y al guardar. |
| **Problems panel** | **Elementos de acción** (⌘6): los errores y avisos de los servidores de lenguaje, y lo que encuentran los dispositivos PURITY y TYPEGUARD del rack en lint y tipos. Como en VS Code, algunos servidores solo informan de los archivos que tienes abiertos; gopls informa de todo el paquete. |
| **Outline** | El **Navegador** (⌘7). |
| **Source Control** | El indicador de git de la barra de estado (rama y cambios, un clic hasta el historial) y el menú **Equipo**. |
| **Workspace Trust** | La misma idea, aplicada antes de ejecutar nada que haya elegido un repositorio: abrir un proyecto clonado no ejecuta nada hasta que confías en él (**Confianza del espacio de trabajo**). |
| **Keyboard Shortcuts editor** | Herramientas ▸ Opciones ▸ Atajos de teclado (en macOS, Settings… ▸ Atajos de teclado): cambia cualquier atajo, o pasa el perfil entero a Eclipse, Emacs o IntelliJ. |

<a id="what-is-honestly-different"></a>
## Lo que, honestamente, es distinto

- **⌘D no es multicursor aquí.** El mismo gesto es **⌘J** (Ctrl+J); ⌘D en
  sí no tiene nada asignado. Reasígnalo en Atajos de teclado si tus dedos
  insisten.
- **⌃\` abre la Terminal y le da el foco; no la oculta.** Y mientras la
  Terminal tiene el foco, las teclas son de tu consola, así que la segunda
  pulsación llega a la consola en vez de devolverte al editor.
- **No se leen `.vscode/tasks.json` ni `launch.json`.** Una tarea es una
  orden que eligió un repositorio, y leerla merece su propio diseño en torno
  a la confianza del espacio de trabajo; hasta entonces, los propios scripts
  del proyecto y las reglas de entrada de depuración de arriba hacen ese
  trabajo.
- **No hay un perfil de teclado «VS Code».** Los atajos de arriba viajan en
  el perfil por omisión y en los otros cuatro. Una excepción deliberada: en
  el perfil **Eclipse**, ⇧⌘E sigue siendo el *Switch to Editor* del propio
  Eclipse, y dentro del editor ⇧⌘P y ⇧⌘X conservan los significados de
  Eclipse (llave correspondiente, mayúsculas): quien eligió Eclipse espera
  Eclipse.
- **En Linux, Ctrl+\` abre la Terminal, no un selector de ventanas.** La
  plataforma tenía ahí un segundo selector para los escritorios (KDE) que se
  quedan con Ctrl+Tab; el selector está en Ctrl+Tab.
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
