# Tutorial: Umstieg von Postman (und von Insomnia, und aus dem Browser)

<!-- languages -->
[English](migrating-from-postman.md) · [Español](migrating-from-postman.es.md) · [Français](migrating-from-postman.fr.md) · **Deutsch** · [Русский](migrating-from-postman.ru.md) · [Українська](migrating-from-postman.uk.md) · [Polski](migrating-from-postman.pl.md) · [Português (Brasil)](migrating-from-postman.pt.md) · [Bahasa Indonesia](migrating-from-postman.id.md) · [Filipino](migrating-from-postman.tl.md) · [Tiếng Việt](migrating-from-postman.vi.md) · [简体中文](migrating-from-postman.zh.md) · [हिन्दी](migrating-from-postman.hi.md) · [עברית](migrating-from-postman.he.md) · [العربية](migrating-from-postman.ar.md)
<!-- /languages -->

Das API-Studio liest die Dateien, die Sie schon haben: eine Sammlung oder
Umgebung aus Postman, einen Export aus Insomnia v4 (Struktur des
Arbeitsbereichs und `{{ _.templates }}` werden übersetzt), einen
HAR-Mitschnitt aus den DevTools, einen curl-Befehl, eine `.http`-Datei,
eine OpenAPI-Spezifikation.
Dieser Rundgang nimmt einen echten Postman-Export von Anfang bis Ende
durch — und zeigt das eine, was NMOX Studio mit Absicht anders macht:
**Geheimnisse landen im Schlüsselbund Ihres Systems, nie in einer Datei
zum Einchecken.**

![Das API-Studio, wo Importe landen: der Sammlungsbaum, eine gesendete Anfrage und ihre Note für die Sicherheits-Header](../images/de/api-studio.png)

## Bevor Sie beginnen

Exportieren Sie Ihre Sammlung aus Postman: Sammlung ▸ … ▸ Export ▸
**Collection v2.1**. (Ein v1-Export wird abgelehnt, mit der Lösung
ausgeschrieben — als v2.1 neu exportieren.) Umgebungen werden getrennt
exportiert und über **Importieren… ▸ Postman-Umgebung…** eingelesen —
einfache Werte kommen herein, Importe gleichen Namens werden
zusammengeführt, ohne zu überschreiben, was Sie schon gesetzt haben, und
Werte, die Postman als *secret* markiert, bleiben draußen, mit einem
Hinweis auf das Auth-Feld mit Schlüsselbund-Anbindung, denn die Umgebungen
des API-Studios liegen in der einzucheckenden `.nmoxapi.json`.

## Schritte

1. **Öffnen Sie das API-Studio** (⌥⌘8) und drücken Sie **Importieren… ▸
   Postman-Sammlung…**. Wählen Sie Ihre exportierte `.json`.

2. **Prüfen Sie, was angekommen ist.** Ordner behalten ihre Identität als
   Namen der Form „Ordner / Anfrage“. Die `{{variables}}` aus Postman
   werden *wörtlich* importiert — sie sind die Syntax des API-Studios
   selbst —, und Sammlungsvariablen treten Ihrer aktiven Umgebung bei, ohne
   etwas zu überschreiben, das Sie schon gesetzt haben. Pfadvariablen `:id`
   werden zu `{{id}}`.

3. **Sehen Sie sich den Tab Auth einer Anfrage an, die ein Bearer-Token
   hatte.** Das Token ist *da* — aber es kam über das Auth-Feld mit
   Schlüsselbund-Anbindung herein, nicht als Header-Zeile. Checken Sie
   `.nmoxapi.json` bedenkenlos ein; das Geheimnis steht nicht darin. Was
   der Import nicht abbilden konnte (Multipart-Bodies, Skripte), wird in
   der Statuszeile benannt, nie stillschweigend verstümmelt.

4. **Importieren Sie einen Browser-Mitschnitt.** Im Tab Network der
   DevTools „Save all as HAR“ wählen, dann **Importieren… ▸
   HAR-Mitschnitt…**. Importiert wird nur Ihr XHR/fetch-Verkehr (Assets der
   Seite werden laut mitgezählt), Sitzungs-Cookies werden verworfen — ein
   mitgeschnittenes Cookie ist ein Zugangsdatum —, und ein aufgezeichneter
   `Authorization`-Header wandert entweder in den Schlüsselbund
   (Bearer/Basic) oder wird verworfen und gezählt (alles Undurchsichtige).

5. **Senden Sie eine.** Wählen Sie eine importierte Anfrage, lösen Sie bei
   Bedarf `{{baseUrl}}` in Ihrer Umgebung auf, drücken Sie **Senden** — und
   lesen Sie bei der Gelegenheit die Note für die Sicherheits-Header im Tab
   Standards.

6. **Gehen Sie den umgekehrten Weg.** **Importieren… ▸ Sammlung nach .http
   exportieren…** schreibt die ganze Sammlung im Dialekt von REST Client,
   für jeden Editor und jeden CI-Runner. Die Authentifizierung steht mit
   Absicht nicht in der Datei; jede authentifizierte Anfrage trägt einen
   Kommentar, der nennt, was wieder hinzuzufügen ist.

## Was Sie gerade gelernt haben

- Der Umstieg ist ein einziges Menü: curl / `.http` / OpenAPI / Postman /
  HAR hinein, `.http` hinaus.
- Das Gesetz der Geheimnisse gilt an jeder Grenze: Was in den Schlüsselbund
  kommt, bleibt im Schlüsselbund.
- Ablehnungen werden benannt, nie verschwiegen — ist etwas nicht
  importiert worden, sagt die Statuszeile, was und warum.
