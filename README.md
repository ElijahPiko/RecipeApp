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
- 顶部搜索栏（按菜名 / 分类 / 食材搜索），占满整行
- 菜谱卡片以**两列网格**陈列，图片为正方形，右下角有 **“+”**，点击即可加入点菜车
- **长按菜谱卡片**：进入 **编辑页**，可修改成品图、菜名、分类、食品材料、调料、操作步骤；
  表单最底部有 **删除这道菜谱** 按钮（二次确认后删除）
- **左下角悬浮圆形按钮（+）**：新建菜谱。不随列表滚动，点击后进入表单（与编辑页是同一个页面）：
  填写成品图（作为 **BLOB** 存入数据库，而非 URL；选图后先**裁剪为固定的 500×500**，可拖动/缩放选区域）、食品材料（名称+用量）、调料（名称+用量）、操作步骤（有序列表）、
  分类（**必填**，下拉选择）
- **右下角悬浮圆形按钮（碗图标）**：进入点菜页面。不随列表滚动，已点菜品数量以红色角标显示在按钮右上角（0 时不显示，超过 99 显示 `99+`）

### 点菜页面
- 查看已点菜谱，可增减数量、移除
- **预估价格**：每道菜按「材料均价（元/千克）× 菜谱标注重量」估算单价，显示在条目里；
  列表上方汇总 **预估总价 = Σ(单价 × 数量)**。该价格**仅作展示与记录，不计入菜单页「总金额」**
  - 只有**同时**满足「菜谱上标了可换算的重量」和「材料账单里有该材料均价」的材料才参与计算
  - 支持 克 / 千克 / 公斤 / 斤 / 两 / 毫升 / 升（毫升按密度 1 折算），以及「约 500 克」「半斤」这类写法；
    「2 个」「适量」这类没有重量的用量不计价，条目会点出**具体哪些材料**未计价（如「番茄、青菜未计价」），全部材料都计价时不显示该提示
  - 材料名优先精确匹配，无精确匹配时接受**唯一的**包含匹配（如「猪排骨」→「排骨」），有歧义则不用
- **一键输出所需材料**：自动汇总所有已点菜谱的食品材料和调料（相同材料自动合并 / 累加用量）
- **按分类随机选菜**：先选择分类（下拉框），再点 **随机选菜**，从该分类中随机挑选若干菜谱加入点菜车；
  未选择分类时表示 **全部分类** 都参与随机
- **确认下单 / 保存菜单**：生成一条历史点菜记录（菜单页「点菜次数」随之 +1，并把当时的预估价快照存入记录）

### 菜单页
- 顶部展示：**点菜次数、总金额、平均点菜花费**
  - **总金额 = 材料账单的累计支出**（不是各条菜单记录里手填的花费合计）
  - **平均花费 = 总金额 ÷ 点菜次数**
- 历史点菜记录列表：每条显示 **时间、点了哪些菜、预估成本**（下单时留档，不计入「总金额」）
- **照片**：默认展示这次点过的菜的成品图缩略图；也可以给某条记录 **上传/更换** 一张自己拍的照片（裁剪成 500×500 后以 BLOB 存服务端），有自己照片时优先显示大图
- **右下角悬浮圆形按钮（菜篮子图标）**：进入 **材料账单** 页面，不随列表滚动

### 材料账单页
- 每笔记录一行：**材料名称 / 重量（千克）/ 金额（元）**
- 顶部显示 **累计支出**、账单条数、材料种类数量
- 录入时若该材料已有历史记录，会实时提示其 **历史均价（元/千克）**
- 每条记录可删除；删除后该材料的均价会按剩余记录自动重算
- 单位暂时统一为 **元每千克**：均价由服务端按 **总金额 ÷ 总重量**（加权平均）维护，而不是单价的简单平均

### 个人页
- 昵称、签名、所在地、喜欢的分类等个人信息配置
- **分类管理**：个人页只保留一个入口，点击后进入独立的 **分类管理页**，在那里对分类做 **新增 / 修改 / 删除**
  （分类会同步用于新建菜谱的下拉选择，以及点菜页的按分类随机选菜）

## 数据库

首次启动后端会自动创建数据库 `recipe_app` 及以下表：`recipe`（菜谱）、`category`（分类）、
`menu_record`（菜单记录）、`users`（账号 + 个人资料）、`material_bill`（材料账单）、
`material_price`（材料均价）。菜谱的食品材料 / 调料 / 步骤以 JSON 列存储。

