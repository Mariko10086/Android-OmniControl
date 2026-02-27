# Android-OmniControl

Android 自动化设备控制台的端侧 Agent，部署于大规模设备集群（万台级别），周期性向中控服务器上报：

- 设备在线状态（心跳）
- 内部存储 / 外置 SD 卡使用情况
- 指定包名 APP 的安装状态与版本信息

---

## 架构概览

```
设备启动
   │
   ▼
OmniControlApp.onCreate()
   │  注册周期任务
   ▼
WorkerScheduler ──────────────────────────────────────────────────────────┐
   │  PeriodicWorkRequest（默认 15 分钟，需联网，指数退避重试）               │
   │  随机抖动 0-5 分钟初始延迟（防止 7 万台同时打请求）                       │
   ▼                                                                      │
ReportWorker.doWork()                                                     │
   │                                                                      │
   ├── DeviceInfoCollector  → DeviceInfo（UUID 设备 ID + Build 信息）      │
   ├── StorageInfoCollector → StorageInfo（StatFs 内/外存）                │
   └── AppStatusCollector  → List<AppStatusInfo>（PackageManager 查询）   │
                                                                          │
   │  POST /v1/devices/report                                             │
   ▼                                                                      │
中控服务器                                                                  │
   │  响应体可携带远程配置更新                                               │
   └── updated_packages / next_interval_minutes / updated_server_url ─────┘
        （动态更新生效，无需重新打包）

BootReceiver  监听 BOOT_COMPLETED / MY_PACKAGE_REPLACED → 重新注册任务
MainActivity  本地 Dashboard，实时展示上报数据预览
```

---

## 快速配置

### 1. 服务器地址

**方式一：编译时配置（推荐，永久生效）**

编辑 `app/build.gradle.kts`，修改对应 Build Type 的 `buildConfigField`：

```kotlin
buildTypes {
    release {
        // 生产服务器地址，必须以 / 结尾
        buildConfigField("String", "SERVER_BASE_URL", "\"https://your-server.example.com/v1/\"")
    }
    debug {
        // 本地调试服务器
        buildConfigField("String", "SERVER_BASE_URL", "\"http://192.168.1.100:8080/v1/\"")
    }
}
```

> 注意：地址必须以 `/` 结尾，否则 Retrofit 拼接路径会出错。

**方式二：运行时远程下发（无需重新打包）**

服务端在任意一次上报的响应体中返回新地址：

```json
{
  "status": "ok",
  "updated_server_url": "https://new-server.example.com/v1/"
}
```

Agent 收到后自动持久化到 SharedPreferences，下次 WorkManager 执行时生效。

---

### 2. 监控包名列表

**方式一：编译时配置**

编辑 `app/src/main/java/com/omnicontrol/agent/config/AppConfig.kt`：

```kotlin
val DEFAULT_TARGET_PACKAGES: List<String> = listOf(
    "com.your.app1",
    "com.your.app2",
    "com.android.chrome"
)
```

**方式二：运行时远程下发**

服务端响应体中返回新的包名列表（**覆盖**，非追加）：

```json
{
  "status": "ok",
  "updated_packages": ["com.your.app1", "com.your.app3"]
}
```

> `updated_packages` 为空数组 `[]` 时不更新，为 `null` 时也不更新。只有非空数组才会覆盖本地配置。

---

### 3. 上报间隔

**方式一：编译时配置**

编辑 `AppConfig.kt`：

```kotlin
const val DEFAULT_REPORT_INTERVAL_MINUTES = 15L  // 最小值为 15（WorkManager 限制）
```

**方式二：运行时远程下发**

```json
{
  "status": "ok",
  "next_interval_minutes": 30
}
```

Agent 收到后重新调度 WorkManager 周期任务。值必须 ≥ 15，否则忽略。

---

### 4. 调试服务器明文 HTTP（开发阶段）

编辑 `app/src/main/res/xml/network_security_config.xml`，在 `domain-config` 中添加你的调试服务器 IP：

```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">192.168.1.100</domain>
    <!-- 添加其他调试 IP -->
</domain-config>
```

