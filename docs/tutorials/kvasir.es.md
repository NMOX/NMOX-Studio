# Tutorial: KVASIR, el explicador de errores con IA

<!-- languages -->
[English](kvasir.md) · **Español** · [Français](kvasir.fr.md) · [Deutsch](kvasir.de.md) · [Русский](kvasir.ru.md) · [Українська](kvasir.uk.md) · [Polski](kvasir.pl.md) · [Português (Brasil)](kvasir.pt.md) · [Bahasa Indonesia](kvasir.id.md) · [Filipino](kvasir.tl.md) · [Tiếng Việt](kvasir.vi.md) · [简体中文](kvasir.zh.md) · [हिन्दी](kvasir.hi.md) · [עברית](kvasir.he.md) · [العربية](kvasir.ar.md)
<!-- /languages -->

KVASIR es un dispositivo del rack que lee tu última ejecución fallida y
le pregunta a tu IA —Claude, ChatGPT o Gemini— qué salió mal. Es
asistencia de IA con la metáfora del rack: un botón, una compuerta de
consentimiento clara y una pantalla LCD honesta. No se envía ningún
archivo del proyecto ni ningún secreto, solo el contexto acotado del
fallo.

![KVASIR explicando una ejecución fallida real: el diagnóstico autorizado en la carátula y los pasos completos de solución en el visor](../images/es/kvasir-explain.png)

## Antes de empezar

Necesitas una clave de API de uno de los tres proveedores que habla
KVASIR: Anthropic (Claude), OpenAI (ChatGPT) o Google (Gemini). Pulsa
**KEY…** en la carátula para elegir el proveedor y guardar su clave en el
llavero del sistema operativo, o exporta la variable de entorno del
proveedor: `ANTHROPIC_API_KEY` / `CLAUDE_API_KEY`, `OPENAI_API_KEY` /
`CHATGPT_API_KEY`, o `GEMINI_API_KEY` / `GOOGLE_API_KEY`. La elección de
proveedor vale para todas las caras de KVASIR y también está en
Opciones ▸ Rack y nube.

## Pasos

1. **Provoca un fallo.** Ejecuta algo que falle: una compilación con un
   error de sintaxis, una prueba que lanza una excepción. El registrador
   de vuelo del rack captura el comando, el código de salida y hasta cinco
   líneas de error de muestra.

2. **Monta KVASIR** desde la paleta (categoría Observar) y pulsa
   **EXPLAIN**.

3. **Da tu consentimiento (la primera vez).** KVASIR tiene su propio
   diálogo de consentimiento, que aparece una vez por proveedor, nombra
   al proveedor que recibe los datos y detalla exactamente lo que sale de
   tu máquina: el comando que falló, su código de salida, ≤5 líneas de
   error, el nombre del dispositivo y el nombre del proyecto, y nada más
   (ni código fuente, ni entorno, ni secretos). La confianza del espacio
   de trabajo protege la *ejecución* de código; este flujo de datos hacia
   fuera tiene su propia compuerta.

4. **Lee el veredicto.** En la pantalla LCD de varias líneas aparece un
   diagnóstico breve; pulsa **VIEW** para abrir la explicación completa
   en una ventana de conversación. El mando **MODEL** elige FAST (por omisión) o DEEP: Haiku /
   Sonnet, GPT-5 mini / GPT-5, o Gemini Flash / Pro, según el proveedor
   que hayas elegido.

## Lo que acabas de aprender

- KVASIR no cuesta nada al arrancar y no hace ninguna llamada de red sin
  que pulses el botón: se imponen tanto la compuerta de la clave como la
  del consentimiento.
- La clave viaja solo en la cabecera de autenticación del proveedor
  (`x-api-key`, `Authorization: Bearer`, `x-goog-api-key`), nunca en una
  URL, en el cuerpo ni en un registro.
- Las claves nunca pasan de un proveedor a otro, y el consentimiento es
  por proveedor: un sí para Anthropic no es un sí para Google ni para
  OpenAI.
- La degradación es honesta: sin clave, sin consentimiento, sin nada que
  explicar, sin conexión o con una negativa, cada caso muestra un mensaje
  claro en la LCD.

## Siguiente

- Conéctalo sin manos: un cable `VERITAS FAIL → KVASIR EXPLAIN` explica
  solo una ejecución de pruebas fallida (la vía por cable nunca pregunta y
  se limita a una consulta cada 30 s); su OUT alimenta MONITOR/PHOSPHOR.
