# Samouczek: Studio API

<!-- languages -->
[English](api-studio.md) · [Español](api-studio.es.md) · [Français](api-studio.fr.md) · [Deutsch](api-studio.de.md) · [Русский](api-studio.ru.md) · [Українська](api-studio.uk.md) · **Polski** · [Português (Brasil)](api-studio.pt.md) · [Bahasa Indonesia](api-studio.id.md) · [Filipino](api-studio.tl.md) · [Tiếng Việt](api-studio.vi.md) · [简体中文](api-studio.zh.md) · [हिन्दी](api-studio.hi.md) · [עברית](api-studio.he.md) · [العربية](api-studio.ar.md)
<!-- /languages -->

Studio API to wbudowane w IDE stanowisko REST w stylu Postmana. Budujesz
żądania, sprawdzasz odpowiedź asercjami i — czego nie znajdziesz nigdzie
indziej — każda odpowiedź dostaje ocenę według standardów nagłówków
bezpieczeństwa w sieci.

![Żywe 200 w 331 ms — i karta Standardy oceniająca nagłówki bezpieczeństwa odpowiedzi](../images/pl/api-studio.png)

## Otwieranie

`⌥⌘8` albo wiersz **Studio API** w kolumnie NARZĘDZIA na stronie Witamy.

## Kroki

1. **Zrób żądanie.** W kreatorze żądania ustaw metodę na `GET`, a adres
   na `https://httpbin.org/json`. Naciśnij **Wyślij**. Treść odpowiedzi
   przychodzi ładnie sformatowana; wiersz stanu pokazuje kod, czas
   i rozmiar. (Rozbiegana odpowiedź ci nie zaszkodzi — treść płynie przez
   limit 8 MB.)

2. **Dodaj asercję.** Na karcie **Testy** dodaj `Status is 200`
   i `Body contains slideshow`. Wyślij ponownie — każda asercja pokazuje
   zielone ✓ albo czerwone ✗ z faktyczną wartością.

3. **Odczytaj ocenę bezpieczeństwa.** Otwórz kartę **Standardy**. Studio
   API ocenia HSTS, CSP, X-Content-Type-Options, ochronę przed
   clickjackingiem, Referrer-Policy i więcej, a na koniec wystawia ocenę
   literową — to samo sprawdzenie, które webdeweloper w 2026 roku robi na
   securityheaders.com, wbudowane w każde wysłanie.

4. **Użyj zmiennej.** Utwórz środowisko z `base =
   https://httpbin.org`, potem ustaw adres żądania na `{{base}}/get`.
   Przełącz środowisko, a naraz przestawisz wszystkie żądania. Jeśli na
   stojaku działa serwer deweloperski, Studio API proponuje nawet jego adres
   jako `{{baseUrl}}`.

5. **Dodaj uwierzytelnianie bezpiecznie.** Na karcie **Auth** wybierz
   Bearer albo Basic i wpisz token. Token **nigdy** nie trafia do
   wersjonowanego `.nmoxapi.json` — mieszka w pęku kluczy systemu,
   przypisany do żądania.

6. **Zaimportuj to, co już masz.** Przycisk **Importuj…** czyta wklejone
   polecenie curl („Copy as cURL” z narzędzi deweloperskich przeglądarki),
   plik żądań `.http`/`.rest` albo specyfikację OpenAPI 3 (JSON lub YAML)
   — każde z nich staje się prawdziwymi żądaniami, a nagłówek
   `Authorization` zostaje przeniesiony prosto do pola Auth opartego na
   pęku kluczy, zamiast wylądować w pliku przestrzeni roboczej.
   **Kopiuj curl** działa w drugą stronę: dokładne polecenie, które
   wykonałoby Wyślij, trafia do schowka.

7. **Zapytaj KVASIR o złą odpowiedź.** Gdy wysłanie wraca z czymś nie
   tak, naciśnij **Wyjaśnij…**. Okno zgody najpierw mówi
   dokładnie, co opuściłoby twoją maszynę — metodę, adres z zamaskowanymi
   *wartościami* parametrów zapytania, status, bezpieczne nagłówki
   (nagłówki z poświadczeniami już usunięte i policzone) oraz przyciętą
   treść — i nic nie zostanie wysłane, dopóki się nie zgodzisz. Odmów,
   a nic się nie wykona; zgódź się, a wyjaśnienie otworzy się jako
   rozmowa, w której możesz dopytywać.

## Czego się właśnie nauczyłeś

- Żądania, środowiska i asercje zapisują się per projekt w
  `.nmoxapi.json` (bez sekretów).
- Ocena bezpieczeństwa zamienia „czy działa” w „czy jest bezpieczne”.
- Wysłanie można anulować (przycisk Wyślij zmienia się w **Anuluj**)
  i nigdy nie blokuje ono reszty IDE.

## Dalej

- Wyceluj żądanie w serwer działający na stojaku dzięki propozycji
  `{{baseUrl}}`.
- Odpowiednik dla baz danych: [Studio baz danych](db-studio.pl.md).
