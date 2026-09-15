# Samouczek: Wyjaśnij wszystko z KVASIR

<!-- languages -->
[English](explain-anything.md) · [Español](explain-anything.es.md) · [Français](explain-anything.fr.md) · [Deutsch](explain-anything.de.md) · [Русский](explain-anything.ru.md) · [Українська](explain-anything.uk.md) · **Polski** · [Português (Brasil)](explain-anything.pt.md) · [Bahasa Indonesia](explain-anything.id.md) · [Filipino](explain-anything.tl.md) · [Tiếng Việt](explain-anything.vi.md) · [简体中文](explain-anything.zh.md) · [हिन्दी](explain-anything.hi.md) · [עברית](explain-anything.he.md) · [العربية](explain-anything.ar.md)
<!-- /languages -->

KVASIR zaczynał jako urządzenie stojaka, które wyjaśnia nieudane
uruchomienia. Dziś sięga czterech miejsc — stojaka, edytora, Studia API
i Studia baz danych — a każde oblicze trzyma się tych samych trzech praw:
**widzisz dokładnie, co opuściłoby twoją maszynę, zanim cokolwiek ją
opuści**, **każda powierzchnia zdobywa własną zgodę** („tak” dla błędów
budowania nigdy nie upoważnia do wysłania kodu ani SQL) oraz **sekrety
z samej konstrukcji nie mogą się zabrać przy okazji** (ujawniane dane
składa studio, do którego należą, nagłówki z poświadczeniami są usuwane,
a hasła są zawsze poza zasięgiem).

![KVASIR wyjaśniający prawdziwe nieudane uruchomienie](../images/pl/kvasir-explain.png)

## Zanim zaczniesz

Jeden klucz obsługuje wszystkie cztery oblicza — od dostawcy, którego
wybierzesz: Claude (Anthropic), ChatGPT (OpenAI) albo Gemini (Google).
Naciśnij **KEY…** na płycie czołowej KVASIR, żeby wybrać dostawcę
i zapisać jego klucz w pęku kluczy systemu, albo wyeksportuj
`ANTHROPIC_API_KEY`, `OPENAI_API_KEY` lub `GEMINI_API_KEY`. Nie ma klucza,
nie ma wywołania — każde oblicze uczciwie o tym mówi.

## Cztery oblicza

1. **Nieudane uruchomienie (stojak).** Wstaw KVASIR, uruchom coś, co
   pada, naciśnij **EXPLAIN**. Co jest wysyłane: polecenie, kod wyjścia
   i do pięciu próbek wierszy błędu. Pełne przejście — razem z kablem,
   który sam, bez udziału rąk, wyjaśnia porażkę VERITAS — znajdziesz w
   [samouczku o KVASIR](kvasir.pl.md).

2. **Twój kod (edytor).** Zaznacz kod w dowolnym języku → kliknij
   prawym → **Ask KVASIR: zapytaj o zaznaczenie…** i wpisz pytanie.
   Co jest wysyłane: przycięte zaznaczenie, nazwa pliku i język — nic
   więcej z twojego projektu. To oblicze ma *własną* bramkę zgody, bo
   zgoda przepływu porażek wprost obiecuje, że kod źródłowy nigdy nie
   opuszcza maszyny.

3. **Odpowiedź API (Studio API).** Po wysłaniu naciśnij **Wyjaśnij…**.
   Co jest wysyłane: metoda, adres z zamaskowanymi
   wartościami parametrów zapytania, status, nagłówki z usuniętymi
   i policzonymi poświadczeniami oraz przycięta treść. Przydaje się
   w chwili, gdy wyskoczy 401 albo dziwny nagłówek CORS.

4. **Błąd bazy danych (Studio baz danych).** Nieudane polecenie dostaje
   pod komunikatem błędu przycisk **Wyjaśnij…**. Co jest wysyłane: SQL,
   który uruchomiłeś — *razem z wartościami literalnymi, i wiersz zgody
   to mówi*, bo błąd zwykle dotyczy właśnie literału — plus komunikat
   błędu i rodzaj silnika. Nigdy połączenie, hasło ani wiersze.

Każde oblicze otwiera okno rozmowy: dopytuj, a model widzi pełną historię
tej wymiany (najwyżej dziesięć wymian, o czym mówi zapis rozmowy). Wybór
**Fast/Deep** (szybki i mocny model wybranego dostawcy) jest zapamiętywany i ustalony na całą
rozmowę, więc zapis nigdy nie kłamie, kto odpowiadał.

## Spróbuj w dwie minuty

Studio baz danych najszybciej pokazać: otwórz ⌥⌘7, utwórz połączenie
SQLite, uruchom `SELECT * FROM user;` na bazie, której tabela nazywa się
`users`, i naciśnij **Wyjaśnij…** przy błędzie. Przeczytaj okno zgody,
zanim się zgodzisz — to obietnica produktu zamknięta w jednym zdaniu.

## Czego się właśnie nauczyłeś

- Cztery powierzchnie, jeden szew: każde studio składa własne ujawnienie,
  a okno zgody cytuje je słowo w słowo.
- Odmowa jest honorowana po cichu i w całości — ani okna, ani wywołania.
- Wynik należy do przestrzeni roboczej, która go wytworzyła: przełączenie
  projektu czyści odpowiedzi i karty wyników, więc Wyjaśnij nigdy nie
  ujawni danych poprzedniego projektu.
