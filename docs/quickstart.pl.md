# Szybki start: pięć minut do działającego projektu

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · [Français](quickstart.fr.md) · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · **Polski** · [Português (Brasil)](quickstart.pt.md) · [Bahasa Indonesia](quickstart.id.md) · [Filipino](quickstart.tl.md) · [Tiếng Việt](quickstart.vi.md) · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · [العربية](quickstart.ar.md)
<!-- /languages -->

Ta strona uruchamia jeden z twoich własnych projektów w NMOX Studio. Omawia
tylko to, czego do tego potrzebujesz. Pełnym podręcznikiem jest
[Podręcznik użytkownika](user-guide.pl.md). Jeśli używasz VS Code, przeczytaj
potem [Przesiadkę z VS Code](coming-from-vscode.pl.md).

<a id="1-install-one-minute"></a>
## 1. Instalacja (minuta)

**macOS, z Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Homebrew prosi o jednorazowe `brew trust` dla każdego zewnętrznego tapa.
Przy aktualizacjach nie zapyta ponownie.

**macOS, Windows, Linux, bez Homebrew:** pobierz najnowsze wydanie dla
swojego systemu ze
[strony wydań](https://github.com/NMOX/NMOX-Studio/releases/latest):

| System | Plik | Potem |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | Przeciągnij aplikację do Aplikacji (Applications). |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | Uruchom instalator. |
| Debian, Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Inny Linux | `NMOX-Studio-<version>-linux.tar.gz` | Rozpakuj i uruchom `bin/nmoxstudio`. |

Każdy z tych plików zawiera własne środowisko uruchomieniowe Javy, więc
niczego więcej nie trzeba instalować. Tylko przenośne archiwum zip wymaga
Javy 21 lub nowszej już zainstalowanej na komputerze.

W macOS aplikacja jest notaryzowana przez Apple. Przy pierwszym otwarciu
macOS pyta, czy otworzyć aplikację pobraną z internetu: kliknij **Otwórz**.

<a id="2-open-your-project-one-minute"></a>
## 2. Otwórz swój projekt (minuta)

Uruchom **NMOX Studio**. Otwiera trzy karty: **Witamy**, **Stojak zadań**
i **Przeglądarka**.

Aby otworzyć projekt, wybierz **Plik ▸ Otwórz katalog…** (⌥⌘O w macOS,
Ctrl+Alt+O w Windows i Linuksie) i wskaż jego katalog. Możesz to zrobić też
z terminala, tak jak z `code .`:

```bash
cd ~/code/my-app
nmox .
```

Polecenie wraca od razu. Jeśli NMOX Studio już działa, dostaje katalog;
jeśli nie, uruchamia się. Homebrew, instalator Windows i pakiety dla Linuksa
dodają `nmox` do PATH. Przy instalacji z DMG zobacz
[jak dodać `nmox` do PATH](user-guide.pl.md#2-first-launch).

Katalog jest projektem, jeśli ma `package.json`, `Cargo.toml`, `go.mod`,
`pom.xml`, `composer.json`, `pyproject.toml` albo jeden z 57 innych plików
projektu. Katalog ze zwykłymi plikami HTML też się liczy.

Po otwarciu projektu dzieją się trzy rzeczy:

- **Studio projektu** po lewej pokazuje twoje pliki.
- Pasek stanu na dole pokazuje gałąź git i liczbę zmienionych plików.
- **Stojak zadań** jest przygotowany pod rodzaj projektu. Projekt Vite
  dostaje konsolę Vite, projekt Cargo dostaje tory uruchamiania,
  debugowania i testów, i tak dalej.

<a id="3-run-it-one-minute"></a>
## 3. Uruchom go (minuta)

Naciśnij **▶** na pasku narzędzi albo F6. Projekt uruchamia się tak, jak
uruchamiają go jego narzędzia: skrypt `dev`, `start` albo `serve`
z `package.json`, `cargo run`, `go run`. Używany jest własny menedżer
pakietów projektu: npm, pnpm albo yarn, a dla projektu Bun — bun.

Za pierwszym razem, gdy uruchamiasz cokolwiek w projekcie, NMOX Studio pyta,
czy ufasz temu katalogowi. Projekt, któremu nie zaufano, nie uruchamia
żadnego własnego kodu: ani skryptów, ani budowania, ani testów. Dla
własnego kodu kliknij **Zaufaj obszarowi roboczemu**.

Jeśli twój projekt jest serwerem deweloperskim, jego adres pojawia się na
pasku stanu obok symbolu **⇄**, a strona otwiera się na karcie
**Przeglądarka**. Zmień plik i zapisz, a strona się przeładuje.

Aby zatrzymać wszystko, co działa, naciśnij **■** obok ▶ albo ⌥⌘. (Option,
Command i kropka).

Jeśli nic się nie dzieje, zajrzyj do karty **Output** na dole. Wyjaśnia,
dlaczego uruchomienie nie mogło wystartować, na przykład że narzędzie nie
jest zainstalowane albo zależności nie są jeszcze zainstalowane, i proponuje
naprawę. **Narzędzia ▸ Diagnostyka środowiska…** wymienia każde narzędzie,
z którego może korzystać NMOX Studio, i pokazuje, które są zainstalowane.

<a id="4-find-anything-thirty-seconds"></a>
## 4. Znajdź cokolwiek (pół minuty)

Naciśnij **⌘I** (Ctrl+I w Windows i Linuksie) i pisz. Szybkie wyszukiwanie
znajduje pliki, akcje menu, symbole, urządzenia stojaka, działające serwery
i polecenia oraz skrypty z twojego `package.json`. Naciśnij Enter, aby
otworzyć albo uruchomić wynik.

Naciśnij **⌘P**, aby otworzyć plik po nazwie.

<a id="5-test-it-thirty-seconds"></a>
## 5. Przetestuj go (pół minuty)

Naciśnij **⌃F6** (Ctrl+F6), aby uruchomić testy projektu. Aby zobaczyć
wszystkie testy projektu, zanim jakikolwiek uruchomisz, otwórz okno
**Testy** skrótem ⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## Jeśli nie masz projektu pod ręką

- **Plik ▸ Nowy projekt…** tworzy prawdziwy projekt z szablonu (Angular,
  Vue, Svelte, React z Vite, czysty JavaScript, PHP, Phoenix i inne).
  Tworzy pliki, zakłada repozytorium git i instaluje zależności.
- **Plik ▸ Nowa przestrzeń nauki…** otwiera kurs z przewodnikiem. *Twoja
  pierwsza strona internetowa* jest pierwsza na liście.

<a id="where-to-go-next"></a>
## Co dalej

- **[Stojak zadań](user-guide.pl.md#4-the-task-rack)**. Każde narzędzie,
  które uruchamiasz, jest urządzeniem na stojaku, a kable między
  urządzeniami łączą je w łańcuchy: na przykład uruchom testy za każdym
  razem, gdy budowanie się powiedzie.
- **[Edytor](user-guide.pl.md#5-the-editor)**. Emmet, próbki kolorów,
  debugowanie z pułapkami dla Node i Chrome oraz szablony Angulara.
- **[Studia](user-guide.pl.md#6-the-studios)**. Studio API, Studio baz
  danych, Studio kontraktów, Studio bloków oraz Tablica zadań.
- **[Słowniczek](glossary.pl.md)** wyjaśnia własne słowa produktu: stojak,
  patch, gniazdo, tor, celowanie, KVASIR.
