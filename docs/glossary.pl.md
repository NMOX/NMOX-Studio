# Słowniczek

<!-- languages -->
[English](glossary.md) · [Español](glossary.es.md) · [Français](glossary.fr.md) · [Deutsch](glossary.de.md) · [Русский](glossary.ru.md) · [Українська](glossary.uk.md) · **Polski** · [Português (Brasil)](glossary.pt.md) · [Bahasa Indonesia](glossary.id.md) · [Filipino](glossary.tl.md) · [Tiếng Việt](glossary.vi.md) · [简体中文](glossary.zh.md) · [हिन्दी](glossary.hi.md) · [עברית](glossary.he.md) · [العربية](glossary.ar.md)
<!-- /languages -->

Słowa, których używa NMOX Studio, a których nie używa inne IDE, oraz
pojęcia NetBeans, które przebijają spod spodu. Każde hasło mówi, co słowo
znaczy tutaj i gdzie przeczytać więcej.

<a id="the-rack"></a>
## Stojak

**Stojak zadań** (Task Rack, ⌘9) — Okno, w którym działają twoje narzędzia. Każde zadanie (instalacja, budowanie, testy, serwowanie,
lintowanie, wdrożenie) jest *urządzeniem* zamontowanym w stojaku, jak sprzęt w studiu nagraniowym.
[Podręcznik §4](user-guide.pl.md#4-the-task-rack).

**Urządzenie** (Device) — Jedno narzędzie na stojaku, na przykład VELOCITY (Vite), VERITAS (testy) albo
PURITY (lint). Urządzenie ma panel przedni (*płytę czołową*) z pokrętłami,
przyciskami, diodami i małym wyświetlaczem oraz panel tylny z *gniazdami*.
Wbudowanych urządzeń jest 53, wymienia je [lista urządzeń](devices.md).
Możesz dodać własne jako plik JSON w `~/.nmox/devices.d/`
([pliki urządzeń](device-files.md)).

**Płyta czołowa** (Faceplate) — Panel przedni urządzenia. Naciśnij **Tab** w stojaku, aby go obrócić i zobaczyć
panel tylny.

**Gniazdo** (Jack) — Złącze na panelu tylnym urządzenia. Gniazda wyjściowe wysyłają sygnały, a gniazda
wejściowe je odbierają. Są trzy rodzaje sygnału:
- **Wyzwalacz** (trigger) to jeden impuls: „budowanie się skończyło”, „OK”, „FAIL”.
- **Bramka** (gate) jest włączona albo wyłączona: „serwer działa”.
- **Dane** (data) niosą tekst, na przykład adres URL albo wiersz wyjścia.

**Kabel** (Cable) — Połączenie gniazda wyjściowego z gniazdem wejściowym. Połącz gniazdo OK
budowania z gniazdem RUN uruchamiacza testów, a testy ruszą za każdym razem, gdy
budowanie się powiedzie. Aby połączyć dwa gniazda, przeciągnij od jednego do
drugiego albo kliknij jedno, a potem drugie.

**Patch** — Cały stojak: jego urządzenia, ich ustawienia i ich kable. Zapisywany obok
projektu jako `.nmoxrack.json`, więc warto go wrzucić do repozytorium.

**Preset** — Gotowy patch, który wczytasz z menu **Presety** stojaka, na przykład
*Bramka wydania* albo *Pętla E2E*. Zapisz dowolny patch w `~/.nmox/presets.d/`,
a też pojawi się w tym menu.

**Stojak startowy** (Starter rack) — Patch, który projekt dostaje przy pierwszym otwarciu, dobrany do rodzaju
projektu: konsola Vite dla aplikacji Vite, tory uruchamiania, debugowania
i testów dla pakietu Cargo, i tak dalej.

**Tor** (Lane) — Dwa znaczenia, oba o uruchamianiu:
- **Potok**: łańcuch urządzeń połączonych kablami, na przykład instalacja →
  budowanie → testy. Kilka torów może działać obok siebie, a QUORUM czeka, aż
  wszystkie się skończą.
- **Tor AUTO** urządzenia: polecenie, które wybiera dla tego projektu. W położeniu
  AUTO urządzenie testowe uruchamia `npm test` w projekcie Node, a
  `cargo test` w projekcie Rust.

**Udostępnij… / Importuj…** (Share… / Import…) — Zapisz stojak do pliku dla kogoś innego albo wczytaj stojak od kogoś. Zanim
cokolwiek zostanie zamontowane, Import pokazuje wszystko, co zawiera plik, a
każde urządzenie przychodzi wyłączone.

**Galeria stojaków** (Rack Gallery) — **Narzędzia ▸ Galeria stojaków…** wymienia stojaki społeczności, presety, stojaki startowe
i twoje zapisane stojaki. Każda pozycja pokazuje, do czego służy i jakich narzędzi
potrzebuje. [Stojaki społeczności](racks.md).

<a id="projects-and-running"></a>
## Projekty i uruchamianie

**Celowanie** / **wycelowany projekt** (Aim / aimed project) — Projekt, nad którym IDE właśnie pracuje. Otwarcie projektu celuje w niego:
stojak, studia, pasek stanu i Uruchom idą za wycelowanym projektem. Wycelowanie
w inny przełącza je wszystkie, a to, co jeszcze działa, zostaje najpierw
zatrzymane, po zapytaniu.

**Zaufanie do obszaru roboczego** (Workspace Trust) — Pytanie, które NMOX Studio zadaje, zanim po raz pierwszy uruchomi własny kod projektu
(skrypty, budowanie, testy). Jeśli odpowiesz
**Zachowaj bezpieczeństwo**, nic z projektu się nie uruchomi. Twoja odpowiedź jest
zapamiętywana dla każdego katalogu.

**▶ i ■** — Uruchom i Zatrzymaj na pasku narzędzi. ▶ (F6) uruchamia wycelowany projekt. ■ (⌥⌘.)
zatrzymuje każde polecenie, które NMOX Studio uruchomiło za ciebie.

**Znacznik ⇄** / **serwowanie** (⇄ chip / serving) — Gdy coś, co uruchamiasz, wypisze lokalny adres, na przykład
`http://localhost:5173/`, adres pojawia się na pasku stanu za symbolem ⇄.
Taki działający serwer to *serwowanie*. Kliknij adres, aby otworzyć
go w Przeglądarce. Szybkie wyszukiwanie wymienia serwowania pod *Aktywne serwery*.

**Eksperyment** (Experiment) — Jednorazowy projekt utworzony z szablonu w `~/.nmox/experiments`. Jego
zależności są już zainstalowane i jest już zaufany. **Awansuj** go,
aby go zachować, albo **Odrzuć**. **Plik ▸ Nowy eksperyment…**.

**Przestrzeń nauki** (Learning space) — Kurs z przewodnikiem dla języka albo frameworka. Tworzy prawdziwy
projekt, przewodnik i stojak z działającym REPL, a **Plik ▸ Sprawdź moją pracę**
sprawdza twoje ćwiczenia. Jest ich 93. **Plik ▸ Nowa przestrzeń nauki…**.

**PREFLIGHT** — Urządzenie do kontroli przed wysyłką. Uruchamia kontrole, które definiuje twój
projekt (lint, typy, testy, budowanie), jako jedno „przechodzi albo nie”.

**Pierwsze kroki** (First Steps) — Lista kontrolna na karcie Witamy. Kroki odhaczają się same, gdy je
wykonujesz, i nigdy się nie cofają.

<a id="the-windows"></a>
## Okna

**Studio** — Okno z własnym narzędziem do jednego rodzaju pracy. Jest ich
pięć: **Studio API** (⌥⌘8), **Studio baz danych** (⌥⌘7), **Studio kontraktów** (⌥⌘6,
inteligentne kontrakty), **Studio bloków** (⌥⌘5, komponenty webowe składane z bloków)
i **Projektant infrastruktury** (⌥⌘9, infrastruktura w chmurze). Każde zapisuje swoją pracę
obok projektu w pliku `.nmox*.json`. **Studio projektu** nosi podobną nazwę, ale jest
drzewem plików i szablonami projektów.

**Stanowisko pracy** (Workbench, ⌥⌘0) — Baza: co działa, co jest otwarte oraz twoje ostatnie projekty
i pliki.

**Tablica zadań** (Task Board, ⌥⌘1) — Tablica kanban dla każdego projektu, ze sprintami i zegarem czasu pracy, zapisywana jako
`.nmoxtasks.json`.

**Witamy** (Welcome) — Karta startowa: czynności na początek, ostatnie projekty, kolumna *NARZĘDZIA*,
która wymienia każde okno, oraz Pierwsze kroki.

<a id="ai"></a>
## SI

**KVASIR** — Nazwa funkcji SI w NMOX Studio: pytanie, edycja, uzupełnianie, wyjaśnianie,
szkic opisu commita. Działa z Claude, ChatGPT albo Gemini na twoim
własnym kluczu API, przechowywanym w pęku kluczy systemu. Każda funkcja raz prosi o
twoją zgodę i dokładnie nazywa, co wyśle. Nic nie jest wysyłane,
dopóki nie użyjesz funkcji. Wcześniejsze wydania nazywały ją ORACLE.

**Agent Port** — **Narzędzia ▸ Agent Port (MCP)…** daje agentowi SI działającemu na twoim
komputerze, na przykład asystentowi programisty, dostęp tylko do odczytu do stanu IDE
przez MCP: otwarte pliki, diagnostyka, uruchomienia, symbole. Jest tylko do odczytu
z założenia i nasłuchuje wyłącznie na twoim własnym komputerze.
[Samouczek](tutorials/agent-port.md).

<a id="netbeans-terms-you-may-see"></a>
## Pojęcia NetBeans, które możesz zobaczyć

NMOX Studio jest zbudowane na platformie NetBeans i kilka jej słów
przebija spod spodu.

**Moduł** / **NBM** — Część aplikacji. *NBM* to plik, w którym moduł jest dostarczany.
**Narzędzia ▸ Wtyczki** instaluje aktualizacje moduł po module.

**Centrum aktualizacji** (Update center) — Miejsce, z którego **Narzędzia ▸ Wtyczki ▸ Aktualizacje** bierze nowe wersje modułów
NMOX Studio. Czyta katalog publikowany z każdym wydaniem na GitHubie.

**userdir** — Katalog, w którym NMOX Studio trzyma ustawienia, układ okien, dzienniki i
zainstalowane aktualizacje. Aby go znaleźć, otwórz **Pomoc ▸ About** (w macOS: menu
aplikacji NMOX Studio). Jego dziennik jest w
`var/log/messages.log`. Aby zacząć od czystych ustawień, uruchom z
`--userdir <pusty katalog>`.

**Opcje** / **Settings…** (Options) — Okno preferencji. W Windows i Linuksie to **Narzędzia ▸ Opcje**,
a w macOS **NMOX Studio ▸ Settings…**.

**Elementy do zrobienia** (Action Items) — Okno z listą problemów znalezionych w projekcie, łącznie z wynikami
lintowania i sprawdzania typów ze stojaka. Kliknij problem, aby przejść do tego wiersza.
