# Release и приёмка Android backend

Этот документ описывает выпуск клиентского APK. Он не требует изменений Nextcloud,
роутера или серверной конфигурации. Проверка с настоящим сервером выполняется только
от имени отдельного обычного пользователя без административных прав.

## 1. Требования к машине сборки

- JDK 17;
- Android SDK Platform 37.0 и Build Tools 36.0.0;
- Gradle 9.3.1 Wrapper и Android Gradle Plugin 9.1.1;
- физический телефон или эмулятор для instrumented-тестов.

Gradle Wrapper уже находится в проекте, а SHA-256 дистрибутива закреплён в
`gradle-wrapper.properties`. Системный Gradle для обычной сборки не требуется.

На текущей машине SDK и Gradle-кеш размещены на диске Data, проект связан с SDK через
игнорируемый Git файл `local.properties`. `compileSdk` равен 37, `targetSdk` намеренно
остаётся 35 до отдельной проверки изменений поведения новых Android.

## 2. Автоматические проверки

Из корня проекта:

```bash
./scripts/release-checks.sh
./gradlew clean testDebugUnitTest lintRelease
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

На 16 октября 2026 года `testDebugUnitTest`, чистые `lintRelease` и `assembleRelease`
проходят. Instrumented-тесты требуют подключённого Android-устройства; без устройства
они не считаются выполненными.

Instrumented-набор включает миграции Room `1 -> 4` и `3 -> 4` с сохранением очереди,
сохранение индекса и очереди после повторного открытия БД, восстановление только
устаревших `running`-заданий, библиотеку
из 50 000 объектов, отсутствие credential-полей в Room и шифрование app password в
Android Keystore. На слабом устройстве тест большой библиотеки может выполняться долго.

## 3. Read-only smoke test обычного Nextcloud-пользователя

Тест `NonAdminNextcloudSmokeTest` пропускается, пока ему не переданы три временных
instrumentation argument:

- `ncServer` — адрес существующего Nextcloud;
- `ncUsername` — отдельный пользователь без admin-прав;
- `ncAppPassword` — одноразовый app password этого пользователя.

Аргументы задаются во временной Android Studio Run Configuration и не сохраняются в
проекте. Тест читает `status.php`, профиль, capabilities и корень WebDAV; он ничего не
записывает и не меняет на сервере. После проверки app password отозвать в Nextcloud.
Следует считать, что instrumentation arguments могут быть видны локальным средствам
отладки, поэтому основной пользовательский пароль здесь недопустим.

## 4. Проверки перед подписью

1. `release-checks.sh` завершился успешно.
2. Unit-, instrumented- и lint-тесты зелёные.
3. R8-сборка завершается без отсутствующих классов и без новых широких `-keep`-правил.
4. В APK нет debug certificate, HTTP logging interceptor, аналитики и ключей подписи.
5. На телефоне вручную пройдены вход Login Flow v2, список медиа, preview, Range-видео,
   ручная и автоматическая загрузка, потеря Wi-Fi и перезапуск приложения.
6. Проверено обновление поверх предыдущей версии тем же ключом без потери Room-данных.

## 5. Подпись APK

Ключ создаётся и хранится вне каталога проекта, резервная копия — отдельно. Пример
создания нового ключа (пароли вводятся интерактивно):

```bash
keytool -genkeypair -v -keystore /защищённый/путь/local-gallery-release.jks \
  -alias local-gallery -keyalg RSA -keysize 4096 -validity 10000
```

После `assembleRelease` выровнять неподписанный APK и подписать его утилитами из
Android SDK Build Tools. `apksigner` запросит пароли интерактивно:

```bash
zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk local-gallery-aligned.apk
apksigner sign --ks /защищённый/путь/local-gallery-release.jks \
  --ks-key-alias local-gallery --out local-gallery-release.apk local-gallery-aligned.apk
apksigner verify --verbose --print-certs local-gallery-release.apk
```

Обновления Android принимаются только при подписи тем же ключом. Сам ключ, пароли,
`keystore.properties` и готовые приватные материалы не копируются в проект и Git.

## 6. Результат выпуска

Сохранить рядом с APK номер версии, SHA-256 APK, вывод `apksigner verify` без секретов и
краткий протокол ручной приёмки. Если любой обязательный тест не запускался, APK нельзя
помечать как проверенный release.
