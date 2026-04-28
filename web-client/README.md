# web-client

独立前端模块（不和后端工程混放），用于调试 `SSE` 流式输出。

## 启动方式

1. 启动后端（默认 `http://localhost:10009`）
2. 进入本目录安装依赖：
   - `npm install`
3. 启动前端：
   - `npm run dev`
4. 打开浏览器：
   - `http://localhost:5173`

## 功能

- 连接 `/api/chat/deepThink`
- 监听 `answer` 事件（答案流）
- 监听 `workflow` 事件（节点状态）
- 显示连接日志，支持手动断开与清空
- 提供登录/注册页面：`/auth.html`（调用 `/api/auth/applyCode`、`/api/auth/register`、`/api/auth/login`）
