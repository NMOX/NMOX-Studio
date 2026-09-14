# Tutorial: Image Kit (Web), comprime tus imágenes

<!-- languages -->
[English](image-kit.md) · **Español** · [Français](image-kit.fr.md) · [Deutsch](image-kit.de.md) · [Русский](image-kit.ru.md) · [Українська](image-kit.uk.md) · [Polski](image-kit.pl.md) · [Português (Brasil)](image-kit.pt.md) · [Bahasa Indonesia](image-kit.id.md) · [Filipino](image-kit.tl.md) · [Tiếng Việt](image-kit.vi.md) · [简体中文](image-kit.zh.md) · [हिन्दी](image-kit.hi.md) · [עברית](image-kit.he.md) · [العربية](image-kit.ar.md)
<!-- /languages -->

Las imágenes suelen ser lo más pesado que publica un sitio. El Image Kit
encuentra los JPEG y PNG de tu proyecto y los comprime para la web:
hermanos `.min.jpg` más ligeros mediante recodificación en Java puro
(sin instalar nada), reducción de tamaño opcional y hermanos `.webp` con
tu propio `cwebp` cuando lo tienes instalado. En la prueba en vivo de
esta versión, un fondo de pantalla de 17,8 MB se quedó en un `.min.jpg`
de 347 KB y un `.webp` de 342 KB: un 98 % menos.

## Las leyes que respeta

- **Los originales nunca se tocan.** Los resultados son hermanos
  (`photo.min.jpg`, `photo.webp`), y un resultado que ya existe se salta
  y se dice: nunca se pisa.
- **Una «optimización» que no ahorra nada se descarta**: una compresión
  que recupera menos del 10 % se borra y se informa como *ya ajustada*,
  en vez de publicar un archivo «optimizado» más grande. (Un resultado
  redimensionado se conserva igualmente: el objetivo eran menos
  píxeles.)
- **La recodificación de PNG falta a propósito.** ImageIO no puede ganar
  a un optimizador de PNG de verdad, así que para los PNG la ganancia
  honesta es el hermano WebP.

## Pasos

1. **Apunta a un proyecto** y elige
   **Archivo ▸ Añadir al proyecto ▸ Image Kit (Web)…**. El diálogo te dice cuántas imágenes encontró y su
   peso total (se saltan node_modules y las salidas de compilación, y
   también sus propios resultados `.min.`: comprimir lo comprimido
   acumularía pérdida).

2. **Elige la compresión.** Calidad JPEG (85 visualmente sin pérdida /
   80 el valor por omisión para web / 70 agresiva), un ancho máximo
   opcional (2560 imagen principal retina / 1600 contenido / 800
   miniaturas) y, si `cwebp` está en tu PATH, hermanos WebP. Si no está,
   la casilla lo dice e indica dónde conseguirlo (`brew install webp`);
   el Doctor del entorno también lo sondea.

3. **Lee el informe.** Por archivo: qué se escribió, el tamaño antes →
   después, o la razón honesta de que no se escribiera nada («ya
   existe», «ya ajustada»). Arriba está el total de bytes ahorrados,
   junto con un fragmento `<picture>` listo para copiar que sirve el WebP
   donde se admite y recurre al original en los demás casos.

## Lo que acabas de aprender

- Optimización de imágenes para la web sin ninguna herramienta
  obligatoria, y con tu propio `cwebp` cuando lo tienes.
- Las leyes de la familia de kits, nunca pisar e informar con honestidad,
  también se aplican a los píxeles.
