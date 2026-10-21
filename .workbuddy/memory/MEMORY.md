# Recipe 项目长期记忆

## 项目概况
Kotlin 菜谱 App，客户端/服务端分离。三模块：`core`（纯 Kotlin 共享层）、`server`（JVM 后端）、`app`（Android 客户端）。
技术栈：Kotlin 2.0.21 / AGP 8.10.1 / Gradle 9.4.1 / JDK 17 / compileSdk 36 / minSdk 26 / Compose+Material 3。
README.md写得详实且与代码基本一致，可作为事实基准参考（个别环境描述已过期，见下）。
**`ANDROID_DEV_GUIDE.md`** 是面向新手的目录结构与环境配置教程（918 行），
用户是 Android 开发新手，改动目录/构建/配置相关时需同步维护该文档。

## 用户背景
- **Android 开发新手**，2026-10-17 首次系统了解安卓项目结构
- 因此解释时需说明"为什么这样设计"，避免只罗列事实
- 用中文交流

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
- 客户端手动 DI，组装入口 `AppContainer`

## 环境配置约定（2026-10-17 建立，务必遵守）
**所有 IP / 端口 / 数据库口令一律不写进源码**，统一放 `config/` 目录。

四层优先级（高 → 低）：
1. 环境变量（`RECIPE_*`）—— 生产密码走这条，不落盘
2. 项目根 `local.properties` —— 本机私有覆盖项，不提交（如真机调试改 `api.baseUrl`）
3. `config/` 下对应环境的 `.properties`
4. 代码内默认值（仅兜底）

文件清单：
- `config/server.local.properties`（提交）｜`config/server.prod.example.properties`（模板，提交）
- `config/client.local.properties`（`10.0.2.2:8080`）｜`config/client.prod.properties`（`120.26.55.209:8080`）
- `config/server.prod.properties` 含密码，**已 gitignore**，部署前从 example 复制

切换方式：
- 后端：`./gradlew :server:run`（默认 local）/ `-Drecipe.env=prod` / `RECIPE_ENV=prod`
- 客户端：productFlavor `local` / `prod`，即 `:app:assembleLocalDebug` / `:app:assembleProdRelease`，
  可用 `-PrecipeEnv=local` 强制 release 包连本地

注入字段：`BuildConfig.API_BASE_URL` / `DEBUG_LOGGING` / `ENV_NAME`

## 连接池（2026-10-20 已修复）
- **旧问题（已解决）**：`Main.kt` 把单个 JDBC Connection 共享给 5 个 DAO，8 线程 HTTP 池并发使用；
  `wait_timeout` 断连后无重连 → 线上所有接口永久 500（进程不退出，属"假死"）。
- **现方案**：HikariCP 5.0.1，`db/ConnectionProvider.kt` 提供 `withConnection { }` 借还语义；
  `Db.open()` 返回 `HikariConnectionProvider`（原 `Db.connect(): Connection` 已删除）。
- **硬约束**：`db.pool.maxLifetimeMs` 必须 < MySQL `wait_timeout`（默认 28800s），默认 25 分钟；
  另有 `keepaliveMs=120000` 给空闲连接探活。
- 改 DAO 注意：方法体统一 `db.withConnection { connection -> ... }`，lambda 内**不能非局部 return**，
  要用表达式求值（`use{}` 是 inline 所以原本的 `return` 写法会编译失败或语义变化）。
- 不要再引入 `autoReconnect=true`。

## 菜谱页 UI 约定（2026-10-21）
- 顶部只有搜索框（撑满整行）；「点菜」「新建菜谱」是两个**不随滚动**的 56dp 圆形悬浮按钮：
  右下角=点菜（图标 `R.drawable.ic_order_dishes`，右上角红色 Badge 显示数量，0 时不显示），
  左下角=新建（`Icons.Filled.Add`，surface 底 + 橙 `#E4572E`）。
- 用 `ui/recipes/RecipesScreen.kt` 里的私有 `CircleFab` 实现，不要换回 `FloatingActionButton`
  （图标要满铺圆形，且 ripple 会被不透明图标遮住，按压反馈改用 scale 0.92）。
- 网格底部留 88dp contentPadding，Snackbar 上移 80dp，否则会压住按钮。
- 无障碍：`clearAndSetSemantics` 把描述和 onClick 放在同一节点，别只用 `onClickLabel`。


## 认证体系（2026-10-18 建立）
- 注册/登录：`POST /api/auth/register|login`，返回 Bearer token；`me`/`logout` 同组
- 其余 `/api/**` 一律要 `Authorization: Bearer <token>`，图片 GET 除外（Coil 无法带 header）
- 密码 PBKDF2（12 万迭代+随机盐）存 `users` 表；token 存 `users.token`
- 数据隔离：recipes/categories 全局共享；menu_record/profile 按 user_id 隔离
- 客户端：TokenStore(SharedPreferences) → RecipeApi tokenProvider 动态注入头
- App 启动流程：无 token → AuthScreen；登录/注册成功 → 主界面；个人页可退出登录
- **改 MenuDao/ProfileDao 接口签名时必须带 userId 参数**（多用户隔离是硬约束）

## 导航约定（2026-10-20 建立）
- BottomBar tab 切换：`popUpTo(startDestinationId) + launchSingleTop`，**禁止 restoreState=true**
  （与临时页进出组合会让 tab 导航变成"恢复错误栈段"，表现为 tab 点不动）
- 临时页（ORDER/NEW_RECIPE）无底栏（isTab=false），退出靠返回键或干净 popBackStack
- ORDER 下单跳转：`popBackStack(RECIPES, false)` + `navigate(MENUS){launchSingleTop}`
- OrderScreen 的 confirmed 导航必须走 LaunchedEffect，禁止组合期直接调 onConfirmed()
