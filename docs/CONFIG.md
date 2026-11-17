# Настройки

Есть два уровня.

1. **`cleanmysalutetv.conf`** — основное: док, приветствие, колонки, обои, заставка, DNS. Описание в
   самом файле (`cleanmysalutetv.conf.example`).
2. **`config.extra.json`** — любой другой ключ лаунчера. Лежит в корне репозитория рядом с conf, в git
   не попадает. Его ключи накладываются поверх собранного из conf.

Пример `config.extra.json`:

```json
{
  "dock_glass": true,
  "dock_opacity": 60,
  "home_idle_clock_minutes": 10,
  "background_color": "#0b0d12"
}
```

Применить: `bash scripts/install.sh --config-only`. Что в итоге ушло на ТВ — `state/config.json`.

Полный список ключей лаунчера (на английском, из cleanyourtv): [upstream/cleanyourtv-themes.md](upstream/cleanyourtv-themes.md),
раздел Keys. Настройки, которые меняются прямо на ТВ (удержать OK на кнопке настроек), —
[upstream/cleanyourtv-launcher.md](upstream/cleanyourtv-launcher.md). В этих документах упоминается
`tools/theme.sh`: здесь вместо него `scripts/install.sh --config-only`.
