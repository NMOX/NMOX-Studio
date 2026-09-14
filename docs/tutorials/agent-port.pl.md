# Agent Port (MCP)

<!-- languages -->
[English](agent-port.md) · [Español](agent-port.es.md) · [Français](agent-port.fr.md) · [Deutsch](agent-port.de.md) · [Русский](agent-port.ru.md) · [Українська](agent-port.uk.md) · **Polski** · [Português (Brasil)](agent-port.pt.md) · [Bahasa Indonesia](agent-port.id.md) · [Filipino](agent-port.tl.md) · [Tiếng Việt](agent-port.vi.md) · [简体中文](agent-port.zh.md) · [हिन्दी](agent-port.hi.md) · [עברית](agent-port.he.md) · [العربية](agent-port.ar.md)
<!-- /languages -->

*Wyceluj agenta SI w swoje IDE — i pozwól mu CZYTAĆ, nigdy uruchamiać.*

![Okno Agent Port — punkt końcowy na pętli zwrotnej, token tworzony przy każdym starcie (na tym zrzucie zastępczy) i gotowa konfiguracja klienta do skopiowania](../images/tabs/agent-port.png)

NMOX Studio ma w komplecie serwer Model Context Protocol. Każdy agent,
który mówi MCP (Claude Code, asystent w edytorze, twój własny skrypt),
może się z nim połączyć i zapytać IDE o to, co wie: który projekt jest
wycelowany, co serwuje, co działa, co edytujesz, gdzie zadeklarowano
nazwę, co ostatnio padło. Jest **tylko do odczytu z konstrukcji**:
budowanie się nie powiedzie, jeśli jakakolwiek klasa w pakiecie Agent
Port choćby nazwie sposób na uruchomienie procesu, zapis pliku albo
zatrzymanie uruchomienia.

## 1. Uruchom go

**Zrób:** Narzędzia ▸ **Agent Port (MCP)…** (wybranie go uruchamia port),
potem **Kopiuj konfigurację**.

**Zobacz:** okno z punktem końcowym (tylko pętla zwrotna, świeży port),
tokenem okaziciela tworzonym przy każdym starcie i gotową konfiguracją
klienta:

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

Wklej ją do `.mcp.json` swojego agenta. Token istnieje tylko w tym
oknie — nigdy nie trafia do dziennika ani na dysk — i umiera razem
z portem. **Zatrzymaj Agent Port** go kończy; zamknięcie IDE również.
Póki nasłuchuje, pasek stanu pokazuje **⌁ agent port :N** — port, który
może czytać twoje IDE, nigdy nie jest niewidoczny; podpowiedź znacznika
liczy agentów podłączonych do strumienia, a kliknięcie ponownie otwiera
okno (konfiguracja albo zatrzymanie).

## 2. Narzędzia

Każde narzędzie odpowiada tekstem dla człowieka ORAZ typowanym
`structuredContent` pod zadeklarowanym `outputSchema` (budowanie
sprawdza schemat na prawdziwym wyjściu) i ma adnotację
`readOnlyHint: true`.

| Narzędzie | Na co odpowiada | Argumenty |
|------|-----------------|-----------|
| `ide_context` | Cały orientacyjny obraz w jednym wywołaniu: projekt, zestaw narzędzi, serwery, uruchomienia, edytowany plik, ostatnia porażka, liczba diagnostyk | — |
| `project_state` | Wycelowany projekt: nazwa, katalog, gałąź git, wykryty rodzaj, menedżer pakietów Node | — |
| `run_history` | Starty i wyjścia z rejestratora lotu, od najnowszych, każde wyjście z poleceniem, kodem i czasem trwania; uruchomienie, które zatrzymałeś sam, czyta się jako `stopped`, nigdy `failed` | `limit` |
| `live_servers` | Każdy serwer deweloperski, o którym IDE wie, że serwuje, z jego adresem | — |
| `live_runs` | Każde polecenie działające w tej chwili (to, co zatrzymałoby ■ na pasku narzędzi), z czasem startu | — |
| `last_failure` | Najnowsze nieudane uruchomienie: urządzenie, polecenie, kod wyjścia, do pięciu wierszy błędu | — |
| `diagnostics` | Co w tej chwili zgłaszają lintery i narzędzia sprawdzające | `file` (filtr podciągu) |
| `find_symbol` | Gdzie zadeklarowano nazwę — ten sam indeks co Przejdź do symbolu (⌥⇧⌘O) | `query`, `limit` |
| `outline` | Struktura jednego pliku — te same elementy co w Nawigatorze | `file` |
| `search_text` | Wiersze zawierające dosłowny tekst, bez rozróżniania wielkości liter, z limitem i z każdym przycięciem zgłoszonym; pliki `.env`, pliki rc menedżerów pakietów i klucze prywatne nigdy nie są przeszukiwane | `query`, `limit` |
| `editor_state` | Edytowany plik (karta edytora z fokusem, a jeśli jej nie ma, ta pokazana w obszarze edytora) i każda otwarta karta, z oznaczeniem niezapisanych | — |
| `rack_devices` | Urządzenia zamontowane na stojaku zadań, po kolei | — |

