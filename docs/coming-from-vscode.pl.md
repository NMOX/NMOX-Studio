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
| Przejdź do definicji | F12 | **F12** albo ⌘B | F12 | **F12** albo Ctrl+B |
| Znajdź odwołania | ⇧F12 | **⇧F12** — Znajdź użycia | Shift+F12 | **Shift+F12** |
| Zmień nazwę symbolu | F2 | **F2** albo ⌃R | F2 | **F2** albo Ctrl+R |
| Szybka poprawka | ⌘. | **⌘.** albo ⌃↩ | Ctrl+. | **Alt+Enter** |
| Przejdź do wiersza | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Wstecz / dalej | ⌃- / ⌃⇧- | **⌃- / ⌃⇧-** | Alt+← / Alt+→ | **Alt+← / Alt+→** |
| Przełącz komentarz wiersza | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Pokaż podpowiedzi | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Dodaj następne wystąpienie do zaznaczenia | ⌘D | **⌘D** albo ⌘J | Ctrl+D | **Ctrl+D** albo Ctrl+J |
| Zaznacz każde wystąpienie | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Dodaj kursor powyżej / poniżej | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Przesuń wiersz w górę / w dół | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Skopiuj wiersz w dół | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Usuń wiersz | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Zwiększ wcięcie wiersza | ⌘] | **⌘]** | Ctrl+] | **Alt+Shift+→** |
| Zamień | ⌥⌘F | **⌥⌘F** albo ⌘R | Ctrl+H | **Ctrl+H** |
| Sformatuj dokument | ⇧⌥F | **⇧⌥F** albo ⌃⇧F | Shift+Alt+F | **Alt+Shift+F** |
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

### Każdy skrót edycji, zmierzony

Skróty, po które sięgają twoje ręce z VS Code podczas edycji, każdy
sprawdzony w dostarczanej mapie klawiszy domyślnego profilu w macOS. Tam,
gdzie skrót VS Code był tu wolny, robi teraz to, co w VS Code (wiersze z
oznaczeniem **To samo:**); tam, gdzie już znaczył coś, na czym polegają
użytkownicy NetBeans, zachowuje to znaczenie, a wiersz mówi, gdzie jest
akcja z VS Code.

