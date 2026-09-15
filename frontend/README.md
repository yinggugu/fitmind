# FitMind 前端

FitMind 的 Vue 3 前端。当前可维护源码重点实现了独立的 **AI 健康助手** 页面，通过 Vite 开发代理调用 Spring Boot 的 `/api/assistant/*` 接口。

## 技术栈

- Vue 3
- Vite
- Vue Router
- Element Plus
- Apache ECharts（保留为项目依赖）
- pnpm

## 页面与功能

当前包含公共顶部导航、数据总览、体重记录、饮食记录、身体画像、健康信和 AI 健康助手。身体画像页面使用 GLB 模型进行 Three.js 展示，并通过 `/api/body-measurements` 读取和保存身体维度；模型是静态形象参考，不会随数值进行不可靠的局部拉伸。

## 启动后端

后端默认运行在 `127.0.0.1:8080`。在项目根目录执行：

```cmd
cd backend
mvn.cmd spring-boot:run
```

## 安装和启动前端

```cmd
cd frontend
pnpm.cmd install
pnpm.cmd run dev
```

访问地址：`http://127.0.0.1:5299/#/assistant`

开发环境中的 `/api` 会由 Vite 代理至 `http://127.0.0.1:8080`，组件中没有写死后端完整地址。

## 构建

```cmd
cd frontend
pnpm.cmd run build
```

## 当前交互

- 从 `GET /api/assistant/suggestions` 加载快捷问题
- 通过 `POST /api/assistant/chat` 发送自然语言问题
- 支持 Enter 发送、请求中防重复提交、自动滚动、网络失败友好提示
- `degraded=true` 时保留后端回答，并显示轻量降级说明
- 聊天记录只保存在当前页面内存，刷新后清空