Każda lista ma limit i to mówi: `find_symbol` i `outline` zgłaszają
częściowy indeks, `search_text` zgłasza `truncated` tylko wtedy, gdy
istnieje kolejne dopasowanie, a `run_history` — gdy starsze zdarzenia
zostały pominięte.

## 3. Zasoby, podpowiedzi i strumień

Te same odpowiedzi można przeglądać jako zasoby, które agent dołącza
jako kontekst — `nmox://context`, `nmox://project`, `nmox://history`,
`nmox://servers`, `nmox://runs`, `nmox://editor`,
`nmox://last-failure`, `nmox://diagnostics`, `nmox://devices` — plus
dwa szablony dla narzędzi, które przyjmują argument:
`nmox://outline/{file}` i `nmox://search/{query}` (kodowane procentowo).
Tekst zasobu to ustrukturyzowany JSON jego narzędzia, bajt w bajt.

Agent, który woli, żeby mu powiedziano, niż pytać znowu, może się
**zasubskrybować**: `resources/subscribe` na dowolnym z tych URI,
a strumień GET portu (kanał serwer–klient z Streamable HTTP, `Accept:
text/event-stream`, ten sam token, bez `Origin`) niesie ramkę
`notifications/resources/updated` w chwili, gdy zmieni się to, co za
nią stoi — startuje uruchomienie i ogłaszane jest `nmox://runs`, serwer
wstaje i ogłaszane jest `nmox://servers`, linter coś zgłasza
i ogłaszane jest `nmox://diagnostics`, zmienia się karta albo zostaje
zapisany plik i ogłaszane jest `nmox://editor`; `nmox://context` idzie
za nimi wszystkimi. Ramka nazywa URI i nic poza tym; agent czyta
ponownie to, co go interesuje. Konspekt, który agent dołączył, też idzie
za swoim plikiem: zasubskrybuj `nmox://outline/src/app.ts`, a port
ogłosi ten URI, gdy plik zmieni się na dysku (zapis, formatowanie,
generator), i jeszcze raz, jeśli zniknie — zwykły plik wewnątrz
wycelowanego projektu, najwyżej trzydzieści dwa takie, odpytywane co
dwie sekundy; ścieżka poza projektem to `-32002`, nigdy nieczytana.

Trzy podpowiedzi wkładają żywy stan w pytanie: `diagnose_failure`
(ostatnia porażka), `review_setup` (cały kontekst) i `where_is`
— ta jedna przyjmuje argument, `name` — która wkłada trafienia symboli
dla tej nazwy.

Agent wypełniający ten argument albo `{file}` szablonu konspektu może
najpierw zapytać: `completion/complete` (czwarty prymityw specyfikacji)
odpowiada na `name` z `where_is` z indeksu symboli (te same trafienia,
które zwraca `find_symbol`, bez powtórzeń, najpierw trafienia
prefiksowe) oraz na `{file}` z plików samego projektu (najpierw
trafienia prefiksowe, potem zawierające; obowiązuje lista pominięć
przeszukiwania, więc `node_modules` nigdy się nie uzupełnia) — najwyżej
100 wartości, `hasMore`, gdy limit je przyciął, i `total` tylko wtedy,
gdy liczba jest dokładna (lista plików zawsze jest; po przekroczeniu
limitu indeks symboli zna tylko dolną granicę, więc nie podaje żadnej
liczby zamiast błędnej). Dosłowny tekst szablonu wyszukiwania może być
czymkolwiek, więc nie uzupełnia się do niczego; nieznana podpowiedź,
szablon albo nazwa argumentu są odrzucane jako `-32602`.

