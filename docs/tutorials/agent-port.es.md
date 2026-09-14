# El Agent Port (MCP)

<!-- languages -->
[English](agent-port.md) · **Español** · [Français](agent-port.fr.md) · [Deutsch](agent-port.de.md) · [Русский](agent-port.ru.md) · [Українська](agent-port.uk.md) · [Polski](agent-port.pl.md) · [Português (Brasil)](agent-port.pt.md) · [Bahasa Indonesia](agent-port.id.md) · [Filipino](agent-port.tl.md) · [Tiếng Việt](agent-port.vi.md) · [简体中文](agent-port.zh.md) · [हिन्दी](agent-port.hi.md) · [עברית](agent-port.he.md) · [العربية](agent-port.ar.md)
<!-- /languages -->

*Apunta un agente de IA a tu IDE — y déjalo LEER, nunca ejecutar.*

![El diálogo del Agent Port — el extremo loopback, el token de cada arranque (un marcador de posición en esta captura) y la configuración de cliente lista para copiar](../images/tabs/agent-port.png)

NMOX Studio incluye un servidor Model Context Protocol. Cualquier agente
que hable MCP (Claude Code, un asistente de editor, tu propio script) puede
conectarse y preguntarle al IDE lo que sabe: a qué proyecto apunta, qué
está sirviendo, qué se está ejecutando, qué estás editando, dónde se
declara un nombre, qué falló por última vez. Es **de solo lectura por
construcción**: la compilación falla si alguna clase del paquete del Agent
Port llega siquiera a nombrar una forma de arrancar un proceso, escribir un
archivo o detener una ejecución.

## 1. Arráncalo

**Haz:** Herramientas ▸ **Agent Port (MCP)…** ▸ **Iniciar**, y luego
**Copiar configuración**.

**Verás:** un diálogo con el extremo (solo loopback, un puerto nuevo), un
token bearer para ese arranque y una configuración de cliente lista:

```json
{
  "mcpServers": {
    "nmox-studio": {
      "type": "http",
      "url": "http://127.0.0.1:PORT/mcp",
      "headers": { "Authorization": "Bearer TOKEN" }
    }
  }
}
```

Pégala en el `.mcp.json` de tu agente. El token solo existe en ese
diálogo — nunca se registra ni se guarda — y muere con el puerto.
**Detener el Agent Port** lo termina; salir del IDE, también. Mientras
escucha, la barra de estado muestra **⌁ agent port :N** — un puerto que
puede leer tu IDE nunca es invisible; la ayuda emergente del indicador
cuenta los agentes conectados al flujo, y un clic vuelve a abrir el
diálogo (la configuración, o Detener).

## 2. Las herramientas

Cada herramienta responde con un texto para personas Y un
`structuredContent` tipado bajo un `outputSchema` declarado (la compilación
valida el esquema contra la salida real), y lleva la anotación
`readOnlyHint: true`.

| Herramienta | Qué responde | Argumentos |
|-------------|--------------|------------|
| `ide_context` | Toda la foto de orientación en una llamada: proyecto, cadena de herramientas, servidores, ejecuciones, el archivo que se edita, el último fallo, un recuento de diagnósticos | — |
| `project_state` | El proyecto al que se apunta: nombre, directorio, rama de git, tipo detectado, gestor de paquetes de Node | — |
| `run_history` | Los arranques y salidas de la caja negra, lo más reciente primero, cada salida con su comando, código y duración; una ejecución que detuviste tú se lee `stopped`, nunca `failed` | `limit` |
| `live_servers` | Cada servidor de desarrollo que el IDE sabe que está sirviendo, con su URL | — |
| `live_runs` | Cada comando que se ejecuta ahora mismo (lo que detendría el ■ de la barra), con la hora a la que empezó | — |
| `last_failure` | La ejecución fallida más reciente: dispositivo, comando, código de salida, hasta cinco líneas de error | — |
| `diagnostics` | Lo que informan ahora mismo los linters y comprobadores | `file` (filtro por subcadena) |
| `find_symbol` | Dónde se declara un nombre — el mismo índice que Ir al símbolo (⌥⇧⌘O) | `query`, `limit` |
| `outline` | La estructura de un archivo — los mismos elementos que el Navegador | `file` |
| `search_text` | Líneas que contienen un literal, sin distinguir mayúsculas, acotado y avisando de cada límite; los archivos `.env`, los archivos rc de gestores de paquetes y las claves privadas nunca se buscan | `query`, `limit` |
| `editor_state` | El archivo que se edita (la pestaña del editor con el foco, si no la que se muestra en el área del editor) y todas las pestañas abiertas, marcando las que tienen cambios sin guardar | — |
| `rack_devices` | Los dispositivos montados en el rack de tareas, en orden | — |

