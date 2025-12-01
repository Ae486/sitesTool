# 导航签到平台（本地版）

一个本地优先的自动化签到平台，聚合网址导航与自动化任务执行。基于 FastAPI + React + Playwright 构建，支持可视化 DSL 编辑、多浏览器执行、详细错误诊断和并发任务管理。

## ✨ 核心特性

- 🌐 **站点管理**：组织和分类网站，支持标签系统
- 🤖 **自动化流程**：基于 JSON DSL 的可视化自动化编辑器
- 🚀 **多浏览器支持**：Chromium、Chrome、Edge、Firefox 及自定义浏览器
- 🔄 **并发执行**：多流程独立进程执行，支持实时中断
- 📊 **执行历史**：详细的步骤级执行记录和错误诊断
- 🎯 **智能错误诊断**：自动截图、错误分类、修复建议
- 🔐 **JWT 认证**：基于 OAuth2 的安全认证系统
- 📅 **定时调度**：APScheduler 支持（开发中）

## 📁 目录结构

```
/
├── backend/          # FastAPI 后端服务
│   ├── app/
│   │   ├── api/      # API 路由
│   │   ├── core/     # 核心配置
│   │   ├── crud/     # 数据库操作
│   │   ├── models/   # 数据模型
│   │   ├── schemas/  # Pydantic 模式
│   │   └── services/ # 业务逻辑和自动化引擎
│   ├── data/         # SQLite 数据库和截图
│   └── test_pages/   # 自动化测试页面
├── frontend/         # React + Vite 前端
│   └── src/
│       ├── api/      # API 客户端
│       ├── components/ # React 组件
│       ├── pages/    # 页面组件
│       └── store/    # 状态管理
└── docs/             # 文档和开发计划
```

## 🚀 快速开始

### 前置要求
- Python 3.11+
- Node.js 20+
- Poetry（Python 依赖管理）
- pnpm（Node.js 包管理器）

### 1. 后端设置

```bash
# 进入后端目录
cd backend

# 安装依赖
poetry install

# 安装 Playwright 浏览器（可选，用于自动化）
poetry run playwright install

# 复制环境变量配置
cp .env.example .env

# 启动后端服务
poetry run uvicorn app.main:app --reload
```

后端服务将在 `http://127.0.0.1:8000` 启动。

### 2. 前端设置

#### 方式 A：开发模式（推荐用于开发）

```bash
# 进入前端目录
cd frontend

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev
```

前端将在 `http://localhost:5173` 启动。

#### 方式 B：生产构建（后端托管）

```bash
# 在前端目录
cd frontend

# 构建生产版本
pnpm build
```

构建完成后，访问 `http://127.0.0.1:8000` 即可使用（后端会自动托管前端）。

### 3. 初始化管理员账户

首次启动后，访问后端创建管理员：

```bash
curl -X POST http://127.0.0.1:8000/api/auth/bootstrap \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@example.com",
    "password": "admin123",
    "full_name": "Admin User"
  }'
```

或直接在前端登录页面使用默认账户：
- 用户名：`admin`
- 密码：`admin`

### 4. 开始使用

1. 登录系统
2. 进入"站点管理"创建站点
3. 进入"自动化流程"创建流程
4. 编写 DSL 并执行
5. 在"执行历史"查看结果

## 💻 开发指南

### 后端开发

```bash
# 代码格式化
poetry run black .

# 代码检查
poetry run ruff check .

# 运行测试
poetry run pytest
```

### 前端开发

```bash
# 代码检查
pnpm lint

# 代码格式化
pnpm format

# 类型检查
pnpm type-check

# 构建
pnpm build
```

## 🎯 已实现功能

### 站点管理
- ✅ 站点 CRUD 操作
- ✅ 标签系统和分类
- ✅ 站点列表和详情展示

### 自动化流程
- ✅ DSL JSON 编辑器
- ✅ 多浏览器支持（Chromium/Chrome/Edge/Firefox/自定义）
- ✅ 无头/有头模式切换
- ✅ 自定义浏览器路径
- ✅ 手动触发执行
- ✅ 实时执行状态显示
- ✅ 并发执行管理
- ✅ 执行中断功能

### DSL 自动化引擎
- ✅ 10+ 操作类型支持
  - 导航：`navigate`, `scroll`
  - 交互：`click`, `input`, `select`, `checkbox`
  - 等待：`wait_for`, `wait_time`
  - 数据：`extract`, `screenshot`
