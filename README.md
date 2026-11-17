# CleanMySaluteTV

Чистый главный экран для телевизоров Sber с Салют ТВ (StarOS): без рекламных рядов и лишних
предустановок, с лёгким нативным лаунчером. Всё через ADB по Wi-Fi, без root. Ничего не удаляется —
только отключается, и одна команда возвращает всё как было.

**Статус: 0.12.0.** Собирается и проходит тесты; работает на Sber SDX-55UQ5231 (Салют ТВ, Android 13).
Целевая модель — Sber SDX-55UQ5231.

## Что умеет

- **Сцены**: пять картинок за главным экраном, большие стеклянные часы, горы заходят на цифры;
  над часами дата и погода. Смена каждые 20 секунд.
- **Док**: маленький (иконки по центру внизу, в конце «…» — все приложения) или большой (широкие
  плитки) — на выбор.
- **Погода** из Open-Meteo, город ищется прямо в меню.
- **Меню настроек** (удержать OK на шестерёнке → «Настройки главного экрана»): вид и сцены, погода,
  приложения, дополнительно.
- **Панель управления** (шестерёнка): Wi-Fi, Bluetooth, звук, изображение, память, заставка, таймер
  сна, выключение экрана, входы.
- **AirPlay**: если стоит AirPlay Server, лаунчер держит ТВ видимым для iPhone и Mac ([docs/AIRPLAY.md](docs/AIRPLAY.md)).
- Только русский и английский; около 2,4 МБ.

## Из чего собран

| Часть | Откуда | Что взято |
|---|---|---|
| Лаунчер (`launcher/`) | [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) | Нативная Java, без фоновых служб, темы, русский и английский языки, проверен на двух ТВ Sber |
| Установка (`scripts/`) | [sridathballeda/cleantv](https://github.com/sridathballeda/cleantv) | Один файл настроек, бэкап, классификация пакетов, запрет на отключение важного, откат |
| Списки для Sber (`profiles/`) | cleanyourtv, `examples/sber-salute-tv-*` | Что отключать, что прятать, чего не трогать |
| Сцены, погода, вид меню (`Scene*.java`, `Weather*.java`, `assets/scenes/`) | [Seagey/couch-tv](https://github.com/Seagey/couch-tv) | Фоновые сцены с большими стеклянными часами, дата и погода над ними, вид меню — переписаны нативно |
| Раскладка меню и панели управления | [techgenius1/tvOS-Launcher](https://github.com/techgenius1/tvOS-Launcher) | Только образец, без кода |
| AirPlay-страж (`AirPlayGuard.java`) | [petrobot-admin/hume-launcher](https://github.com/petrobot-admin/hume-launcher) | Проверка видимости AirPlay и перезапуск приёмника, Apache 2.0 |

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
- [docs/AIRPLAY.md](docs/AIRPLAY.md) — AirPlay через AirPlay Server и как лаунчер держит его видимым.
- [docs/ROADMAP.md](docs/ROADMAP.md) — что дальше.
- [AGENTS.md](AGENTS.md) — пошаговая инструкция для ИИ-ассистента.

## Лицензии

Код — MIT ([LICENSE](LICENSE)), включая части из трёх проектов выше: их уведомления в [NOTICE](NOTICE), полные лицензии в [licenses/](licenses/).
Шрифт Outfit — SIL OFL 1.1 (`launcher/FONT-LICENSE-OFL.txt`), шрифт часов Antonio — SIL OFL 1.1
(`launcher/FONT-LICENSE-OFL-ANTONIO.txt`), картинки сцен — из couch-tv, сделаны для него и свободны
к использованию (см. NOTICE).
