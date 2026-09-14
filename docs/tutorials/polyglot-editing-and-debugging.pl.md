# Samouczek: Edycja wielu języków i debugowanie

<!-- languages -->
[English](polyglot-editing-and-debugging.md) · [Español](polyglot-editing-and-debugging.es.md) · [Français](polyglot-editing-and-debugging.fr.md) · [Deutsch](polyglot-editing-and-debugging.de.md) · [Русский](polyglot-editing-and-debugging.ru.md) · [Українська](polyglot-editing-and-debugging.uk.md) · **Polski** · [Português (Brasil)](polyglot-editing-and-debugging.pt.md) · [Bahasa Indonesia](polyglot-editing-and-debugging.id.md) · [Filipino](polyglot-editing-and-debugging.tl.md) · [Tiếng Việt](polyglot-editing-and-debugging.vi.md) · [简体中文](polyglot-editing-and-debugging.zh.md) · [हिन्दी](polyglot-editing-and-debugging.hi.md) · [עברית](polyglot-editing-and-debugging.he.md) · [العربية](polyglot-editing-and-debugging.ar.md)
<!-- /languages -->

NMOX Studio edytuje ponad 70 języków z prawdziwym kolorowaniem składni,
strukturą w Nawigatorze i inteligencją serwerów języka — a JavaScript
i TypeScript (oraz przeglądarkę) debuguje od razu, z pułapkami, które
naprawdę zatrzymują program. W tym samouczku zatrzymasz się na pułapce
w aplikacji Node.

![Pułapka w JavaScripcie trafiona: wykonanie wstrzymane, stos wywołań Node i żywe zmienne V8](../images/debug-javascript.png)

## Zanim zaczniesz

Otwórz (albo postaw od zera) mały projekt Node ze skryptem, który da się
uruchomić, np. trasą Express albo zwykłym `node server.js`.

## Kroki

1. **Otwórz plik źródłowy.** Kolorowanie, dopasowanie nawiasów, zwijanie
   kodu i zaznaczanie wystąpień włączają się same. **Nawigator** pokazuje
   strukturę pliku; serwery języka (instalowane według podpowiedzi z
   `Narzędzia ▸ Diagnostyka środowiska…`) dokładają uzupełnianie
   i diagnostykę.

2. **Postaw pułapkę.** Kliknij margines edytora przy wierszu wewnątrz
   swojej funkcji obsługi — pojawi się kropka pułapki.

3. **Debuguj plik.** Uruchom **Debuguj plik (punkty przerwania)** (albo
   **Debuguj w Chrome (punkty przerwania)** dla strony HTML/JS).
   Jednorazowe pytanie o zaufanie do przestrzeni roboczej pilnuje
   uruchomienia; potem dołączony adapter `js-debug` startuje twój program.

4. **Zatrzymaj się na pułapce.** Wywołaj tę ścieżkę kodu (wyślij żądanie
   albo pozwól skryptowi dojść do wiersza). Wykonanie **zatrzymuje się**
   na twojej pułapce — oglądaj zmienne, chodź po stosie wywołań, przechodź
   krokiem nad i do wnętrza. Przy debugowaniu w przeglądarce otwiera się
   Chrome z jednorazowym profilem na żywym adresie twojego serwera
   deweloperskiego, a pułapki na stronie mapują się z powrotem do IDE.

## Czego się właśnie nauczyłeś

- Edytor traktuje ponad 70 języków jako pełnoprawne (gramatyki TextMate +
  CSL + LSP); pliki konfiguracyjne (YAML, TOML, Dockerfile, nginx…) też są
  objęte.
- Debugowanie JS/TS jest wbudowane — multiplekser sesji spłaszcza sesje
  potomne js-debug, żeby jednosesyjny debuger platformy mógł nim sterować.
- Każde uruchomienie debugowania przechodzi przez bramkę zaufania, a po
  zatrzymaniu ginie całe drzewo procesów (żadnych sierot).

## Dalej

- **Uruchom wskazany test** uruchamia pojedynczą metodę testową w każdym
  języku.
- Diagnostyka z narzędzi stojaka (eslint/tsc/phpstan) trafia do okna
  Action Items platformy.