- ✅ 变量系统（提取和使用）
- ✅ 步骤级错误处理
- ✅ 详细执行日志

### 执行历史与诊断
- ✅ 完整执行历史记录
- ✅ 步骤级执行详情（时间轴展示）
- ✅ 自动错误截图
- ✅ 错误类型分类（TIMEOUT/ELEMENT_NOT_FOUND/等）
- ✅ 智能错误提示和修复建议
- ✅ 详细错误上下文（URL/选择器/元素状态）

### 用户界面
- ✅ 响应式布局
- ✅ 仪表盘统计
- ✅ 流程编辑器
- ✅ 执行历史查看器
- ✅ JWT 认证和路由守卫

## 🚧 开发中功能

- ⏳ Cookie 管理和持久化
- ⏳ 定时任务调度（APScheduler 集成）
- ⏳ 站点批量导入/导出
- ⏳ 更多 DSL 操作类型（文件上传、iframe 处理等）
- ⏳ 执行结果通知（邮件/Webhook）
- ⏳ 性能优化和资源管理

## 🔧 技术栈

### 后端
- **框架**: FastAPI (Python 3.11+)
- **数据库**: SQLite + SQLModel/SQLAlchemy
- **认证**: JWT + passlib/bcrypt
- **自动化**: Playwright
- **调度**: APScheduler
- **依赖管理**: Poetry

### 前端
- **框架**: React 18 + TypeScript
- **构建工具**: Vite
- **UI 库**: Ant Design 5
- **状态管理**: Zustand + TanStack Query
- **路由**: React Router v6
- **HTTP 客户端**: Axios
- **包管理**: pnpm

## 📚 文档

- [DSL 示例](docs/dsl-examples.md) - 自动化 DSL 语法和示例
- [测试指南](docs/testing-guide.md) - 如何测试自动化流程
- [错误诊断](docs/feature-error-display.md) - 错误信息展示说明
- [实现路线图](docs/implementation-roadmap.md) - 功能开发计划

## 🧪 测试

### 测试页面
访问 `http://127.0.0.1:8000/test/` 可以看到内置的测试页面，包含：
- 登录表单
- 签到按钮
- 动态内容
- 各种表单元素

### 示例 DSL
```json
{
  "version": 1,
  "steps": [
    {
      "type": "navigate",
      "url": "http://127.0.0.1:8000/test/",
      "description": "打开测试页面"
    },
    {
      "type": "input",
      "selector": "#username",
      "value": "testuser",
      "description": "输入用户名"
    },
    {
      "type": "input",
      "selector": "#password",
      "value": "password123",
      "description": "输入密码"
    },
    {
      "type": "click",
      "selector": "#login-btn",
      "description": "点击登录按钮"
    },
    {
      "type": "wait_for",
      "selector": "#checkin-btn",
      "description": "等待签到按钮出现"
    },
    {
      "type": "click",
      "selector": "#checkin-btn",
      "description": "点击签到"
    }
  ]
}
```

## 📖 API 参考

### 认证
- `POST /api/auth/bootstrap` - 创建首个管理员（仅当用户表为空）
- `POST /api/auth/token` - 登录获取 JWT Token
- `GET /api/auth/me` - 获取当前用户信息

### 站点管理
- `GET /api/sites` - 获取站点列表
- `POST /api/sites` - 创建站点
- `GET /api/sites/{id}` - 获取站点详情
- `PUT /api/sites/{id}` - 更新站点
- `DELETE /api/sites/{id}` - 删除站点

### 自动化流程
- `GET /api/flows` - 获取流程列表
- `POST /api/flows` - 创建流程
- `GET /api/flows/{id}` - 获取流程详情
- `PUT /api/flows/{id}` - 更新流程
- `DELETE /api/flows/{id}` - 删除流程
- `POST /api/flows/{id}/trigger` - 手动触发执行
- `POST /api/flows/{id}/stop` - 停止执行

### 执行历史
- `GET /api/history` - 获取执行历史列表
- `GET /api/history/{id}` - 获取历史详情
- `DELETE /api/history/{id}` - 删除历史记录

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

## 🙏 致谢

- [FastAPI](https://fastapi.tiangolo.com/)
- [React](https://react.dev/)
- [Playwright](https://playwright.dev/)
- [Ant Design](https://ant.design/)
