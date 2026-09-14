# Tutorial: explícalo todo con KVASIR

<!-- languages -->
[English](explain-anything.md) · **Español** · [Français](explain-anything.fr.md) · [Deutsch](explain-anything.de.md) · [Русский](explain-anything.ru.md) · [Українська](explain-anything.uk.md) · [Polski](explain-anything.pl.md) · [Português (Brasil)](explain-anything.pt.md) · [Bahasa Indonesia](explain-anything.id.md) · [Filipino](explain-anything.tl.md) · [Tiếng Việt](explain-anything.vi.md) · [简体中文](explain-anything.zh.md) · [हिन्दी](explain-anything.hi.md) · [עברית](explain-anything.he.md) · [العربية](explain-anything.ar.md)
<!-- /languages -->

KVASIR nació como un dispositivo del rack que explica ejecuciones
fallidas. Hoy llega a cuatro sitios —el rack, el editor, el Estudio de
API y el Estudio de bases de datos— y todas sus caras siguen las mismas
tres leyes: **ves exactamente lo que saldría de tu máquina antes de que
salga nada**, **cada superficie se gana su propio consentimiento** (decir
que sí a los errores de compilación nunca autoriza a enviar código ni
SQL) y **los secretos no pueden colarse, por construcción** (lo que se
comparte lo arma el estudio dueño de los datos, sin las cabeceras de
credenciales y sin acceso posible a las contraseñas).

![KVASIR explicando una ejecución fallida real](../images/kvasir-explain.png)

## Antes de empezar

Una sola clave cubre las cuatro caras, del proveedor que elijas: Claude
(Anthropic), ChatGPT (OpenAI) o Gemini (Google). Pulsa **KEY…** en la
carátula de KVASIR para elegir el proveedor y guardar su clave en el
llavero de tu sistema operativo, o exporta `ANTHROPIC_API_KEY`,
`OPENAI_API_KEY` o `GEMINI_API_KEY`. Sin clave no hay llamada, y cada cara
lo dice con honestidad.

## Las cuatro caras

1. **Una ejecución fallida (el rack).** Monta KVASIR, ejecuta algo que
   falle y pulsa **EXPLAIN**. Qué se envía: el comando, el código de
   salida y hasta cinco líneas de error de muestra. Consulta [el tutorial
   de KVASIR](kvasir.es.md) para el recorrido completo, incluido el cable
   que explica solo un fallo de VERITAS.

2. **Tu código (el editor).** Selecciona código en cualquier lenguaje →
   clic derecho → **Preguntar a KVASIR sobre la selección…** y escribe
   una pregunta. Qué se envía: la selección acotada, el nombre del archivo
   y el lenguaje, y nada más de tu proyecto. Esta cara tiene su *propia*
   compuerta de consentimiento, porque el consentimiento del flujo de
   fallos promete explícitamente que el código fuente nunca sale de la
   máquina.

3. **Una respuesta de API (el Estudio de API).** Tras un envío, pulsa
   **Explicar…**. Qué se envía: el método, la URL con los valores de la
   consulta enmascarados, el estado, las cabeceras sin credenciales (se
   eliminan y se cuentan) y un cuerpo acotado. Útil en cuanto aparece un
   401 o una cabecera CORS rara.

4. **Un error de base de datos (el Estudio de bases de datos).** Una
   sentencia fallida muestra un botón **Explicar…** bajo su mensaje de
   error. Qué se envía: el SQL que ejecutaste —*incluidos sus valores
   literales, y la línea de consentimiento lo dice*, porque el error suele
   tener que ver con un literal— más el mensaje de error y el tipo de
   motor. Nunca la conexión, la contraseña ni las filas.

Cada cara abre una ventana de conversación: haz preguntas de seguimiento
y el modelo verá todo el historial de ese intercambio (con un tope de
diez intercambios, que se indica en la transcripción). La elección
**Rápido/Profundo** (Haiku/Sonnet) se recuerda y queda fija en cada
conversación, para que la transcripción nunca mienta sobre quién
respondió.

## Pruébalo en dos minutos

El Estudio de bases de datos es la cara más rápida de enseñar: abre ⌥⌘7,
crea una conexión SQLite, ejecuta `SELECT * FROM user;` contra una base
de datos cuya tabla se llama `users` y pulsa **Explicar…** en el error.
Lee el diálogo de consentimiento antes de aceptar: es la promesa del
producto, en una frase.

## Lo que acabas de aprender

- Cuatro superficies, una sola costura: cada estudio arma lo que comparte
  y el diálogo de consentimiento lo cita literalmente.
- Rechazar se respeta en silencio y por completo: ni ventana ni llamada.
- Un resultado pertenece al espacio de trabajo que lo produjo: cambiar de
  proyecto borra las respuestas y las pestañas de resultados, así que
  Explicar nunca puede revelar datos de un proyecto anterior.
