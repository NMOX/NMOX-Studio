# Drugi tydzień

<!-- languages -->
[English](the-second-week.md) · [Español](the-second-week.es.md) · [Français](the-second-week.fr.md) · [Deutsch](the-second-week.de.md) · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · **Polski** · [Português (Brasil)](the-second-week.pt.md) · [Bahasa Indonesia](the-second-week.id.md) · [Filipino](the-second-week.tl.md) · [Tiếng Việt](the-second-week.vi.md) · [简体中文](the-second-week.zh.md) · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*Zatwierdzaj, przeglądaj, rozwiązuj, proponuj — bez przechodzenia do innego narzędzia.*

Pierwsza godzina to otworzyć projekt i go uruchomić. Drugi tydzień to
wszystko wokół kodu: dwadzieścia commitów dziennie, diff do przeczytania
przed każdym z nich, konflikt po pullu, pull request po pushu, ślad stosu
do prześledzenia, README, które ma pozostać uczciwe. Ten przewodnik to
jedno posiedzenie w repozytorium git, które już masz, a każdy krok to coś,
co jutro zrobisz znowu.

## 1. Zrób z NMOX Studio edytor gita

**Zrób:** Zespół ▸ **Używaj NMOX Studio z Git…**

**Zobacz:** sześć globalnych ustawień gita, które czynią NMOX Studio
edytorem, difftoolem i mergetoolem gita, każde obok wartości, jaką ma
**teraz**, więc nic nie zostaje zastąpione bez twojej wiedzy. **Zastosuj**
je ustawia (**Zamknij** jest przyciskiem domyślnym, bo to zapisuje twoją
globalną konfigurację gita); **Kopiuj polecenia** zamiast tego kładzie
wiersze `git config` do schowka. Gdy git już używa NMOX Studio, okno to
mówi i nie proponuje Zastosuj.

Te same wiersze, jeśli wolisz terminal:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. Zatwierdź

**Zrób:** zmień plik, a potem w terminalu:

```bash
git commit -a
```

**Zobacz:** opis commita otwiera się w NMOX Studio, a pasek stanu mówi, że
czeka na niego terminal. Wiersze gita zaczynające się od `#` to
komentarze; pisownię sprawdza się tylko w tym, co piszesz ty; wiersz
podsumowania dłuższy niż 72 znaki, na których ucinają go narzędzia samego
gita, dostaje ostrzeżenie za 72. znakiem. Zapisz, zamknij kartę i commit
gotowy — terminal czekał, aż to zrobisz. Wyjście z IDE, gdy opis jest
jeszcze otwarty, też oddaje go gitowi, z tym, co było zapisane.

`git rebase -i` otwiera swoją listę tak samo: każde polecenie i każdy
commit wyróżnione, a **Przełącz komentarz** wyłącza wiersz bez usuwania
go.

## 3. Wiedz, gdzie jesteś

**Zobacz:** **plakietkę ⎇** na pasku stanu — `⎇ main ±3 ↑2 ↓1` to twoja
gałąź, trzy zmienione pliki, dwa commity do wypchnięcia i jeden do
pobrania (strzałki pojawiają się tylko wtedy, gdy jest coś do wypchnięcia
lub pobrania). Jej menu zaczyna się od **Przełącz gałąź…** i
**Zatwierdź…**.

**Zrób:** postaw kursor na dowolnym wierszu śledzonego pliku.

**Zobacz:** obok plakietki, kto ostatni zmienił ten wiersz, jak dawno i
dlaczego: `Ada Lovelace, 3 dni temu · Fix the parser`. Wiersz, którego
jeszcze nie zatwierdziłeś, mówi to, a plik z niezapisanymi zmianami mówi
właśnie to, zamiast wskazać złego autora. Kliknij notkę, by zobaczyć
adnotacje całego pliku; **Widok ▸ Autor wiersza** ją wyłącza.

## 4. Przejrzyj diff

**Zrób:**

```bash
git difftool
```

