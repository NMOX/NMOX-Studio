# NMOX Studio — Podręcznik użytkownika

> Tłumaczenie częściowe: rozdziały 1–2 są po polsku. Resztę znajdziesz w [pełnym podręczniku po angielsku](user-guide.md).

Jak używać produktu. Podręcznik omawia funkcje w kolejności, w jakiej je napotkasz: instalacja, pierwsze uruchomienie, projekty, stojak, studia, kreatory i siatki bezpieczeństwa.

---

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
