# 三国守城

PICO Vibe Lab 原作品版本更新。版本 0.3.1 / versionCode 5。Android 包名 `com.vrgames.sanguo`。

变更内容见[更新说明](更新说明.md)，旧版说明作为历史记录保存在 docs/原工程说明.md。

## 构建

仓库根目录是 Gradle 工程。使用 JDK 21 和 Android SDK 35：

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Windows 使用 gradlew.bat。运行代码、素材与测试位于 app/src。正式发布沿用原应用签名，外部签名配置可通过 VRGAMES_SIGNING 指定；凭据与密钥不在仓库中。
