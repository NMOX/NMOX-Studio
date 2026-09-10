# NMOX Studio — Guía del usuario

> Traducción parcial: los capítulos 1–2 están en español. Para el resto, consulta la [guía completa en inglés](user-guide.md).

Cómo usar el producto. Esta guía recorre las funciones en el orden en que las encontrarás: instalación, primer arranque, proyectos, el rack, los estudios, los asistentes y las redes de seguridad.

---

## 1. Instalación

**macOS (recomendado):**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

La línea `brew trust` es la confirmación única de Homebrew para cualquier tap de terceros: no se te volverá a preguntar en las actualizaciones. La aplicación está firmada ad-hoc pero no notarizada, así que una copia en cuarentena sería rechazada por Gatekeeper en el primer arranque: el cask elimina el atributo de cuarentena por sí mismo en un paso `postflight` y lo indica en la salida de la instalación. Nada silencioso.

**Todo lo demás:** descarga un archivo de la [última versión](https://github.com/NMOX/NMOX-Studio/releases/latest) — `.dmg` para macOS, `-setup.exe` para Windows, `.deb` para Debian/Ubuntu, `.tar.gz` genérico para Linux. Los cuatro incluyen su propio entorno de ejecución de Java; no hace falta instalar nada antes. El `-portable.zip` es el único artefacto que usa tu propio Java (necesita Java 21+ en el PATH, o arráncalo con `--jdkhome <ruta-al-jdk>`).

> **macOS, primer arranque:** la aplicación está firmada ad-hoc pero no notarizada, así que Gatekeeper pregunta antes de ejecutarla. **Haz clic derecho en la app → Abrir** la primera vez y confirma, o ejecuta
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`. Cualquiera de las dos lo resuelve para siempre.

### Actualizar

El IDE se actualiza solo: **Herramientas ▸ Complementos ▸ Actualizaciones** ofrece los módulos de cualquier versión más reciente. Instala, reinicia cuando se te pida y listo — sin volver a descargar la aplicación completa. Una advertencia honesta: el entorno de ejecución de Java incluido y el lanzador solo cambian con un instalador completo, así que para saltos importantes de plataforma sigue siendo correcto instalar de nuevo desde un archivo de la versión.

## 2. Primer arranque

Desde una terminal, `nmoxstudio --open <carpeta>` arranca la aplicación con esa carpeta abierta como proyecto y el rack apuntando a ella — la misma puerta que abre «Abrir carpeta…» en la página de bienvenida.

El IDE se abre con todas las pestañas del conjunto junto al área del editor: **Bienvenida → Rack de tareas → Estudio de bases de datos → Estudio de contratos → Diseñador de infraestructura → Estudio de API → Panel de Docker** — cada superficie principal está a un clic desde el primer minuto. En el panel izquierdo: **Estudio de proyecto** (árbol de archivos y plantillas), la base **Banco de trabajo** y el **Explorador de NPM**. Se crea una carpeta `~/NMOX` como espacio de trabajo predeterminado; el rack apunta ahí hasta que abras un proyecto.

![Primer arranque — la página de bienvenida con todas las pestañas abiertas](images/welcome.png)

Atajos que conviene aprender el primer día (también aparecen todos en la pestaña de bienvenida):

| Atajo | Abre |
|---|---|
| **⌘I** | Búsqueda rápida — llega a todo |
| **⌘9** | Rack de tareas |
| **⌥⌘0** | Banco de trabajo |
| **⌥⌘3** | Cliente de chat IRC |
| **⌥⌘4** | Navegador (WebKit integrado, con DevTools) |
| **⌥⌘5** | Estudio de bloques |
| **⌥⌘6** | Estudio de contratos |
| **⌥⌘7** | Estudio de bases de datos |
| **⌥⌘8** | Estudio de API |
| **⌥⌘9** | Diseñador de infraestructura |
| **⌘8** | Panel de Docker |
| **⌘7** | Esquema del archivo actual |
| **⇧⌘N / ⌥⌘O** | Nuevo proyecto… / Abrir carpeta… |
| **⇧⌘E / ⇧⌘L** | Nuevo experimento… / Nuevo espacio de aprendizaje… |
