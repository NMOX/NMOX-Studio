# Samouczek: napisz własne urządzenie stojaka

<!-- languages -->
[English](your-own-device.md) · [Español](your-own-device.es.md) · [Français](your-own-device.fr.md) · [Deutsch](your-own-device.de.md) · [Русский](your-own-device.ru.md) · [Українська](your-own-device.uk.md) · **Polski** · [Português (Brasil)](your-own-device.pt.md) · [Bahasa Indonesia](your-own-device.id.md) · [Filipino](your-own-device.tl.md) · [Tiếng Việt](your-own-device.vi.md) · [简体中文](your-own-device.zh.md) · [हिन्दी](your-own-device.hi.md) · [עברית](your-own-device.he.md) · [العربية](your-own-device.ar.md)
<!-- /languages -->

*Jedno posiedzenie. Dodasz urządzenie do stojaka edytorem tekstu,
naciśniesz jego przycisk, zobaczysz, jak uruchamia prawdziwe polecenie,
i podłączysz jego wyjście do MONITOR — bez jednego wiersza Javy.*

Nowość w 2.0.0. Stojak przyszedł z pięćdziesięcioma trzema urządzeniami
i do tej pory z jednym sposobem na dodanie pięćdziesiątego czwartego:
napisaniem wtyczki NetBeans. To jest ten drugi sposób.

![Stojak zadań: półka urządzeń po lewej to miejsce, gdzie urządzenie z ~/.nmox/devices.d pojawia się obok wbudowanych](../images/tabs/the-task-rack.png)

## 1. Utwórz katalog

```bash
mkdir -p ~/.nmox/devices.d
```

To cała instalacja. Stojak czyta ten katalog leniwie, więc niczego nie
trzeba uruchamiać ponownie.

## 2. Napisz urządzenie

Wstaw to do `~/.nmox/devices.d/counter.json`:

```json
{
  "id": "com.example.counter",
  "title": "COUNTER",
  "tagline": "counts the files in the project",
  "accent": "#7FB3D5",
  "category": "OBSERVE",
  "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\nPatch OUT into MONITOR to read the list, or DONE onward to chain.",
  "knobs": [
    { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] }
  ],
  "ports": [
    { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "buttons": [
    { "label": "COUNT", "role": "QUERY",
      "command": ["git", "ls-files", "*.{{kind}}"],
      "emit": "done", "trigger": "count" }
  ]
}
```

Każdy element ma tu swoje zadanie: **pokrętło** staje się `{{kind}}`
w poleceniu, rola **QUERY** maluje przycisk na niebiesko (prawo kolorów:
niebieski pyta, zielony działa, czerwony zatrzymuje), a dwa porty
pozwalają go okablować.

## 3. Zamontuj je

Otwórz **Stojak zadań** (`⌘9` albo karta Stojak zadań) i zajrzyj do
szuflady **Obserwacja** na półce. COUNTER tam jest, z twoim opisem pod
nazwą. Przeciągnij go na szynę.

Najedź na jego kartę *Jak używać* — to twój tekst `usage` i właśnie
dlatego format wymaga dwóch prawdziwych wierszy.

## 4. Naciśnij je

> Zauważ, że nie ma wiersza `units`: półka mierzy płytę czołową
> i wybiera najmniejszą wysokość, która się mieści (to urządzenie
> potrzebuje 2U na pokrętło). Deklaruj `units` tylko wtedy, gdy chcesz
> więcej miejsca.

Wyceluj stojak w projekt git, ustaw **KIND** na `js` i naciśnij
**COUNT**.

Pierwsze naciśnięcie wywołuje pytanie **Zaufanie do obszaru roboczego**,
bo plik urządzenia uruchamia prawdziwe polecenia, a gospodarz pilnuje
każdego uruchomienia tak samo jak przy urządzeniu wbudowanym. Zaufaj,
a wyświetlacz pokaże polecenie, a potem ostatni wiersz wyjścia. Gniazdo
DONE mignie na zielono.

Odmów, a nic się nie uruchomi — ta odmowa to funkcja.

## 5. Okabluj je

Przeciągnij kabel z **OUT** urządzenia COUNTER do **TAP** urządzenia
MONITOR. Naciśnij COUNT jeszcze raz: każdy wiersz ląduje na monitorze,
bo zadeklarowany port `OUT`/`DATA` dostaje wyjście uruchomienia bez
dodatkowej konfiguracji.

Teraz przeciągnij kabel z taktu TEMPO do wejścia **COUNT** urządzenia
COUNTER. Urządzenie napisane w edytorze tekstu chodzi teraz według
zegara.

## 6. Zepsuj je celowo

Zmień w pliku polecenie na coś z potokiem:

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

Zapisz, a COUNTER *znika* z półki. To format odmawia wiersza powłoki:
polecenie jest tablicą argumentów, żeby czytelnik — ty za pół roku albo
kolega przeglądający plik — widział dokładnie, co zostanie uruchomione.
Dziennik IDE mówi, który plik pominięto i dlaczego:

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

Przywróć postać tablicy, a urządzenie wróci. Tak samo jest z narzędziem
podanym ścieżką (`./x.sh`), nieznaną `{{variable}}` albo jednowierszowym
`usage`: plik jest pomijany w całości, a nie wczytywany do połowy, bo
urządzenie, którego napis kłamie, jest gorsze niż żadne.

## Czego się właśnie nauczyłeś

- Urządzenie to **plik**: `~/.nmox/devices.d/*.json`, czytany leniwie,
  bez ponownego uruchamiania, bez budowania.
- Pokrętła stają się `{{variables}}`; role wybierają kolory; porty
  pozwalają okablować urządzenie i czytać jego wyjście.
- **Prawa zostają u gospodarza** — zaufanie do obszaru roboczego przy
  każdym uruchomieniu, prawo kolorów, słownik portów, prawo półki — więc
  plik urządzenia nie wyrazi polecenia bez bramki ani czerwonego GO, choćby
  próbował.
- Odmowy są głośne w dzienniku i całkowite w skutkach.

## Dalej

- [device-files.md](../device-files.md) — pełna dokumentacja
- [Stojak zadań](the-task-rack.pl.md) — kable, bramki i zestawy
- [device-spi.md](../device-spi.md) — SPI w Javie, dla urządzeń, które
  potrzebują prawdziwego stanu: własnego rysowania, odpytywania,
  długotrwałych połączeń
