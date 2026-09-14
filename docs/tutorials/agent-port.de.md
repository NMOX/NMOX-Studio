# Der Agent Port (MCP)

<!-- languages -->
[English](agent-port.md) · [Español](agent-port.es.md) · [Français](agent-port.fr.md) · **Deutsch** · [Русский](agent-port.ru.md) · [Українська](agent-port.uk.md) · [Polski](agent-port.pl.md) · [Português (Brasil)](agent-port.pt.md) · [Bahasa Indonesia](agent-port.id.md) · [Filipino](agent-port.tl.md) · [Tiếng Việt](agent-port.vi.md) · [简体中文](agent-port.zh.md) · [हिन्दी](agent-port.hi.md) · [עברית](agent-port.he.md) · [العربية](agent-port.ar.md)
<!-- /languages -->

*Richten Sie einen KI-Agenten auf Ihre IDE — und lassen Sie ihn LESEN,
nie ausführen.*

![Der Dialog Agent Port — der Loopback-Endpunkt, das beim Start erzeugte Token (in dieser Aufnahme ein Platzhalter) und die fertige Client-Konfiguration zum Kopieren](../images/tabs/agent-port.png)

NMOX Studio bringt einen Model-Context-Protocol-Server mit. Jeder Agent,
der MCP spricht (Claude Code, ein Editor-Assistent, Ihr eigenes Skript),
kann sich damit verbinden und die IDE fragen, was sie weiß: auf welches
Projekt sie gerichtet ist, was ausgeliefert wird, was läuft, was Sie
bearbeiten, wo ein Name deklariert ist, was zuletzt fehlgeschlagen ist.
Er ist **von Bauart nur lesend**: Der Build schlägt fehl, sobald eine
Klasse im Paket des Agent Port auch nur eine Möglichkeit benennt, einen
Prozess zu starten, eine Datei zu schreiben oder einen Lauf anzuhalten.

## 1. Starten

**Tun:** Extras ▸ **Agent Port (MCP)…** (die Auswahl startet den Port),
dann **Konfiguration kopieren**.

**Sehen:** Ein Dialog mit dem Endpunkt (nur Loopback, ein frischer
Port), einem Bearer-Token für diesen einen Start und einer fertigen
Client-Konfiguration:

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

Fügen Sie sie in die `.mcp.json` Ihres Agenten ein. Das Token existiert
nur in diesem Dialog — es wird nie protokolliert oder gespeichert — und
stirbt mit dem Port. **Agent Port stoppen** beendet ihn, ebenso das
Beenden der IDE. Solange er lauscht, zeigt die Statuszeile
**⌁ Agent Port :N** — ein Port, der Ihre IDE lesen kann, ist nie
unsichtbar; der Kurzhinweis des Zeichens zählt die verbundenen Agenten
mit offenem Stream, und ein Klick öffnet den Dialog wieder
(Konfiguration oder Stoppen).

## 2. Die Werkzeuge

Jedes Werkzeug antwortet mit einem Text für Menschen UND einem typisierten
`structuredContent` unter einem deklarierten `outputSchema` (der Build
prüft das Schema gegen die echte Ausgabe) und ist mit
`readOnlyHint: true` annotiert.

| Werkzeug | Was es beantwortet | Argumente |
|------|-----------------|-----------|
| `ide_context` | Die ganze Lagebeschreibung in einem Aufruf: Projekt, Toolchain, Server, Läufe, die bearbeitete Datei, der letzte Fehlschlag, die Anzahl der Diagnosen | — |
| `project_state` | Das angezielte Projekt: Name, Verzeichnis, Git-Branch, erkannte Art, Node-Paketmanager | — |
| `run_history` | Starts und Enden aus dem Flugschreiber, das Neueste zuerst, jedes Ende mit Befehl, Code und Dauer; ein Lauf, den Sie selbst angehalten haben, liest sich `stopped`, nie `failed` | `limit` |
| `live_servers` | Jeder Entwicklungsserver, von dem die IDE weiß, dass er ausliefert, mit seiner URL | — |
| `live_runs` | Jeder gerade laufende Befehl (das, was das ■ der Werkzeugleiste anhalten würde), mit seiner Startzeit | — |
| `last_failure` | Der jüngste fehlgeschlagene Lauf: Gerät, Befehl, Exit-Code, bis zu fünf Fehlerzeilen | — |
| `diagnostics` | Was Linter und Prüfer derzeit melden | `file` (Teilstring-Filter) |
| `find_symbol` | Wo ein Name deklariert ist — derselbe Index wie Gehe zu Symbol… (⌥⇧⌘O) | `query`, `limit` |
| `outline` | Die Struktur einer Datei — die Einträge des Navigators selbst | `file` |
| `search_text` | Zeilen, die eine wörtliche Zeichenkette enthalten, ohne Beachtung der Groß- und Kleinschreibung, begrenzt und mit jeder Kappung gemeldet; `.env`-Dateien, rc-Dateien von Paketmanagern und private Schlüssel werden nie durchsucht | `query`, `limit` |
| `editor_state` | Die bearbeitete Datei (der Editor-Tab mit Fokus, sonst der im Editorbereich sichtbare) und jeder offene Tab, ungespeicherte markiert | — |
| `rack_devices` | Die im Task-Rack eingebauten Geräte, in ihrer Reihenfolge | — |

