# Samouczek: Image Kit (Web) — skompresuj swoje obrazy

<!-- languages -->
[English](image-kit.md) · [Español](image-kit.es.md) · [Français](image-kit.fr.md) · [Deutsch](image-kit.de.md) · [Русский](image-kit.ru.md) · [Українська](image-kit.uk.md) · **Polski** · [Português (Brasil)](image-kit.pt.md) · [Bahasa Indonesia](image-kit.id.md) · [Filipino](image-kit.tl.md) · [Tiếng Việt](image-kit.vi.md) · [简体中文](image-kit.zh.md) · [हिन्दी](image-kit.hi.md) · [עברית](image-kit.he.md) · [العربية](image-kit.ar.md)
<!-- /languages -->

Obrazy to zwykle najcięższa rzecz, jaką wysyła witryna. Image Kit
znajduje w projekcie pliki JPEG i PNG i przygotowuje je dla sieci:
mniejsze pliki `.min.jpg` obok oryginałów dzięki ponownemu kodowaniu
w czystej Javie (nic do instalowania), opcjonalne zmniejszanie oraz pliki
`.webp` przez twój własny `cwebp`, jeśli jest zainstalowany. W próbie na
żywo dla tego wydania tapeta o wadze 17,8 MB stała się plikiem `.min.jpg`
o wadze 347 KB i plikiem `.webp` o wadze 342 KB — o 98% mniej.

## Prawa, których pilnuje

- **Oryginałów nikt nie rusza.** Wyniki trafiają obok
  (`photo.min.jpg`, `photo.webp`), a wynik, który już istnieje, zostaje
  pominięty z komunikatem — nigdy nadpisany.
- **„Optymalizacja”, która nic nie oszczędza, idzie do kosza**: kompresja
  odzyskująca mniej niż 10% zostaje usunięta i zgłoszona jako *already
  tight*, zamiast wysyłać większy „zoptymalizowany” plik. (Wynik
  ze zmienionym rozmiarem zostaje zawsze — chodziło właśnie o mniej
  pikseli.)
- **Ponownego kodowania PNG celowo nie ma.** ImageIO nie pokona
  prawdziwego optymalizatora PNG, więc dla PNG uczciwą wygraną jest plik
  WebP obok.

## Kroki

1. **Wyceluj w projekt** i wybierz **Plik ▸ Dodaj do projektu ▸ Image Kit (Web)…**.
   Okno mówi, ile obrazów znalazło i ile razem ważą (node_modules i wyniki
   budowania są pomijane, podobnie jak własne wyniki `.min.` — kompresja
   już skompresowanego tylko pogłębiałaby straty).

2. **Wybierz kompresję.** Jakość JPEG (85 wizualnie bezstratnie / 80
   domyślnie dla sieci / 70 agresywnie), opcjonalna maksymalna szerokość
   (2560 duży obraz na ekran retina / 1600 treść / 800 miniatury) i — jeśli
   `cwebp` jest w PATH — pliki WebP obok. Jeśli go nie ma, pole wyboru
   o tym mówi i podpowiada, skąd go wziąć (`brew install webp`);
   Diagnostyka środowiska też go sprawdza.

3. **Przeczytaj raport.** Dla każdego pliku: co zapisano, rozmiary przed →
   po albo uczciwy powód, dla którego nic nie powstało („already exists”,
   „already tight”). Łączna liczba zaoszczędzonych bajtów jest na górze,
   razem z gotowym do skopiowania fragmentem `<picture>`, który podaje
   WebP tam, gdzie jest obsługiwany, a w pozostałych przypadkach wraca do
   oryginału.

## Czego się właśnie nauczyłeś

- Optymalizacja obrazów dla sieci bez żadnych wymaganych narzędzi —
  i z twoim `cwebp`, gdy go masz.
- Prawa rodziny zestawów — nigdy nie nadpisuj, raportuj uczciwie —
  obowiązują także piksele.
