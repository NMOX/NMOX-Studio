# Tutorial: edición y depuración políglota

<!-- languages -->
[English](polyglot-editing-and-debugging.md) · **Español** · [Français](polyglot-editing-and-debugging.fr.md) · [Deutsch](polyglot-editing-and-debugging.de.md) · [Русский](polyglot-editing-and-debugging.ru.md) · [Українська](polyglot-editing-and-debugging.uk.md) · [Polski](polyglot-editing-and-debugging.pl.md) · [Português (Brasil)](polyglot-editing-and-debugging.pt.md) · [Bahasa Indonesia](polyglot-editing-and-debugging.id.md) · [Filipino](polyglot-editing-and-debugging.tl.md) · [Tiếng Việt](polyglot-editing-and-debugging.vi.md) · [简体中文](polyglot-editing-and-debugging.zh.md) · [हिन्दी](polyglot-editing-and-debugging.hi.md) · [עברית](polyglot-editing-and-debugging.he.md) · [العربية](polyglot-editing-and-debugging.ar.md)
<!-- /languages -->

NMOX Studio edita más de 70 lenguajes con resaltado de sintaxis real,
esquema en el Navegador e inteligencia de servidores de lenguaje, y
depura JavaScript/TypeScript (y el navegador) de serie, con puntos de
interrupción que de verdad se detienen. Este tutorial alcanza un punto de
interrupción en una app Node.

![Un punto de interrupción de JavaScript alcanzado: ejecución en pausa, la pila de llamadas de Node y las variables vivas de V8](../images/debug-javascript.png)

## Antes de empezar

Abre (o genera) un proyecto Node pequeño con un script que puedas
ejecutar, por ejemplo una ruta de Express o un simple `node server.js`.

## Pasos

1. **Abre un archivo fuente.** El resaltado, el emparejado de corchetes,
   el plegado de código y el marcado de apariciones aparecen solos. El
   **Navegador** muestra el esquema del archivo; los servidores de
   lenguaje (instalados siguiendo las pistas de
   `Herramientas ▸ Doctor del entorno…`) añaden completado y
   diagnósticos.

2. **Pon un punto de interrupción.** Haz clic en el margen del editor, en
   una línea dentro de tu manejador: aparece el punto.

3. **Depura el archivo.** Ejecuta **Depurar archivo** (o «Depurar en
   Chrome (puntos de interrupción)» para una página HTML/JS). Una
   confirmación de confianza del espacio de trabajo, que solo aparece una
   vez, protege el arranque; después el adaptador `js-debug` incluido
   lanza tu programa.

4. **Alcanza el punto de interrupción.** Recorre ese camino del código
   (haz la petición, o deja que el script llegue a la línea). La ejecución
   **se detiene** en tu punto de interrupción: inspecciona variables,
   recorre la pila de llamadas, avanza paso a paso sobre o dentro de las
   funciones. Al depurar en el navegador, se abre un Chrome con perfil
   desechable en la URL viva de tu servidor de desarrollo y los puntos de
   interrupción de la página se corresponden con el código del IDE.

## Lo que acabas de aprender

- El editor trata más de 70 lenguajes como de primera clase (gramáticas
  TextMate + CSL + LSP); también cubre los archivos de configuración
  (YAML, TOML, Dockerfile, nginx…).
- La depuración de JS/TS viene integrada: un multiplexor de sesiones
  aplana las sesiones hijas de js-debug para que el depurador de sesión
  única de la plataforma pueda manejarlo.
- Cada arranque de depuración pasa por la confianza del espacio de
  trabajo y, al detenerse, se mata el árbol de procesos entero (sin
  huérfanos).

## Siguiente

- **Ejecutar la prueba enfocada** ejecuta un único método de prueba, en
  cada lenguaje.
- Los diagnósticos de las herramientas del rack (eslint/tsc/phpstan)
  llegan a la ventana **Elementos de acción** de la plataforma.
