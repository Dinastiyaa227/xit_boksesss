Сборка через GitHub (ничего ставить на ПК не нужно):
1. Создай репозиторий на github.com и загрузи СОДЕРЖИМОЕ папки legitclient
   (build.gradle, src, .github и т.д. должны лежать в корне репозитория).
2. Вкладка Actions -> workflow "build" запустится сам (или Run workflow).
3. Готовый .jar: Actions -> последний запуск -> Artifacts -> legitclient.
   Или тег v1.0.0 -> jar появится во вкладке Releases.
4. Положи jar в .minecraft/mods вместе с Fabric API (Minecraft 1.21.11, Fabric Loader 0.18+, Java 21).

Управление:
- Right Shift: открыть/закрыть Click GUI
- Кнопка модуля: вкл/выкл
- Кнопка Bind: нажми её, затем нужную клавишу (Backspace - снять бинд, Esc - отмена)
- Бинды работают в игре, когда экран не открыт
