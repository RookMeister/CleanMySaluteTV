"""Собирает config.json лаунчера из настроек cleanmysalutetv.conf.

install.sh передаёт значения через переменные окружения CMST_*. Ключи config.json описаны в
launcher/app/src/main/java/com/rookmeister/cleanmysalutetv/Config.java (метод чтения темы).
Если рядом с conf лежит config.extra.json, его ключи накладываются сверху: так можно задать
любой ключ лаунчера, которого нет в conf.

    python3 -I scripts/make_config.py <out.json> [extra.json]
"""
import json
import os
import sys

# Приложения Sber, которые сервис платформы включает обратно после перезагрузки, и служебные
# заводские приложения CVTE: прячем их с главного экрана (из examples/sber-salute-tv-* в cleanyourtv).
SBER_HIDDEN = [
    "com.cvte.fac.menu", "com.cvte.factory.at", "com.cvte.license.at", "com.cvte.tv.tools",
    "ru.sberdevices.TvFactoryHelper", "ru.sberdevices.webview", "com.sberbank.settings",
    "com.android.tv.settings", "ru.more.play", "ru.sberdevices.subscriptions",
    "ru.sberdevices.smarthome.promo", "ru.sberdevices.tv", "ru.sberdevices.rcfupdater",
    "ru.sberdevices.openbeta", "ru.sberdevices.starkidsroom.prod",
]
SBER_LABELS = [
    "ru.sberdevices.starlauncher=Салют ТВ",
    "ru.sberdevices.starmarket=Каталог Сбера",
    "ru.sberdevices.settings=Центр управления",
]


def env_list(name):
    raw = os.environ.get(name, "")
    return [x.strip() for x in raw.split(",") if x.strip()]


def env_yes(name, default):
    raw = os.environ.get(name, "").strip().lower()
    if not raw:
        return default
    return raw in ("yes", "y", "true", "1", "on", "да")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    cfg = {}
    dock = env_list("CMST_DOCK")
    if dock:
        cfg["dock_apps"] = dock
    hidden = SBER_HIDDEN + [p for p in env_list("CMST_HIDDEN_APPS") if p not in SBER_HIDDEN]
    cfg["hidden_apps"] = hidden
    cfg["app_labels"] = SBER_LABELS + env_list("CMST_APP_LABELS")
    cfg["live_tv_package"] = os.environ.get("CMST_LIVE_TV_PACKAGE", "")
    name = os.environ.get("CMST_GREETING_NAME", "").strip()
    if name:
        cfg["greeting_name"] = name
    cols = os.environ.get("CMST_COLUMNS", "").strip()
    if cols:
        cfg["columns"] = max(4, min(7, int(cols)))
    cfg["app_library"] = env_yes("CMST_APP_LIBRARY", True)
    cfg["show_stats"] = env_yes("CMST_SHOW_STATS", False)
    cfg["reduced_motion"] = env_yes("CMST_REDUCED_MOTION", False)

    if len(sys.argv) > 2 and os.path.isfile(sys.argv[2]):
        with open(sys.argv[2], encoding="utf-8") as f:
            extra = json.load(f)
        if not isinstance(extra, dict):
            sys.exit("config.extra.json должен быть объектом JSON")
        cfg.update(extra)

    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
