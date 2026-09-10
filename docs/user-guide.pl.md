# NMOX Studio — Podręcznik użytkownika

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · **Polski** · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Tłumaczenie częściowe: rozdziały 1–10 są po polsku. Resztę znajdziesz w [pełnym podręczniku po angielsku](user-guide.md).

Jak używać produktu. Podręcznik omawia funkcje w kolejności, w jakiej je napotkasz: instalacja, pierwsze uruchomienie, projekty, stojak, studia, kreatory i siatki bezpieczeństwa.

---

<a id="1-install"></a>
## 1. Instalacja

**macOS (zalecane):**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Wiersz `brew trust` to jednorazowe potwierdzenie Homebrew dla dowolnego zewnętrznego tapa — przy aktualizacjach nikt nie zapyta ponownie. Aplikacja jest podpisana ad-hoc, ale nienotaryzowana, więc kopię w kwarantannie Gatekeeper odrzuciłby przy pierwszym uruchomieniu: cask sam usuwa atrybut kwarantanny w kroku `postflight` i pisze o tym w wyniku instalacji. Nic po cichu.

**Wszystko inne:** pobierz plik z [najnowszego wydania](https://github.com/NMOX/NMOX-Studio/releases/latest) — `.dmg` dla macOS, `-setup.exe` dla Windows, `.deb` dla Debiana/Ubuntu, zwykły `.tar.gz` dla Linuksa. Wszystkie cztery niosą własne środowisko uruchomieniowe Javy; niczego nie trzeba instalować wcześniej. `-portable.zip` to jedyny artefakt korzystający z twojej Javy (wymaga Javy 21+ w PATH albo uruchomienia z `--jdkhome <ścieżka-do-jdk>`).

> **macOS, pierwsze uruchomienie:** aplikacja jest podpisana ad-hoc, ale nienotaryzowana, więc Gatekeeper pyta przed uruchomieniem. Za pierwszym razem **kliknij aplikację prawym przyciskiem → Otwórz** i potwierdź albo wykonaj
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`. Każdy ze sposobów załatwia to na zawsze.

### Aktualizacja

IDE aktualizuje się samo: **Narzędzia ▸ Wtyczki ▸ Aktualizacje** proponuje moduły każdego nowszego wydania. Zainstaluj, uruchom ponownie na żądanie i gotowe — bez pobierania całej aplikacji od nowa. Uczciwe zastrzeżenie: dołączone środowisko Javy i program uruchamiający zmieniają się tylko z pełnym instalatorem, więc przy dużych skokach platformy nadal właściwa jest instalacja od nowa z pliku wydania.

<a id="2-first-launch"></a>
## 2. Pierwsze uruchomienie

Z terminala `nmoxstudio --open <katalog>` uruchamia aplikację z tym katalogiem otwartym jako projekt i wycelowanym w niego stojakiem — te same drzwi, które otwiera „Otwórz katalog…” na stronie powitalnej.

IDE otwiera się ze wszystkimi kartami zestawu przy obszarze edytora: **Witamy → Stojak zadań → Studio baz danych → Studio kontraktów → Projektant infrastruktury → Studio API → Panel Dockera** — każda główna powierzchnia o jedno kliknięcie od pierwszej minuty. W lewym doku: **Studio projektu** (drzewo plików i szablony), baza **Stanowisko pracy** oraz **Eksplorator NPM**. Powstaje katalog `~/NMOX` jako domyślna przestrzeń robocza; stojak wskazuje tam, dopóki nie otworzysz projektu.

![Pierwsze uruchomienie — strona powitalna ze wszystkimi otwartymi kartami](images/welcome.png)

Skróty warte nauczenia się pierwszego dnia (wszystkie są też wypisane na karcie powitalnej):

| Skrót | Otwiera |
|---|---|
| **⌘I** | Szybkie wyszukiwanie — sięga wszędzie |
| **⌘9** | Stojak zadań |
| **⌥⌘0** | Stanowisko pracy |
| **⌥⌘3** | Klient czatu IRC |
| **⌥⌘4** | Przeglądarka (wbudowany WebKit, z DevTools) |
| **⌥⌘5** | Studio bloków |
| **⌥⌘6** | Studio kontraktów |
| **⌥⌘7** | Studio baz danych |
| **⌥⌘8** | Studio API |
| **⌥⌘9** | Projektant infrastruktury |
| **⌘8** | Panel Dockera |
| **⌘7** | Struktura bieżącego pliku |
| **⇧⌘N / ⌥⌘O** | Nowy projekt… / Otwórz katalog… |
| **⇧⌘E / ⇧⌘L** | Nowy eksperyment… / Nowa przestrzeń nauki… |

<a id="3-projects"></a>
## 3. Projekty

**Otwieranie:** każdy katalog z jednym z 60 rozpoznawanych manifestów otwiera się jako prawdziwy projekt — `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `foundry.toml`, `bower.json`, `Gruntfile.js` i pokrewne — łącznie z manifestami łańcuchów kontraktowych: repozytorium Aiken (`aiken.toml`) albo Clarinet (`Clarinet.toml`) otwiera się z podpiętymi prawdziwymi torami. Zwykły katalog z HTML-em i znacznikami `<script>`, **bez** manifestu, też się otwiera — jako projekt STATIC: klasyczny web jest tu pełnoprawny, a nie błędem.

**Tworzenie:** *Nowy projekt…* oferuje prawdziwe rusztowania — Angular, Vue, Svelte, czysty JavaScript, Elixir/Phoenix, PHP Web (LEMP) i Klasyczny web (jQuery). Każde przychodzi z podpiętymi konfiguracjami lintera, formatowania i testów oraz z zainicjowanym repozytorium git: jeden commit rusztowania, który — gdy kreator wykona instalację za ciebie — zawiera też plik blokady, więc twój pierwszy `git status` jest czysty.

**Przełączanie jest bezpieczne:** jeśli urządzenia pracują (serwer deweloperski, obserwator), IDE pyta przed przełączeniem i zatrzymuje je czysto. Nic nie działa dalej za twoimi plecami — nigdy. Nawet wymuszone zamknięcie IDE nie osieroci procesu.

**Eksperymenty** to najszybszy sposób, by spróbować stosu. **Plik ▸ Nowy eksperyment…** (⇧⌘E) wybiera szablon i tworzy jednorazowy projekt w `~/.nmox/experiments`: bez gita, bez ostatnio używanych, już zaufany, z zainstalowanymi zależnościami — żeby **pierwsze uruchomienie po prostu zadziałało**. Otwiera się na własnym przewodniku `EXPERIMENT.md`, który mówi, co nacisnąć, który plik zmienić i gdzie mieszka inteligencja IDE dla tego stosu. Zachowaj to, z czego coś wyrosło: **Plik ▸ Eksperymenty…** ▸ **Awansuj** wynosi go na zewnątrz i inicjuje gita, **Powiel** tworzy obok kopię na drugie podejście, **Odrzuć** sprząta resztę. Półka pokazuje wiek każdego i jego zmierzony koszt na dysku. Wolisz drogę z przewodnikiem? Okno wysuwa na przód 93 przestrzenie nauki.

![Półka przestrzeni nauki — liczba, koszt na dysku, wiek i cały cykl życia](images/spaces-shelf.png)

![Świeży eksperyment Express: przewodnik otwarty, zależności zainstalowane, API już odpowiada](images/experiment-walkthrough.png)

**Uruchom, zbuduj, przetestuj — i zatrzymaj:** ▶ na pasku (F6) uruchamia projekt tak, jak uruchamia go jego zestaw narzędzi: skrypt `start`, jeśli package.json go ma, `cargo run`, `go run`, `dotnet run`, a dla katalogu z HTML-em mały serwer statyczny na pierwszym wolnym porcie od 8080. Zbuduj, Przetestuj i Wyczyść są obok i w menu Uruchom. Serwer deweloperski, który ogłosi swój adres, zapala wskaźnik ⇄ na pasku stanu i otwiera stronę we wbudowanej przeglądarce. Wszystko za pierwszym razem przechodzi przez pytanie o zaufanie do przestrzeni roboczej. Uruchomienie, które nie mogło wystartować, mówi to wprost i proponuje otwarcie Doktora środowiska. Aby zatrzymać: ■ na prawo od Debuguj (⌥⌘.) zatrzymuje naraz każde działające polecenie i mówi, co zatrzymał; **Uruchom ▸ Zatrzymaj** zatrzymuje jedno i proponuje potem **Powtórz**. ■ widzi wszystko, co produkt uruchamia za ciebie, łącznie z instalacjami; po najechaniu podpowiedź nazywa dokładnie to, co zatrzymałoby naciśnięcie, i od kiedy każde działa.

**`.env` wszędzie:** jeśli twój projekt ma `.env`, urządzenia uruchamiane ze stojaka dostają te zmienne. Zmień go, a pasek stanu odnotuje, że ponowne uruchomienia je podchwycą — działające procesy uczciwie zachowują swoje dawne środowisko.

<a id="4-the-task-rack"></a>
## 4. Stojak zadań

![Stojak zadań](images/tabs/the-task-rack.png)

Stojak jest sercem produktu. Każde narzędzie twojego procesu pracy — npm, bundler, uruchamiacz testów, serwer deweloperski, linter, git, wdrożenie — jest urządzeniem w stojaku: pokrętła wybierają zadanie, GO je uruchamia, diody pokazują stan, a wyświetlacz mówi słowami, co się stało.

![Stojak wycelowany w klasyczną stronę na jQuery — zestaw Classic Web Bench: MAESTRO, CRATE, DYNAMO (jego pokrętło TASK odczytało prawdziwy Gruntfile), IGNITION serwuje statykę, VITALS pilnuje jakości](images/task-rack.png)

**Podstawy:**

- **Dodawaj urządzenia**, przeciągając je z palety (ma kategorie i filtr wyszukiwania). Każde urządzenie ma swoją kartę *Jak używać*.
- **Uruchom coś**, naciskając przycisk GO urządzenia. Najpierw najedź na niego: podpowiedź pokaże dokładny wiersz poleceń, który zostanie wykonany. Żadnej magii.
- **Okabluj potok:** naciśnij **Tab**, aby obrócić stojak tyłem. Przeciągnij kabel krosowy z gniazda **OK** jednego urządzenia do gniazda **GO** następnego. Teraz `instalacja → budowa → testy` to jedno naciśnięcie: łańcuch idzie sam i zatrzymuje się na pierwszej porażce. Wyjście przewija się po luminoforowym ekranie urządzenia MONITOR.
- **Cofnij dowolną zmianę struktury** przez **⌘Z** — dodanie, usunięcie, przełożenie kabli. Usunięcie działającego urządzenia najpierw zatrzymuje jego proces.
- **Zestawy** dają cały okablowany stojak jednym kliknięciem — Ship Gate, Dev Intelligence, Monorepo Lanes, E2E Loop, LAMP Bench, Web3 Bench, Uptime Watch. Układy zapisują się per projekt automatycznie.

![Tab obraca stojak — kable krosowe prowadzą MAESTRO przez CRATE, DYNAMO i IGNITION do VITALS](images/rack-rear.png)

**Koordynacja, gdy potok rośnie:**

- **QUORUM** łączy tory: odpala się dopiero wtedy, gdy *wszystkie* jego podłączone wejścia zakończyły się powodzeniem — klasyczne „czekaj na linter I testy I sprawdzenie typów”.
- **Bramki ENABLE** na długo działających: wejście ENABLE serwera deweloperskiego znaczy „nie startuj, dopóki to nie odpali”.
- **REFLEX** obserwuje pliki i rozsyła je według wzorca — `src/**/*.css` do jednego łańcucha, `**/*.ts` do drugiego, per tor w monorepozytorium.
- **ROSETTA** wybiera tor narzędziowy w mieszanych repozytoriach (stojak wykrywa Node/Rust/Go/PHP/… per katalog i celuje każdym urządzeniem odpowiednio).

**Tory mówią językiem twoich własnych narzędzi.** W położeniu AUTO urządzenia lintujące i formatujące (PURITY, GLOSS) mówią narzędziami samego projektu, zamiast wszędzie sięgać po narzędzia Node: przestrzeń robocza Deno używa `deno lint` i `deno fmt`, projekt Cargo — `cargo clippy` i `cargo fmt`, moduł Go — `go vet` (albo `golangci-lint`, gdy projekt niesie swoją konfigurację) i `gofmt`. Plik `biome.json` przestawia tory Node na Biome, a jawne położenia pokrętła zawsze wygrywają z AUTO.

**Twoje własne urządzenia.** Półkę rozszerza się edytorem tekstu: dowolny `*.json` w `~/.nmox/devices.d/` staje się prawdziwym urządzeniem — pokrętła, przyciski, diody, porty i kable, zapisane w układzie i osiągalne z ⌘I. Zadeklaruj polecenie jako tablicę argumentów, nazwij pokrętło, a `{{pokrętło}}` podstawi się przy naciśnięciu przycisku. Prawa zostają u gospodarza, nie w twoim pliku: **zaufanie do przestrzeni roboczej pilnuje pierwszego uruchomienia dokładnie tak, jak przy urządzeniu wbudowanym**.

**Bramki jakości** zamieniają „wygląda na skończone” w „jest skończone”:

- **VITALS** puszcza Lighthouse na twój żywy serwer i wymaga progu wydajności, dostępności, dobrych praktyk albo widoczności w wyszukiwarkach.
- **VERITAS** pilnuje progu pokrycia i powtarza dokładnie te testy, które padły, po nazwie.
- **GAUNTLET** obciąża punkt końcowy i wymaga minimalnej przepustowości. **PRISM** pilnuje rozmiaru paczki, **BEACON** certyfikatu i dostępności adresu, a **PREFLIGHT** to lista kontrolna przed wysyłką — podłącz jego OK do urządzenia wdrożeniowego, a wdrożenia fizycznie nie ruszą, dopóki wszystko nie będzie na zielono.
- **GOVERNOR** pilnuje regresji gazu w pracy z Solidity (`.gas-snapshot`).

**Wszystko inne:** **SOLDER** opakowuje dowolne polecenie powłoki w pełnoprawne urządzenie — a cały stojak **eksportuje się do GitHub Actions** (twój lokalny potok i twoja integracja to to samo okablowanie). **HELM** wykonuje polecenia na zdalnym serwerze po ssh, **TAIL** śledzi dowolny dziennik, a **PHOSPHOR** to terminal wewnątrz stojaka. Jeśli polecenie wypisze lokalny adres, wskaźnik ⇄ zapala się jak przy każdym serwującym urządzeniu i gaśnie, gdy uruchomienie się kończy.

**Stojak sam trzyma się w zgodzie.** Zmień `package.json`, a pokrętło skryptów NPM-9000 zaktualizuje się na miejscu. Zmień `Gruntfile`, a DYNAMO odczyta swoje zadania na nowo. Dodaj zależność, a wyświetlacz CRATE się odświeży. Bez ponownego celowania, bez przycisków odświeżania.

### KVASIR — wyjaśnia ostatnią porażkę

![KVASIR wyjaśniający prawdziwe nieudane uruchomienie: zatwierdzona diagnoza na płycie czołowej i pełne kroki naprawy w podglądzie](images/kvasir-explain.png)

**KVASIR** to pomoc SI po stojakowemu: urządzenie, które wyjaśnia błąd leżący właśnie na szynie MONITOR, a nie boczny panel czatu. Gdy uruchomienie padnie, naciśnij **EXPLAIN**, a KVASIR spyta twoją SI, co poszło nie tak i jaki jest konkretny następny krok. Krótki werdykt ląduje na wyświetlaczu; **VIEW** otwiera pełną odpowiedź. **MODEL** wybiera **FAST** (szybko i tanio, domyślnie) albo **DEEP** (mocniej). EXPLAIN jest niebieski: czyta i pyta, nigdy nie dotyka twojego projektu.

**Wybierz swoją SI, włóż swój klucz.** KVASIR działa z **Claude (Anthropic)**, **ChatGPT (OpenAI)** albo **Gemini (Google)** — twój klucz, twój wybór. Naciśnij **KEY…**, aby wybrać dostawcę i wkleić jego klucz; wybór jest zapamiętywany, a klucz mieszka wyłącznie w pęku kluczy systemu. Zwykłe zmienne środowiskowe każdego dostawcy też są czytane, a klucz zapisany wygrywa z kluczem ze środowiska.

**Co KVASIR wysyła — i to wszystko, co wysyła.** Przy pierwszym naciśnięciu EXPLAIN okno wylicza dokładnie to, co opuści twoją maszynę, i to, co jej nie opuści; bez tej zgody nie wysyła się nic, a zgoda obowiązuje osobno dla każdego dostawcy. Po udanym EXPLAIN przycisk **VIEW** otwiera odpowiedź jako rozmowę — możesz dopytywać o tę samą porażkę.

**Zapytaj KVASIR o swój kod.** Ten sam asystent sięga do edytora: zaznacz kod i wybierz **Zapytaj KVASIR o zaznaczenie…** albo **Edytuj z KVASIR…**, żeby powiedzieć, co zmienić, i zobaczyć „przed” i „po”, zanim cokolwiek zostanie zastosowane. **⌥⌘G** uzupełnia przy kursorze widmowym tekstem, który wstawi się dopiero po Tab, a wskaźnik gałęzi git potrafi napisać twój opis commita.

**Wyceluj agenta w swoje IDE.** Narzędzia ▸ Agent Port (MCP)… otwiera punkt końcowy MCP, który zewnętrzny asystent może odpytywać: jest **tylko do odczytu z konstrukcji**, wyłączony, dopóki go nie włączysz, nasłuchuje wyłącznie na interfejsie lokalnym i wymaga tokenu utworzonego przy starcie.

Stojak jest rozszerzalny: wtyczki innych osób mogą dodawać urządzenia (zainstaluj ich NBM przez Narzędzia ▸ Wtyczki). Aby napisać własne, zobacz [device-spi.md](device-spi.md).

<a id="5-the-editor"></a>
## 5. Edytor

![Kod jQuery w palecie NMOX Phosphor, struktura w Nawigatorze](images/editor.png)

Ponad 70 języków jest kolorowanych jak należy — nowoczesny zestaw, klasyczny (razem z CoffeeScriptem) i cała warstwa konfiguracji, aż po `.env`, `.editorconfig`, konfiguracje nginksa i Apache, pliki Dockerfile oraz pliki blokad.

- **Uzupełnianie** zna kontekst, a także *klasyczne biblioteki*: jeśli twój projekt niesie jQuery, MooTools, Prototype, Backbone/Underscore albo Knockout (przez zależności npm *lub* zwykłe znaczniki `<script>`), ich API pojawiają się przy uzupełnianiu. Projekty na jQuery 1.x i 2.x dostają uczciwą plakietkę o końcu wsparcia, a nie natrętne przypomnienie.
- **Struktura w Nawigatorze (⌘7)** pokazuje budowę pliku dla 58 rodzajów; kliknięcie przenosi na miejsce.
- **Minimapa** — sylwetka całego pliku obok paska przewijania każdego edytora; kliknij albo pociągnij, żeby przewinąć. Cały dokument zawsze mieści się w pasku: wiersze kurczą się, gdy plik rośnie. Widok ▸ Minimapa włącza ją i wyłącza naraz we wszystkich otwartych edytorach.
- **Lepkie przewijanie** — deklaracje obejmujące górę widoku (klasa, a potem metoda, do której zjechałeś) zostają przypięte nad tekstem, do trzech wierszy samego kodu; kliknięcie przenosi do wiersza. Pasek znika, gdy nic nie obejmuje pierwszego widocznego wiersza.
- **Idź do symbolu (⌥⇧⌘O)** przenosi do dowolnej funkcji, klasy, reguły albo nagłówka w całym projekcie po wpisaniu nazwy — z dopasowaniem po przedrostku, po wielkich literach wewnątrz słowa albo po masce. Indeks jest ograniczony i uczciwy: `node_modules` jest pomijany, a przy bardzo dużym projekcie okno mówi, że zindeksowało pierwsze 2000 plików, zamiast udawać, że przeczytało wszystko.
- **Okno testów (⌥⌘2)** pokazuje wszystkie testy projektu *zanim cokolwiek się uruchomi*, i uruchamia jeden test, plik albo całość.

### Rozwiń skrót (⌥⌘E)

Wpisz skrót Emmeta i naciśnij **⌥⌘E**: `ul>li*3` zamienia się w gotową listę. Działa w HTML-u, w szablonach Angulara i — w postaci CSS — wewnątrz bloków `<style>` i atrybutów `style`, gdzie wycinek jest ograniczony do regionu, więc nigdy nie połknie otaczającego znacznika. Skrót, którego produkt nie zna, zostaje odrzucony i zostawia twój tekst nietknięty.

### Tokeny projektowe (właściwości własne)

Wpisanie `var(` podpowiada tokeny zadeklarowane w prawdziwych arkuszach stylów twojego projektu, każdy z próbką koloru i miejscem deklaracji. **⌘-kliknięcie** na użyciu `var(--token)` przenosi do jego deklaracji. Kolory są malowane tym kolorem, którym są — hex, `rgb()`, `hsl()`, nazwy, a także `oklch()`, `lab()` i `color-mix()` — a **⌘-kliknięcie** na literale koloru otwiera wybierak, który zastąpi go dokładnie w tym zapisie, w jakim go napisałeś.

### Atrybut class zna twoje arkusze stylów

Pisanie wewnątrz `class="…"` podpowiada klasy, które twój projekt naprawdę definiuje, wraz z arkuszem, z którego pochodzą; **⌘-kliknięcie** na klasie przenosi do jej reguły, a **⌘-kliknięcie** na selektorze `.klasa` do jej pierwszego użycia w znaczniku. **Zmień nazwę klasy…** zmienia nazwę w całym projekcie — tylko całe tokeny, z liczbą na plik — i odmawia na głos, jeśli nowa nazwa jest już zajęta albo zostały niezapisane zmiany.

### Uruchom skrypt, prosto od kursora

W sekcji `scripts` pliku `package.json` polecenie **Uruchom skrypt** wykonuje wiersz, w którym stoi kursor — przez to samo pytanie o zaufanie do przestrzeni roboczej i to samo ■, co każde inne uruchomienie.

### Klucze środowiska, pełnoprawne

Wpisanie `process.env.` albo `import.meta.env.` podpowiada klucze, które twoja rodzina plików `.env` naprawdę definiuje, a **⌘-kliknięcie** przenosi do wiersza z deklaracją. Wartości pokazywane są przycięte: przypomnienie jest, sekretu nie ma.

### Szablony Angulara, pełnoprawne

Pliki `.component.html` otwierają się z własnym kolorowaniem szablonu, z blokami `@if`/`@for` i dyrektywami strukturalnymi w uzupełnianiu. Zainstaluj Angular Language Service, a sprawdzanie typów w szablonie naprawdę dociera: pomyl nazwę właściwości, a własny kompilator Angulara podpowie właściwą. **⌘B** w szablonie przenosi do deklaracji, a menu kontekstowe przechodzi między komponentem, jego szablonem, stylami i testem.

### Komponenty Vue i Svelte, pełnoprawne

Pliki `.vue` i `.svelte` otwierają się z własnym kolorowaniem, własnym uzupełnianiem (łącznie z kropkowanymi runami Svelte 5) i Emmetem wewnątrz bloków szablonu. Diagnostyka Vue naprawdę dociera do edytora — przez własny serwer języka Vue.

### Debugowanie z prawdziwymi pułapkami

Kliknij na lewym marginesie, wybierz **Debuguj plik (pułapki)** i program zatrzyma się w tym miejscu — ze stosem, zmiennymi i obliczaniem wyrażeń. JavaScript i TypeScript działają od razu dzięki dołączonemu adapterowi; Python używa debugpy, a Go delve, które instalujesz sam. **Debuguj w Chrome** robi to samo dla strony: pułapki w twoim źródle zatrzymują się w IDE, podczas gdy przeglądarka chodzi na jednorazowym profilu. Wszystko najpierw przechodzi przez pytanie o zaufanie do przestrzeni roboczej.

### Pokazywanie i dzielenie się

**Widok ▸ Tryb prezentacji** naraz powiększa każdy otwarty edytor, stronę we wbudowanej przeglądarce, okno wyjścia i terminal — i przy wyjściu przywraca wszystko dokładnie tak, jak było. **Widok ▸ Pokaż naciśnięcia** wyświetla wielkim drukiem właśnie naciśnięty skrót, ale nigdy tego, co piszesz. **Edycja ▸ Kopiuj jako Markdown** kopiuje zaznaczenie jako ogrodzony blok z właściwą etykietą języka, a wariant **z odnośnikiem** dokłada odnośnik GitHub do tych samych wierszy. **Narzędzia ▸ Zapisz zrzut…** maluje całe okno w podwójnym rozmiarze, a warianty obejmują samą kartę edytora, schowek i drzewo projektu jako Markdown.

<a id="6-the-studios"></a>
## 6. Studia

### Dostęp z klawiatury i przez czytnik ekranu

Każdy element sterujący w stojaku ma dostępną nazwę, a sprawdza się to przy każdej budowie. Pokrętła są suwakami, które słuchają strzałek, Home i End; przyciski słuchają spacji i Entera, także te przygaszone, które mówią, dlaczego odmawiają; diody i wyświetlacze ogłaszają swój stan. Tab obraca stojak — poza sytuacją, gdy ognisko jest na elemencie sterującym, gdzie ustępuje zwykłemu przechodzeniu.

### Git na pasku stanu

Plakietka **⎇ gałąź** pokazuje, na której gałęzi jesteś i ile plików się zmieniło; czyta się ją z dysku, więc nie kosztuje żadnego procesu. Kliknięcie otwiera pełną historię, a menu niesie **Różnice projektu**, **Adnotuj**, żądania scalenia przez twoje własne `gh` oraz **Napisz opis commita z KVASIR**.

### Tablica zadań (⌥⌘1)

Kanban per projekt zapisany w `.nmoxtasks.json` — obok twojego kodu i wersjonowany razem z nim. Przeciągaj karty albo przesuwaj je klawiaturą: **⌘↑/⌘↓** zmienia kolejność, a przesunięta karta zachowuje ognisko. Limity pracy w toku są radą, nie zaporą: nagłówek czerwienieje i nic cię nie zatrzymuje. Przycisk **Przegląd** zamienia kolumny na pulpit — co w toku, zrobione dziś i w tym tygodniu, przepływ dzienny, starzejące się karty — a zegar (**Odbij kartę**) mierzy prawdziwy czas na kartę, przy czym na całej tablicy chodzi tylko jeden zegar. **Standup** zamienia to wszystko w raport gotowy do wklejenia.

### Studio bloków (⌥⌘5)

Składaj prawdziwe Web Components z typowanych, zazębiających się elementów, na sposób Scratcha: niedozwolone zagnieżdżenia są odrzucane, kod powstaje jako samodzielny element własny, a kliknięcie elementu podświetla jego wiersze. Serwer podglądu w pamięci pokazuje komponent naprawdę, złożony z pozostałymi poprawnymi komponentami twojej biblioteki. Obieg jest dokładny: ponowne wygenerowanie tego, co właśnie wczytano, daje bajt w bajt ten sam plik.

### Studio API (⌥⌘8)

Kolekcje, żądania, środowiska ze `{{zmiennymi}}` i testy, zapisywane w `.nmoxapi.json` — sekrety wyłącznie w pęku kluczy, nigdy w tym pliku. Każda odpowiedź dostaje ocenę bezpieczeństwa ze swoich nagłówków. Import z curl, `.http`, OpenAPI, Postmana, Insomnii i HAR; eksport do `.http` oraz kopiowanie jako curl albo jako `fetch`, z już podstawionymi zmiennymi.

### Studio baz danych (⌥⌘7)

SQLite, PostgreSQL, MySQL, MariaDB, MongoDB i CouchDB, ze sterownikami w komplecie i hasłami wyłącznie w pęku kluczy. Konsola zna silnik, każde polecenie ma własną siatkę wyników, a wiersze edytuje się w samej siatce, gdy jest klucz główny — z podglądem dokładnych UPDATE przed zastosowaniem i uczciwym powodem, gdy coś jest tylko do odczytu. Eksport do CSV albo JSON, z unieszkodliwionymi formułami.

### Studio kontraktów (⌥⌘6)

Drzewo artefaktów Foundry i Hardhata, **Interakcja** prowadzona przez ABI z rozszyfrowanymi zwrotami i wycofaniami, panel **Obserwuj** śledzący bloki i zdarzenia, oraz **Nadzór** z tabelą gazu, werdyktami rozmiaru EIP-170 i książką adresową. **Nigdy żadnych kluczy prywatnych**: wysyłki idą przez odblokowane konta sieci lokalnej, a tajne adresy mieszkają w pęku kluczy.

### Projektant infrastruktury (⌥⌘9)

Płótno dla DigitalOcean, Hetznera i Cloudflare: zsynchronizuj to, co istnieje naprawdę, odśwież, by zobaczyć rozbieżności, i zniszcz stos, mając jego koszt przed oczami. Niedozwolone połączenia odmawiają na głos i mówią dlaczego, a póki trwa operacja w chmurze, płótno blokuje się z paskiem, który to oznajmia.

### IRC (⌥⌘3)

Pełny klient wewnątrz IDE: TLS z prawdziwym sprawdzeniem nazwy, SASL, rozszerzenia IRCv3, uzupełnianie tabulatorem, podświetlenia, odnośniki otwierające się we wbudowanej przeglądarce, zapis na dysk, własne filtry i lista kanałów, którą przesiewasz w trakcie pisania.

### Dołączona witryna

**Pomoc ▸ Witryna NMOX Studio (lokalnie)** podaje witrynę produktu z jego własnego stojaka, na interfejsie lokalnym. Mówi tymi samymi trzynastoma językami co IDE; przełącznik jest w stopce.

### Przeglądarka (⌥⌘4)

Prawdziwa przeglądarka wewnątrz IDE, z własnymi narzędziami deweloperskimi — konsolą, DOM-em, siecią, magazynem i panelami dla Vue, Svelte i Angulara — bo silnik nie niesie własnego inspektora, a ten jest nasz. Zna twoje źródła: wskaż element, otwórz wiersz, który go wytworzył, zmień jego styl na miejscu, a deklaracja wyląduje w źródłowym arkuszu stylów. Zapisanie pliku przeładowuje stronę, a prawdziwe rozmiary urządzeń służą do sprawdzania układu responsywnego.

<a id="7-docker"></a>
## 7. Docker

Karta Docker to pulpit sterowniczy: stan silnika, kontenery, obrazy, wolumeny i sieci, z uruchamianiem, zatrzymywaniem, dziennikami i sprzątaniem. Urządzenie HARBOR na stojaku pokazuje to samo jednym spojrzeniem. I jak już powiedziano: uruchom kontener Postgresa, MySQL-a albo Mongo, a Studio baz danych zaproponuje ci gotowe połączenie.

Karta **Dockerize** tworzy `Dockerfile` klasy produkcyjnej, `.dockerignore` oraz plik kompozycji dopasowane do łańcucha narzędzi twojego projektu — Node, PHP-FPM z nginksem i inne.

<a id="8-wizards-and-kits"></a>
## 8. Kreatory i zestawy

Wszystkie mieszkają w *Nowy plik…* oraz w menu kontekstowym projektu i wszystkie są **idempotentne i nigdy nie nadpisują**: kolejne uruchomienie odświeża to, co należy do samego zestawu, a twoje zmiany zostawia w spokoju; czego przepisać nie wolno, ląduje obok jako plik `.suggested`.

### Zestaw standardów

`robots.txt`, `sitemap.xml`, manifest sieciowy, `security.txt` zgodny z RFC 9116 oraz `humans.txt`, utworzone z twoich odpowiedzi.

### Zestaw PWA

Pełny komplet ikon wykuty z jednego obrazu, wraz z wariantami maskowalnymi; czytelny service worker — powłoka aplikacji albo sieć najpierw, twój wybór — strona na czas bez sieci i okablowanie w `index.html`, które wiąże to wszystko razem.

### Zestaw dostępności

Dostępność jako punkt wyjścia, a nie audyt po fakcie: `a11y.css` (widoczny pierścień fokusa, narzędzie dla tekstu czytanego tylko przez czytniki ekranu, style odnośnika pomijającego i blok dla tych, którzy wolą mniej ruchu), `A11Y-NOTES.md` z przejściem po klawiaturze i pytaniami, na które żadna automatyka nie odpowie, oraz idempotentne okablowanie `index.html` — język, odnośnik pomijający, arkusz stylów. O viewporcie zakazującym powiększania dostaniesz ostrzeżenie, ale nikt go nie przepisze; czego zestaw naprawić nie umie, to nazywa, a nie rusza.

### Zestaw internacjonalizacji

Przetłumaczalny od pierwszego dnia, brat zestawu dostępności: `locales/en.json` i `locales/es.json` (po jednym katalogu na język, te same klucze), `i18n.js` bez zależności, który stosuje katalog do znaczników `data-i18n`, pilnuje, by `<html lang>` mówił prawdę, i pokazuje brakujący klucz jako jego samego, nigdy jako cichą pustkę; do tego `I18N-NOTES.md` — żadnych sklejanych fragmentów, `Intl` do dat i liczb, przejście od prawej do lewej i pseudolokalizacja.

### Zestaw kontraktów (Web3)

Wybierz łańcuch — Solidity z Foundry, Soroban, Solana, CosmWasm, ink!, Cairo, Move, Bitcoin z Miniscriptem, Clarity na Stacks, Cardano z Aikenem albo TON z Tactem — oraz nazwę kontraktu, a zestaw postawi sprawdzony na żywo początek: manifest, kontrakt, natywny test i CONTRACT-NOTES.md nazywający urządzenia stojaka i kroki jednorazowe. Klucze nigdy nie dotykają środowiska.

### Zestaw klasyczny

Dodaj do dowolnego kodu jQuery, MooTools, Prototype, Backbone z Underscore albo Knockout — czy to w samym repozytorium (przypięte wersje, zapisany sha256), czy jako zależności npm; do tego rusztowania webpacka, grunta, gulpa lub bowera.

<a id="9-quick-search-status-line-and-staying-oriented"></a>
## 9. Szybkie wyszukiwanie, pasek stanu i trzymanie się kursu

### Znacznik ⇄ obsługuje

Na pasku stanu pojawia się znacznik **⇄ obsługuje**, gdy tylko działają serwery: uruchomienie samego środowiska, urządzenia obsługujące oraz każde polecenie, które wypisało lokalny adres. Kliknij i wybierz jeden — otworzy się we wbudowanej przeglądarce albo w przeglądarce systemu, gdy tamta karta go nie przyjmie.

### ⌘I, wyszukiwarka do wszystkiego

Jedno pole sięga twoich projektów (ostatnich i znanych), każdego urządzenia na stojaku — prosto do jego pokręteł —, **działających serwerów** (Enter otwiera je w przeglądarce), żądań Studia API, połączeń i tabel Studia baz danych, kontraktów, węzłów infrastruktury oraz kart Tablicy zadań, a trafienie nazywa kolumnę, w której karta stoi.

### Pasek stanu mówi, co żyje

Obok znacznika serwerów stoi wycelowany projekt ze swoim łańcuchem narzędzi oraz gałąź Gita wraz z liczbą zmienionych plików. Wszystko to czyta się z dysku albo z zapisów, które produkt i tak prowadzi — spojrzenie nie kosztuje ani jednego procesu.

### Stanowisko pracy

To macierzysty port: bieżący projekt, pliki otwarte i ostatnie, ostatnie projekty oraz uruchomienie każdej powierzchni. Dopóki coś działa, stronę otwiera sekcja **DZIAŁA** — każde polecenie, które produkt uruchomił za ciebie, z jego adresem, jeśli go ogłosiło, i od której godziny idzie, a do tego każdy serwer obsługiwany przez urządzenie ze stojaka. W każdym wierszu są prawdziwe przyciski **Otwórz** i **Zatrzymaj**, dosięgalne z klawiatury i przez czytnik ekranu, więc jedno uruchomienie można zatrzymać, nie kładąc reszty. Wszystkie tytuły na Stanowisku pracy to prawdziwe przyciski: Tab tam dochodzi, Enter otwiera. ⌘I sięga tych samych uruchomień: wpisz „zatrzymaj”, a Enter zatrzyma dokładnie to jedno. To, co zatrzymałeś sam, czyta się jako *zatrzymane* wszędzie tam, gdzie mowa o wyniku, i nigdy jako porażka.

### Skróty Emacsa (a także Eclipse i IntelliJ)

Narzędzia ▸ Opcje ▸ Skróty klawiszowe przełącza cały profil: ruchy oraz wycinanie i wklejanie Emacsa w każdym edytorze, albo zestawy Eclipse i IDEA, jeśli tam siedzi twoja pamięć mięśniowa. Każdy skrót NMOX jest zapisany we wszystkich pięciu profilach, więc zmiana profilu nigdy nie kosztuje cię skrótów studiów.

<a id="10-the-safety-nets-things-you-dont-have-to-do-anything-for"></a>
## 10. Siatki bezpieczeństwa (to, po co nie trzeba nic robić)

### Wskrzeszenie sesji

Stojak co kilka sekund robi zdjęcie temu, co działa. Wymuszone zamknięcie, awaria, `kill -9` — przy następnym starcie dymek proponuje wznowić dokładnie tę utraconą sesję, jednym kliknięciem.

### Gwarancja bez sierot

Wyjście ze środowiska zabija każdy proces, który ono uruchomiło — serwery deweloperskie, interpretery, łańcuchy, obserwatorów — najpierw TERM, potem KILL, gdy się opierają, wraz z potomkami.

### BLACKBOX i SONAR

Wstaw **BLACKBOX** na stojak, a masz rejestrator lotu: każdy start i każde wyjście, z czasami trwania, tendencjami i tym, co zmieniło się od ostatniej zielonej kompilacji. To, co zatrzymałeś sam, czyta się jako ZATRZYMANE — ani zielone, ani porażka, i nigdy to, co prosi się KVASIRa wyjaśnić. **SONAR** pokazuje, kto zajmuje twoje porty, zestawiając to z Dockerem, i jednym kliknięciem wyrzuca tego, kto rozsiadł się na 3000.

### Pliki, których nikt nie nadpisuje

Cztery pliki robocze studiów (`.nmoxapi.json`, `.nmoxdb.json`, `.nmoxweb3.json`, `.nmoxinfra.json`) wczytują się ponownie, gdy zmienisz je poza środowiskiem — ale jeśli masz niezapisane zmiany, dostaniesz pytanie, a nie nadpisanie. Uszkodzony plik odkłada się jako `.bak` i mówi ci o tym; nigdy nie podmienia się go po cichu.

### TypeScript bez budowania

Projekt, którego wejściem jest `index.ts`, `main.ts` albo `src/index.ts`, uruchamia się z IGNITION przy pomocy własnego zdejmowania typów Node-a (`--experimental-strip-types`, od Node 22.6; domyślnie od 23.6 i 22.18 LTS). Odmowa starszego Node-a zostaje przetłumaczona na zdanie, które nazywa ten próg.

### Twój język

NMOX Studio mówi trzynastoma językami: English, Español, Français, Deutsch, Русский, Українська, Polski, Português (Brasil), Bahasa Indonesia, Filipino, Tiếng Việt, 简体中文 i हिन्दी. Wybierz swój w **Opcje ▸ Ogólne ▸ Język** — każdy zapisany własną nazwą, żebyś zawsze znalazł swój. Wybór trafia do twoich ustawień uruchamiania (`etc/nmoxstudio.conf`, jako argument `--locale`) i działa też od razu. Zmieniają się menu, okna dialogowe, podpowiedzi, paski stanu, ekran powitalny i opcje. Zostaje słownictwo płyt czołowych stojaka (GO, STOP, EXPLAIN — to napisy na urządzeniu, jak na syntezatorze) oraz głębsze okna samej platformy, dla których tłumaczenia jeszcze nie ma.

### Codzienne sprawdzanie aktualizacji

Ciche, raz dziennie: jeśli jest nowsze wydanie, powiadomienie prowadzi cię do menedżera modułów, na jego kartę aktualizacji, gdzie centrum aktualizacji instaluje nowe moduły na miejscu. Wyłącza się w Opcje ▸ Ogólne.