Ten sam strumień niesie **komunikaty dziennika**: każdy wiersz, który
wypisuje każde uruchomienie, przychodzi jako `notifications/message`
z uruchomieniem jako `logger` — cykl życia na poziomie `info`
(`$ npm run build`, `[exit 0]`, `[exit 143] stopped`; nieudane wyjście
na poziomie `error`), stderr na `warning`, zwykłe wyjście na `debug`.
Poziom startuje od `info`, więc agent słyszy start i koniec uruchomień
i nic więcej, dopóki nie poprosi: `logging/setLevel` z `debug` otwiera
pełny strumień. Budowanie, które wypisuje szybciej, niż klient czyta,
nigdy nie powiększa pamięci portu — powyżej tysiąca niezapisanych
wierszy nadmiar jest liczony i ogłaszany jako jeden wiersz `warning`,
nigdy po cichu gubiony. Poziom, którego specyfikacja nie nazywa, jest
odrzucany jako `-32602`.

## 4. Przejście, ręcznie

Z tokenem w zmiennej powłoki (nigdy w wierszu poleceń, który
mógłbyś gdzieś wkleić):

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_symbol","arguments":{"query":"checkout"}}}'
```

**Zobacz:** `checkout (function) — src/cart.js:12`, i to samo jako
`structuredContent.hits[0]`.

Strumień, ręcznie: otwórz go w jednej powłoce i zasubskrybuj z drugiej —

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

**Zobacz:** `: connected`, potem `: keepalive` co piętnaście sekund;
naciśnij ▶, a pierwsza powłoka wypisze `notifications/resources/updated`
dla `nmox://runs` i każdy wiersz uruchomienia jako
`notifications/message` (`$ npm run dev` na `info`, wyjście na `debug`);
naciśnij ■, a `[exit 143] stopped` przyjdzie na `info`.

To samo przejście z **oficjalnym klientem**, wszystkimi prymitywami
naraz, jest w repozytorium: `scripts/agent-port-walk.mjs` (jego nagłówek
mówi, jak zainstalować `@modelcontextprotocol/sdk` w katalogu roboczym
i gdzie trafiają adres i token — zmienne powłoki, nigdy wiersz
poleceń). Wypisuje jeden wiersz na krok i kończy się napisem WALK CLEAN
albo liczbą niespodzianek jako kodem wyjścia (w kroku sprawdzającym
odmowę niespodzianką jest ODPOWIEDŹ), więc zadanie CI może to odczytać;
naciśnij ▶ i ■ w IDE, póki nasłuchuje, a komunikaty dziennika
przyjdą.

## 5. Odmowy to funkcje

| Robisz | Port odpowiada |
|--------|---------------|
| Wywołujesz bez tokenu albo z nieaktualnym | `401` — nic więcej, nawet listy narzędzi |
| Wywołujesz ze strony w przeglądarce (dowolny `Origin`) | `403` |
| Zwykły `GET` | `405` — port nie jest stroną; obsługiwany jest tylko `GET` SSE (z `Accept: text/event-stream`), jako strumień subskrypcji |
| Subskrybujesz `nmox://nonesuch` albo konspekt spoza projektu | JSON-RPC `-32002` (nie znaleziono zasobu) |
| Subskrybujesz trzydziesty trzeci konspekt | `-32602`, z nazwą limitu |
| Czytasz `nmox://nonesuch` | JSON-RPC `-32002` (nie znaleziono zasobu) |
| Pytasz `where_is` bez `name` | `-32602`, z nazwą brakującego argumentu |
| Prosisz o plik spoza projektu (`../../.zshrc`) | `outline` odmawia — *poza wycelowanym projektem* — i nigdy go nie czyta |
| Szukasz wartości, która mieszka w `.env` (albo `app.env`), `.npmrc`, `.htpasswd`, `secrets.yaml`, `credentials.json` czy `.pem` — albo prosisz o ich konspekt | nic — te pliki nigdy nie są przeszukiwane, liczone ani uzupełniane, a `outline` odmawia ich z nazwy; własne prawo IDE dotyczące środowiska (nazwa klucza, nigdy jego wartość) obowiązuje także agentów |
| Ustawiasz poziom dziennika na `loud` | `-32602`, z listą ośmiu poziomów |
| Prosisz, żeby coś uruchomił, zapisał albo zatrzymał | takiego narzędzia nie ma; test rejestru pilnuje, żeby tak zostało |

Ten ostatni wiersz to cały projekt. Agent, który może uruchomić twój
serwer, może go też zatrzymać, a agent, który może pisać, może też
usuwać; Agent Port pozostaje sposobem na PYTANIE. Jeśli przyszła wersja
doda powierzchnię wykonawczą, przyjdzie ona z własnym projektem zgody,
tak jak przyszedł wychodzący przepływ danych KVASIR-a.

Zobacz też: stację 24 w Kitchen Sink i akapit o Agent Port
w podręczniku użytkownika.
