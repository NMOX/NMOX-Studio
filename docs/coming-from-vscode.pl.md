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
| Przełącz komentarz blokowy | ⇧⌥A | **⇧⌥A** | Shift+Alt+A (Ctrl+Shift+A w Linuksie) | **Shift+Alt+A** (oraz Ctrl+Shift+A w Linuksie) |
| Przełącz zawijanie wierszy | ⌥Z | **⌥Z** — Widok ▸ Zawijanie wierszy | Alt+Z | **Alt+Z** |
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
| Skopiuj ścieżkę edytowanego pliku | ⌥⌘C | **⌥⌘C** — Edycja ▸ Kopiuj ścieżkę | Shift+Alt+C | **Ctrl+Alt+C** |
| Skopiuj jego ścieżkę względną | ⇧⌥⌘C | Edycja ▸ Kopiuj ścieżkę względną (bez skrótu) | Ctrl+K Ctrl+Shift+Alt+C | Edycja ▸ Kopiuj ścieżkę względną (bez skrótu) |
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
- **Kopiuj ścieżkę względną nie ma skrótu.** ⇧⌥⌘C z VS Code to na PC
  Ctrl+Alt+Shift+C, czyli *Clear Split* platformy w każdym profilu
  klawiatury; wiersz jest w menu Edycja, a ⇧⌘P znajduje go po tytule,
  który nadaje mu sam VS Code: *File: Copy Relative Path of Active
  File*.
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
| ⌘L | Expand Line Selection | Zaznacz identyfikator, który zachowuje ten skrót w domyślnej mapie klawiszy; Rozszerz zaznaczenie o wiersz jest w Szybkim wyszukiwaniu pod tą nazwą, a pod samym ⌘L w profilach mapy klawiszy IntelliJ i Emacs, które zostawiają ten skrót wolny |
| ⇧⌘L | Select All Occurrences | Wklej jako wiersze w edytorze; wszystkie wystąpienia zaznacza ⌃⇧⌘J |
| ⌘/ | Toggle Line Comment | To samo, jak zawsze |
| ⇧⌥A | Toggle Block Comment | **To samo:** otacza zaznaczenie albo wiersz z kursorem ogranicznikami komentarza blokowego danego języka i z powrotem je zdejmuje; zaznaczenie, które już zawiera ogranicznik, zostaje odrzucone, zamiast zostać zepsute |
| ⌘] | Indent Line | **To samo:** Przesuń w prawo |
| ⌘[ | Outdent Line | Skok do pasującego nawiasu, jak zawsze; wcięcie zmniejsza ⇧Tab albo ⌃⇧← |
| ⌥Z | Toggle Word Wrap | **To samo:** Widok ▸ Zawijanie wierszy. Przełącza zawijanie we wszystkich edytorach języka pliku, a nie w jednej karcie, a wybór zostaje zapisany |
| ⌘B | Toggle Sidebar | Przejdź do deklaracji; ⇧⌘↩ zostawia sam edytor, ⇧Esc maksymalizuje okno, w którym jesteś |
| ⌘J | Toggle Panel | Dodaje następne wystąpienie w edytorze (tak jak ⌘D); okno Output to ⌘4 |
| ⌘\ | Split Editor | Uzupełnij kod w edytorze; edytor dzieli ⌃⇧⌘V |
| ⇧⌘T | Reopen Closed Editor | To samo, jak zawsze: skrót Otwórz ostatni plik ponownie otwiera ostatnio zamknięty plik |
| ⌃- / ⌃⇧- | Go Back / Go Forward | **To samo:** Wstecz i Dalej po miejscach ostatnich edycji, tak jak ⌃← / ⌃→ (skróty, które macOS zwykle zostawia do przełączania biurek) |
| ⌘G / ⇧⌘G | Find Next / Previous | To samo, jak zawsze |
| ⌥⌘F | Replace | **To samo:** Zamień, tak jak ⌘R |
| ⇧⌘F | Find in Files | To samo, jak zawsze: Znajdź w projektach |
| ⇧⌘O | Go to Symbol in Editor | Otwórz projekt; symbole pliku są w Szybkim wyszukiwaniu: ⌘I, potem `@name` jak w VS Code (Nawigacja ▸ Przejdź do symbolu w tym pliku… wpisuje `@` za ciebie), a jako drzewo w Nawigatorze (⌘7) |
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

Przełącz komentarz blokowy i Przełącz zawijanie wierszy mają swoje skróty
z VS Code także w Windows i Linuksie. Zawijanie wierszy to Alt+Z w każdym
profilu. Przełącz komentarz blokowy to Shift+Alt+A w profilach domyślnym,
Emacs i IntelliJ (profile Eclipse i NetBeans 5.5 zachowują tam własne
Alt+Shift+A), a w Linuksie także Ctrl+Shift+A, czyli skrót VS Code dla
Linuksa, we wszystkich pięciu. Rozszerz zaznaczenie o wiersz to Ctrl+L
w Windows i Linuksie tylko w profilu IntelliJ; w profilu Emacs Ctrl+L
pozostaje tam własnym poleceniem recenter Emacsa.

Język bez komentarza blokowego, na przykład Python, mówi o tym na pasku
stanu. W pliku HTML, Vue albo Svelte blok `<script>` albo `<style>` jest
komentowany jako własny język, podobnie jak frontmatter komponentu Astro;
zaznaczenie, które wniosłoby komentarz do takiego bloku albo z niego
wyniosło, zostaje odrzucone z nazwy, zamiast zepsuć plik. Bez zaznaczenia
skrót przełącza wiersz; nie szuka komentarza, który tylko otacza kursor, więc aby usunąć komentarz
obejmujący kilka wierszy, zaznacz go.

W Szybkim wyszukiwaniu symbole pliku to kategoria **Symbole w tym pliku**.
Wpisanie samego `@` wymienia je od początku pliku; `m name` (litera,
spacja, potem nazwa) przeszukuje tę kategorię i żadną inną.

### Profil mapy klawiszy VS Code

Powyższe tabele opisują profil domyślny, w którym skrót, na którym polegają
użytkownicy NetBeans, zachowuje swoje znaczenie. Jeśli twoje ręce wolą
całą mapę klawiszy VS Code, przełącz profil: wpisz *Użyj mapy klawiszy
VS Code* w Szybkim wyszukiwaniu (⇧⌘P albo ⌘I; wiersz brzmi *Preferences:
Use the VS Code Keymap*) albo wybierz **VS Code**
w Narzędzia ▸ Opcje ▸ Skróty klawiszowe ▸ Profile
(w macOS: NMOX Studio ▸ Settings… ▸ Skróty klawiszowe ▸ Profile). Pasek stanu mówi, w którym profilu jesteś i jak
wrócić; nic nigdy nie przełącza go za ciebie.

W tym profilu domyślne skróty VS Code robią to samo co w VS Code, w macOS
tak samo jak w Windows i Linuksie, wszędzie tam, gdzie ten produkt ma daną
akcję: ⌥↑ / ⌥↓ przesuwają wiersz, ⇧⌘K go usuwa, ⌘L rozszerza zaznaczenie
o wiersz, ⇧⌘L zaznacza wszystkie wystąpienia, ⌘[ / ⌘] zmniejszają
i zwiększają wcięcie, ⌘↩ wstawia wiersz poniżej, ⇧⌘\\ skacze do nawiasu,
⌥⌘[ / ⌥⌘] zwijają i rozwijają, ⌘K ⌘0 / ⌘K ⌘J zwijają i rozwijają
wszystko, ⌘K ⌘X usuwa końcowe odstępy, ⌘J pokazuje okno Output, ⌘\\
dzieli edytor, ⌘T i ⇧⌘O przechodzą do symbolu w projekcie i w pliku, ⇧⌘M
pokazuje Elementy do zrobienia, ⇧⌘D okno debugera, ⌘K ⌘S zestawienie
Skróty klawiszowe, ⌘K ⌘W zamyka wszystkie edytory, ⌘K ⌘O otwiera folder,
⌃R otwiera ostatni projekt, ⇧⌘B buduje, F1 to paleta poleceń, a klawisze
debugera są takie jak w VS Code: F5 rozpoczyna debugowanie projektu albo
wznawia wstrzymany program, ⇧F5 zatrzymuje, ⌃F5 uruchamia bez
debugowania, F9 przełącza punkt przerwania, F10, F11 i ⇧F11 wykonują krok
z pominięciem, do wnętrza i na zewnątrz. Każdy skrót produktu, którego
VS Code nie zajmuje (okna ⌥⌘, ⌥⌘E Emmeta, ⌥⌘G), zostaje tam, gdzie jest.

Co w profilu VS Code nadal działa inaczej, z nazwy:

- **Żadnego skrótu**, bo nic tutaj nie jest tą akcją: ⌘B (*Toggle
  Primary Side Bar*), ⇧⌘W (*Close Window*), ⇧⌘F5 (*Restart* debugowania),
  F8 / ⇧F8 (*Go to Next / Previous Problem in Files*), ⌥F12 (*Peek
  Definition*), ⇧⌘↩ (*Insert Line Above*) i ⌘U (*Cursor Undo*). Te
  klawisze nic nie robią, zamiast robić coś innego.
- **Zapisz wszystko nie ma skrótu w Windows i Linuksie** (w VS Code
  Ctrl+K S); w macOS to ⌥⌘S.
- **Co skróty VS Code zabrały NetBeans.** F1 nie otwiera już pomocy, ⌘B
  nie przechodzi już do deklaracji (robi to F12), a zmiana wielkości liter
  pod ⌘U, uzupełnianie słów pod ⌘K / ⇧⌘K, zamiana liter ⌘T i historia
  schowka ⇧⌘D nie mają w tym profilu skrótu; nie mają go też Debuguj plik,
  Przełącz zakładkę, Otwórz projekt i pokazanie samego edytora. Pełna
  lista dla każdego systemu to `scripts/vscode-keymap/displaced.txt`
  w źródłach.


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
| **Extensions** | **Narzędzia ▸ Wtyczki** instaluje i aktualizuje moduły, łącznie z aktualizacjami samego NMOX. Rozszerzenia VS Code się tu nie instalują, więc **Narzędzia ▸ Zalecane rozszerzenia VS Code…** odpowiada na pytanie, które stawia plik `.vscode/extensions.json` repozytorium: co w NMOX Studio wykonuje pracę każdego zalecanego przez niego rozszerzenia — wbudowana funkcja, okno, które można otworzyć, urządzenie stojaka, serwer języka (i czy ten serwer jest zainstalowany) albo nic — a o rozszerzeniu, którego nie zna, mówi, że jest nieznane, zamiast zgadywać. Wiele z tego, co w VS Code dodaje rozszerzenie, jest tu **urządzeniem stojaka** — a jedno możesz napisać jako plik JSON w `~/.nmox/devices.d` ([pliki urządzeń](device-files.md)). |
| **`tasks.json`** | Plik `.vscode/tasks.json` twojego repozytorium jest czytany: wpisz nazwę zadania w Szybkim wyszukiwaniu (⇧⌘P albo ⌘I), a Enter na *Uruchom zadanie: build — make all* je uruchamia (możesz też wybrać je z listy, którą pokazuje **Uruchom ▸ Uruchom zadanie…**); w projekcie, któremu jeszcze nie ufasz, najpierw pojawia się pytanie o zaufanie do obszaru roboczego, wynik trafia do okna Output, a ■ na pasku narzędzi je zatrzymuje. Zadania z jego `dependsOn` uruchamiają się najpierw, `${file}` to plik otwarty w edytorze, a `${input:…}` pyta cię, zanim cokolwiek wystartuje. `problemMatcher` zadania zamienia jego wyjście w problemy w oknie Elementy do zrobienia i podkreślenia w edytorze (`$tsc`, `$eslint-stylish` i inne wbudowane wzorce VS Code z nazwy albo wzorzec zapisany bezpośrednio w zadaniu), a na obserwatora w tle, takiego jak `tsc -w`, IDE czeka, aż jego wzorzec powie, że cykl się zakończył. Obok tego własne skrypty projektu, uruchamiane tak, jak są napisane: Uruchom / Zbuduj / Testuj na pasku narzędzi (F6, F11, ⌃F6), **Uruchom skrypt** na wierszu `scripts` w `package.json`, **Eksplorator NPM** i **Stojak zadań** (⌘9), gdzie zadania są urządzeniami, które łączysz kablami. |
| **`launch.json`** | Plik `.vscode/launch.json` twojego repozytorium jest czytany: wpisz nazwę konfiguracji w Szybkim wyszukiwaniu (⇧⌘P albo ⌘I), a Enter na *Debuguj: Launch Program — ${workspaceFolder}/server.js* uruchamia debuger z pułapkami na tym programie (te same konfiguracje wymienia **Debuguj ▸ Rozpocznij debugowanie…**), po pytaniu o zaufanie do obszaru roboczego. Konfiguracje Node (`node`, `pwa-node`) debugują swój `program` w swoim `cwd`, ze swoimi `args`, swoim `env` i `envFile`, pod swoim `runtimeExecutable` i `runtimeArgs` — więc konfiguracja z `npm run dev`, `tsx` albo `--experimental-strip-types` startuje tak, jak jest napisana — a `"request": "attach"` dla Node dołącza do procesu `node --inspect` na tym komputerze. Konfiguracje Pythona (`python`, `debugpy`) debugują swój `program` z `args`, `env`, `envFile` i interpreterem, który wskazuje ich `python`; konfiguracje Chrome (`chrome`, `pwa-chrome`) otwierają swój `url` (albo `file`) ze swoim `webRoot`. `"program": "${file}"` debuguje plik, który pokazuje twój edytor, a `preLaunchTask` wskazujący zadanie z twojego `tasks.json` uruchamia się najpierw: debuger startuje, gdy zadanie się powiedzie. Bez `launch.json` **Debuguj plik** (⇧⌘F5) i przycisk debugowania na pasku narzędzi same ustalają, co uruchomić, z samego projektu — wejście skryptu `start`, `main`, `index.js` — a urządzenie stojaka **INSPECTOR** uruchamia debuger jako krok potoku. |
| **Integrated terminal** | Okno **Terminal** (⌃\`): pierwsze naciśnięcie uruchamia powłokę w katalogu projektu, kolejne przywracają ją na wierzch. |
| **`settings.json`** | Narzędzia ▸ Opcje (w macOS: NMOX Studio ▸ Settings…). Plik `.vscode/settings.json` repozytorium też jest czytany: `editor.tabSize`, `editor.insertSpaces` i `editor.indentSize` ustalają wcięcia jego plików podczas pisania, `files.trimTrailingWhitespace` i `files.insertFinalNewline` (gdy mają wartość `true`) działają przy zapisie, `files.eol` to zakończenie wiersza, z którym zapisywane są pliki, `"editor.formatOnSave": false` nie pozwala, aby zapis przeformatował plik, pierwsza wartość `editor.rulers` to miejsce, w którym edytor rysuje linię prawego marginesu (pusta lista nie rysuje żadnej), `editor.wordWrap` z wartością `"on"` albo `"off"` zawija pliki tego projektu albo zostawia je niezawinięte, `files.exclude` ukrywa to, co wymienia, w drzewach projektu, a **Znajdź w projektach** pomija to, co wymieniają `files.exclude` i `search.exclude`; blok języka, taki jak `"[typescript]"`, nadpisuje je dla swojego języka. Tam, gdzie repozytorium ma też `.editorconfig`, to `.editorconfig` wygrywa wszędzie, gdzie oba coś mówią. Twoje własne ustawienia VS Code przenosi się raz, na żądanie: **Narzędzia ▸ Importuj ustawienia VS Code…** |
| **Problems panel** | **Elementy do zrobienia** (⌘6) albo kliknięcie licznika **✕ ⚠** na pasku stanu: błędy i ostrzeżenia serwerów języka oraz wyniki lintowania i typów z urządzeń PURITY i TYPEGUARD na stojaku. Tak jak w VS Code, niektóre serwery zgłaszają tylko otwarte pliki; gopls zgłasza cały pakiet. |
| **Search view** (`search.useIgnoreFiles`) | **Znajdź w projektach** (⇧⌘F). Tak jak w VS Code, wyszukiwanie pomija to, co ignorują pliki `.gitignore` repozytorium i `.git/info/exclude`, więc `node_modules` i `dist/` nie trafiają do wyników, gdy wymienia je `.gitignore`; poza repozytorium pomija po nazwie `node_modules`, `dist`, `build` i pozostałe foldery kompilacji. Zaznacz **Szukaj w źródłach generowanych** w jego oknie, aby przeszukać i je. Globalny plik wykluczeń git nie jest czytany. |
| **Outline** | **Nawigator** (⌘7). |
| **Snippets** (`.vscode/*.code-snippets`) | Pliki fragmentów kodu (snippetów), które zatwierdził twój zespół, są czytane takie, jakie są: wpisz prefiks, naciśnij ⌃Space, a fragment pojawi się na liście jako *prefiks — Nazwa (opis)* ze swoim plikiem obok, tylko w językach, które wymienia jego `scope`. Zaakceptowanie go wstawia treść z jej pozycjami tabulatora, lustrzanymi kopiami, zmiennymi (`TM_FILENAME`, `CURRENT_YEAR`, `UUID` i resztą) oraz transformacjami `/regex/format/`; Tab przechodzi po kolejnych pozycjach, a Enter idzie dalej i po ostatniej ląduje w `$0`. |
| **Auto Save** (`files.autoSave`) | **Plik ▸ Automatyczny zapis** włącza go i wyłącza; to, jak często zapisuje i czy zapisuje także wtedy, gdy plik traci fokus, ustawia się w ustawieniach, w kategorii Edytor na karcie Automatyczny zapis. To twoja własna preferencja: `files.autoSave` repozytorium nie jest czytane. |
| **Markdown preview** | Karta **Podgląd** u góry edytora Markdown, obok karty **Źródło**. |
| **Timeline** (local history) | Karta **Historia** u góry każdego edytora: wersje pliku, które IDE zachowywało przy kolejnych zapisach; każdą można porównać z plikiem w obecnym stanie i przywrócić. |
| **Breadcrumbs** | **Widok ▸ Pokaż ścieżkę nawigacji**. |
| **Source Control** | Wskaźnik gałęzi git na pasku stanu (gałąź i zmiany, jedno kliknięcie do historii) oraz menu **Zespół**. |
| **Workspace Trust** | Ten sam pomysł, egzekwowany przed uruchomieniem czegokolwiek, co wybrało repozytorium: otwarcie sklonowanego projektu nie uruchamia niczego, dopóki mu nie zaufasz. |
| **Keyboard Shortcuts editor** | Narzędzia ▸ Opcje ▸ Skróty klawiszowe (w macOS: NMOX Studio ▸ Settings… ▸ Skróty klawiszowe) — zmień dowolny skrót albo przełącz cały profil na VS Code, Eclipse, Emacs lub IntelliJ. |

Gdy po raz pierwszy otworzysz repozytorium z `.vscode/tasks.json`,
`launch.json`, `settings.json` albo `extensions.json`, powiadomienie mówi,
co znaleziono i gdzie to jest; kliknij je, aby otworzyć Szybkie
wyszukiwanie (albo, w przypadku rozszerzeń, zestawienie, które mówi, co
zastępuje każde z nich). Mówi to raz na projekt.

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
  jego `args` (listę napisów), jego `env` i `envFile` oraz środowisko
  uruchomieniowe, które go startuje (`runtimeExecutable` i `runtimeArgs`
  dla Node, `python` dla Pythona); w przypadku Node dołącza też do procesu,
  który już działa. Konfiguracja, która ustawia `postDebugTask`, `restart`
  albo dowolne inne pole, którego go nie nauczono, jest na liście, ale się
  nie uruchamia: Enter wymienia te pola na pasku stanu. Uruchomienie
  programu bez nich debugowałoby coś innego, niż mówi plik. `args` albo
  `runtimeArgs` zapisane jako jeden napis (VS Code przekazuje go powłoce)
  i wartość `null` w `env` (która usuwa zmienną) są odrzucane tak samo,
  podobnie jak wpis `compounds`, typ, dla którego nie ma tu adaptera (`go`,
  `msedge`, `cppdbg` i reszta), dołączanie do czegokolwiek poza Node,
  wartość, którą może dostarczyć tylko VS Code (`${input:…}`,
  `${command:…}`), oraz program, katalog roboczy albo `envFile` poza
  projektem. Pola, które tylko kształtują to, co pokazuje debuger —
  `skipFiles`, `outFiles`, `sourceMaps`, `console`, `justMyCode`,
  `presentation` — są przyjmowane, ale nie są stosowane; wyjście programu
  trafia do okna Output. Pięć rzeczy warto wiedzieć, zanim naciśniesz
  Enter:
  - **`preLaunchTask` uruchamia się najpierw i musi się powieść.** Zadanie
    jest uruchamiane tak, jak uruchomiłby je Enter na nim w Szybkim
    wyszukiwaniu — zadania z jego `dependsOn`, jego pytania, jego własna
    karta Output — a debuger startuje, gdy zakończy się ono kodem wyjścia
    0; tam, gdzie VS Code po nieudanym zadaniu pyta *Debug Anyway?*, tutaj
    pasek stanu mówi, że konfiguracja nie została uruchomiona. `program`,
    który zadanie buduje (`dist/server.js`), jest szukany po zadaniu, a nie
    przed nim. Etykieta, której `tasks.json` nie definiuje, etykieta
    wspólna dla dwóch zadań i forma obiektowa
    (`{"type": "npm", "script": "build"}`) są odrzucane z nazwy, zanim
    cokolwiek się uruchomi. Na zadanie w tle (`"isBackground": true`,
    obserwator, który nigdy się nie kończy) IDE czeka, aż jego wzorzec
    dopasowania problemów zgłosi zakończony cykl; potem zadanie działa
    dalej, a ponowne naciśnięcie Debuguj używa go ponownie. Zadanie bez
    wzorca, który potrafi powiedzieć, kiedy jest gotowe, zostaje odrzucone
    z nazwy.
  - **`envFile`, którego nie ma, zostaje odrzucony**, podczas gdy VS Code
    uruchamia program bez niego. Jego zmienne są dodawane do środowiska,
    a wpis w `env` wygrywa z plikiem, tak jak w VS Code. Plik jest czytany
    jako zwykłe wiersze `NAME=value` (wiersze komentarza, `export` i jedna
    para cudzysłowów wokół wartości są w porządku); wiersz, który VS Code
    odczytałby inaczej — sekwencja ucieczki w podwójnych cudzysłowach, `#`
    po wartości, odwrócony apostrof, a w konfiguracji Pythona `export` albo
    `${NAME}` — zostaje odrzucony z podaniem pliku i numeru wiersza, nigdy
    wartości.
  - **`${file}`, `${relativeFile}`, `${fileBasename}`,
    `${fileBasenameNoExtension}`, `${fileExtname}`, `${fileDirname}`
    i pozostałe zmienne pliku oznaczają plik aktywnego edytora**: kartę
    z fokusem, gdy jest edytorem, a w przeciwnym razie kartę widoczną
    w obszarze edytora — ten sam plik, który oznacza `${file}` w zadaniu.
    Gdy żaden plik nie jest otwarty, konfiguracja zostaje odrzucona
    z podaniem zmiennej, a `${file}` poza projektem jest odrzucany jak
    każdy inny program stamtąd.
  - **`runtimeExecutable` to nazwa albo ścieżka bezwzględna.** Nazwa
    (`npm`, `tsx`, `nodemon`) jest szukana w twoim PATH, a potem
    w `node_modules/.bin` projektu; `${workspaceFolder}/node_modules/.bin/tsx`
    musi tam być; ścieżka względna zostaje odrzucona, bo VS Code szukałby
    jej jako nazwy. Gdy środowisko uruchomieniowe jest całym poleceniem
    (`npm run dev` bez `program`), skrypt, który ono startuje, jest
    debugowany jako osobna sesja, widoczna w oknie Sessions.
  - **Dołączanie dotyczy tego komputera.** `port` (9229, jeśli nie podano)
    i `address` równy `localhost`, `127.0.0.1` albo `::1`; każdy inny adres
    zostaje odrzucony, bo to nie jest zdalne programowanie. Jeśli nic nie
    nasłuchuje, mówi o tym pasek stanu, a zakończenie sesji zostawia twój
    program uruchomiony.
- **`tasks.json` jest czytany, a to, czego nie da się uruchomić tak, jak
  napisano, zostaje odrzucone.** Zadanie uruchamia się po zadaniach ze
  swojego `dependsOn` (razem albo jedno po drugim przy
  `"dependsOrder": "sequence"`), z `${file}`, `${relativeFile}`,
  `${lineNumber}`, `${selectedText}` i resztą tej rodziny wypełnionymi
  z pliku otwartego w edytorze oraz z pytaniami `${input:…}`
  (`promptString`, `pickString`) zadanymi, zanim cokolwiek wystartuje.
  Całe uruchomienie jest rozstrzygane z góry: jeśli jedno z jego zadań nie
  może się uruchomić tak, jak napisano, nie uruchamia się nic, a Enter mówi
  na pasku stanu, które zadanie i dlaczego. Dotyczy to etykiety
  w `dependsOn`, której plik nie definiuje, zależności będącej zadaniem
  w tle bez wzorca dopasowania problemów (problem matcher), który potrafi
  powiedzieć, kiedy jest gotowe, `${file}` bez otwartego pliku oraz pytania, które anulujesz. Zależność, która się nie powiedzie,
  zatrzymuje uruchomienie w tym miejscu. Nadal odrzucane z nazwy: wartość,
  którą może dostarczyć tylko VS Code (`${config:…}`, `${command:…}`, dane
  wejściowe o `"type": "command"`), zależność zapisana jako obiekt
  (`{"type": "npm", …}`) zamiast etykiety, typ zadania, który dostarcza
  rozszerzenie (`gulp`, `typescript`), oraz katalog roboczy poza projektem.
- **Fragmenty kodu pochodzą z twojego repozytorium, a trzy rzeczy działają
  inaczej.** Wybór (`${1|a,b|}`) zaczyna jako swoja pierwsza opcja, bez
  listy do wybierania; symbol zastępczy zagnieżdżony w wartości domyślnej
  innego staje się częścią tej wartości; a fragment, którego transformacji
  nie da się bezpiecznie wykonać (wyrażenie regularne, którego Java nie
  potrafi skompilować, albo takie, które mogłoby się nie skończyć), zostaje
  pominięty i wymieniony w dzienniku, zamiast zostać wstawiony w połowie.
  Wpisanie prefiksu nie otwiera listy samo z siebie: robi to ⌃Space.
  Fragmenty bez `prefix`, fragmenty użytkownika i `isFileTemplate` nie są
  czytane. Pliki fragmentów są czytane tylko dla plików wewnątrz
  repozytorium, które je zawiera, a fragment, który wstawiłby więcej niż
  milion znaków, nie zostaje wstawiony.
- **`problemMatcher` zadania jest czytany.** Każde uruchomienie zastępuje
  poprzednie problemy tego zadania, a czyste uruchomienie je usuwa.
  Wbudowane wzorce VS Code działają z nazwy (`$tsc`, `$tsc-watch`,
  `$tsgo-watch`, `$eslint-stylish`, `$eslint-compact`, `$jshint`,
  `$jshint-stylish`, `$msCompile`, `$lessCompile`, `$gulp-tsc`, `$go`,
  `$lessc`), podobnie jak `$gcc` i `$rustc` z rozszerzeń C/C++
  i rust-analyzer; działają też wzorce zapisane bezpośrednio w zadaniu
  i `{"base": "$tsc", …}`, łącznie z wzorcami wielowierszowymi z `loop`,
  z każdym `fileLocation` poza `"search"`. Wzorzec, którego to IDE nie ma
  albo którego wyrażenie regularne znaczy tu co innego, nie zatrzymuje
  zadania: zadanie się uruchamia, a pasek stanu raz wymienia wzorzec,
  który nie został zastosowany.
- **Formatuj formatuje JavaScript i TypeScript przy użyciu Prettier
  projektu.** Źródło ▸ Formatuj (⇧⌥F) uruchamia Prettier skonfigurowany
  w projekcie, najpierw pytając o zaufanie do obszaru roboczego, gdy ten
  Prettier jest własnym Prettier projektu; projekt bez konfiguracji
  Prettier mówi o tym na pasku stanu, zamiast bez słowa zostawić plik
  taki, jaki był.
- **Twoje własne ustawienia przenosi się raz, na żądanie.** **Narzędzia ▸ Importuj ustawienia VS Code…**
  (albo *import vs code settings*
  w Szybkim wyszukiwaniu) czyta twój osobisty `settings.json` z VS Code —
  z VS Code, Insiders albo VSCodium — i wymienia każde rozpoznane
  ustawienie razem z tym, czym staje się tutaj: szerokość tabulacji
  i spacje, zawijanie wierszy, linijki, pokazywanie odstępów, usuwanie
  końcowych odstępów przy zapisie, formatowanie przy zapisie, minimapa,
  przypięte nagłówki i automatyczny zapis. Ustawienia, które znaczą tu
  dokładnie to samo, są od początku zaznaczone; te bliskie mówią, czym się
  różnią, i są niezaznaczone. Zastosuj zapisuje zaznaczone, a one od razu
  zaczynają działać; Anuluj niczego nie zapisuje. Czcionkę edytora
  ustawia się w ustawieniach Czcionki i kolory, a skróty klawiszowe to
  profile mapy klawiszy. Wszystko inne w twoim pliku — tokeny, ścieżki,
  ustawienia rozszerzeń — jest tylko liczone, nigdy pokazywane ani
  kopiowane.
- **`files.associations` nie jest czytane.** Platforma ustala typ pliku
  raz i go zachowuje, więc mapowania z `settings.json` nie dałoby się
  uszanować dokładnie; wykluczenie warunkowe (`"when"`), linijki po
  pierwszej i ich kolory oraz zawijanie w określonej kolumnie też nie są
  czytane.
- **Zadanie `"type": "shell"` działa w powłoce, której użyłby VS Code.**
  W macOS i Linuksie to twój `$SHELL` z `-c` (zsh, bash albo fish w macOS
  startuje jako powłoka logowania, `-l`, tak jak w domyślnych profilach
  VS Code); w Windows to PowerShell, `pwsh`, jeśli jest zainstalowany.
  `options.shell` jest respektowane tak jak w VS Code: podaj `executable`,
  a zadanie uruchomi się dokładnie z tymi `args`, które podasz, więc bash
  potrzebuje `"args": ["-c"]`. W Windows uruchamiane są tylko PowerShell
  (argumenty kończące się na `-Command`) i `cmd.exe` (argumenty kończące
  się na `/c`); każda inna powłoka jest tam odrzucana z nazwy, zamiast
  dostać wiersz poleceń zacytowany na chybił trafił. Nazwa pliku,
  zaznaczenie albo odpowiedź na pytanie w `command` zadania powłoki są
  cytowane dla tej powłoki, więc `"command": "python ${file}"` działa dla
  każdej nazwy pliku, także ze spacją albo `$`; w `cmd.exe` wartość
  zawierająca `%`, `!`, cudzysłów podwójny albo podział wiersza zostaje
  odrzucona z nazwy, bo tam żadne cytowanie nie czyni ich nieszkodliwymi.
- **Powyższe tabele opisują domyślny profil mapy klawiszy.** W profilu
  **VS Code** (zob. *Profil mapy klawiszy VS Code* wyżej) własne skróty
  VS Code wygrywają wszędzie tam, gdzie ten produkt ma daną akcję. Cztery
  pierwsze skróty są w każdym profilu. Jeden celowy wyjątek: w profilu
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
