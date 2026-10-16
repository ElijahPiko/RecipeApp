# Recipe 项目长期记忆

## 项目概况
Kotlin 菜谱 App，客户端/服务端分离。三模块：`core`（纯 Kotlin 共享层）、`server`（JVM 后端）、`app`（Android 客户端）。
技术栈：Kotlin 2.0.21 / AGP 8.10.1 / Gradle 9.4.1 / JDK 17 / compileSdk 36 / minSdk 26 / Compose+Material 3。
README.md写得详实且与代码基本一致，可作为事实基准参考（个别环境描述已过期，见下）。

## 本机构建环境（重要）
- **Android SDK 实际在 `D:\Application\Android`**（android-36、build-tools 36.0.0）。
  环境变量 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 均未设置，需靠 `local.properties` 指定。
- JDK 17：`JAVA_HOME=D:\Application\jdks\jdk17`
- MySQL 8 在本机 3306，账号 `root / 123456`，库 `recipe_app`
- Gradle wrapper 9.4.1 已缓存；`~/.m2/repository` 有本地 Maven 镜像（本机访问不了官方仓库）
- **构建时不要加 `--offline`** —— 离线模式会绕过 `~/.m2` 本地镜像导致依赖解析失败

## 约定与坑位
- `settings.gradle.kts` 用 `RepositoriesMode.PREFER_SETTINGS`，`mavenLocal()` 置于仓库列表最前
- 根 `build.gradle.kts` 强制统一 `kotlin-stdlib:2.1.10`（离线缓存兼容性问题）
- `tools/` 下有两个 PowerShell 脚本用于维护本地镜像：
  `build_m2_mirror.ps1`（重建缓存到 `~/.m2`）、`offline_gap_fix.ps1`（补单个依赖）

## 架构约定
- `server/Main.kt` 支持双存储：MySQL DAO 与 InMemory DAO，MySQL 连不上时**自动降级为内存存储**
  （不报错退出，只是数据不落盘）
- 后端可用环境变量覆盖：`RECIPE_SERVER_HOST/PORT`、`RECIPE_DB_*`、`RECIPE_IN_MEMORY`
- 成品图以 `MEDIUMBLOB` 存`recipe.image_data`，不存 URL，单张上限 16MB（后端校验，超限返回 413）
- 食材/调料/步骤以 JSON 列存储
- `Db.kt` 启动时自动建库建表，并含旧库迁移逻辑（`image_data` 列的ALTER）
- 客户端手动 DI，组装入口 `AppContainer`，API 地址由 `app/build.gradle.kts` 的
  `BuildConfig.API_BASE_URL` 注入
