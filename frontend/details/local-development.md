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

当前测试覆盖轨迹球面距离和组织层级路径. 生产构建执行 TypeScript 严格检查. Element Plus 当前按完整组件库引入, 首期管理端可以接受; 当网络加载时间成为实际问题时再改为按需引入.

## Docker Compose

复制项目根目录的 `.env.example` 为 `.env.local`, 填写 `VITE_AMAP_KEY` 和配套的
`AMAP_SECURITY_JSCODE`, 然后在项目根目录执行:

```powershell
docker compose --env-file .env.local up -d --build frontend
docker compose ps
```

前端容器使用 Nginx 提供静态文件, 并把 `/api` 和 `/actuator` 同源代理到 `backend:8080`. 前端容器不保存业务状态.
`VITE_AMAP_KEY` 会在 Vite 构建阶段写入静态资源, 更换 Key 后必须带 `--build` 重新构建前端镜像;
未配置 Key 时 Dockerfile 会直接终止构建, 避免生成只能显示空地图的镜像.

## 高德地图配置

直接运行 `npm run dev` 时, 复制 `frontend/.env.example` 中的变量到本机
`frontend/.env.local` 后填写开发 Key. 使用 Docker Compose 时改为填写项目根目录的
`.env.local`. 根目录 `.gitignore` 已忽略这两个文件, 不得强制加入 Git.

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
- 车辆分页返回 200 辆车, 北京和上海地图分别返回 100 个 GCJ-02 点位.
- 车辆详情和历史轨迹均通过 Nginx 同源代理返回成功.
- 地图城市切换仍会重置中心, 普通拖动和缩放不会被响应式状态拉回城市中心.
- 固定 Mock 数据共 10,640 个高德道路轨迹点, 每辆车 25 至 60 点.
- 真实高德地图组件、中文车辆信息和轨迹折线已完成浏览器渲染验证.
- 登录页, Redis 会话恢复, 登录后 CSRF 令牌轮换和退出入口已完成浏览器验证.
- 运营看板, 空间设施地图, 围栏编辑抽屉, 组织用户表和审计日志均完成桌面视口验证.
- 前端 Docker 构建当前通过 2 个测试文件, 3 个测试用例和 TypeScript 严格检查.

内置浏览器自动化进程受 Windows 沙箱错误 `1385` 阻断时, 使用本机 Edge 无头模式完成页面渲染检查.
