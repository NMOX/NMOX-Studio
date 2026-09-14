# Tutorial: escribe tu propio dispositivo para el rack

<!-- languages -->
[English](your-own-device.md) · **Español** · [Français](your-own-device.fr.md) · [Deutsch](your-own-device.de.md) · [Русский](your-own-device.ru.md) · [Українська](your-own-device.uk.md) · [Polski](your-own-device.pl.md) · [Português (Brasil)](your-own-device.pt.md) · [Bahasa Indonesia](your-own-device.id.md) · [Filipino](your-own-device.tl.md) · [Tiếng Việt](your-own-device.vi.md) · [简体中文](your-own-device.zh.md) · [हिन्दी](your-own-device.hi.md) · [עברית](your-own-device.he.md) · [العربية](your-own-device.ar.md)
<!-- /languages -->

*Una sola sesión. Añadirás un dispositivo al rack con un editor de texto,
pulsarás su botón, lo verás ejecutar un comando real y conectarás su
salida a MONITOR — sin escribir una línea de Java.*

Nuevo en 2.0.0. El rack venía con cincuenta y tres dispositivos y, hasta
ahora, una sola manera de añadir el quincuagésimo cuarto: escribir un
complemento de NetBeans. Esta es la otra manera.

![El Rack de tareas: el estante de dispositivos de la izquierda es donde aparece un dispositivo de ~/.nmox/devices.d, junto a los integrados](../images/tabs/the-task-rack.png)

## 1. Crea la carpeta

```bash
mkdir -p ~/.nmox/devices.d
```

Esa es toda la instalación. El rack lee la carpeta cuando la necesita,
así que no hay nada que reiniciar.

## 2. Escribe el dispositivo

Pon esto en `~/.nmox/devices.d/counter.json`:

```json
{
  "id": "com.example.counter",
  "title": "COUNTER",
  "tagline": "counts the files in the project",
  "accent": "#7FB3D5",
  "category": "OBSERVE",
  "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\nPatch OUT into MONITOR to read the list, or DONE onward to chain.",
  "knobs": [
    { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] }
  ],
  "ports": [
    { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "buttons": [
    { "label": "COUNT", "role": "QUERY",
      "command": ["git", "ls-files", "*.{{kind}}"],
      "emit": "done", "trigger": "count" }
  ]
}
```

Cada línea tiene su función: el **mando** se convierte en `{{kind}}` dentro
del comando, el rol **QUERY** pinta el botón de azul (la ley del color: el
azul pregunta, el verde hace, el rojo detiene) y los tres puertos permiten
cablearlo.

## 3. Móntalo

Abre el **Rack de tareas** (`⌘9`, o la pestaña Rack de tareas) y busca en
el cajón **Observar** del estante. Ahí está COUNTER, con tu lema debajo.
Arrástralo a un raíl.

Haz clic derecho en él y elige **Cómo usar COUNTER…** — es tu texto `usage`, y por eso el
formato exige dos líneas de verdad.

## 4. Púlsalo

> Fíjate en que no hay línea `units`: el estante mide la carátula y
> elige la altura más pequeña en la que cabe (esta necesita 2U por el
> mando). Declara `units` solo cuando quieras más espacio.

Apunta el rack a un proyecto git, gira **KIND** hasta `js` y pulsa
**COUNT**.

La primera pulsación abre la confirmación **Confianza del espacio de
trabajo**, porque un archivo de dispositivo ejecuta comandos reales y el
anfitrión controla cada arranque igual que con un dispositivo integrado.
Concédela y la pantalla LCD muestra el comando y después la última línea
de la salida. El jack DONE parpadea en verde.

Si la rechazas, no arranca nada — la negativa es la función.

## 5. Conéctalo

Arrastra un cable desde el **OUT** de COUNTER hasta el **IN** de MONITOR.
Vuelve a pulsar COUNT: cada línea llega al monitor, porque un puerto
`OUT`/`DATA` declarado recibe la salida de la ejecución sin más
configuración.

Ahora arrastra desde el pulso de TEMPO hasta la entrada **COUNT** de
COUNTER. El dispositivo que escribiste en un editor de texto ya va con
reloj.

## 6. Rómpelo a propósito

Edita el archivo y cambia el comando por algo con una tubería:

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

Guarda, y COUNTER *desaparece* del estante. Es el formato negándose a
aceptar una línea de shell: un comando es un arreglo argv, para que quien
lo lea — tú dentro de seis meses, o un compañero que revisa el archivo —
vea exactamente qué se va a ejecutar. El registro del IDE dice qué archivo
se omitió y por qué:

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

Vuelve a poner la forma de arreglo y reaparece. Lo mismo ocurre con una
herramienta nombrada por ruta (`./x.sh`), una `{{variable}}` desconocida o
un `usage` de una sola línea: el archivo se omite entero en vez de
cargarse a medias, porque un dispositivo cuya etiqueta miente es peor que
ningún dispositivo.

## Lo que acabas de aprender

- Un dispositivo es un **archivo**: `~/.nmox/devices.d/*.json`, leído
  cuando hace falta, sin reiniciar y sin compilar.
- Los mandos se convierten en `{{variables}}`; los roles eligen colores;
  los puertos permiten cablearlo y leer su salida.
- **Las leyes las mantiene el anfitrión** — confianza del espacio de
  trabajo en cada arranque, la ley del color, el léxico de puertos, la ley
  del estante —, así que un archivo de dispositivo no puede expresar un
  comando sin control ni un GO rojo aunque lo intente.
- Las negativas se oyen en el registro y son totales en su efecto.

## Siguiente

- [device-files.md](../device-files.md) — la referencia completa
- [El Rack de tareas](the-task-rack.es.md) — cables, puertas y preajustes
- [device-spi.md](../device-spi.md) — el SPI de Java, para dispositivos que
  necesitan estado real: pintado propio, sondeo, conexiones de larga
  duración
