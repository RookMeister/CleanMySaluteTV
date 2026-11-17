# CleanMySaluteTV

Чистый главный экран для телевизоров Sber с Салют ТВ (StarOS): без рекламных рядов и лишних
предустановок, с лёгким нативным лаунчером. Всё через ADB по Wi-Fi, без root. Ничего не удаляется —
только отключается, и одна команда возвращает всё как было.

**Статус: 0.4.0.** Собирается, проходит тесты, сцены, погода и оба размера дока проверены на эмуляторе Android TV; на телевизоре ещё не проверялся.
Целевая модель — Sber SDX-55UQ5231.

## Из чего собран

| Часть | Откуда | Что взято |
|---|---|---|
| Лаунчер (`launcher/`) | [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) | Нативная Java, ~590 КБ, без сети и фоновых служб, темы, 31 язык включая русский, проверен на двух ТВ Sber |
| Установка (`scripts/`) | [sridathballeda/cleantv](https://github.com/sridathballeda/cleantv) | Один файл настроек, бэкап, классификация пакетов, запрет на отключение важного, откат |
| Списки для Sber (`profiles/`) | cleanyourtv, `examples/sber-salute-tv-*` | Что отключать, что прятать, чего не трогать |
| Сцены и погода (`Scene*.java`, `Weather*.java`, `assets/scenes/`) | [Seagey/couch-tv](https://github.com/Seagey/couch-tv) | Пять фоновых сцен с большими стеклянными часами, дата и погода над ними, переписаны нативно. Док и быстрые кнопки — дальше, см. [docs/ROADMAP.md](docs/ROADMAP.md) |

## Что нужно

- ТВ и Mac в одной сети Wi-Fi, на ТВ включена отладка по USB ([docs/SBER.md](docs/SBER.md#включить-adb)).
- На Mac: `adb`, JDK 17, Android SDK 34, Python 3.

```bash
brew install openjdk@17
brew install --cask android-commandlinetools android-platform-tools
JAVA_HOME=/opt/homebrew/opt/openjdk@17 sdkmanager --sdk_root=/opt/homebrew/share/android-commandlinetools "platforms;android-34" "build-tools;34.0.0"
```

## Установка

```bash
cp cleanmysalutetv.conf.example cleanmysalutetv.conf   # впиши TV_IP, док, приветствие
bash scripts/preflight.sh                              # одна сеть? отладка включена? разрешена?
bash scripts/connect.sh                                # на ТВ: «Разрешить отладку?» → Всегда → OK
bash scripts/backup.sh                                 # всегда первым

# по желанию: убрать лишнее
bash scripts/inspect.sh                                # state/inspect/report.tsv: disable / ask / keep / never / unknown
#   выпиши нужные пакеты в debloat-list.txt
bash scripts/debloat.sh debloat-list.txt               # пачками по 4, скриншот после каждой

bash scripts/install.sh                                # собрать, поставить, применить настройки, сделать Home
```

Поменять настройки: правишь `cleanmysalutetv.conf` → `bash scripts/install.sh --config-only`.
Вернуть всё: `bash scripts/restore.sh` (с `--uninstall` — удалить и лаунчер).
**После работы выключи отладку по USB.**

Только собрать APK: `bash scripts/build.sh` (с `--test` — ещё и тесты).

## Документация

- [docs/SBER.md](docs/SBER.md) — особенности Салют ТВ: ADB, что Sber возвращает после перезагрузки, чего не трогать.
- [docs/CONFIG.md](docs/CONFIG.md) — все настройки.
- [docs/ROADMAP.md](docs/ROADMAP.md) — что дальше.
- [AGENTS.md](AGENTS.md) — пошаговая инструкция для ИИ-ассистента.

## Лицензии

Код — MIT ([LICENSE](LICENSE)), включая части из трёх проектов выше: их уведомления в [NOTICE.md](NOTICE.md).
Шрифт Outfit — SIL OFL 1.1 (`launcher/FONT-LICENSE-OFL.txt`), шрифт часов Antonio — SIL OFL 1.1
(`launcher/FONT-LICENSE-OFL-ANTONIO.txt`), картинки сцен — из couch-tv, сделаны для него и свободны
к использованию (см. NOTICE.md), значки Fluent Emoji — MIT
(`launcher/ICONS-LICENSE-FLUENT-EMOJI.txt`).
