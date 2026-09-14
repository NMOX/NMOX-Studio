# Samouczki NMOX Studio

<!-- languages -->
[English](README.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Русский](README.ru.md) · [Українська](README.uk.md) · **Polski** · [Português (Brasil)](README.pt.md) · [Bahasa Indonesia](README.id.md) · [Filipino](README.tl.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh.md) · [हिन्दी](README.hi.md) · [עברית](README.he.md) · [العربية](README.ar.md)
<!-- /languages -->

Krótkie przejścia do samodzielnego wykonania, po systemach, które
odróżniają NMOX Studio od zwykłego IDE. Każde mieści się w jednym
posiedzeniu — otwórz okno, przejdź kroki i już naprawdę użyłeś funkcji.

Szerokie omówienie (instalacja, każde menu, każda siatka bezpieczeństwa)
znajdziesz w [Podręczniku użytkownika](../user-guide.pl.md). Pełna lista
urządzeń jest w [devices.md](../devices.md).

## Systemy

| Samouczek | Co zrobisz | Jak otworzyć |
|----------|----------------|------------|
| [Stojak zadań](the-task-rack.pl.md) | Połączysz układ uruchomienie→monitor i zobaczysz, jak odpala | ⌘9 / karta Stojak zadań |
| [Własne urządzenie](your-own-device.pl.md) | Dodasz urządzenie do stojaka w edytorze tekstu — bez Javy, bez restartu | `~/.nmox/devices.d/` |
| [Stanowisko pracy](workbench.pl.md) | Użyjesz bazy, żeby przeskakiwać między projektami i narzędziami | ⌥⌘0 |
| [Studio projektu](project-studio.pl.md) | Postawisz projekt i uruchomisz go bez terminala | karta Studio projektu |
| [Studio API](api-studio.pl.md) | Wyślesz żądanie, sprawdzisz je asercją i odczytasz ocenę bezpieczeństwa | ⌥⌘8 |
| [Studio baz danych](db-studio.pl.md) | Połączysz się z SQLite i zmienisz wiersz w siatce | ⌥⌘7 |
| [Studio kontraktów](contract-studio.pl.md) | Skompilujesz kontrakt, wdrożysz go na lokalny łańcuch i wywołasz | ⌥⌘6 (Web3) |
| [Projektant infrastruktury](infra-designer.pl.md) | Narysujesz droplet z firewallem i zrobisz próbne wdrożenie | ⌥⌘9 |
| [Studio bloków](block-studio.pl.md) | Zbudujesz Web Component z zazębiających się bloków | ⌥⌘5 |
| [Edycja wielu języków i debugowanie](polyglot-editing-and-debugging.pl.md) | Postawisz pułapkę w aplikacji Node i zatrzymasz się na niej | dowolny otwarty projekt |
| [Od przeglądarki do źródła](browser-to-source.pl.md) | Klikniesz element na stronie, trafisz do jego źródła i zmienisz mu styl z DevTools | ⌥⌘4 → DevTools → DOM |
| [Agent Port (MCP)](agent-port.pl.md) | Wycelujesz agenta SI w żywy stan IDE — tylko do odczytu z samej konstrukcji | Narzędzia ▸ Agent Port (MCP)… |
| [Panel Docker](docker-panel.pl.md) | Obejrzysz kontenery i zdockeryzujesz projekt | karta Panel Docker |
| [Tablica zadań i sprinty](task-board.pl.md) | Poprowadzisz kanban z zegarem pracy, standupem jednym kliknięciem i wykresem spalania sprintu — wszystko z jednego wersjonowanego pliku | ⌥⌘1 |
| [Pokaż to sali](show-it-to-a-room.pl.md) | Będziesz prezentować, udostępniać i robić zrzuty wprost z IDE — od trybu prezentacji po kopiowanie drzewa projektu jako Markdown | Widok ▸ Tryb prezentacji |
| [KVASIR](kvasir.pl.md) | Zapytasz SI, dlaczego uruchomienie padło | Stojak → KVASIR |
| [Wyjaśnij wszystko](explain-anything.pl.md) | Użyjesz czterech obliczy KVASIR: uruchomień, kodu, odpowiedzi API i błędów bazy | wszędzie, gdzie coś pada |
| [Przesiadka z Postmana](migrating-from-postman.pl.md) | Zaimportujesz kolekcje, przechwycenia HAR i więcej — sekrety trafiają do pęku kluczy | ⌥⌘8 → Importuj… |
| [Image Kit (Web)](image-kit.pl.md) | Skompresujesz obrazy projektu: mniejsze JPEG-i, bliźniacze WebP, uczciwy raport | Plik ▸ Dodaj do projektu ▸ Image Kit (Web)… |
| [Przestrzenie nauki](learning-spaces.pl.md) | Postawisz piaskownicę z przewodnikiem i żywym REPL-em | Nowa przestrzeń nauki… |
| [Kreatory i zestawy](wizards-and-kits.pl.md) | Dodasz PWA, pliki standardów albo rusztowania klasycznego webu | Plik ▸ Dodaj do projektu |

> **Słowo o skrótach.** Na macOS studia mieszkają w rodzinie `⌥⌘`
> (Option-Command) — `⌥⌘6`–`⌥⌘9`, `⌥⌘5`, `⌥⌘0` — bo zwykłe skróty `⇧⌘`
> zajmuje platforma. Na Linuksie i Windows modyfikatorem jest `Alt+`;
> menu (Okno ▸ …) działają zawsze, niezależnie od tego.

Pierwsze uruchomienie pokazuje trzy karty — Witamy, Stojak zadań
i Przeglądarkę — a obok nich zadokowane są Studio projektu, Stanowisko
pracy i Eksplorator NPM. Każde inne okno jest o jeden skrót stąd
i figuruje w kolumnie NARZĘDZIA na stronie Witamy.