Jede Liste ist begrenzt und sagt das: `find_symbol` und `outline` melden
einen unvollständigen Index, `search_text` meldet `truncated` nur, wenn es
einen weiteren Treffer gibt, `run_history` meldet, wenn ältere Ereignisse
weggelassen wurden.

## 3. Ressourcen, Prompts und der Stream

Dieselben Antworten lassen sich als Ressourcen durchsuchen, die ein Agent
als Kontext anhängt — `nmox://context`, `nmox://project`,
`nmox://history`, `nmox://servers`, `nmox://runs`, `nmox://editor`,
`nmox://last-failure`, `nmox://diagnostics`, `nmox://devices` — dazu
zwei Vorlagen für die Werkzeuge, die ein Argument nehmen:
`nmox://outline/{file}` und `nmox://search/{query}` (prozentkodiert).
Der Text einer Ressource ist das strukturierte JSON ihres Werkzeugs,
Byte für Byte.

Ein Agent, der lieber benachrichtigt wird, statt erneut zu fragen, kann
**abonnieren**: `resources/subscribe` auf eine dieser URIs, und der
GET-Stream des Ports (der Kanal vom Server zum Client bei Streamable
HTTP, `Accept:
text/event-stream`, dasselbe Token, kein `Origin`) trägt einen
`notifications/resources/updated`-Frame in dem Moment, in dem sich das
Dahinterliegende ändert — ein Lauf startet, und `nmox://runs` wird
angekündigt, ein Server geht live, und `nmox://servers` wird es, ein
Linter meldet etwas, und `nmox://diagnostics` wird es, ein Tab wechselt
oder eine Datei wird gespeichert, und `nmox://editor` wird es;
`nmox://context` folgt allen. Der Frame nennt die URI und sonst nichts;
der Agent liest erneut, was ihn interessiert. Auch eine Gliederung, die
ein Agent angehängt hat, folgt ihrer Datei: Abonnieren Sie
`nmox://outline/src/app.ts`, und der Port kündigt diese URI an, wenn sich
die Datei auf der Platte ändert (Speichern, Formatieren, ein Generator),
und noch einmal, falls sie verschwindet — eine reguläre Datei im
angezielten Projekt, höchstens zweiunddreißig davon, alle zwei Sekunden
abgefragt; ein Pfad außerhalb des Projekts ergibt `-32002` und wird nie
gelesen.

Drei Prompts falten den aktuellen Zustand in eine Frage: `diagnose_failure`
(der letzte Fehlschlag), `review_setup` (der ganze Kontext) und `where_is`
— der eine mit Argument, `name` —, der die Symboltreffer für diesen Namen
einfaltet.

Ein Agent, der dieses Argument oder das `{file}` der Gliederungsvorlage
ausfüllt, kann vorher fragen: `completion/complete` (das vierte Primitiv
der Spezifikation) beantwortet `name` von `where_is` aus dem Symbolindex
(dieselben Treffer, die `find_symbol` liefert, ohne Dubletten,
Präfixtreffer zuerst) und `{file}` aus den Dateien des Projekts selbst
(erst Präfixtreffer, dann enthaltende; die Ausschlussliste der Suche
gilt, `node_modules` wird also nie vervollständigt) — höchstens 100
Werte, `hasMore`, wenn die Kappung gegriffen hat, und `total` nur, wenn
die Zahl exakt ist (eine Dateiliste ist es immer; jenseits der Kappung
nennt der Symbolindex nur eine Untergrenze, also steht dort lieber keine
Zahl als eine falsche). Die wörtliche Zeichenkette der Suchvorlage kann
alles sein, sie wird also zu nichts vervollständigt; ein unbekannter
Prompt, eine unbekannte Vorlage oder ein unbekannter Argumentname wird mit
`-32602` abgelehnt.

