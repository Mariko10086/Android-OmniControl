# OmniControl ROM 预置系统 APP 部署指南

> 适用版本：Android 10 ~ 16（API 29 ~ 36）  
> 包名：`com.omnicontrol.agent`  
> 模块名：`OmniControl`  
> 最后更新：2026-04-01

---

## 目录

1. [概述](#1-概述)
2. [需要提供给 ROM 技术的文件清单](#2-需要提供给-rom-技术的文件清单)
3. [文件内容详解](#3-文件内容详解)
4. [各 Android 版本注意事项](#4-各-android-版本注意事项)
5. [目录放置规范](#5-目录放置规范)
6. [编译验证方法](#6-编译验证方法)
7. [常见问题排查](#7-常见问题排查)

---

## 1. 概述

OmniControl 是一个设备管理 Agent APP，需要以 **priv-app（特权系统 APP）** 级别预置进 ROM，原因：

| 需要的权限 | 普通 APP | 系统 APP（/system/app） | 特权 APP（/system/priv-app）|
|---|---|---|---|
| `INSTALL_PACKAGES`（静默安装） | ❌ | ❌ | ✅ |
| `DELETE_PACKAGES`（静默卸载） | ❌ | ❌ | ✅ |
| `REBOOT`（重启设备） | ❌ | ❌ | ✅ |
| `READ_PHONE_STATE`（读取 IMEI） | 需运行时授权 | 需运行时授权 | ✅ 自动授予 |
| `WRITE_SETTINGS`（修改系统设置） | ❌ | ❌ | ✅ |
| 开机自启 | ❌ 可被禁止 | ✅ | ✅ |
| 恢复出厂不丢失 | ❌ | ✅ | ✅ |

**结论：必须放 `/system/priv-app/`，同时必须提供权限白名单 XML。**

---

## 2. 需要提供给 ROM 技术的文件清单

| 编号 | 文件名 | 作用 |
|---|---|---|
| ① | `OmniControl.apk` | APP 安装包（release 签名版） |
| ② | `Android.mk` | 告诉编译系统如何打包该 APK |
| ③ | `privapp-permissions-OmniControl.xml` | 特权权限白名单（Android 8+ 必须） |

> ⚠️ **三个文件缺一不可。** 只放 APK 而不放白名单 XML，`INSTALL_PACKAGES` 等权限将全部被拒绝，APP 核心功能（静默安装、远程管理）无法正常工作。

---

## 3. 文件内容详解

### ① OmniControl.apk

直接提供 release 签名的 APK 文件即可。

> **注意**：APK 必须是 release 签名版（非 debug），否则部分厂商 ROM 会拒绝安装。

---

### ② Android.mk

```makefile
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# 模块名（编译产物名，不含 .apk）
LOCAL_MODULE := OmniControl

LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

# ★ 关键配置：特权 APP（放入 priv-app 目录）
LOCAL_PRIVILEGED_MODULE := true

# 使用 APK 自带签名（不用平台签名重签）
LOCAL_CERTIFICATE := PRESIGNED

# APK 源文件
LOCAL_SRC_FILES := OmniControl.apk

# 关闭 dex 预优化（避免编译期问题，运行时 JIT 即可）
LOCAL_DEX_PREOPT := false

# Android 10+ 支持多 ABI，如设备是 arm64 填 arm64-v8a
# 如不确定，留空让系统自动处理
# LOCAL_MULTILIB := both

include $(BUILD_PREBUILT)
```

> **Android.bp 版本**（Soong 构建系统，Android 10+ 新项目推荐）：
>
> ```bp
> android_app_import {
>     name: "OmniControl",
>     apk: "OmniControl.apk",
>     privileged: true,
>     certificate: "PRESIGNED",
>     dex_preopt: {
>         enabled: false,
>     },
>     presigned: true,
> }
> ```

---

### ③ privapp-permissions-OmniControl.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.omnicontrol.agent">
        <!-- APK 静默安装/卸载 -->
        <permission name="android.permission.INSTALL_PACKAGES"/>
        <permission name="android.permission.DELETE_PACKAGES"/>
        <!-- 读取设备标识（IMEI 等） -->
        <permission name="android.permission.READ_PHONE_STATE"/>
        <!-- 远程重启设备 -->
        <permission name="android.permission.REBOOT"/>
        <!-- 修改系统设置（屏幕常亮等） -->
        <permission name="android.permission.WRITE_SETTINGS"/>
        <!-- 控制锁屏 -->
        <permission name="android.permission.DISABLE_KEYGUARD"/>
        <!-- 忽略电池优化 -->
        <permission name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
        <!-- 前台服务类型（Android 14+） -->
        <permission name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
    </privapp-permissions>
</permissions>
```

---

## 4. 各 Android 版本注意事项

### Android 10（API 29）

- ✅ 支持 `Android.mk` 和 `Android.bp` 两种构建方式
- ✅ 外部存储访问：AndroidManifest 已加 `android:requestLegacyExternalStorage="true"`，无需额外处理
- ⚠️ 白名单 XML 放 `/system/etc/permissions/` 即可

---

### Android 11（API 30）

- ✅ 分区存储强制生效，但 priv-app 不受限
- ⚠️ **软件包可见性**：`QUERY_ALL_PACKAGES` 权限新增，如需枚举所有已安装 APP，需要在白名单 XML 中补充：
  ```xml
  <permission name="android.permission.QUERY_ALL_PACKAGES"/>
  ```
  > OmniControl 当前通过 `pm list packages` 枚举，已绕过此限制，暂不需要。

---

### Android 12（API 31/32）

- ✅ 无特殊变化，按标准流程处理
- ⚠️ `READ_EXTERNAL_STORAGE` 在 API 33 前仍有效，AndroidManifest 已加 `maxSdkVersion="32"` 限制，无需改动

---

### Android 13（API 33）

- ⚠️ `READ_EXTERNAL_STORAGE` 被细分权限替代，但系统 APP 走 `getExternalFilesDir()` 路径，不受影响
- ✅ `FOREGROUND_SERVICE_DATA_SYNC` 在此版本新增，已列入白名单 XML

---

### Android 14（API 34）

- ⚠️ **`FOREGROUND_SERVICE` 类型强制要求**：必须在 AndroidManifest 中声明 `foregroundServiceType`，已声明 `dataSync`，符合要求
- ⚠️ **`INSTALL_PACKAGES` 静默安装限制**：
  - Android 14 起，`pm install` 静默安装默认需要 `INSTALL_ALLOW_TEST` 或通过 `PackageInstaller` API
  - 但 **priv-app 持有 `INSTALL_PACKAGES` 权限时，`pm install -r` 仍然有效**，无需改动
- ⚠️ 编译时建议使用 `Android.bp`（Soong），`Android.mk` 在部分 AOSP 14 分支已被限制

---

### Android 15（API 35）

- ✅ priv-app 预置方式无变化
- ⚠️ **后台进程限制加强**：`MqttService` 以 Foreground Service 运行，已通过 `START_STICKY` + `ProcessGuard` watchdog 保活，无需改动
- ⚠️ 如厂商基于 AOSP 15 深度定制，部分 OEM 会对 `DISABLE_KEYGUARD` 做额外限制，建议编译后实机验证锁屏控制功能

---

### Android 16（API 36）

- ✅ 截至文档编写时（2026-04），priv-app 预置方式与 Android 15 相同，无 Breaking Change
- ⚠️ **健康检查建议**：Android 16 对电量优化和后台限制进一步收紧，建议编译完成后重点验证：
  1. 开机自启是否正常（`BootReceiver`）
  2. MQTT 长连接在息屏 30 分钟后是否存活
  3. 静默安装功能是否正常

---

## 5. 目录放置规范

### 方案 A：vendor 目录（推荐，隔离性好）

```
vendor/<品牌>/apps/OmniControl/
├── OmniControl.apk
└── Android.mk          ← 或 Android.bp

vendor/<品牌>/etc/permissions/
└── privapp-permissions-OmniControl.xml
```

在 `vendor/<品牌>/vendor.mk`（或设备对应的 device.mk）中添加：

```makefile
# OmniControl 设备管理 Agent
PRODUCT_PACKAGES += OmniControl
```

---

### 方案 B：device 目录（常见替代方案）

```
device/<品牌>/<型号>/prebuilt/OmniControl/
├── OmniControl.apk
└── Android.mk

device/<品牌>/<型号>/permissions/
└── privapp-permissions-OmniControl.xml
```

在 `device/<品牌>/<型号>/device.mk` 中添加：

```makefile
PRODUCT_PACKAGES += OmniControl

PRODUCT_COPY_FILES += \
    device/<品牌>/<型号>/permissions/privapp-permissions-OmniControl.xml:\
    $(TARGET_COPY_OUT_SYSTEM)/etc/permissions/privapp-permissions-OmniControl.xml
```

> **注意**：`Android.mk` / `Android.bp` 会自动处理 APK 的复制，但权限 XML **必须手动通过 `PRODUCT_COPY_FILES` 指定目标路径**，否则不会被打包进 ROM。

---

### 方案 C：直接修改 AOSP frameworks（不推荐，耦合度高）

```
frameworks/base/data/etc/
└── privapp-permissions-OmniControl.xml    ← 放这里也生效，但不建议
```

---

## 6. 编译验证方法

### 编译后检查 APK 是否打包进去

```bash
# 在编译输出目录检查
find out/target/product/<device>/system/priv-app/OmniControl/ -name "*.apk"
# 期望输出：out/target/product/<device>/system/priv-app/OmniControl/OmniControl.apk

# 检查权限白名单 XML
find out/target/product/<device>/system/etc/permissions/ -name "*OmniControl*"
# 期望输出：...privapp-permissions-OmniControl.xml
```

### 刷机后设备上验证

```bash
# 1. 确认 APP 是系统 APP
adb shell dumpsys package com.omnicontrol.agent | grep -E "flags|pkgFlags"
# 期望包含：SYSTEM

# 2. 确认特权权限已授予
adb shell dumpsys package com.omnicontrol.agent | grep -A1 "INSTALL_PACKAGES"
# 期望：granted=true

adb shell dumpsys package com.omnicontrol.agent | grep -A1 "READ_PHONE_STATE"
# 期望：granted=true

adb shell dumpsys package com.omnicontrol.agent | grep -A1 "REBOOT"
# 期望：granted=true

# 3. 检查 APP 是否在 priv-app 目录
adb shell ls /system/priv-app/OmniControl/
# 期望：OmniControl.apk

# 4. 检查白名单 XML 是否存在
adb shell ls /system/etc/permissions/privapp-permissions-OmniControl.xml
```

### 功能验证清单

- [ ] APP 开机自启（重启设备后 MQTT 自动连接）
- [ ] 设备在后台管理平台显示 ONLINE
- [ ] 下发 APK 更新指令，设备能静默安装
- [ ] 息屏 30 分钟后 MQTT 连接未断开
- [ ] 恢复出厂设置后 APP 仍存在且自动启动

---

## 7. 常见问题排查

### Q1：APP 装进去了，但 `INSTALL_PACKAGES` 显示 `granted=false`

**原因**：权限白名单 XML 没有打包进去，或放错了路径。

**排查**：
```bash
adb shell ls /system/etc/permissions/ | grep OmniControl
```
如果没有，说明 XML 未打包。检查 `PRODUCT_COPY_FILES` 配置。

---

### Q2：编译时报错 `Privileged permissions not in privapp-permissions`

**原因**：APP 在 priv-app 目录，但 AndroidManifest 中声明了某个特权权限，而白名单 XML 中没有列出。

**解决**：在 XML 中补充对应权限，或检查 logcat 中 `PrivappPermissions` tag 的具体提示：
```bash
adb logcat | grep PrivappPermissions
```

---

### Q3：Android 14+ 设备静默安装失败

**排查步骤**：
```bash
# 检查 pm install 返回码
adb shell pm install -r /sdcard/test.apk
# 如果提示 INSTALL_FAILED_VERIFICATION_FAILURE，需要关闭 Play Protect
# 如果提示 INSTALL_FAILED_USER_RESTRICTED，说明 INSTALL_PACKAGES 权限未授予
```

---

### Q4：开机后 APP 没有自动启动

**排查**：
```bash
adb logcat | grep -E "BootReceiver|OmniControl|MqttService"
```

常见原因：
1. `RECEIVE_BOOT_COMPLETED` 权限未授予（普通安装时未授权）→ priv-app 安装后自动授予，无需手动
2. 厂商 ROM 限制了第三方开机广播 → priv-app 级别通常可绕过此限制

---

### Q5：某些 MTK 机型 `/system` 挂载为只读无法自提权

这是自提权方案（非 ROM 预置方案）的限制。**使用 ROM 预置方案则完全不存在此问题**，APK 直接编译进分区，无需运行时 mount。

---

## 附录：完整文件下载

| 文件 | 说明 |
|---|---|
| `OmniControl-release.apk` | 由开发团队提供 release APK |
| `Android.mk` | 见本文第 3 节，直接复制使用 |
| `privapp-permissions-OmniControl.xml` | 见本文第 3 节，直接复制使用 |

> 如需更新 APK 版本，仅需替换 `OmniControl.apk` 文件后重新编译 ROM，`Android.mk` 和权限 XML 无需修改。
