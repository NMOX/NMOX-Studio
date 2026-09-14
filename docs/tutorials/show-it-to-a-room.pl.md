# Samouczek: pokaż to na sali

<!-- languages -->
[English](show-it-to-a-room.md) · [Español](show-it-to-a-room.es.md) · [Français](show-it-to-a-room.fr.md) · [Deutsch](show-it-to-a-room.de.md) · [Русский](show-it-to-a-room.ru.md) · [Українська](show-it-to-a-room.uk.md) · **Polski** · [Português (Brasil)](show-it-to-a-room.pt.md) · [Bahasa Indonesia](show-it-to-a-room.id.md) · [Filipino](show-it-to-a-room.tl.md) · [Tiếng Việt](show-it-to-a-room.vi.md) · [简体中文](show-it-to-a-room.zh.md) · [हिन्दी](show-it-to-a-room.hi.md) · [עברית](show-it-to-a-room.he.md) · [العربية](show-it-to-a-room.ar.md)
<!-- /languages -->

Bywają dni, kiedy produktem nie jest kod, tylko *pokazanie* go:
rzutnik, README, komentarz w zgłoszeniu, slajd. NMOX Studio ma mały
zestaw do prezentowania właśnie dla tej osoby, a każdy jego element
jedzie na czymś, co IDE już miało, zamiast być doklejonym dodatkiem:
na powiększeniu tekstu samego edytora, na jednym słowniku języków,
który nazywa bloki kodu, na rysowaniu z kuźni dokumentacji. Ten
samouczek przechodzi przez całość na jednym posiedzeniu, od ostatniego
rzędu po schowek.

![Tryb prezentacji włączony: szablon Angulara i okno Output, oba +10 pt, przywrócone dokładnie po wyłączeniu trybu](../images/presentation-mode.png)

![Sama karta edytora, zapisana przez Zapisz zrzut edytora… w rozmiarze 2x](../images/editor-screenshot-2x.png)

## Zanim zaczniesz

Otwórz projekt, który mieszka w repozytorium GitHub (gest z odnośnikiem
potrzebuje `origin` na GitHubie — wszystko inne jest odrzucane na głos,
a nie zgadywane) i otwórz jeden z jego plików źródłowych. Zapisz go:
gest z odnośnikiem odrzuca też bufor z niezapisanymi zmianami, bo blok,
który nie zgadza się ze swoim odnośnikiem, to kłamstwo. Na potrzeby
kroku 2 uruchom też projekt (▶ na pasku narzędzi albo stojak), żeby we
wbudowanej Przeglądarce była strona, a w oknie Output — wyjście.

## Kroki

1. **Niech sala da radę to przeczytać.** `Widok ▸ Tryb prezentacji`.
   Każdy otwarty edytor rośnie o dziesięć punktów, na żywo, i tak samo
   każdy edytor, który otworzysz, póki tryb jest włączony. Pozycja menu
   dostaje znacznik, a pasek stanu podaje wielkość powiększenia. Nic nie
   trafia do twoich ustawień — wyłącz tryb (albo uruchom IDE ponownie),
   a czcionka jest dokładnie taka jak przedtem, łącznie z dostrojeniem
   ⌥-kółkiem, które dołożyłeś na wierzch.

2. **Patrz, jak reszta IDE idzie za nim.** Przy włączonym trybie strona
   we wbudowanej Przeglądarce powiększa się do 150% twojego dotychczasowego
   powiększenia, tekst w oknie Output rośnie o te same dziesięć punktów,
   a każdy otwarty Terminal też dostaje powiększenie — każde przywracane
   przy wyjściu do własnego rozmiaru. Pokaz działającej aplikacji, jej
   wyjścia i powłoki, w której piszesz, czyta się z ostatniego rzędu,
   a nie tylko sam kod.

3. **Pokaż swoje ręce.** `Widok ▸ Pokazuj naciśnięcia klawiszy`, potem
   naciśnij `⌘S`. Na chwilę u dołu okna pojawia się duża ciemna pastylka
   z napisem `⌘S` (powtórzenie czyta się `⌘Z ×3`). Teraz napisz jakieś
   słowo: nic się nie pojawia. Pokazują się tylko skróty z ⌘, ⌃ albo ⌥
   oraz klawisze funkcyjne i Escape — zwykłe pisanie nigdy, więc hasło
   wpisane w terminalu nie wyląduje na rzutniku.

