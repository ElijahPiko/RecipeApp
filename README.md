# 菜谱 App (Recipe)

一个基于 **Kotlin + Jetpack Compose (Material Design 3)** 的 Android 菜谱应用，数据存储在本机的
**MySQL 8** 中。项目采用客户端 / 服务端分离结构，Android 客户端通过 REST API 与后端交互，后端通过
JDBC 读写 MySQL。

## 项目结构

```
Recipe/
├── app/        # Android 客户端（Compose UI，Material 3，目标 Android 16 / API 36）
├── server/     # JVM 后端（JDK HttpServer + MySQL JDBC + REST API）
├── core/       # 纯 Kotlin 共享模块（领域模型、仓库接口、聚合/校验/统计等业务逻辑）
├── tools/      # 离线构建辅助脚本（本地依赖镜像工具，仅本机环境使用）
├── config/     # 环境配置（本地 / 生产隔离，含 IP、端口、数据库口令）
├── settings.gradle.kts
├── build.gradle.kts
└── gradle/libs.versions.toml
```

## 技术栈

- 目标平台：Android 16（`compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`）
- 开发语言：Kotlin 2.0.21
- UI：Jetpack Compose + Material Design 3 + Navigation Compose + Coil（图片加载）
- 网络：OkHttp + Gson
- 数据：MySQL 8（本机 `root / 123456`），后端用 JDBC（`mysql-connector-j`）
- 测试：JUnit 4 + kotlinx-coroutines-test（核心、后端含 MySQL 集成测试、Android 单测）
- 构建：AGP 8.10.1 + Gradle 9.4.1 + JDK 17

## 功能模块

### 菜谱页
- 顶部搜索栏（按菜名 / 分类 / 食材搜索）
- 左上角 **点菜**：进入点菜页面
- 菜谱卡片以**两列网格**陈列，图片为正方形，右下角有 **“+”**，点击即可加入点菜车
- 右上角 **新建菜谱**：填写成品图（作为 **BLOB** 存入数据库，而非 URL；选图后先**裁剪为固定的 500×500**，可拖动/缩放选区域）、食品材料（名称+用量）、调料（名称+用量）、操作步骤（有序列表）、
  分类（**必填**，下拉选择）

### 点菜页面
- 查看已点菜谱，可增减数量、移除
- **一键输出所需材料**：自动汇总所有已点菜谱的食品材料和调料（相同材料自动合并 / 累加用量）
- **按分类随机选菜**：先选择分类（下拉框），再点 **随机选菜**，从该分类中随机挑选若干菜谱加入点菜车；
  未选择分类时表示 **全部分类** 都参与随机
- **确认下单 / 保存菜单**：生成一条历史点菜记录

### 菜单页
- 顶部展示：**点菜次数、总金额、平均点菜花费**
- 历史点菜记录列表，支持修改每次做菜的 **花费金额** 并保存

### 个人页
- 昵称、签名、所在地、喜欢的分类等个人信息配置
- **分类管理**：支持对分类进行 **新增 / 修改 / 删除**（分类会同步用于新建菜谱的下拉选择，以及点菜页的按分类随机选菜）

## 数据库

首次启动后端会自动创建数据库 `recipe_app` 及四张表：`recipe`（菜谱）、`category`（分类）、
`menu_record`（菜单记录）、`profile`（个人信息）。菜谱的食品材料 / 调料 / 步骤以 JSON 列存储。

`category` 为**独立的分类表**，启动时会自动填充内置分类（家常菜 / 汤羹 / 凉菜 / 主食 / 烘焙 / 饮品 / 其他），
随后可在 **个人页** 对其增删改。菜谱的 `category` 列只保存分类名（字符串），新建菜谱时从下拉框中选择，不能自由输入。

菜谱成品图以 **`MEDIUMBLOB`** 形式直接写入 `recipe.image_data` 列（不存 URL），单张图片上限为
**16 MB**，由后端在接收时校验，超限返回 `413`。

