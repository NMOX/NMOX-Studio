# Z przeglądarki do źródła: wskaż, przeskocz, zmień styl

<!-- languages -->
[English](browser-to-source.md) · [Español](browser-to-source.es.md) · [Français](browser-to-source.fr.md) · [Deutsch](browser-to-source.de.md) · [Русский](browser-to-source.ru.md) · [Українська](browser-to-source.uk.md) · **Polski** · [Português (Brasil)](browser-to-source.pt.md) · [Bahasa Indonesia](browser-to-source.id.md) · [Filipino](browser-to-source.tl.md) · [Tiếng Việt](browser-to-source.vi.md) · [简体中文](browser-to-source.zh.md) · [हिन्दी](browser-to-source.hi.md) · [עברית](browser-to-source.he.md) · [العربية](browser-to-source.ar.md)
<!-- /languages -->

*Jedno posiedzenie. Klikniesz element we wbudowanej Przeglądarce,
wylądujesz w pliku, który go wytworzył, zmienisz jego styl z DevTools
i zobaczysz, jak zmiana trafia do twojego arkusza stylów — bez
przepisywania czegokolwiek.*

Najstarszy podział w tworzeniu stron polega na tym, że przeglądarka
i edytor wiedzą co innego: przeglądarka wie, *o który element ci
chodzi*, edytor wie, *gdzie mieszka kod*, a ty przenosisz tę wiedzę
między nimi ręcznie. Przeglądarka NMOX Studio zamyka ten podział. Ten
samouczek przechodzi całą pętlę na stronie, którą zrobisz w dwie minuty.

![Panel DOM w DevTools z nagłówkiem h1 wskazanym na stronie: Wskaż element, Otwórz źródło i Edytuj styl… obok żywego drzewa](../images/story-06-devtools-pick.png)

## 1. Zrób stronę

Utwórz katalog z dwoma plikami (wystarczy **Nowy plik…** w Studiu
projektu albo dowolny inny sposób):

`index.html`

```html
<!doctype html>
<html>
<head>
    <title>Loop Demo</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <header class="hero">
        <h1 id="headline">Hello, loop</h1>
        <p class="tagline">watch this paragraph change color</p>
    </header>
</body>
</html>
```

`style.css`

```css
.hero {
    background: #222;
    color: white;
    padding: 2rem;
}

.tagline {
    color: gray;
    font-style: italic;
}
```

## 2. Otwórz ją w Przeglądarce

Otwórz kartę **Przeglądarka** (⌥⌘4), wpisz ścieżkę pliku w pasek adresu
jako adres `file://` — na przykład
`file:///Users/you/NMOX/loopdemo/index.html` — i naciśnij Return.

> Strona serwowana przez któreś z serwujących urządzeń stojaka
> (IGNITION, VELOCITY, HALO i spółka) działa dokładnie tak samo —
> Przeglądarka wie, do którego projektu należy działający serwer. **Nie**
> zadziała natomiast zdalna witryna: pętla ufa tylko stronom, które
> potrafi powiązać z plikami na twoim dysku, i powie to wprost, zamiast
> zgadywać.

Kliknij **DevTools** na pasku narzędzi Przeglądarki i wybierz kartę
**DOM**.

## 3. Wskaż element na stronie

Kliknij **Wskaż element**. Kursor nad stroną zmienia się w celownik.
Teraz kliknij nagłówek na samej stronie.

Trzy rzeczy dzieją się naraz: kliknięcie zostaje połknięte (żadnej
nawigacji), drzewo DOM zaznacza `h1#headline`, a niebieska obwódka
otacza element na stronie. Panel szczegółów wypełnia się jego atrybutami
i obliczonymi stylami — łącznie z werdyktem kontrastu WCAG, gdy znane są
oba kolory.

## 4. Przeskocz do źródła

