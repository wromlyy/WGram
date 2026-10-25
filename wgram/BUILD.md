# Сборка WGram

Исходная база: официальный Telegram Android 13.1.1,
коммит `3de37c8cfb039452be7398909461da6288babf15`.
Сохранены Gradle Wrapper, native build и лицензия исходного проекта.

Для работы только с телефона: [пошаговая инструкция](PHONE.md).

## Локально на компьютере

Нужны JDK 17, Android SDK 36, Build Tools 36.0.0, NDK 27.2.12479018
и CMake 3.22.1. Подойдут Android Studio 2025.1.4 или совместимая версия
и Linux/macOS/Windows с поддерживаемым NDK. Сборка внутри Android/Termux
не проверялась: обычные инструменты Android SDK/NDK рассчитаны на хост-компьютер.

1. В корне WGram скачайте native-подмодули:

   ```sh
   git submodule update --init --recursive --depth=1
   ```

   В текущем рабочем каталоге основные файлы восстановлены полностью,
   native-подмодули ещё не скачаны.

2. Получите собственные `api_id` и `api_hash` на https://my.telegram.org/apps.
   Скопируйте `wgram.properties.example` в `wgram.properties` и заполните поля.
   Также поддерживаются переменные `WGRAM_API_ID` и `WGRAM_API_HASH`.
   Значения автоматически попадают в BuildConfig; Java-файлы менять не нужно.

3. Для первой сборки Firebase **не требуется**. По умолчанию FCM отключён:
   отправка и чтение сообщений используют обычное соединение Telegram,
   но фоновые push-уведомления через Google недоступны. После закрытия
   приложения или при ограничениях батареи уведомления могут задерживаться.

   Для экспериментов с Firebase положите собственный файл для package
   `org.wgram.messenger.beta` в
   `TMessagesProj_App/src/debug/google-services.json` (исключён из Git)
   и передайте `-PwgramFirebase=true` или `WGRAM_FIREBASE_ENABLED=true`.
   Доставка уведомлений через серверы Telegram этим не подтверждена;
   наличие Firebase JSON само по себе её не гарантирует.

4. Укажите Android SDK в `local.properties` (`sdk.dir=...`) или через
   `ANDROID_HOME`, затем:

   ```sh
   sh wgram/test.sh
   python3 wgram/verify_resources.py
   ./gradlew :TMessagesProj_App:assembleAfatDebug
   ```

   APK ожидается в `TMessagesProj_App/build/outputs/apk/afat/debug/app.apk`.
   Debug-ключ создаёт Android Gradle Plugin на компьютере разработчика.
   Постоянный ключ подписи и публикацию нужно настроить перед выпуском.
   В GitHub Actions можно задать `WGRAM_DEBUG_KEYSTORE_BASE64` — содержимое
   своего debug.keystore в Base64 (JKS, alias androiddebugkey, пароли android).
   Тогда debug-сборки имеют одну подпись и устанавливаются как обновления.
   Без этого секрета ключи разных runner могут отличаться.

Пакет WGram отличается от официального клиента: приложения могут быть
установлены одновременно. Автообновления официального Telegram отключены
для WGram. Google Play Billing и passkeys официального приложения отключены;
их подключение к своему package требует отдельной настройки.

## GitHub Actions

В подготовленном репозитории есть два workflow:

- **WGram checks** — тесты логики, Java-синтаксис и XML, без SDK и ключей.
- **WGram debug APK** — ручной запуск сборки APK.

Для второго обязательны только repository secrets `WGRAM_API_ID`
и `WGRAM_API_HASH`. `WGRAM_FIREBASE_JSON` необязателен: без него сборка
идёт с отключённым FCM, с ним проверяется package `org.wgram.messenger.beta`
и включается получение Firebase-токена. После успешной сборки APK будет
доступен как artifact `WGram-debug`. Workflow подготовлен, удалённо
не запускался. Ошибки Android-компиляции и native-зависимостей пока возможны.

## Что проверяют локальные тесты

`wgram/test.sh` компилирует настоящие классы WGramConfig, WGramEmojiWire
и WGramEmojis с минимальными заглушками Android и Telegram.
Проверяются 64-битные ID, неверные ссылки, границы UTF-16, неизменность
исходных entities, передача и восстановление эмодзи, секретные чаты,
настоящий Premium, разделение аккаунтов и выбор графического режима.
Дополнительно Java compiler разбирает синтаксис изменённых исходников.

Эти проверки не заменяют Android-сборку, проверку типов всего клиента,
измерение FPS и обмен сообщениями между двумя реальными клиентами.

Для отдельной проверки Java-типов Android-клиента в этом же workflow
есть флаг `check_java_only`. Он запускает compileAfatDebugJavaWithJavac
без упаковки и публикации APK. Параметры API по-прежнему нужны.