Derselbe Stream trägt **Protokollmeldungen**: Jede Zeile, die ein Lauf
ausgibt, kommt als `notifications/message` mit dem Lauf als `logger` an
— Lebenszyklus auf `info` (`$ npm run build`, `[exit 0]`, `[exit 143]
stopped`; ein fehlgeschlagenes Ende auf `error`), stderr auf `warning`,
gewöhnliche Ausgabe auf `debug`. Die Stufe beginnt bei `info`, ein Agent
hört also Läufe beginnen und enden und sonst nichts, bis er fragt:
`logging/setLevel` mit `debug` öffnet den Feuerwehrschlauch. Ein Build,
der schneller ausgibt, als der Client liest, lässt den Speicher des Ports
nie wachsen — jenseits von tausend ungeschriebenen Zeilen wird der
Überlauf gezählt und als eine `warning`-Zeile angekündigt, nie still
verloren. Eine Stufe, die die Spezifikation nicht nennt, wird mit
`-32602` abgelehnt.

## 4. Der Rundgang, von Hand

Mit dem Token in einer Shell-Variablen (nie auf einer Befehlszeile, die
Sie irgendwo einfügen würden):

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_symbol","arguments":{"query":"checkout"}}}'
```

**Sehen:** `checkout (function) — src/cart.js:12`, und dasselbe als
`structuredContent.hits[0]`.

Der Stream, von Hand: Öffnen Sie ihn in einer Shell und abonnieren Sie
aus einer anderen —

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

**Sehen:** `: connected`, dann alle fünfzehn Sekunden `: keepalive`;
drücken Sie ▶, und die erste Shell gibt `notifications/resources/updated`
für `nmox://runs` aus sowie jede Zeile, die der Lauf ausgibt, als
`notifications/message` (`$ npm run dev` auf `info`, die Ausgabe auf
`debug`); drücken Sie ■, und `[exit 143] stopped` kommt auf `info` an.

Denselben Rundgang mit dem **offiziellen Client**, alle Primitive auf
einmal, liefert das Repository mit: `scripts/agent-port-walk.mjs` (sein
Kopfkommentar sagt, wie Sie `@modelcontextprotocol/sdk` in einem
Wegwerfverzeichnis installieren und wohin URL und Token gehören —
Shell-Variablen, nie eine Befehlszeile). Es gibt eine Zeile je Schritt
aus und endet mit WALK CLEAN oder mit der Zahl der Überraschungen als
Exit-Code (bei einem Ablehnungsschritt zählt eine ANTWORT als
Überraschung), sodass ein CI-Job es auswerten kann; drücken Sie ▶ und ■
in der IDE, während es lauscht, und die Protokollmeldungen kommen an.

## 5. Die Ablehnungen sind Funktionen

| Sie tun | Der Port sagt |
|--------|---------------|
| Aufruf ohne Token oder mit einem veralteten | `401` — sonst nichts, nicht einmal die Werkzeugliste |
| Aufruf aus einer Seite im Browser (irgendein `Origin`) | `403` |
| Ein einfaches `GET` | `405` — der Port ist keine Seite; bedient wird nur das SSE-`GET` (mit `Accept: text/event-stream`) als Abonnement-Stream |
| `nmox://nonesuch` abonnieren, oder eine Gliederung außerhalb des Projekts | JSON-RPC `-32002` (Ressource nicht gefunden) |
| Eine dreiunddreißigste Gliederung abonnieren | `-32602`, mit Nennung der Grenze |
| `nmox://nonesuch` lesen | JSON-RPC `-32002` (Ressource nicht gefunden) |
| `where_is` ohne `name` fragen | `-32602`, mit Nennung des fehlenden Arguments |
| Eine Datei außerhalb des Projekts erfragen (`../../.zshrc`) | `outline` lehnt ab — *außerhalb des angezielten Projekts* — und liest sie nie |
| Nach einem Wert suchen, der in `.env` (oder `app.env`), `.npmrc`, `.htpasswd`, `secrets.yaml`, `credentials.json` oder einer `.pem` steht — oder deren Gliederung erfragen | nichts — diese Dateien werden nie durchsucht, nie gezählt, nie vervollständigt, und `outline` lehnt sie namentlich ab; das eigene env-Gesetz der IDE (der Name eines Schlüssels, nie sein Wert) gilt auch für Agenten |
| Die Protokollstufe auf `loud` setzen | `-32602`, mit Nennung der acht Stufen |
| Ihn bitten, etwas auszuführen, zu schreiben oder anzuhalten | ein solches Werkzeug gibt es nicht; der Ledger-Test sorgt dafür, dass es so bleibt |

Diese letzte Zeile ist das Konzept. Ein Agent, der Ihren Server starten
kann, kann ihn auch anhalten, und ein Agent, der schreiben kann, kann
auch löschen; der Agent Port bleibt ein Weg zu FRAGEN. Bringt eine
künftige Version eine Möglichkeit zum Ausführen, kommt sie mit einem
eigenen Zustimmungskonzept, so wie es der nach außen gerichtete
Datenfluss von KVASIR bekommen hat.

Siehe auch: Station 24 der Kitchen Sink und den Absatz zum Agent Port im
Benutzerhandbuch.
