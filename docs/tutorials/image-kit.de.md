# Tutorial: Image Kit (Web) — Ihre Bilder pressen

<!-- languages -->
[English](image-kit.md) · [Español](image-kit.es.md) · [Français](image-kit.fr.md) · **Deutsch** · [Русский](image-kit.ru.md) · [Українська](image-kit.uk.md) · [Polski](image-kit.pl.md) · [Português (Brasil)](image-kit.pt.md) · [Bahasa Indonesia](image-kit.id.md) · [Filipino](image-kit.tl.md) · [Tiếng Việt](image-kit.vi.md) · [简体中文](image-kit.zh.md) · [हिन्दी](image-kit.hi.md) · [עברית](image-kit.he.md) · [العربية](image-kit.ar.md)
<!-- /languages -->

Bilder sind meist das Schwerste, was eine Website ausliefert. Das Image Kit
findet die JPEGs und PNGs Ihres Projekts und presst sie fürs Web: kleinere
`.min.jpg`-Dateien daneben durch Neukodierung in reinem Java (nichts zu
installieren), optionales Verkleinern und `.webp`-Dateien daneben über Ihr
eigenes `cwebp`, sofern es installiert ist. Beim Live-Nachweis für diese
Version wurde aus einem Hintergrundbild von 17,8 MB eine `.min.jpg` mit
347 KB und eine `.webp` mit 342 KB — 98 % kleiner.

## Die Regeln, die es einhält

- **Originale werden nie angefasst.** Ergebnisse liegen daneben
  (`photo.min.jpg`, `photo.webp`), und ein bereits vorhandenes Ergebnis
  wird übersprungen und benannt — nie überschrieben.
- **Eine „Optimierung“, die nichts spart, wird verworfen**: Eine Pressung,
  die weniger als 10 % gewinnt, wird gelöscht und als *bereits knapp*
  gemeldet, statt eine größere „optimierte“ Datei auszuliefern. (Ein
  verkleinertes Ergebnis bleibt in jedem Fall erhalten — weniger Pixel
  waren ja das Ziel.)
- **PNG-Neukodierung fehlt mit Absicht.** ImageIO schlägt keinen echten
  PNG-Optimierer, daher ist bei PNGs die WebP-Datei daneben der ehrliche
  Gewinn.

## Schritte

1. **Richten Sie ein Projekt aus** und wählen Sie **Datei ▸ Zum Projekt hinzufügen ▸ Image Kit (Web)…**.
   Der Dialog sagt Ihnen, wie viele Bilder er gefunden hat und wie schwer
   sie zusammen sind (node_modules und Build-Ausgaben werden übersprungen,
   ebenso seine eigenen `.min.`-Ergebnisse — eine Pressung erneut zu pressen
   würde den Verlust vervielfachen).

2. **Wählen Sie Ihre Pressung.** JPEG-Qualität (85 visuell verlustfrei /
   80 Web-Standard / 70 aggressiv), eine optionale Maximalbreite (2560
   Retina-Hero / 1600 Inhalt / 800 Vorschaubilder) und — wenn `cwebp` in
   Ihrem PATH liegt — WebP-Dateien daneben. Liegt es dort nicht, sagt das
   Kontrollkästchen das und woher Sie es bekommen (`brew install webp`);
   auch der Environment Doctor prüft es.

3. **Lesen Sie den Bericht.** Je Datei: was geschrieben wurde, die Größe
   vorher → nachher, oder der ehrliche Grund, warum nichts geschrieben
   wurde („existiert bereits“, „bereits knapp“). Oben stehen die insgesamt
   gesparten Bytes, dazu ein kopierfertiges `<picture>`-Schnipsel, das die
   WebP-Datei ausliefert, wo sie unterstützt wird, und sonst auf das
   Original zurückfällt.

## Was Sie gerade gelernt haben

- Bildoptimierung fürs Web ohne vorausgesetzte Werkzeuge — und mit Ihrem
  eigenen `cwebp`, wenn Sie es haben.
- Die Regeln der Kit-Familie — nie überschreiben, ehrlich berichten —
  gelten auch für Pixel.
