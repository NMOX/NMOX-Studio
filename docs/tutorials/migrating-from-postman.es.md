# Tutorial: migrar desde Postman (y desde Insomnia, y desde el navegador)

<!-- languages -->
[English](migrating-from-postman.md) · **Español** · [Français](migrating-from-postman.fr.md) · [Deutsch](migrating-from-postman.de.md) · [Русский](migrating-from-postman.ru.md) · [Українська](migrating-from-postman.uk.md) · [Polski](migrating-from-postman.pl.md) · [Português (Brasil)](migrating-from-postman.pt.md) · [Bahasa Indonesia](migrating-from-postman.id.md) · [Filipino](migrating-from-postman.tl.md) · [Tiếng Việt](migrating-from-postman.vi.md) · [简体中文](migrating-from-postman.zh.md) · [हिन्दी](migrating-from-postman.hi.md) · [עברית](migrating-from-postman.he.md) · [العربية](migrating-from-postman.ar.md)
<!-- /languages -->

El Estudio de API lee los archivos que ya tienes: una colección o un
entorno de Postman, una exportación de Insomnia v4 (con la estructura del
espacio de trabajo y las `{{ _.templates }}` traducidas), una captura HAR
de las devtools, un comando curl, un archivo `.http`, una especificación
OpenAPI.
Este recorrido lleva una exportación real de Postman de principio a fin, y
muestra lo único que NMOX Studio hace distinto a propósito: **los
secretos acaban en el llavero de tu sistema operativo, nunca en un
archivo versionable.**

![El Estudio de API, donde aterrizan las importaciones: el árbol de colecciones, una petición enviada y la nota de sus cabeceras de seguridad](../images/api-studio.png)

## Antes de empezar

Exporta tu colección desde Postman: colección ▸ … ▸ Export ▸
**Collection v2.1**. (Una exportación v1 se rechaza explicando la
solución: vuelve a exportar como v2.1.) Los entornos se exportan por
separado y se importan con **Importar… ▸ Entorno de Postman…**: los
valores normales entran, las importaciones con el mismo nombre se
fusionan sin pisar lo que ya habías puesto, y los valores que Postman
marca como *secret* se quedan fuera con una nota que remite al campo Auth
respaldado por el llavero, porque los entornos del Estudio de API viven en
el `.nmoxapi.json` versionable.

## Pasos

1. **Abre el Estudio de API** (⌥⌘8) y pulsa
   **Importar… ▸ Colección de Postman…**. Elige el `.json` que exportaste.

2. **Revisa lo que ha llegado.** Las carpetas conservan su identidad como
   nombres «Carpeta / Petición». Las `{{variables}}` de Postman se importan
   *tal cual* —son la sintaxis propia del Estudio de API— y las variables
   de la colección se suman a tu entorno activo sin pisar nada de lo que
   ya tenías. Las variables de ruta `:id` se convierten en `{{id}}`.

3. **Mira la pestaña Auth de una petición que llevaba un token bearer.**
   El token *está ahí*, pero entró por el campo Auth respaldado por el
   llavero, no como fila de cabecera. Versiona `.nmoxapi.json` sin miedo:
   el secreto no está dentro. Todo lo que la importación no podía
   representar (cuerpos multipart, scripts) se nombra en la línea de
   estado, nunca se destroza en silencio.

4. **Importa una captura del navegador.** En la pestaña Network de las
   devtools, «Save all as HAR», y luego **Importar… ▸ Captura HAR…**. Solo
   se importa tu tráfico XHR/fetch (los recursos de la página se cuentan
   en voz alta), las cookies de sesión se descartan —una cookie capturada
   es una credencial— y un `Authorization` grabado o pasa al llavero
   (Bearer/Basic) o se descarta y se cuenta (cualquier cosa opaca).

5. **Envía una.** Elige una petición importada, resuelve `{{baseUrl}}` en
   tu entorno si hace falta, pulsa **Enviar** y, ya que estás, lee la nota
   de cabeceras de seguridad en la pestaña Estándares.

6. **Ve en sentido contrario.**
   **Importar… ▸ Exportar colección a .http…** escribe la colección entera en el dialecto de REST Client para
   cualquier editor o ejecutor de CI. La autenticación no va en el archivo,
   a propósito; cada petición autenticada lleva un comentario que dice qué
   volver a añadir.

## Lo que acabas de aprender

- Migrar es un solo menú: curl / `.http` / OpenAPI / Postman / HAR para
  entrar, `.http` para salir.
- La ley de los secretos se cumple en cada frontera: entran al llavero y
  en el llavero se quedan.
- Las negativas tienen nombre, nunca son silenciosas: si algo no se
  importó, la línea de estado dice qué y por qué.
