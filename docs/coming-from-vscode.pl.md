# Przesiadka z VS Code

<!-- languages -->
[English](coming-from-vscode.md) · [Español](coming-from-vscode.es.md) · [Français](coming-from-vscode.fr.md) · [Deutsch](coming-from-vscode.de.md) · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · **Polski** · [Português (Brasil)](coming-from-vscode.pt.md) · [Bahasa Indonesia](coming-from-vscode.id.md) · [Filipino](coming-from-vscode.tl.md) · [Tiếng Việt](coming-from-vscode.vi.md) · [简体中文](coming-from-vscode.zh.md) · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

Twoje ręce już wiedzą, gdzie co jest. Ta strona to mapa z tych
nawyków do NMOX Studio: najpierw skróty, potem to, gdzie mieszka każdy
pomysł z VS Code, a na końcu to, co uczciwie działa inaczej.

Pierwsze cztery skróty, które naciska użytkownik VS Code, robią to, czego
się spodziewa: **⇧⌘P** otwiera paletę poleceń, **⇧⌘E** drzewo plików,
**⇧⌘X** wtyczki, a **⌃\`** terminal. Są zapisane we wszystkich pięciu
profilach skrótów, które dostarcza platforma, a bramka w buildzie
rozwiązuje każdy z nich przez złożoną mapę klawiszy w macOS, Windows
i Linuksie, żeby nic innego nie odpaliło w jego miejsce.

<a id="the-chords"></a>
## Skróty

Kolumny macOS używają symboli z paska menu (⌃ Control, ⌥ Option, ⇧ Shift,
⌘ Command); kolumny Windows i Linux to ten sam skrót na klawiaturze PC.

| Chcesz | VS Code, macOS | NMOX, macOS | VS Code, Win/Linux | NMOX, Win/Linux |
|---|---|---|---|---|
| Paleta poleceń | ⇧⌘P | **⇧⌘P** (albo ⌘I) — Szybkie wyszukiwanie | Ctrl+Shift+P | **Ctrl+Shift+P** (albo Ctrl+I) |
| Otwórz plik po nazwie | ⌘P | **⌘P** — Przejdź do pliku | Ctrl+P | **Ctrl+P** |
| Drzewo plików | ⇧⌘E | **⇧⌘E** — Studio projektu | Ctrl+Shift+E | **Ctrl+Shift+E** |
| Rozszerzenia | ⇧⌘X | **⇧⌘X** — Narzędzia ▸ Wtyczki | Ctrl+Shift+X | **Ctrl+Shift+X** |
| Terminal w katalogu projektu | ⌃\` | **⌃\`** | Ctrl+\` | **Ctrl+\`** |
| Otwórz ostatni projekt | ⌃R | **⌥⌘P** — Przełącz projekt… | Ctrl+R | **Ctrl+Alt+P** |
| Przejdź do symbolu w projekcie | ⌘T | **⌥⇧⌘O** | Ctrl+T | **Ctrl+Alt+Shift+O** |
| Przejdź do definicji | F12 | **⌘B** | F12 | **Ctrl+B** |
| Zmień nazwę symbolu | F2 | **⌃R** | F2 | **Ctrl+R** |
| Przejdź do wiersza | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Przełącz komentarz wiersza | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Pokaż podpowiedzi | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Dodaj następne wystąpienie do zaznaczenia | ⌘D | **⌘J** | Ctrl+D | **Ctrl+J** |
| Zaznacz każde wystąpienie | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Dodaj kursor powyżej / poniżej | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Przesuń wiersz w górę / w dół | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Skopiuj wiersz w dół | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Usuń wiersz | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Sformatuj dokument | ⇧⌥F | **⌃⇧F** | Shift+Alt+F | **Alt+Shift+F** |
| Zamknij kartę edytora | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| Panel problemów | ⇧⌘M | **⌘6** — Elementy do zrobienia (⇧⌘M przełącza tu zakładkę) | Ctrl+Shift+M | **Ctrl+6** |
| Przełącz pułapkę | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| Zacznij debugowanie | F5 | **⇧⌘F5** — Debuguj plik | F5 | **Ctrl+Shift+F5** |
| Uruchom bez debugowania | ⌃F5 | **F6** — Uruchom projekt | Ctrl+F5 | **F6** |
| Ustawienia | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | Narzędzia ▸ Opcje (bez skrótu) |

Każdy skrót NMOX w tabeli został odczytany z dostarczanej mapy klawiszy, a nie
z pamięci (⌘, należy do menu aplikacji w macOS). Kilku rzeczy tabela
nie powie w komórce:

- **F5 jest zajęte podczas debugowania.** Znaczy tu *Kontynuuj*, tak jak
  w każdym IDE z rodziny NetBeans, więc debugowanie zaczyna się od
  **⇧⌘F5** (Ctrl+Shift+F5), a wznawia od F5.
- **⌃R to tu Zmiana nazwy**, dlatego *Przełącz projekt* mieszka pod ⌥⌘P,
  a nie pod skrótem Open Recent z VS Code. Zmiana nazwy działa tam, gdzie
  obsługuje ją język stojący za plikiem.
- **Ctrl+, w Windows i Linuksie** cofa przez historię twoich edycji,
  jak zawsze w NetBeans; ustawienia są pod
  Narzędzia ▸ Opcje (w macOS w menu aplikacji: **Settings…**, ⌘,).

**Pomoc ▸ Skróty klawiszowe…** wymienia każdy skrót NMOX z twojego czynnego
profilu, łącznie z czterema skrótami VS Code, odczytany z działającej mapy
klawiszy, więc nie może rozminąć się z tym, co robią klawisze.


<a id="from-the-terminal"></a>
## Z terminala

`code .` to `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox            # just start the IDE
```

Polecenie wraca od razu, a drugie `nmox` przekazuje swój katalog IDE, które
już działa. Homebrew, instalator Windows (*Add "nmox" to PATH*) i pakiety
dla Linuksa dodają je do PATH; przy instalacji z DMG
[podręcznik](user-guide.pl.md#2-first-launch) pokazuje jednowierszowe
dowiązanie.

<a id="where-each-vs-code-idea-lives"></a>
## Gdzie mieszka każdy pomysł z VS Code

| W VS Code | W NMOX Studio |
|---|---|
| **Explorer** | **Studio projektu** (⇧⌘E) — drzewo plików, szablony i edytor `package.json` projektu. **Stanowisko pracy** (⌥⌘0) to baza: otwarte pliki, ostatnie pliki, ostatnie projekty i wszystko, co działa. |
| **Command Palette** | **Szybkie wyszukiwanie** (⇧⌘P albo ⌘I) — akcje, pliki, ostatnie projekty, urządzenia stojaka, aktywne serwery, żądania Studia API, symbole. |
| **Extensions** | **Narzędzia ▸ Wtyczki** instaluje i aktualizuje moduły, łącznie z aktualizacjami samego NMOX. Wiele z tego, co w VS Code dodaje rozszerzenie, jest tu **urządzeniem stojaka** — a jedno możesz napisać jako plik JSON w `~/.nmox/devices.d` ([pliki urządzeń](device-files.md)). |
| **`tasks.json`** | Własne skrypty projektu, uruchamiane tak, jak są napisane: Uruchom / Zbuduj / Testuj na pasku narzędzi (F6, F11, ⌃F6), **Uruchom skrypt** na wierszu `scripts` w `package.json`, **Eksplorator NPM** i **Stojak zadań** (⌘9), gdzie zadania są urządzeniami, które łączysz kablami. |
| **`launch.json`** | **Debuguj plik** (⇧⌘F5) i przycisk debugowania na pasku narzędzi same ustalają, co uruchomić, z samego projektu — wejście skryptu `start`, `main`, `index.js` — a urządzenie stojaka **INSPECTOR** uruchamia debuger jako krok potoku. |
| **Integrated terminal** | Okno **Terminal** (⌃\`): pierwsze naciśnięcie uruchamia powłokę w katalogu projektu, kolejne przywracają ją na wierzch. |
| **`settings.json`** | Narzędzia ▸ Opcje (w macOS: NMOX Studio ▸ Settings…). `.editorconfig` twojego projektu działa podczas pisania i przy zapisie. |
| **Problems panel** | **Elementy do zrobienia** (⌘6): błędy i ostrzeżenia serwerów języka oraz wyniki lintowania i typów z urządzeń PURITY i TYPEGUARD na stojaku. Tak jak w VS Code, niektóre serwery zgłaszają tylko otwarte pliki; gopls zgłasza cały pakiet. |
| **Outline** | **Nawigator** (⌘7). |
| **Source Control** | Wskaźnik gałęzi git na pasku stanu (gałąź i zmiany, jedno kliknięcie do historii) oraz menu **Zespół**. |
| **Workspace Trust** | Ten sam pomysł, egzekwowany przed uruchomieniem czegokolwiek, co wybrało repozytorium: otwarcie sklonowanego projektu nie uruchamia niczego, dopóki mu nie zaufasz. |
| **Keyboard Shortcuts editor** | Narzędzia ▸ Opcje ▸ Skróty klawiszowe (w macOS: NMOX Studio ▸ Settings… ▸ Skróty klawiszowe) — zmień dowolny skrót albo przełącz cały profil na Eclipse, Emacs lub IntelliJ. |

<a id="what-is-honestly-different"></a>
## Co uczciwie działa inaczej

- **⌘D nie jest tu wielokursorem.** Ten sam gest to **⌘J** (Ctrl+J);
  samo ⌘D nie ma przypisania. Przypisz je w Skrótach klawiszowych, jeśli palce
  nalegają.
- **⌃\` otwiera Terminal i przenosi na niego fokus; nie ukrywa go.** A gdy
  Terminal ma fokus, klawisze należą do twojej powłoki, więc drugie
  naciśnięcie trafia do powłoki, zamiast przenosić cię z powrotem do edytora.
- **`.vscode/tasks.json` i `launch.json` nie są czytane.** Zadanie to
  polecenie wybrane przez repozytorium, a jego czytanie zasługuje na własny
  projekt wokół zaufania do obszaru roboczego; do tego czasu tę pracę wykonują
  własne skrypty projektu i opisane wyżej reguły wejścia debugowania.
- **Nie ma profilu skrótów „VS Code”.** Powyższe skróty jadą na
  domyślnym profilu i na pozostałych czterech. Jeden celowy wyjątek: w profilu
  **Eclipse** ⇧⌘E pozostaje własnym *Switch to Editor* Eclipse’a, a
  w edytorze ⇧⌘P i ⇧⌘X zachowują znaczenia z Eclipse’a (pasujący
  nawias, wielkie litery) — kto wybrał Eclipse, spodziewa się Eclipse’a.
- **Na Linuksie Ctrl+\` otwiera Terminal, a nie przełącznik okien.**
  Platforma trzymała tam drugi przełącznik dla pulpitów (KDE), które
  przechwytują Ctrl+Tab; przełącznik jest pod Ctrl+Tab.
- **Skróty z Ctrl+Alt mogą kolidować z AltGr.** W Windows układy
  klawiatury, które wpisują znaki przez AltGr (na przykład polski), wysyłają
  dla niego Ctrl+Alt. Jeśli Ctrl+Alt+P albo Ctrl+Alt+K wpisuje ci znak,
  przenieś *Przełącz projekt* albo skróty eksperymentów w Skrótach klawiszowych.
- **Rozszerzenia VS Code się tu nie instalują.** Inteligencja języków pochodzi
  z serwerów języka, które zna NMOX (Diagnostyka środowiska wymienia, czego
  brakuje i jak to zainstalować), z własnych gramatyk edytora
  i z wtyczek zbudowanych dla platformy NetBeans.