**Zobacz:** każdy zmieniony plik obok siebie w widoku różnic NMOX Studio,
z **Poprzednia różnica / Następna różnica** i „Różnica 2 z 5” nad nim.
Dodany albo usunięty plik pokazuje brakującą stronę jako pusty panel
(„brak pliku”); plik binarny jest pokazany jako binarny, a pasek mówi, czy
dwa pliki binarne się różnią. Zamknij kartę, a git przejdzie do
następnego pliku.

## 5. Rozwiąż konflikt

**Zrób:** scal gałąź, która wchodzi w konflikt, a potem:

```bash
git mergetool
```

**Zobacz:** plik z konfliktem w edytorze, bieżąca i przychodząca strona
podbarwione, oraz ostrzeżenie na każdym wierszu `<<<<<<<`. Postaw na nim
kursor i naciśnij ⌘. (gdzie indziej Alt+Enter) albo użyj **Źródło ▸ Napraw
kod…**: **Zaakceptuj bieżącą zmianę**, **Zaakceptuj przychodzącą zmianę**
lub **Zaakceptuj obie zmiany**, każda to jedna edycja do cofnięcia. Blok,
który zmienił się od chwili propozycji, zostaje odrzucony, a nie
zgadnięty. Zapisz, zamknij kartę i odpowiedz gitowi.

## 6. Zaproponuj zmiany

**Zrób:** wypchnij, a potem Zespół ▸ **Nowy pull request na GitHubie**
(jest też w menu plakietki).

**Zobacz:** własną stronę New Pull Request GitHuba dla twojej gałęzi, w
twojej przeglądarce, w której jesteś zalogowany. Z edytora **Edycja ▸
Otwórz w GitHub** i **Kopiuj odnośnik GitHub** dają wiersz lub wiersze, na
których jesteś; w drzewie Studia projektu dają plik albo folder.

## 7. Wyśledź błąd

**Zrób:** uruchamiaj testy w Terminalu (⌃\`), aż któryś się nie powiedzie.

**Zobacz:** miejsce w wyjściu — `src/app.ts:42:7`, ramka stosu
`(/abs/app.js:10:5)`, `--> src/main.rs:3:5`, `File "x.py", line 12` —
otwiera się w tym wierszu i tej kolumnie po ⌘-kliknięciu (Ctrl-kliknięcie
w Windows i Linuksie). URL ani `localhost:3000` nigdy nie są odnośnikiem,
a ścieżka, której nie ma, zostaje odrzucona z nazwy, a nie zgadnięta.

## 8. Dbaj o uczciwość README

**Zrób:** Narzędzia ▸ **Sprawdź linki Markdown…**

**Zobacz:** każdy względny odnośnik i każdy obraz w Markdownie projektu
sprawdzone tak, jak wyświetla je GitHub — plik musi istnieć, a `#heading`
musi być nagłówkiem tego pliku. Martwy odnośnik to błąd, brakujący
nagłówek to ostrzeżenie, oba jako faliste podkreślenia i w Elementach do
zrobienia, z jednym zdaniem na pasku stanu. Nic nie opuszcza twojej
maszyny: odnośnik ze schematem nie jest sprawdzany.

## 9. Przekaż to agentowi

**Zrób:** Narzędzia ▸ **Agent Port (MCP)…**, zaznacz **Zachowaj ten adres
i token**, potem **Kopiuj dla Claude Code** i raz uruchom skopiowany
wiersz.

**Zobacz:** agenta, który może czytać to, co wie IDE — wycelowany projekt,
co serwuje i co działa, co edytujesz, ostatnią porażkę — i który jutro
nadal się połączy, bo token jest przechowywany w pęku kluczy twojego
systemu, a port zostaje użyty ponownie. Port pozostaje tylko do odczytu z
konstrukcji. Odznaczenie **Zachowaj ten adres i token** usuwa wpis z pęku
kluczy.

## Co zrobiłeś

Napisałeś opis commita, przeczytałeś diff, rozwiązałeś konflikt,
otworzyłeś pull request, sprawdziłeś, kto napisał wiersz, prześledziłeś
ślad stosu i sprawdziłeś README — wszystko w oknie, w którym już byłeś.
Nic z tego nie zastąpiło gita: każdy krok jest krokiem samego gita,
otwartym tam, gdzie pracujesz.
