# Tutorial: el Rack de tareas

<!-- languages -->
[English](the-task-rack.md) · **Español** · [Français](the-task-rack.fr.md) · [Deutsch](the-task-rack.de.md) · [Русский](the-task-rack.ru.md) · [Українська](the-task-rack.uk.md) · [Polski](the-task-rack.pl.md) · [Português (Brasil)](the-task-rack.pt.md) · [Bahasa Indonesia](the-task-rack.id.md) · [Filipino](the-task-rack.tl.md) · [Tiếng Việt](the-task-rack.vi.md) · [简体中文](the-task-rack.zh.md) · [हिन्दी](the-task-rack.hi.md) · [עברית](the-task-rack.he.md) · [العربية](the-task-rack.ar.md)
<!-- /languages -->

El Rack de tareas es la idea que define a NMOX Studio: tus herramientas
de compilar, probar y servir dispuestas como un rack de aparatos que
conectas con cables. Un dispositivo ejecuta un comando real; un cable
lleva una señal real. Este tutorial monta un patch diminuto —ejecutar
algo y encender un indicador cuando termine— para que la metáfora encaje.

![El rack apuntando a un proyecto real, con los dispositivos montados y en marcha](../images/task-rack.png)

![Tab da la vuelta al rack: los cables conectan los dispositivos por detrás](../images/rack-rear.png)

## Antes de empezar

Abre un proyecto (cualquier proyecto Node sirve;
`Archivo ▸ Nuevo proyecto…` → «Vanilla JS» si necesitas uno). Abrir un proyecto **apunta**
el rack a él, así que cada dispositivo se ejecuta en el directorio de ese
proyecto.

## Pasos

1. **Abre el rack.** Haz clic en la pestaña **Rack de tareas** (o pulsa
   `⌘9`). El rack inicial trae un único **MONITOR**: el dispositivo de
   consola que muestra la salida de los comandos y las líneas de error.

2. **Añade un ejecutor.** Arrastra **IGNITION** desde el estante de la
   izquierda hasta el rack. IGNITION es el dispositivo políglota de
   «ejecutar»; apuntado a un proyecto Node ejecuta `npm run dev` (detecta
   solo tu gestor de paquetes y tu cadena de herramientas).

3. **Conéctalo al monitor.** Haz clic en el control de **giro** para ver
   la parte trasera; después haz clic en el conector **OUT** de IGNITION
   y luego en el conector **TAP** de MONITOR: un cable los une. (También
   puedes arrastrar entre conectores; hacer clic es más fácil cuando el
   rack es ancho.)

4. **Dispáralo.** Vuelve a la parte delantera y pulsa el botón **GO** de
   IGNITION. Arranca el proceso; la salida corre por MONITOR y se
   encienden los LED de estado. Si el proyecto aún no es de confianza,
   antes verás una confirmación de confianza del espacio de trabajo, que
   solo aparece una vez: es la guarda que impide que un repositorio
   clonado ejecute sus scripts sin tu permiso.

5. **Guarda el patch.** `⌘S` (o el botón Guardar patch) escribe
   `.nmoxrack.json` junto a tu proyecto. Vuelve a abrir el proyecto más
   tarde y el patch —dispositivos, cables, posiciones de los mandos— vuelve
   tal cual.

## Lo que acabas de aprender

- **Los dispositivos son herramientas con carátula.** Los mandos eligen
  opciones, los botones GO ejecutan, los LED y las LCD informan del estado,
  y cada control es real (no hay mandos muertos; una prueba de contrato lo
  garantiza).
- **Los cables coordinan carriles.** OUT→TAP es la conexión más simple;
  las compuertas de disponibilidad (`ENABLE`), las barreras de unión
  (`QUORUM`) y los cables de disparo te permiten componer una tubería
  entera que reacciona a sí misma.
- **Todo persiste.** El patch es un archivo que puedes versionar; el rack
  incluso resucita una sesión en marcha tras un fallo.

## Siguiente

- Hay 53 dispositivos: explóralos en [devices.md](../devices.md) o en las
  fichas «Cómo se usa» del estante.
- Pídele a [KVASIR](kvasir.es.md) que explique una ejecución fallida.
- Exporta un patch a un flujo de trabajo de GitHub Actions con el botón
  **Exportar CI…** del rack.
