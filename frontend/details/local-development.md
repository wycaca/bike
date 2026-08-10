# 桌面前端本地开发

## 环境

- Node.js 22.
- npm 使用项目内 `.npmrc` 配置的国内镜像.
- 后端默认地址为 `http://localhost:8080`.
- Docker Compose 前端地址为 `http://localhost:8081`.

## 开发启动

```powershell
cd frontend
npm install
npm run dev
```

Vite 默认监听 `http://localhost:5173`, 并将 `/api` 和 `/actuator` 转发到后端. 需要修改后端地址时设置 `VITE_API_PROXY_TARGET`.

## 构建和测试

```powershell
cd frontend
npm test
npm run build
```

当前最小测试覆盖轨迹球面距离计算. 生产构建执行 TypeScript 严格检查. Element Plus 当前按完整组件库引入, 首期管理端可以接受; 当网络加载时间成为实际问题时再改为按需引入.

## Docker Compose

在项目根目录执行:

```powershell
docker compose up -d --build frontend
docker compose ps
```

前端容器使用 Nginx 提供静态文件, 并把 `/api` 和 `/actuator` 同源代理到 `backend:8080`. 前端容器不保存业务状态.

## 高德地图配置

复制 `.env.example` 中的变量到本机 `frontend/.env.local` 后填写开发 Key. 根目录 `.gitignore` 已忽略该文件, 不得强制加入 Git.

- `VITE_AMAP_KEY`: 高德 Web Key.
- `VITE_AMAP_SERVICE_HOST`: 生产安全代理地址, 本地开发可以留空.

根目录 Git 忽略的 `.env.local` 可以保存 `AMAP_WEB_SERVICE_KEY`, 仅供本地生成道路级 Mock 轨迹. 该 Key 不属于前端配置, 不得写入 `VITE_` 变量, 浏览器构建产物或生成的 JSON. Web JS Key 和 Web Service Key 按用途隔离, 不得互换.

地图和轨迹接口当前请求 `GCJ02`, 后端统一转换坐标. 前端只有在接口返回 `GCJ02` 且存在 `VITE_AMAP_KEY` 时加载高德地图; 否则显示带坐标系标识的坐标预览.

2021-12-02 之后创建的高德 Key 还需要安全密钥. 前端不得配置明文 `securityJsCode`; 生产环境必须设置 `VITE_AMAP_SERVICE_HOST`, 由同源服务代理安全鉴权. 本地 Docker 构建会把 Web Key 写入浏览器静态资源, 该镜像只允许本机测试, 不得推送到公共镜像仓库.

## 已验证

2026-08-10 已完成:

- `npm test` 通过, 1 个测试文件和 1 个测试用例.
- `npm run build` 通过.
- 前端 Docker 镜像通过国内镜像站构建成功.
- `http://localhost:8081/actuator/health` 返回 `UP`.
- 车辆分页返回 20 辆车, 北京和上海地图分别返回 10 个 GCJ-02 点位.
- 车辆详情和历史轨迹均通过 Nginx 同源代理返回成功.
- 地图城市切换仍会重置中心, 普通拖动和缩放不会被响应式状态拉回城市中心.
- 北京道路级 Mock 轨迹返回 56 点, 上海返回 61 点, 最长采样间隔为 10 秒.
- 真实高德地图组件和轨迹折线已完成生产构建, 浏览器渲染仍待连接恢复后确认.

当前环境的内置浏览器自动化进程受 Windows 沙箱错误 `1385` 阻断, 尚未完成页面截图和点击流程验证. 恢复浏览器连接后需要补测地图点选, 详情抽屉, 城市切换和轨迹播放.