Toda lista está acotada y lo dice: `find_symbol` y `outline` avisan de un
índice parcial, `search_text` indica `truncated` solo cuando existe otra
coincidencia más, `run_history` avisa cuando se dejaron fuera eventos
anteriores.

## 3. Recursos, prompts y el flujo

Las mismas respuestas se pueden explorar como recursos que un agente
adjunta como contexto — `nmox://context`, `nmox://project`,
`nmox://history`, `nmox://servers`, `nmox://runs`, `nmox://editor`,
`nmox://last-failure`, `nmox://diagnostics`, `nmox://devices` — más dos
plantillas para las herramientas que reciben un argumento:
`nmox://outline/{file}` y `nmox://search/{query}` (con codificación por
porcentaje). El texto de un recurso es el JSON estructurado de su
herramienta, byte a byte.

Un agente que prefiere que le avisen a volver a preguntar puede
**suscribirse**: `resources/subscribe` sobre cualquiera de esas URI, y el
flujo GET del puerto (el canal de servidor a cliente de Streamable HTTP,
`Accept: text/event-stream`, el mismo token, sin `Origin`) lleva una trama
`notifications/resources/updated` en cuanto cambia lo que hay detrás —
arranca una ejecución y se anuncia `nmox://runs`, un servidor se pone en
marcha y se anuncia `nmox://servers`, un linter informa y se anuncia
`nmox://diagnostics`, cambia una pestaña o se guarda un archivo y se
anuncia `nmox://editor`; `nmox://context` los sigue a todos. La trama
nombra la URI y nada más; el agente vuelve a leer lo que le interesa. Un
esquema que adjuntó un agente también sigue a su archivo: suscríbete a
`nmox://outline/src/app.ts` y el puerto anuncia esa URI cuando el archivo
cambia en disco (al guardar, al formatear, un generador), y una vez más si
desaparece — un archivo normal dentro del proyecto al que se apunta, como
mucho treinta y dos, sondeados cada dos segundos; una ruta fuera del
proyecto es `-32002`, y nunca se lee.

Tres prompts pliegan el estado en vivo en una pregunta: `diagnose_failure`
(el último fallo), `review_setup` (todo el contexto) y `where_is` — el que
recibe un argumento, `name` —, que pliega las coincidencias de símbolos de
ese nombre.

Un agente que rellena ese argumento, o el `{file}` de la plantilla de
esquema, puede preguntar antes: `completion/complete` (la cuarta primitiva
de la especificación) responde al `name` de `where_is` desde el índice de
símbolos (las mismas coincidencias que devuelve `find_symbol`, sin
repetir, primero las de prefijo) y a `{file}` desde los archivos del propio
proyecto (primero las de prefijo, luego las que contienen; se aplica la
lista de exclusiones del recorrido de búsqueda, así que `node_modules`
nunca se completa) — como mucho 100 valores, `hasMore` cuando el límite los
cortó, y `total` solo cuando la cifra es exacta (una lista de archivos
siempre lo es; pasado el límite, el índice de símbolos solo responde un
mínimo, así que no se da ningún número antes que dar uno erróneo). El
literal de la plantilla de búsqueda puede ser cualquier cosa, así que no
completa nada; un nombre de prompt, plantilla o argumento desconocido se
rechaza como `-32602`.