| VS Code, macOS | Co robi VS Code | W NMOX Studio |
|---|---|---|
| F12 | Go to Definition | **To samo:** Przejdź do deklaracji, tak jak ⌘B |
| ⇧F12 | Go to References | **To samo:** Znajdź użycia, tak jak ⌃F7 |
| F2 | Rename Symbol | **To samo:** Zmień nazwę, tak jak ⌃R |
| ⌘. | Quick Fix | **To samo:** poprawki dla wiersza, tak jak pokazuje je ⌃↩ |
| ⌥↑ / ⌥↓ | Move Line Up / Down | Poprzednie / następne oznaczone wystąpienie; wiersz przenosi ⌃⇧↑ / ⌃⇧↓ |
| ⇧⌥↑ / ⇧⌥↓ | Copy Line Up / Down | To samo, jak zawsze |
| ⇧⌘K | Delete Line | Wstaw następne pasujące słowo (uzupełnia słowo na podstawie pliku); wiersz usuwa ⌘E |
| ⌘L | Expand Line Selection | Zaznacz identyfikator; zaznaczanie wiersza nie ma skrótu |
| ⇧⌘L | Select All Occurrences | Wklej jako wiersze w edytorze; wszystkie wystąpienia zaznacza ⌃⇧⌘J |
| ⌘/ | Toggle Line Comment | To samo, jak zawsze |
| ⇧⌥A | Toggle Block Comment | Nic: nie ma osobnej akcji komentarza blokowego, a ⌘/ przełącza komentarz |
| ⌘] | Indent Line | **To samo:** Przesuń w prawo |
| ⌘[ | Outdent Line | Skok do pasującego nawiasu, jak zawsze; wcięcie zmniejsza ⇧Tab albo ⌃⇧← |
| ⌘B | Toggle Sidebar | Przejdź do deklaracji; ⇧⌘↩ zostawia sam edytor, ⇧Esc maksymalizuje okno, w którym jesteś |
| ⌘J | Toggle Panel | Dodaje następne wystąpienie w edytorze (tak jak ⌘D); okno Output to ⌘4 |
| ⌘\ | Split Editor | Uzupełnij kod w edytorze; edytor dzieli ⌃⇧⌘V |
| ⇧⌘T | Reopen Closed Editor | To samo, jak zawsze: skrót Otwórz ostatni plik ponownie otwiera ostatnio zamknięty plik |
| ⌃- / ⌃⇧- | Go Back / Go Forward | **To samo:** Wstecz i Dalej po miejscach ostatnich edycji, tak jak ⌃← / ⌃→ (skróty, które macOS zwykle zostawia do przełączania biurek) |
| ⌘G / ⇧⌘G | Find Next / Previous | To samo, jak zawsze |
| ⌥⌘F | Replace | **To samo:** Zamień, tak jak ⌘R |
| ⇧⌘F | Find in Files | To samo, jak zawsze: Znajdź w projektach |
| ⇧⌘O | Go to Symbol in Editor | Otwórz projekt; symbole pliku są w Nawigatorze (⌘7) |
| ⌘T | Go to Symbol in Workspace | Zamienia miejscami dwie litery przy kursorze w edytorze; symbole projektu to ⌥⇧⌘O |
| ⌃G | Go to Line | To samo, jak zawsze |
| ⌘K ⌘S | Keyboard Shortcuts | ⌘K to Wstaw poprzednie pasujące słowo; zestawienie to **Pomoc ▸ Skróty klawiszowe…** |
| ⌘, | Settings | To samo, jak zawsze: NMOX Studio ▸ Settings… |
| ⇧⌥F | Format Document | **To samo:** Formatuj, tak jak ⌃⇧F |

Skróty oznaczone **To samo:** są przypisane w każdym profilu mapy
klawiszy, który zostawia je wolne, a profil, który nadaje któremuś z nich
własne znaczenie, to znaczenie zachowuje: F12 w profilach Eclipse, Emacs i
NetBeans 5.5, F2 we wszystkich profilach poza domyślnym, ⇧F12 w Emacs i
NetBeans 5.5, ⌃- i ⌃⇧- w Emacs i IntelliJ, ⇧⌥F w IntelliJ. W Windows i
Linuksie F12, ⇧F12 i F2 działają tak samo; pozostałe skróty VS Code są tam
inne, a tabela wyżej podaje oba.


<a id="from-the-terminal"></a>
## Z terminala

`code .` to `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

Polecenie wraca od razu, a drugie `nmox` przekazuje swój katalog IDE, które
już działa. Kolumna (`src/app.ts:42:7`) jest przyjmowana, a edytor
otwiera się na początku wiersza; nazwa, której nie ma, zostaje odrzucona
w terminalu, zamiast cokolwiek uruchamiać. Przełącznik `-r` jest
przyjmowany, `-n` otwiera w tym jednym oknie, a `-a` i `-v` zostają
odrzucone z nazwy.

`-w` (`--wait`) otwiera plik i czeka, aż zamkniesz jego kartę, a `-d`
(`--diff`) porównuje dwa pliki obok siebie, więc NMOX Studio może być
edytorem, difftoolem i mergetoolem gita, tak jak `code --wait`:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

Wtedy `git commit` otwiera wiadomość w IDE; zapisz ją i zamknij kartę, a git
działa dalej. Zamknięcie IDE, gdy plik jest jeszcze otwarty, też go oddaje —
z tym, co zostało zapisane.
`git mergetool` otwiera każdy plik z konfliktem w ten sam sposób. Tam, gdzie
VS Code umieszcza nad konfliktem *Accept Current Change | Accept Incoming
Change | Accept Both Changes*, NMOX Studio barwi obie strony i stawia
ostrzeżenie na wierszu `<<<<<<<`; żarówka na marginesie albo szybka poprawka z
kursorem na tym wierszu (⌘. na Macu, Alt+Enter gdzie indziej) proponuje te
same trzy, każdy jako jedną edycję do cofnięcia. Zapisz, zamknij kartę, a git
przejdzie do następnego pliku. Kolory i trzy wybory działają w każdym pliku ze
znacznikami konfliktu, z `git mergetool` lub bez.
**Zespół ▸ Używaj NMOX Studio z Git…** ustawia te same wiersze za ciebie,
po pokazaniu, na co każdy z nich jest teraz ustawiony.
Homebrew, instalator Windows
(*Add "nmox" to PATH*) i pakiety dla Linuksa dodają je do PATH; przy instalacji z DMG
[podręcznik](user-guide.pl.md#2-first-launch) pokazuje jednowierszowe
dowiązanie.

<a id="where-each-vs-code-idea-lives"></a>
## Gdzie mieszka każdy pomysł z VS Code

| W VS Code | W NMOX Studio |
|---|---|
| **Explorer** | **Studio projektu** (⇧⌘E) — drzewo plików (prawy przycisk na pliku daje Kopiuj ścieżkę, Kopiuj ścieżkę względną i Pokaż w Finderze), szablony i edytor `package.json` projektu. **Stanowisko pracy** (⌥⌘0) to baza: otwarte pliki, ostatnie pliki, ostatnie projekty i wszystko, co działa. |
| **Command Palette** | **Szybkie wyszukiwanie** (⇧⌘P albo ⌘I) — akcje, pliki, ostatnie projekty, urządzenia stojaka, aktywne serwery, żądania Studia API, symbole. Działają też nazwy poleceń z samego VS Code: *Format Document*, *Toggle Terminal*, *Git: Commit* albo *Open Settings* pokazuje akcję, która robi tu to samo, pod **Polecenia VS Code**, z jej własną nazwą i skrótem. |
| **Extensions** | **Narzędzia ▸ Wtyczki** instaluje i aktualizuje moduły, łącznie z aktualizacjami samego NMOX. Wiele z tego, co w VS Code dodaje rozszerzenie, jest tu **urządzeniem stojaka** — a jedno możesz napisać jako plik JSON w `~/.nmox/devices.d` ([pliki urządzeń](device-files.md)). |
| **`tasks.json`** | Plik `.vscode/tasks.json` twojego repozytorium jest czytany: wpisz nazwę zadania w Szybkim wyszukiwaniu (⇧⌘P albo ⌘I), a Enter na *Uruchom zadanie: build — make all* je uruchamia; w projekcie, któremu jeszcze nie ufasz, najpierw pojawia się pytanie o zaufanie do obszaru roboczego, wynik trafia do okna Output, a ■ na pasku narzędzi je zatrzymuje. Obok tego własne skrypty projektu, uruchamiane tak, jak są napisane: Uruchom / Zbuduj / Testuj na pasku narzędzi (F6, F11, ⌃F6), **Uruchom skrypt** na wierszu `scripts` w `package.json`, **Eksplorator NPM** i **Stojak zadań** (⌘9), gdzie zadania są urządzeniami, które łączysz kablami. |
| **`launch.json`** | Plik `.vscode/launch.json` twojego repozytorium jest czytany: wpisz nazwę konfiguracji w Szybkim wyszukiwaniu (⇧⌘P albo ⌘I), a Enter na *Debuguj: Launch Program — ${workspaceFolder}/server.js* uruchamia debuger z pułapkami na tym programie, po pytaniu o zaufanie do obszaru roboczego. Konfiguracje Node (`node`, `pwa-node`) i Pythona (`python`, `debugpy`) debugują swój `program` w swoim `cwd`, ze swoimi `args` i `env`; konfiguracje Chrome (`chrome`, `pwa-chrome`) otwierają swój `url` (albo `file`) ze swoim `webRoot`. Bez `launch.json` **Debuguj plik** (⇧⌘F5) i przycisk debugowania na pasku narzędzi same ustalają, co uruchomić, z samego projektu — wejście skryptu `start`, `main`, `index.js` — a urządzenie stojaka **INSPECTOR** uruchamia debuger jako krok potoku. |
| **Integrated terminal** | Okno **Terminal** (⌃\`): pierwsze naciśnięcie uruchamia powłokę w katalogu projektu, kolejne przywracają ją na wierzch. |
| **`settings.json`** | Narzędzia ▸ Opcje (w macOS: NMOX Studio ▸ Settings…). Plik `.vscode/settings.json` repozytorium też jest czytany: `editor.tabSize`, `editor.insertSpaces` i `editor.indentSize` ustalają wcięcia jego plików podczas pisania, `files.trimTrailingWhitespace` i `files.insertFinalNewline` (gdy mają wartość `true`) działają przy zapisie, a blok języka, taki jak `"[typescript]"`, nadpisuje je dla swojego języka. Tam, gdzie repozytorium ma też `.editorconfig`, to `.editorconfig` wygrywa wszędzie, gdzie oba coś mówią. |
| **Problems panel** | **Elementy do zrobienia** (⌘6) albo kliknięcie licznika **✕ ⚠** na pasku stanu: błędy i ostrzeżenia serwerów języka oraz wyniki lintowania i typów z urządzeń PURITY i TYPEGUARD na stojaku. Tak jak w VS Code, niektóre serwery zgłaszają tylko otwarte pliki; gopls zgłasza cały pakiet. |
| **Outline** | **Nawigator** (⌘7). |
| **Source Control** | Wskaźnik gałęzi git na pasku stanu (gałąź i zmiany, jedno kliknięcie do historii) oraz menu **Zespół**. |
| **Workspace Trust** | Ten sam pomysł, egzekwowany przed uruchomieniem czegokolwiek, co wybrało repozytorium: otwarcie sklonowanego projektu nie uruchamia niczego, dopóki mu nie zaufasz. |
| **Keyboard Shortcuts editor** | Narzędzia ▸ Opcje ▸ Skróty klawiszowe (w macOS: NMOX Studio ▸ Settings… ▸ Skróty klawiszowe) — zmień dowolny skrót albo przełącz cały profil na Eclipse, Emacs lub IntelliJ. |

Gdy po raz pierwszy otworzysz repozytorium z `.vscode/tasks.json`,
`launch.json` albo `settings.json`, powiadomienie mówi, co znaleziono
i gdzie to jest; kliknij je, aby otworzyć Szybkie wyszukiwanie. Mówi to
raz na projekt.

<a id="what-is-honestly-different"></a>
## Co uczciwie działa inaczej

- **⌘D dodaje następne wystąpienie w domyślnej mapie klawiszy, ale nie w
  każdym profilu.** Profil Eclipse zachowuje ⌘D jako *Delete Line* z
  Eclipse’a, profil NetBeans 5.5 jako *Shift Line Left*, a profil Emacs
  jako *kill word* (a Ctrl+D jako *delete character* w Windows i
  Linuksie); profil IntelliJ ma ⌘D w macOS i zachowuje Ctrl+D jako
  *Duplicate Line* w Windows i Linuksie. Drugi skrót tego gestu też zależy
  od profilu: ⌘J (Ctrl+J) w domyślnym, ⌃J (Alt+J) w Eclipse i IntelliJ, a
  w Emacs i NetBeans 5.5 żaden — tam można go nadać w Skrótach
  klawiszowych.
- **⌃\` otwiera Terminal i przenosi na niego fokus; nie ukrywa go.** A gdy
  Terminal ma fokus, klawisze należą do twojej powłoki, więc drugie
  naciśnięcie trafia do powłoki, zamiast przenosić cię z powrotem do edytora.
- **`launch.json` jest czytany, a to, czego debuger nie potrafi uszanować,
  zostaje odrzucone.** Debuger przekazuje tu program, jego katalog roboczy,
  jego `args` (listę napisów) i jego `env` (napisy dodawane do
  odziedziczonego środowiska), więc konfiguracja, która ustawia `envFile`,
  `runtimeExecutable`, `runtimeArgs`, `preLaunchTask` albo dowolne inne pole,
  którego go nie nauczono, jest na liście, ale się nie uruchamia: Enter
  wymienia te pola na pasku stanu. Uruchomienie programu bez nich
  debugowałoby coś innego, niż mówi plik. `args` zapisane jako jeden
  napis (VS Code przekazuje go powłoce) i wartość `null` w `env` (która
  usuwa zmienną) są odrzucane tak samo, podobnie jak `"request": "attach"`,
  wpis `compounds`, typ, dla którego nie ma tu adaptera (`go`, `msedge`,
  `cppdbg` i reszta), wartość, którą może dostarczyć tylko VS Code
  (`${file}`, `${input:…}`), oraz ścieżka poza projektem. Pola, które tylko kształtują to, co pokazuje debuger —
  `skipFiles`, `outFiles`, `sourceMaps`, `console`, `justMyCode`,
  `presentation` — są przyjmowane, ale nie są stosowane; wyjście programu
  trafia do okna Output.
- **`tasks.json` jest czytany, a to, czego nie da się uruchomić tak, jak
  napisano, zostaje odrzucone.** Zadanie, które używa
  wartości dostępnej tylko w VS Code (`${input:…}`, `${file}`, `${config:…}`,
  `${command:…}`) albo przez `dependsOn` zależy od innego zadania, jest na
  liście, ale się nie uruchamia: Enter mówi na pasku stanu, o którą zmienną
  albo o które zadanie chodzi. Uruchomienie go z pustą wartością albo bez
  zadania, od którego zależy, uruchomiłoby coś innego, niż mówi plik. Tak
  samo byłoby z typem zadania, który dostarcza rozszerzenie (`gulp`,
  `typescript`), i z katalogiem roboczym poza projektem.
- **Zadanie `"type": "shell"` działa w powłoce, której użyłby VS Code.**
  W macOS i Linuksie to twój `$SHELL` z `-c` (zsh, bash albo fish w macOS
  startuje jako powłoka logowania, `-l`, tak jak w domyślnych profilach
  VS Code); w Windows to PowerShell, `pwsh`, jeśli jest zainstalowany.
  `options.shell` jest respektowane tak jak w VS Code: podaj `executable`,
  a zadanie uruchomi się dokładnie z tymi `args`, które podasz, więc bash
  potrzebuje `"args": ["-c"]`. W Windows uruchamiane są tylko PowerShell
  (argumenty kończące się na `-Command`) i `cmd.exe` (argumenty kończące
  się na `/c`); każda inna powłoka jest tam odrzucana z nazwy, zamiast
  dostać wiersz poleceń zacytowany na chybił trafił.
- **Nie ma profilu skrótów „VS Code”.** Powyższe skróty jadą na
  domyślnym profilu i na pozostałych czterech. Jeden celowy wyjątek: w profilu
  **Eclipse** ⇧⌘E pozostaje własnym *Switch to Editor* Eclipse’a, a
  w edytorze ⇧⌘P i ⇧⌘X zachowują znaczenia z Eclipse’a (pasujący
  nawias, wielkie litery) — kto wybrał Eclipse, spodziewa się Eclipse’a.
- **Na Linuksie Ctrl+\` otwiera Terminal, a nie przełącznik okien.**
  Przełącznik jest pod Ctrl+Tab. Na pulpicie, który zabiera Ctrl+Tab dla
  siebie (na przykład KDE), otwarte pliki wymienia zamiast tego
  **Okno ▸ Dokumenty…**.
- **Skróty z Ctrl+Alt mogą kolidować z AltGr.** W Windows układy
  klawiatury, które wpisują znaki przez AltGr (na przykład polski), wysyłają
  dla niego Ctrl+Alt. Jeśli Ctrl+Alt+P albo Ctrl+Alt+K wpisuje ci znak,
  przenieś *Przełącz projekt* albo skróty eksperymentów w Skrótach klawiszowych.
- **Rozszerzenia VS Code się tu nie instalują.** Inteligencja języków pochodzi
  z serwerów języka, które zna NMOX (Diagnostyka środowiska wymienia, czego
  brakuje i jak to zainstalować), z własnych gramatyk edytora
  i z wtyczek zbudowanych dla platformy NetBeans.
