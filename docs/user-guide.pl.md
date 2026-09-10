# NMOX Studio — Podręcznik użytkownika

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · **Polski** · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Tłumaczenie częściowe: rozdziały 1–3 są po polsku. Resztę znajdziesz w [pełnym podręczniku po angielsku](user-guide.md).

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