El mismo flujo lleva **mensajes de registro**: cada línea que imprime cada
ejecución llega como `notifications/message` con la ejecución como su
`logger` — el ciclo de vida en `info` (`$ npm run build`, `[exit 0]`,
`[exit 143] stopped`; una salida fallida en `error`), stderr en `warning`,
la salida normal en `debug`. El nivel empieza en `info`, así que un agente
oye arrancar y terminar las ejecuciones y nada más hasta que lo pida:
`logging/setLevel` con `debug` abre el grifo. Una compilación que imprime
más rápido de lo que lee el cliente nunca hace crecer la memoria del
puerto — pasadas mil líneas sin escribir, el desbordamiento se cuenta y se
anuncia como una sola línea `warning`, nunca se pierde en silencio. Un
nivel que la especificación no nombra se rechaza como `-32602`.

## 4. El recorrido, a mano

Con el token en una variable de la shell (nunca en una línea de comandos
que pegarías en cualquier sitio):

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_symbol","arguments":{"query":"checkout"}}}'
```

**Verás:** `checkout (function) — src/cart.js:12`, y lo mismo como
`structuredContent.hits[0]`.

El flujo, a mano: ábrelo en una shell y suscríbete desde otra —

```bash
curl -N -s "$URL" -H "Authorization: Bearer $TOKEN" -H "Accept: text/event-stream"
```

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"resources/subscribe","params":{"uri":"nmox://runs"}}'
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"logging/setLevel","params":{"level":"debug"}}'
```

**Verás:** `: connected` y después `: keepalive` cada quince segundos;
pulsa ▶ y la primera shell imprime `notifications/resources/updated` para
`nmox://runs` y cada línea que imprime la ejecución como
`notifications/message` (`$ npm run dev` en `info`, la salida en `debug`);
pulsa ■ y llega `[exit 143] stopped` en `info`.

El mismo recorrido con el **cliente oficial**, todas las primitivas a la
vez, viene en el repositorio: `scripts/agent-port-walk.mjs` (su cabecera
explica cómo instalar `@modelcontextprotocol/sdk` en un directorio de
pruebas y dónde van la URL y el token — variables de la shell, nunca una
línea de comandos). Imprime una línea por paso y termina con WALK CLEAN o
con el número de sorpresas como código de salida (en un paso de negativa,
la sorpresa es una RESPUESTA), así que un trabajo de integración continua
puede leerlo; pulsa ▶ y ■ en el IDE mientras escucha y llegan los mensajes
de registro.

## 5. Las negativas son funciones

| Haces | El puerto dice |
|-------|----------------|
| Llamar sin el token, o con uno caducado | `401` — nada más, ni siquiera la lista de herramientas |
| Llamar desde una página en un navegador (cualquier `Origin`) | `403` |
| Un `GET` normal | `405` — el puerto no es una página; solo se sirve el `GET` de SSE (con `Accept: text/event-stream`), como flujo de suscripción |
| Suscribirte a `nmox://nonesuch`, o a un esquema fuera del proyecto | JSON-RPC `-32002` (recurso no encontrado) |
| Suscribirte a un trigésimo tercer esquema | `-32602`, nombrando el límite |
| Leer `nmox://nonesuch` | JSON-RPC `-32002` (recurso no encontrado) |
| Pedir `where_is` sin `name` | `-32602`, nombrando el argumento que falta |
| Pedir un archivo fuera del proyecto (`../../.zshrc`) | `outline` se niega — *fuera del proyecto al que se apunta* — y nunca lo lee |
| Buscar un valor que vive en `.env` (o `app.env`), `.npmrc`, `.htpasswd`, `secrets.yaml`, `credentials.json` o un `.pem` — o pedir su esquema | nada — esos archivos nunca se buscan, nunca se cuentan, nunca se completan, y `outline` los rechaza por su nombre; la ley de entorno del propio IDE (el nombre de una clave, nunca su valor) se aplica también a los agentes |
| Poner el nivel de registro en `loud` | `-32602`, nombrando los ocho niveles |
| Pedirle que ejecute, escriba o detenga algo | no existe tal herramienta; la prueba del registro de clases se encarga de que siga así |

Esa última fila es el diseño. Un agente que puede arrancar tu servidor
también puede pararlo, y un agente que puede escribir también puede
borrar; el Agent Port sigue siendo una forma de PREGUNTAR. Si una versión
futura añade una superficie de ejecución, llegará con su propio diseño de
consentimiento, como hizo el flujo de datos hacia fuera de KVASIR.

Consulta también la estación 24 del Kitchen Sink y el párrafo sobre el
Agent Port de la guía de usuario.