生产环境的 `base-config` 强制 HTTPS，无需修改。

---

## API 接口说明

### POST `/v1/devices/report`

**请求 Body（JSON）**

```json
{
  "device": {
    "device_id": "550e8400-e29b-41d4-a716-446655440000",
    "model": "Pixel 6",
    "manufacturer": "Google",
    "android_version": "13",
    "sdk_int": 33,
    "brand": "google",
    "product": "oriole"
  },
  "storage": {
    "internal": {
      "total_bytes": 128000000000,
      "available_bytes": 54000000000,
      "used_bytes": 74000000000,
      "used_percent": 57.8
    },
    "external_sd": null
  },
  "apps": [
    {
      "package_name": "com.your.app1",
      "installed": true,
      "version_name": "3.4.1",
      "version_code": 341,
      "enabled": true,
      "first_install_time_ms": 1700000000000,
      "last_update_time_ms": 1710000000000
    },
    {
      "package_name": "com.your.app2",
      "installed": false,
      "version_name": null,
      "version_code": null,
      "enabled": null,
      "first_install_time_ms": null,
      "last_update_time_ms": null
    }
  ],
  "reported_at_ms": 1740000000000,
  "report_sequence": 142
}
```

**响应 Body（JSON）**

```json
{
  "status": "ok",
  "next_interval_minutes": 15,
  "updated_packages": [],
  "updated_server_url": null
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | String | `"ok"` 或错误描述 |
| `next_interval_minutes` | Long? | 新的上报间隔（≥15），null 则不更新 |
| `updated_packages` | List\<String\>? | 非空数组时覆盖监控包名列表，null/空数组不更新 |
| `updated_server_url` | String? | 非空时更新服务器地址，null 不更新 |

---

## 数据说明

### 设备 ID（device_id）

首次启动时自动生成 UUID 并持久化到 SharedPreferences，后续不变。
不使用 IMEI 或序列号（Android 10+ 需要特权权限）。

### 存储信息

- `internal`：基于 `Environment.getDataDirectory()` 使用 `StatFs` 采集，无需权限
- `external_sd`：检测真实 SD 卡挂载点（区别于内部虚拟外存），不存在时为 `null`

### APP 状态

使用 `PackageManager.getPackageInfo()` 查询，无需任何额外权限。未安装的包，`installed` 为 `false`，其余字段为 `null`。

---

## 构建与运行

```bash
# 调试包（连接本地服务器）
./gradlew assembleDebug

# 发布包（连接生产服务器）
./gradlew assembleRelease

# 安装到已连接设备
./gradlew installDebug
```

---

## 验证方法

```bash
# 1. 查看 OkHttp 网络请求日志（Debug 包才有）
adb logcat -s OkHttp

# 2. 查看 WorkManager 任务状态
adb shell dumpsys jobscheduler | grep omnicontrol

# 3. 模拟重启后任务是否恢复
adb reboot
# 等待启动后
adb shell dumpsys jobscheduler | grep omnicontrol
```

---

## 项目结构

```
app/src/main/java/com/omnicontrol/agent/
├── config/
│   └── AppConfig.kt              # 服务器地址、包名列表、上报间隔（改这里配置）
├── data/
│   ├── model/                    # 数据模型（DeviceReport、StorageInfo 等）
│   └── prefs/DevicePreferences.kt # 设备 ID 和序列号持久化
├── network/
│   ├── ApiService.kt             # Retrofit 接口定义
│   └── NetworkClient.kt          # OkHttp + Retrofit 构建
├── collector/
│   ├── DeviceInfoCollector.kt    # 采集设备基础信息
│   ├── StorageInfoCollector.kt   # 采集存储空间
│   └── AppStatusCollector.kt     # 检查 APP 安装状态
├── worker/
│   ├── ReportWorker.kt           # WorkManager 任务主体
│   ├── WorkerScheduler.kt        # 注册/调度周期任务
│   └── BootReceiver.kt           # 重启后恢复任务
└── ui/
    ├── DashboardViewModel.kt     # Dashboard 数据层
    └── DashboardUiState.kt       # UI 状态
```