4. **Podziel się kodem.** Zaznacz kilka wierszy i wybierz `Edycja ▸ Kopiuj jako Markdown`
   (albo kliknij prawym przyciskiem w edytorze). Wklej do README,
   zgłoszenia albo czatu: dostajesz ogrodzony blok oznaczony językiem
   pliku (` ```html `, ` ```typescript `, ` ```bash `…), zakończony
   dokładnie jednym znakiem nowego wiersza, z dłuższym ogrodzeniem, jeśli
   sam fragment zawiera trzy grawisy, żeby wyrenderował się w całości.
   Bez zaznaczenia kopiowany jest cały plik. Pasek stanu mówi, ile
   wierszy i jaka etykieta.

5. **Powiedz, gdzie to mieszka.** To samo zaznaczenie, `Edycja ▸ Kopiuj jako Markdown z odnośnikiem`
   (albo prawy przycisk). Wklejasz ten sam blok, a po nim
   `[src/app/app.ts#L3-L12](https://github.com/you/repo/blob/main/src/app/app.ts#L3-L12)`
   — gałąź, na której jesteś (odłączony HEAD linkuje po commicie), bo
   lokalny commit, którego nigdy nie wypchnięto, byłby błędem 404
   przebranym za stały odnośnik. Plik poza repozytorium, repozytorium bez
   `origin`, origin, który nie jest GitHubem, albo niezapisane zmiany:
   pasek stanu odmawia i nic nie jest kopiowane.

6. **Złap obraz.** `Narzędzia ▸ Kopiuj zrzut edytora` kładzie do schowka
   zaznaczoną kartę obszaru edytora — pasek narzędzi, margines, kod,
   paski boczne, bez ramy IDE — jako obraz 2x; wklej go prosto do czatu
   albo na slajd. Bierze kartę, na którą patrzysz, nawet gdy fokus jest
   w Nawigatorze, a gdy w obszarze edytora nic nie jest otwarte, mówi to,
   zamiast kopiować pusty obraz. `Narzędzia ▸ Zapisz zrzut edytora…`
   zapisuje ten sam zrzut jako PNG nazwany od dokumentu
   (`app.ts-<stamp>.png`), a `Narzędzia ▸ Zapisz zrzut ekranu…` zapisuje
   całe okno IDE (`nmox-studio-<stamp>.png`, domyślnie w Obrazach).
   Ponieważ IDE rysuje się samo, nie trzeba nadawać uprawnienia do
   nagrywania ekranu, w kadrze nie ma pulpitu i nie ma czego przycinać.

7. **Wklej drzewo.** `Narzędzia ▸ Kopiuj drzewo projektu jako Markdown`.
   Układ wycelowanego projektu ląduje jako ogrodzone drzewo z ramek, takie
   jak w README: najpierw katalogi, `node_modules/ …` i jego ciężkie
   rodzeństwo nazwane, ale nigdy nie odwiedzane, głębokie albo ogromne
   drzewa przycięte z policzoną resztą zamiast po cichu upuszczonej, a
   własne pliki IDE `.nmox*.json` pominięte, bo należą do produktu, nie
   do projektu.

8. **Zejdź ze sceny.** Znowu `Widok ▸ Tryb prezentacji`. Edytory,
   Przeglądarka, Output i każdy terminal wracają dokładnie tam, gdzie
   były; wyłącz `Widok ▸ Pokazuj naciśnięcia klawiszy`, a pastylka znika.

## Czego się właśnie nauczyłeś

- **Prezentowanie to stan, nie ustawienie.** Tryb prezentacji działa na
  żywo i nigdy nie jest zapisywany — po ponownym uruchomieniu wszystko
  wraca do normy — i jest jednym stanem całego produktu, który przełącza
  edytor, a za którym może pójść każde okno.
- **Nakładka jest celowo wąska.** Pokazywanie naciśnięć klawiszy powtarza
  tylko skróty i klawisze funkcyjne; to, co piszesz, nigdy się nie
  pokazuje.
- **Kopia, która nie może za siebie ręczyć, nie kopiuje nic.** Kopiuj
  jako Markdown z odnośnikiem odrzuca każdy szczebel, którego nie może
  sprawdzić — brak origin, nie GitHub, niezapisany bufor — na pasku
  stanu, zamiast wklejać kłamiący odnośnik.
- **Każde udostępnienie jest ograniczone i proste.** Drzewo nigdy nie
  idzie za dowiązaniem symbolicznym, nigdy nie wchodzi do ciężkiego
  katalogu, przycina to, co wypisuje, i liczy resztę; zrzut edytora to
  obraz i tylko obraz.

## Dalej

- Pokaż z ostatniego rzędu także działającą aplikację: [Z przeglądarki
  do źródła](browser-to-source.pl.md) przechodzi przez wbudowaną
  Przeglądarkę i jej DevTools.
- Standup, który wklejasz do czatu, pochodzi z [Tablicy zadań
  i sprintów](task-board.pl.md).
- Notatki wydania do wpisu zaczynają się w `Pomoc ▸ Co nowego…`, a potem
  jego przycisku **Kopiuj jako Markdown**; cała część o prezentowaniu
  jest w [Podręczniku użytkownika](../user-guide.pl.md).
