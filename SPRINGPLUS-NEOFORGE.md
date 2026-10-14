# SpringPlus NeoForge 26.2

Это отдельная рабочая копия для порта Female Gender Mod на NeoForge. Она не находится внутри Fabric-проекта `FemaleGenderMod`.

Версия SpringPlusCosmetics начинается с `0.1.0-beta.1`; суффикс `+mc26.2` указывает только совместимую версию Minecraft.

Исходники разделены Stonecutter по назначению:

- `common/src/main/java` — общая логика и геометрия персонажа.
- `neoforge/src/main/java` — NeoForge-события, сеть, кнопка инвентаря и косметика SpringPlus.
- `neoforge/src/main/resources` — метаданные и mixin-конфигурация NeoForge.
- `neoforge/versions/26.2` — сгенерированный проект версии; исходники сюда вручную не кладутся.

Готовый файл для установки лежит в `dist/SpringPlusCosmetics-neoforge-0.1.0-beta.1+mc26.2.jar`.

Пересборка из корня проекта: `gradlew.bat :neoforge:26.2:build`.