表结构由 [server/src/main/kotlin/com/recipe/server/db/Db.kt](server/src/main/kotlin/com/recipe/server/db/Db.kt)
自动初始化。

## 环境配置（本地 / 生产隔离）

所有 IP、端口、数据库口令等**不再写在代码里**，统一放在 `config/` 目录：

```
config/
├── server.local.properties            # 本地后端配置（提交）
├── server.prod.example.properties     # 生产后端配置模板（提交，复制后填真实值）
├── client.local.properties            # 本地客户端配置（提交）
└── client.prod.properties             # 生产客户端配置（提交）
```

优先级从高到低：

1. **环境变量**（如 `RECIPE_DB_PASSWORD`）—— 生产密码建议走这条，不落盘
2. **项目根 `local.properties`** —— 本机私有覆盖项，不提交，可临时切换接口地址
3. **`config/` 下对应环境的配置文件**
4. 代码内默认值（仅当上面都没有时才生效）

### 切换后端环境

```bash
gradlew :server:run                      # 自动用 config/server.local.properties
gradlew :server:run -Drecipe.env=prod    # 显式指定生产配置
RECIPE_ENV=prod gradlew :server:run      # 同上（跨平台写法）
```

若`config/server.prod.properties` 存在且未指定环境，默认按 `prod` 加载；
否则用 `local`。启动日志会打印实际生效的配置来源：

```
Config env: local (source: D:\...\config\server.local.properties)
Effective: http://0.0.0.0:8080 | db=127.0.0.1:3306/recipe_app | inMemory=false
```

`server.prod.properties` 含密码，已被 `.gitignore` 忽略；部署时更推荐直接注入
`RECIPE_DB_PASSWORD` 环境变量，配置文件里留空即可。

### 切换客户端环境

客户端通过 productFlavor 区分环境，配置在构建期注入 `BuildConfig`：

| Flavor | 配置文件 | 用途 |
| --- | --- | --- |
| `local` | `config/client.local.properties` | 本地调试（模拟器 `10.0.2.2`） |
| `prod` | `config/client.prod.properties` | 生产包 |

```bash
gradlew :app:assembleLocalDebug        # 本地包
gradlew :app:assembleProdRelease       # 生产包
gradlew :app:assembleProdRelease -PrecipeEnv=local   # 生产签名但连本地后端
```

注入的字段：`BuildConfig.API_BASE_URL`、`BuildConfig.DEBUG_LOGGING`、`BuildConfig.ENV_NAME`。

> **真机调试**：模拟器用 `10.0.2.2`，真机需改成电脑局域网 IP。
> 推荐不要改 `config/`，而是在根目录 `local.properties` 里加一行覆盖（优先级更高）：
> ```properties
> api.baseUrl=http://192.168.1.5:8080
> ```

## 运行后端

默认连接本机 MySQL（`127.0.0.1:3306`，用户 `root`，密码 `123456`），启动后监听 `0.0.0.0:8080`。

```bash
# 在项目根目录执行
gradlew :server:run
```

也可用环境变量覆盖任意配置项（优先级高于配置文件，见上文）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `RECIPE_ENV` | 自动判断 | 选择环境：`local` / `prod` |
| `RECIPE_SERVER_HOST` | `0.0.0.0` | 监听地址 |
| `RECIPE_SERVER_PORT` | `8080` | 端口 |
| `RECIPE_DB_HOST` | `127.0.0.1` | MySQL 主机 |
| `RECIPE_DB_PORT` | `3306` | MySQL 端口 |
| `RECIPE_DB_NAME` | `recipe_app` | 数据库名 |
| `RECIPE_DB_USER` | `root` | 用户 |
| `RECIPE_DB_PASSWORD` | `123456` | 密码 |
| `RECIPE_IN_MEMORY` | `false` | 设为 `true` 使用内存存储（不依赖 MySQL） |

## 运行 Android 客户端

客户端默认连`http://10.0.2.2:8080`（模拟器中 `10.0.2.2` 即宿主机），地址由
`config/client.<env>.properties` 注入，详见上文「切换客户端环境」。