`users` **把账号和个人资料合并在同一行**：`username` / `password_hash` / `salt` / `token` 之外，
还有 `nickname` / `avatar_url` / `signature` / `location` / `favorite_category`。
账号存在，资料就存在，不需要额外的一张 profile 表。
老版本库里独立的 `profile` 表会在启动时自动并入 `users`，随后改名为
`profile_merged_backup` 留档（确认无误后可以手动 `DROP TABLE profile_merged_backup`）。

`material_bill` 每行是一次采购（`name` / `weight_kg` / `price` / `created_at`），按 `user_id` 隔离；
`material_price` 以 `(user_id, name)` 为主键，维护累计重量、累计金额与条数，
读取时按 `total_price / total_weight_kg` 得出 **元/千克** 加权均价。
新增账单时累加、删除账单时按剩余记录重算，两张表始终保持一致。

`menu_record.estimated_cost` 存的是下单那一刻的预估价快照（材料均价 × 菜谱用量），
只用于回看，不参与任何统计；老数据该列默认为 0。
`menu_record.image_data` 存用户给这条菜单上传的照片（`MEDIUMBLOB`，可为空），
`photo_updated_at` 作为照片版本号拼进 URL（`?v=...`），换图后客户端不会继续用旧缓存。

`category` 为**独立的分类表**，启动时会自动填充内置分类（家常菜 / 汤羹 / 凉菜 / 主食 / 烘焙 / 饮品 / 其他），
随后可在 **个人页** 对其增删改。菜谱的 `category` 列只保存分类名（字符串），新建菜谱时从下拉框中选择，不能自由输入。

菜谱成品图以 **`MEDIUMBLOB`** 形式直接写入 `recipe.image_data` 列（不存 URL），单张图片上限为
**16 MB**，由后端在接收时校验，超限返回 `413`。

表结构由 [server/src/main/kotlin/com/recipe/server/db/Db.kt](server/src/main/kotlin/com/recipe/server/db/Db.kt)
自动初始化。

### 连接池（HikariCP）

后端通过 **HikariCP** 连接池访问 MySQL（见
[ConnectionProvider.kt](server/src/main/kotlin/com/recipe/server/db/ConnectionProvider.kt)）。
DAO 每次调用**借一条连接、用完立刻归还**，不再持有单条 `Connection`。

> 早期版本把同一条 `Connection` 注入给所有 DAO，并由 8 个 HTTP 工作线程并发共用。
> 一旦 MySQL 的 `wait_timeout` 回收了这条连接，驱动会把它永久标记为 closed，
> 之后所有请求都会抛 `ConnectionIsClosedException: No operations allowed after connection closed`
> 且无法自愈 —— 表现就是「进程还活着，但所有接口都 500」。

池的关键参数（括号内为环境变量）：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `db.pool.maxSize` (`RECIPE_DB_POOL_MAX_SIZE`) | `10` | 最大连接数，与 HTTP 工作线程数（8）相当 |
| `db.pool.minIdle` (`RECIPE_DB_POOL_MIN_IDLE`) | `2` | 常驻空闲连接数 |
| `db.pool.connectionTimeoutMs` (`RECIPE_DB_POOL_CONNECTION_TIMEOUT_MS`) | `5000` | 借连接的最长等待时间 |
| `db.pool.maxLifetimeMs` (`RECIPE_DB_POOL_MAX_LIFETIME_MS`) | `1500000` (25 分钟) | **必须小于 MySQL `wait_timeout`**，由池主动淘汰旧连接 |
| `db.pool.idleTimeoutMs` (`RECIPE_DB_POOL_IDLE_TIMEOUT_MS`) | `600000` (10 分钟) | 超出 `minIdle` 的空闲连接存活时间 |
| `db.pool.keepaliveMs` (`RECIPE_DB_POOL_KEEPALIVE_MS`) | `120000` (2 分钟) | 空闲连接探活间隔，`0` 关闭 |

启动日志会打印实际生效值：

```
Connected to MySQL 127.0.0.1:3306/recipe_app.
Connection pool=recipe-pool maxSize=10 minIdle=2 maxLifetime=1500000ms idleTimeout=600000ms keepalive=120000ms
```

