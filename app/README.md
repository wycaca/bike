# 安卓移动作业端

移动端由 Vue 3/Vant H5 和 Kotlin WebView 壳组成，共用桌面端后端会话、角色权限和运维任务状态机。

## 角色页面

- 管理人员：运营总览、车辆查询、任务派单、验收、异常闭环、自动规则和批量任务。
- 运维人员：任务池抢单、我的作业、路线优化、车辆查询、定位、扫码、照片凭证和异常上报。
- 路由守卫会拦截跨角色页面，最终接口权限仍由 Spring Security 校验。

## 本地运行

从仓库根目录执行：

```powershell
docker compose --env-file .env.local up -d --build mobile-web
```

浏览器访问 `http://localhost:8082`。安卓模拟器中的调试包会访问 `http://10.0.2.2:8082`。

Mock 账号：管理员 `admin`，北京运维人员 `operator.bj`，上海运维人员 `operator.sh`；密码使用 `.env.local` 中的 `APP_ADMIN_PASSWORD`。

## 测试与构建

前端镜像在构建阶段自动执行 6 个角色路由和点击流程测试：

```powershell
docker compose --env-file .env.local build mobile-web
```

Android 构建镜像使用 DockerProxy 的 Android SDK 和 DaoCloud 的 Gradle 基础镜像，并优先从阿里云 Maven 仓库下载依赖：

```powershell
docker build --target artifact --output type=local,dest=app/android/app/build/outputs/apk/debug app/android
```

该命令会运行 `testDebugUnitTest` 和 `assembleDebug`，并直接导出 `app/android/app/build/outputs/apk/debug/bike-operations-debug.apk`。构建产物不提交 Git。

发布包必须传入受控 HTTPS 地址：

```powershell
app\android\gradlew.bat -p app\android assembleRelease -PBIKE_WEB_APP_URL=https://ops.example.com
```

## 原生能力

- `BikeBridge.requestLocation`：定位授权、近期位置复用、单次定位与超时错误。
- `BikeBridge.scanVehicleCode`：二维码和条码扫描。
- WebChromeClient：相机拍照或相册选择，并交给 H5 上传作业凭证。
- 仅配置的同源页面可以调用 Bridge；调试包允许本地 HTTP，发布包禁止明文流量。