```bash
gradlew :app:installLocalDebug     # 安装到已连接的模拟器/设备
gradlew :app:assembleLocalDebug    # 仅构建 APK
gradlew :app:assembleProdRelease   # 生产包
```

APK 输出：`app/build/outputs/apk/localDebug/app-local-debug.apk`

## 运行测试

```bash
gradlew :core:test :server:test :app:testDebugUnitTest
```

- `core`：领域 / 业务逻辑单元测试（校验、统计、食材聚合、点菜车）
- `server`：内存 DAO 单元测试、REST 接口往返测试，以及针对本机 MySQL 的集成测试
  （`MySqlDaoIntegrationTest`，MySQL 不可达时自动跳过）
- `app`：各 ViewModel 单元测试与数据序列化契约测试

> Gradle Wrapper 已固定为 **Gradle 9.4.1**（首次运行会从
> `~/.gradle/wrapper/dists` 本地缓存读取，无需联网下载）。

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/recipes?search=&category=` | 菜谱列表 / 搜索 / 按分类过滤（`category` 可选） |
| GET | `/api/recipes/{id}` | 单个菜谱 |
| POST | `/api/recipes` | 新建菜谱 |
| PUT | `/api/recipes/{id}` | 更新菜谱 |
| DELETE | `/api/recipes/{id}` | 删除菜谱 |
| POST | `/api/recipes/{id}/image` | 上传成品图（原始字节，BLOB，≤16MB） |
| GET | `/api/recipes/{id}/image` | 下载成品图（BLOB，按魔数推断图片类型） |
| GET | `/api/categories` | 分类列表 |
| POST | `/api/categories` | 新增分类（body: `{name, colorHex?, sortOrder?}`） |
| PUT | `/api/categories/{id}` | 修改分类 |
| DELETE | `/api/categories/{id}` | 删除分类 |
| GET | `/api/menus` | 菜单记录列表 |
| GET | `/api/menus/stats` | 点菜统计（次数 / 总金额 / 平均） |
| POST | `/api/menus` | 确认下单 |
| PUT | `/api/menus/{id}` | 修改花费金额 |
| DELETE | `/api/menus/{id}` | 删除记录 |
| GET | `/api/profile` | 获取个人信息 |
| PUT | `/api/profile` | 保存个人信息 |

当某道菜谱存在图片时，`GET /api/recipes`（及 `GET /api/recipes/{id}`）返回的 `hasImage` 为 `true`，
且 `imageUrl` 指向对应的 `/api/recipes/{id}/image` 端点。

## 本机构建环境说明

本机无法直接访问 Maven/Gradle 官方仓库，因此项目对依赖做了一次 **本地 Maven 镜像**：
把此前 Gradle 缓存中的依赖（含 AAR）重建到 `~/.m2/repository`，并在 `settings.gradle.kts` 中把
`mavenLocal()` 放在仓库列表最前面。构建时**不要加 `--offline`**（离线模式会绕过本地镜像），
依赖会全部从 `~/.m2` 解析。

`settings.gradle.kts` 的仓库模式使用 `RepositoriesMode.PREFER_SETTINGS`，并在 settings 中显式声明了腾讯云镜像
（`https://mirrors.cloud.tencent.com/maven`）。这样即使某些机器通过全局 `init.gradle.kts`（例如
`GRADLE_USER_HOME/init.gradle.kts`）向项目注入镜像仓库，也只会产生警告而不会让构建失败。

相关辅助脚本在 `tools/`：

- `tools/build_m2_mirror.ps1`：把 Gradle 缓存重建到 `~/.m2`（含 `-release.aar` 等 KMP 产物）
- `tools/offline_gap_fix.ps1`：把指定 `group:artifact:version` 从缓存补进 `~/.m2`

项目根目录的 `local.properties` 指向本机 Android SDK（`D:\Application\Android`），`java.home` 使用 JDK 17。
该文件同时可作为本机私有覆盖项（见上文「环境配置」），且不会被提交。