Z zaznaczonym elementem kliknij **Otwórz źródło** (to samo robi
dwukrotne kliknięcie węzła drzewa). Edytor otwiera `index.html`
z kursorem dokładnie w wierszu, który wytworzył element.

Jak znajduje wiersz i kiedy odmawia:

- Element **z identyfikatorem** jest odnajdywany po tym id — id są
  unikalne, więc to trafienie dokładne.
- Element **bez identyfikatora** jest odnajdywany jako N-te wystąpienie
  jego znacznika w kolejności dokumentu, z pominięciem komentarzy oraz
  treści `<script>`/`<style>` (`<div>` w komentarzu albo w łańcuchu JS
  nie jest elementem).
- Elementu, który **istnieje tylko dlatego, że stworzył go skrypt**, nie
  ma w twoim źródle wcale — pasek stanu mówi „prawdopodobnie
  wygenerowane skryptem”, zamiast skakać w złe miejsce.
- Strona, za którą nie stoi lokalny plik — zdalna witryna, nieznany
  serwer deweloperski — odmawia z komunikatem „nie jest serwowane
  z tutejszego projektu”.

Odmowy są tu sednem: skok, który może być błędny, jest gorszy niż brak
skoku.

## 5. Zmień styl — i patrz, jak zmienia się źródło

Zaznacz podpis (`p.tagline`) — wskaż go na stronie albo kliknij
w drzewie — i naciśnij **Edytuj styl…**. W oknie wybierz właściwość
`color`, wpisz wartość `tomato` i naciśnij OK.

Dzieją się dwie rzeczy, po kolei:

1. **Strona natychmiast się przerysowuje.** Poprawka jest najpierw
   stosowana inline, więc zawsze widzisz to, o co prosiłeś.
2. **Zmienia się źródłowy arkusz stylów.** Pasek stanu zgłasza
   `Zapisano w style.css (.tagline)` — otwórz `style.css`, a
   `color: gray;` stało się `color: tomato;`, na miejscu, bez ruszania
   żadnego innego bajtu.

Regułę do edycji wybiera się, pytając *stronę*, które reguły arkuszy
stylów pasowały do elementu — to odpowiedź samej kaskady, wygrywa
ostatnie dopasowanie — więc zapis ląduje w regule, która naprawdę
nadaje styl temu, co widzisz, nawet gdy ten sam selektor występuje
w pliku dwa razy.

## 6. Uczciwe granice

**Edytuj styl…** odmawia, z powodem na pasku stanu, zawsze gdy zapis
byłby zgadywaniem albo zniszczyłby czyjąś pracę. Podgląd inline
i tak działa w każdym przypadku — widzisz poprawkę, a komunikat mówi,
dlaczego nie została zapisana.

| Sytuacja | Co mówi |
|-----------|--------------|
| Reguła mieszka w osadzonym bloku `<style>` | „reguła znajduje się w osadzonym `<style>`, a nie w pliku arkusza stylów” |
| Arkusz stylów jest zdalny albo pochodzi z nieznanego serwera | „nie jest serwowany z tutejszego projektu” |
| Plik `.css` ma obok siebie `.scss`/`.less`/`.sass` | „to wynik kompilacji — edytuj źródło preprocesora” (zapis tutaj przepadłby przy następnej kompilacji) |
| Plik ma niezapisane zmiany w edytorze | „ma niezapisane zmiany w edytorze — najpierw zapisz” |
| Do elementu nie pasuje żadna reguła arkusza stylów | „Zastosowano tylko na stronie — żadna reguła arkusza stylów nie pasuje do tego elementu” |

## 7. Zamknij pętlę

Jeśli stronę serwuje urządzenie ze stojaka, nie musisz nawet
przeładowywać: przeładowanie po zapisie w Przeglądarce obserwuje
zapisywanie plików webowych i samo odświeża lokalne strony. Wskaż →
popraw → źródło zaktualizowane → strona przeładowana z tego źródła.
Przeglądarka i edytor jako jedna powierzchnia.
