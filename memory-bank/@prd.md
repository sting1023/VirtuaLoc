# VirtuaLoc - 虚拟定位应用 PRD

## 1. 项目概述

- **项目名称**: VirtuaLoc
- **包名**: `com.sting.virtualloc`
- **一句话描述**: 通过 Android 开发者模式的 Mock Location API 实现虚拟定位，无需 Root 权限。
- **目标用户**: 需要在特定位置测试应用、玩基于位置的游戏、或保护隐私的用户。
- **核心价值**: 无需 Root，直接利用 Android 原生 Mock Location 功能，安全可靠。

## 2. 功能需求

### MVP 功能
1. **坐标输入**: 用户输入经纬度（支持小数格式）
2. **开启虚拟定位**: 启动前台服务，持续上报虚拟位置
3. **关闭虚拟定位**: 停止前台服务，恢复真实定位
4. **地图预览**: 用 osmdroid 显示当前虚拟位置（离线可用）
5. **开发者模式引导**: 内置引导说明如何开启「允许模拟位置」

### 交互设计
- 输入框：纬度 (-90 ~ 90)、经度 (-180 ~ 180)
- 按钮：开启 / 关闭
- 状态显示：当前是否在虚拟定位模式
- 地图：实时显示虚拟位置标记

## 3. 技术方案

| 组件 | 技术选型 |
|------|----------|
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 地图 | osmdroid 6.1.18（无需 API Key，离线可用）|
| 定位 API | LocationManager.addTestProvider（开发者模式）|
| 后台运行 | Foreground Service + Notification |
| Min SDK | 26 |
| Target SDK | 34 |
| Gradle | 8.4 |
| AGP | 8.2.2 |
| Java | 17 |

## 4. 实现要点

### Mock Location 原理
1. 用户需开启「开发者选项 → 开启 USB 调试 + 允许模拟位置」
2. 应用调用 `LocationManager.addTestProvider()` 注册测试 Provider
3. 通过 `LocationManager.setTestProviderLocation()` 持续更新虚拟位置
4. 其他 App 读取位置时，Mock Location 会覆盖真实 GPS

### 前台服务设计
- Notification Channel: `virtualloc_channel`
- Notification ID: `1`
- 服务持续运行，直到用户点击「停止」

### 权限
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`（Android 14+）
- `POST_NOTIFICATIONS`（Android 13+）
- `INTERNET`（地图瓦片下载）

## 5. 文件结构

```
app/src/main/
├── java/com/sting/virtualloc/
│   ├── MainActivity.kt          # Compose UI + 状态管理
│   ├── LocationService.kt       # Foreground Service
│   └── MockLocationManager.kt   # addTestProvider 封装
└── res/
    └── values/strings.xml
```