> 如果自行调大 `db.pool.maxLifetimeMs`，请同时确认它仍小于 MySQL 的
> `SHOW VARIABLES LIKE 'wait_timeout'`（默认 28800 秒），否则问题会原样复现。

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
gradlew :server:run                      # 固定用 config/server.local.properties
gradlew :server:run -Drecipe.env=prod    # 显式指定生产配置
RECIPE_ENV=prod gradlew :server:run      # 同上（跨平台写法）
```

`gradlew :server:run` 会被 Gradle 强制指定为 `local`，**不会**因为
`config/server.prod.properties` 存在就切到生产配置——那是给服务器用的，账号密码与本机不同，
连不上 MySQL 时服务端会静默降级成内存存储（重启即丢数据）。
直接跑 jar（部署场景）时仍沿用老规则：`RECIPE_ENV` 未指定且 prod 配置存在则按 `prod` 加载。

启动日志会打印实际生效的配置来源，**每次启动都该看一眼前两行**：

```
Config env: local (source: D:\...\config\server.local.properties)
Effective: http://0.0.0.0:8080 | db=127.0.0.1:3306/recipe_app | inMemory=false
```

如果看到 `Falling back to in-memory storage`，说明 MySQL 没连上，接下来所有数据都不会落盘。

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
| `RECIPE_SERVER_THREADS` | `8` | HTTP 工作线程数（与 `db.pool.maxSize` 保持一致） |
| `RECIPE_DB_HOST` | `127.0.0.1` | MySQL 主机 |
| `RECIPE_DB_PORT` | `3306` | MySQL 端口 |
| `RECIPE_DB_NAME` | `recipe_app` | 数据库名 |
| `RECIPE_DB_USER` | `root` | 用户 |
| `RECIPE_DB_PASSWORD` | `123456` | 密码 |
| `RECIPE_IN_MEMORY` | `false` | 设为 `true` 使用内存存储（不依赖 MySQL） |
| `RECIPE_DB_POOL_MAX_SIZE` | `10` | 连接池最大连接数 |
| `RECIPE_DB_POOL_MAX_LIFETIME_MS` | `1500000` | 连接最大存活时间（须小于 MySQL `wait_timeout`） |

## 健康检查

`GET /api/health` 是**免鉴权**的探针（不需要 token，也不返回任何用户数据），供 systemd /
监控脚本 / 负载均衡判断实例是否可服务：

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/health
```

| 状态码 | 含义 | 响应示例 |
| --- | --- | --- |
| `200` | 服务和数据库都可用 | `{"status":"ok","db":"up","pool":{"active":1,"idle":2,"total":3,"waiting":0,"max":10},"uptimeMs":91234}` |
| `200` | 内存模式（未接 MySQL） | `{"status":"ok","db":"in-memory","uptimeMs":91234}` |
| `503` | 服务在线但数据库探活失败 | `{"status":"degraded","db":"down","pool":{...},"uptimeMs":91234}` |

探活方式是向连接池借一条连接执行 `Connection.isValid()`：连接池已死掉/MySQL 不可达时返回
`503`，而不是让请求挂住。探测本身最长耗时约等于 `db.pool.connectionTimeoutMs`（默认 5 秒），
所以脚本超时建议给到 8 秒以上。

配合看门狗（进程不会因为数据库断开而退出，因此必须靠 HTTP 状态判断）：

```bash
# 每 5 分钟一次；非 2xx 就重启
code=$(curl -s -o /dev/null -w '%{http_code}' -m 8 http://127.0.0.1:8080/api/health)
[ "${code:0:1}" = "2" ] || systemctl restart recipe-server
```

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
| GET | `/api/health` | **免鉴权**健康探针（200 正常 / 503 数据库不可用） |
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
| PUT | `/api/menus/{id}` | 修改花费金额（App 界面已不再使用，保留接口） |
| DELETE | `/api/menus/{id}` | 删除记录 |
| POST | `/api/menus/{id}/photo` | 给这条记录上传照片（原始字节，BLOB，≤16MB） |
| GET | `/api/menus/{id}/photo` | 读取这条记录的照片（**免鉴权**，Coil 无法带鉴权头） |
| GET | `/api/profile` | 获取个人信息 |
| PUT | `/api/profile` | 保存个人信息 |
| GET | `/api/materials/bills` | 材料账单列表（新→旧） |
| POST | `/api/materials/bills` | 新增一条采购（body: `{name, weightKg, price}`，`createdAt` 由服务端打） |
| DELETE | `/api/materials/bills/{id}` | 删除一条采购，并按剩余记录重算该材料均价 |
| GET | `/api/materials/prices` | 各材料均价（元/千克，加权平均） |
| GET | `/api/materials/stats` | 账单合计：`{totalPrice, entryCount}` |

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
