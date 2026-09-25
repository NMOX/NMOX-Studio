# La segunda semana

<!-- languages -->
[English](the-second-week.md) · **Español** · [Français](the-second-week.fr.md) · [Deutsch](the-second-week.de.md) · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · [Polski](the-second-week.pl.md) · [Português (Brasil)](the-second-week.pt.md) · [Bahasa Indonesia](the-second-week.id.md) · [Filipino](the-second-week.tl.md) · [Tiếng Việt](the-second-week.vi.md) · [简体中文](the-second-week.zh.md) · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*Confirmar, revisar, resolver, proponer — sin irte a otra herramienta.*

La primera hora es abrir un proyecto y ejecutarlo. La segunda semana es
todo lo que rodea al código: veinte commits al día, un diff que leer antes
de cada uno, un conflicto después de un pull, una pull request después de
un push, una traza de pila que perseguir, un README que mantener honesto.
Este recorrido es una sola sesión en un repositorio git que ya tienes, y
cada paso es algo que volverás a hacer mañana.

## 1. Haz de NMOX Studio el editor de git

**Haz:** Equipo ▸ **Usar NMOX Studio con Git…**

**Verás:** los seis ajustes globales de git que hacen de NMOX Studio el
editor, la difftool y la mergetool de git, cada uno junto al valor que
tiene **ahora**, para que nada se sustituya sin que lo veas. **Aplicar**
los establece (**Cerrar** es el botón predeterminado, porque esto escribe
tu configuración global de git); **Copiar comandos** pone en cambio las
líneas `git config` en el portapapeles. Cuando git ya usa NMOX Studio, el
diálogo lo dice y no ofrece Aplicar.

Las mismas líneas, si prefieres una terminal:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. Confirma

**Haz:** cambia un archivo y luego, en una terminal:

```bash
git commit -a
```

**Verás:** el mensaje de commit se abre en NMOX Studio, y la línea de
estado dice que una terminal lo está esperando. Las líneas `#` de git son
comentarios; solo se revisa la ortografía de lo que tú escribes; una línea
de resumen de más de 72 caracteres, donde las propias herramientas de git
la cortan, recibe un aviso a partir del carácter 73. Guarda, cierra la
pestaña y el commit queda hecho: la terminal esperaba a que lo hicieras.
Si sales del IDE con el mensaje aún abierto, también se le devuelve a git,
con lo que estuviera guardado.

`git rebase -i` abre su lista de la misma manera: cada comando y cada
commit resaltados, y **Alternar comentario** descarta una línea sin
borrarla.

## 3. Sabe dónde estás

**Verás:** el **indicador ⎇** en la línea de estado — `⎇ main ±3 ↑2 ↓1`
es tu rama, tres archivos cambiados, dos commits por enviar y uno por
traer (las flechas solo aparecen cuando hay algo que enviar o traer). Su
menú empieza con **Cambiar de rama…** y **Confirmar…**.

**Haz:** pon el cursor en cualquier línea de un archivo versionado.

**Verás:** junto al indicador, quién cambió esa línea por última vez,
hace cuánto y por qué: `Ada Lovelace, hace 3 días · Fix the parser`. Una
línea que aún no has confirmado lo dice, y un archivo con cambios sin
guardar dice eso en lugar de nombrar al autor equivocado. Haz clic en la
nota para ver las anotaciones de todo el archivo; **Ver ▸ Autoría de la
línea** la desactiva.

## 4. Revisa un diff

**Haz:**

```bash
git difftool
```

**Verás:** cada archivo cambiado lado a lado en la vista de diferencias de
NMOX Studio, con **Diferencia anterior / Diferencia siguiente** y
«Diferencia 2 de 5» encima. Un archivo añadido o eliminado muestra su lado
ausente como un panel vacío («ningún archivo»); un archivo binario se
muestra como binario, y la barra dice si dos binarios difieren. Cierra la
pestaña y git pasa al siguiente archivo.

## 5. Resuelve un conflicto

**Haz:** fusiona una rama que entre en conflicto y luego:

```bash
git mergetool
```

**Verás:** el archivo en conflicto en el editor, con el lado actual y el
entrante teñidos, y un aviso en cada línea `<<<<<<<`. Pon el cursor en ella
y pulsa ⌘. (Alt+Enter en otros sistemas), o usa **Código ▸ Corregir
código…**: **Aceptar cambio actual**, **Aceptar cambio entrante** o
**Aceptar ambos cambios**, cada uno una sola edición que se puede deshacer.
Un bloque que haya cambiado desde la oferta se rechaza en vez de adivinarse.
Guarda, cierra la pestaña y responde a git.

## 6. Propónlo

**Haz:** haz push y luego Equipo ▸ **Nueva pull request en GitHub**
(también en el menú del indicador).

**Verás:** la propia página New Pull Request de GitHub para tu rama, en tu
navegador, donde tienes la sesión iniciada. Desde un editor, **Edición ▸
Abrir en GitHub** y **Copiar enlace de GitHub** dan la línea o las líneas
en las que estás; en el árbol del Estudio de proyecto dan un archivo o una
carpeta.

## 7. Persigue un fallo

**Haz:** ejecuta tus pruebas en la Terminal (⌃\`) hasta que una falle.

**Verás:** una ubicación en la salida — `src/app.ts:42:7`, un marco de pila
`(/abs/app.js:10:5)`, `--> src/main.rs:3:5`, `File "x.py", line 12` — se
abre en esa línea y columna con ⌘-clic (Ctrl-clic en Windows y Linux). Una
URL o `localhost:3000` nunca es un enlace, y una ruta que no existe se
rechaza por su nombre en vez de adivinarse.

## 8. Mantén honesto el README

**Haz:** Herramientas ▸ **Comprobar enlaces de Markdown…**

**Verás:** cada enlace relativo y cada imagen del Markdown del proyecto
comprobados tal como GitHub los muestra: el archivo debe existir, y un
`#heading` debe ser un encabezado de ese archivo. Un enlace roto es un
error y un encabezado que falta, un aviso, ambos como subrayados ondulados
y en Elementos de acción, con una frase en la línea de estado. Nada sale
de tu máquina: un enlace con esquema no se comprueba.

## 9. Pásaselo a un agente

**Haz:** Herramientas ▸ **Agent Port (MCP)…**, marca **Conservar esta
dirección y este token**, luego **Copiar para Claude Code** y ejecuta una
vez la línea copiada.

**Verás:** un agente que puede leer lo que sabe el IDE — el proyecto
apuntado, lo que se sirve y lo que se ejecuta, lo que estás editando, el
último fallo — y que mañana sigue conectándose, porque el token se guarda
en el llavero de tu sistema y el puerto se reutiliza. El puerto sigue
siendo de solo lectura por construcción. Desmarcar **Conservar esta
dirección y este token** borra la entrada del llavero.

## Lo que hiciste

Escribiste un mensaje de commit, leíste un diff, resolviste un conflicto,
abriste una pull request, averiguaste quién escribió una línea, seguiste
una traza de pila y comprobaste un README, todo en la ventana en la que ya
estabas. Nada de eso sustituyó a git: cada paso es el propio de git,
abierto donde trabajas.
