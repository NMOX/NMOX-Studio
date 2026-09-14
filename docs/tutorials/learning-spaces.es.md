# Tutorial: espacios de aprendizaje

<!-- languages -->
[English](learning-spaces.md) · **Español** · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![El selector de Nuevo espacio de aprendizaje: busca entre los tutoriales incorporados, y el sondeo de disponibilidad te dice de antemano si esta máquina tiene la herramienta del espacio](../images/tabs/learning-spaces.png)

Un espacio de aprendizaje es un entorno autónomo para aprender un
lenguaje, un framework o una biblioteca: NMOX Studio genera código de
ejemplo, un tutorial guiado y un rack ya cableado con un **REPL de verdad
dentro del rack** en el que escribes. Hay 93 incorporados.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## Ábrelo

`Archivo ▸ Nuevo espacio de aprendizaje…` (el lanzador enumera todos los
espacios incorporados).

## Pasos

1. **Elige un espacio.** Escoge uno: Python, Rust, Solid, htmx, Solidity,
   Elm, un REPL para un lenguaje de sistemas, un espacio de E2E con
   Playwright, etc. Antes de nada, el selector comprueba si el intérprete
   o la cadena de herramientas están disponibles.

2. **Deja que se genere.** NMOX Studio crea el espacio en
   `~/.nmox/learn/<slug>`: un ejemplo mínimo que funciona y un tutorial
   que te lo recorre, señalando la consola o el dispositivo que toca.

3. **Escribe en el REPL.** El rack ya cableado incluye un dispositivo
   **REPL** cuyo mando ENGINE está puesto en el lenguaje del espacio (un
   motor por cada lenguaje con REPL del catálogo, cada uno con sus opciones de modo interactivo ya
   preparadas). Escribe una expresión y pulsa Entrar: la salida corre por
   la pantalla del REPL. ¿Falta el intérprete? El botón **INSTALL** lo
   instala desde el rack.

4. **Sigue el tutorial.** Avanza por los pasos; el código de ejemplo es
   real y ejecutable, y el espacio es tuyo para modificarlo.

## Lo que acabas de aprender

- Un espacio de aprendizaje es un proyecto completo con tutorial y rack
  cableado, no un simple fragmento.
- El REPL es un proceso interactivo real, no una grabación.
- Puedes añadir los tuyos: deja un `*.json` en
  `~/.nmox/learn-catalog.d/` y se suma al selector (el esquema está en
  [learning-spaces.md](../learning-spaces.md)).

## Siguiente

- Los espacios de frameworks (Astro/SvelteKit/Nuxt/Next) señalan su
  consola del rack (COSMOS/KINETIC/NIMBUS/NEXUS).
