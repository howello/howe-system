# howe-system

`howe-system` 是一个面向个人与家庭的综合后台平台，由 **Spring Boot 后端**与 **Vue 3 管理端**组成。在 RuoYi 管理框架基础上二次开发，除系统管理能力外，还提供博客内容管理、AI 调用网关、家庭点餐与 WebDAV 文件分享等业务模块。

项目同时服务三个前端：

- `admin-ui`：平台管理端
- `blog-ui`：静态博客站点
- `meal-ui`：家庭点餐 App / H5

## 功能

### 平台基础能力

- 用户、角色、菜单、部门、岗位、字典、参数、公告与操作日志
- JWT 无状态鉴权、按角色 / 权限点控制、部门数据权限
- 图形验证码与 Cloudflare Turnstile 人机校验
- 动态菜单路由、公告已读、锁屏、密码强度策略
- 首页卡片式工作台：按模块与权限动态下发卡片
- 基于 MySQL + Redis 的会话、缓存与限流
- 对象存储：本地磁盘或 Cloudflare R2

### 博客管理

- 文章索引、草稿管理与 GitHub 仓库同步
- GitHub webhook 自动同步文章
- 友链、RSS 朋友圈、说说、公告管理
- 面向 `blog-ui` 的匿名只读接口，以及带令牌的开放发布接口

### AI 调用网关

- 统一 `AiClient` 门面，屏蔽厂商 SDK 差异
- 文本对话、流式输出、结构化输出、文生图、图片理解与向量
- 多渠道、多模型、场景路由与有序降级
- 密钥加密存储、调用重试、用量与调用记录
- 生成图片自动转存对象存储，避免临时链接失效

### 家庭点餐

- 菜品分类、菜品、订单、评价与新菜提案
- 家庭下单、厨师接单与制作进度、备菜清单、订单通知
- 家庭邀请码注册、家庭成员与角色管理
- 公共菜谱与家庭私有菜谱隔离
- App wgt 版本管理、热更新发布与企业微信发版通知

### 文件分享

- 配置 WebDAV 源，浏览文件并生成短链
- 支持访问密码、过期时间、次数限制与阅后即焚
- 文件异步预缓存，按类型与大小分流到本地磁盘或 R2
- 图片、PDF、Markdown、文本与视频在线预览

## 技术栈

### 后端

| 分类 | 技术 |
|---|---|
| 运行时 | Java 17 |
| 框架 | Spring Boot 3.5 |
| 安全 | Spring Security + JWT |
| 数据访问 | MyBatis + MyBatis-Plus + PageHelper |
| 数据库 | MySQL |
| 缓存 | Redis |
| 连接池 | Druid |
| 接口文档 | Springdoc OpenAPI |
| 对象存储 | Cloudflare R2（S3 兼容）、本地存储 |
| AI | Spring AI + Spring AI Alibaba |
| 工具库 | Hutool、Lombok |
| 构建 | Maven 多模块 |

### 管理端

| 分类 | 技术 |
|---|---|
| 框架 | Vue 3.5 + TypeScript |
| 构建工具 | Vite 6 |
| UI | Element Plus |
| 状态管理 | Pinia 3 |
| 路由 | Vue Router 4 |
| 图表 | ECharts |
| 编辑器 | Quill、md-editor-v3 |

## 仓库结构

```text
howe-system/
├─ server/                 # Spring Boot 多模块后端
│  ├─ module-common/       # 公共工具、基类、存储与配置抽象
│  ├─ module-system/       # 系统管理业务域
│  ├─ module-framework/    # 安全、JWT、切面、验证码等框架能力
│  ├─ module-admin/        # 启动模块与既有模块的 Controller
│  ├─ module-blog/         # 博客内容管理
│  ├─ module-ai/           # AI 调用网关
│  ├─ module-meal/         # 家庭点餐
│  ├─ module-share/        # WebDAV 文件分享
│  ├─ sql/                 # 数据库初始化与模块脚本
│  └─ config/              # 外部配置覆盖示例
├─ admin-ui/               # Vue 管理端
├─ .github/workflows/      # 后端与管理端镜像构建
├─ docker-compose.yml      # 本地构建部署
└─ docker-compose.prod.yml # 拉取 CI 镜像部署
```

## 快速开始

### 环境要求

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8+
- Redis
- 可选：Docker / Docker Compose

### 1. 初始化数据库

创建数据库 `howe-system`（`utf8mb4`），然后按需执行脚本：

```bash
mysql -u root -p howe-system < server/sql/base.sql
mysql -u root -p howe-system < server/sql/blog.sql
mysql -u root -p howe-system < server/sql/ai.sql
mysql -u root -p howe-system < server/sql/meal.sql
mysql -u root -p howe-system < server/sql/share.sql
```

`base.sql` 必须先执行；其余模块脚本按实际启用的功能导入。脚本会创建对应菜单、权限与参数配置。

### 2. 配置环境变量

复制 `.env.example` 为 `.env`，至少填写：

- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`
- `JWT_SECRET`
- `HOWE_SECRET_KEY`（Base64 编码的 16 / 24 / 32 字节密钥）

`HOWE_SECRET_KEY` 用于加密 AI 密钥、WebDAV 密码以及参数表中的敏感项。生成示例：

```bash
openssl rand -base64 32
```

更换该密钥后，已有密文将无法解密，需要重新保存相关密钥。

### 3. 启动后端

```bash
cd server
mvn spring-boot:run -pl module-admin
```

后端默认监听 `http://localhost:9527`。本地直接在 `server/` 目录运行时，需要先设置数据库、Redis 等环境变量；具体变量见仓库根目录的 `.env.example`。

构建可执行 JAR：

```bash
mvn clean package -DskipTests
```

产物位于 `server/module-admin/target/module-admin.jar`。

### 4. 启动管理端

```bash
cd admin-ui
npm install
npm run dev
```

访问 `http://localhost:9528`。开发服务器会把 `/dev-api` 代理到 `http://localhost:9527`。

生产构建：

```bash
npm run build:prod
```

## Docker 部署

### 本机构建

```bash
cp .env.example .env
docker compose up -d --build
```

### 拉取 CI 镜像

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

后端与管理端默认端口分别为 `9527`、`9528`，二者默认只绑定到 `127.0.0.1`，由上层反向代理对外提供 HTTPS。

> MySQL 与 Redis 是外部依赖，compose 不内置。Linux 服务器上不能使用默认的 `host.docker.internal`，需要改为宿主机可达地址。

## 发布相关

- 后端 `.github/workflows/build-app.yml` 构建 `kilimaca/howe-system-app` 镜像
- 管理端 `.github/workflows/build-web.yml` 构建 `kilimaca/howe-system-web` 镜像
- 每次构建推送 `latest` 与 `main-<短SHA>` 标签；生产环境可用具体标签回滚
- `VITE_*` 变量在管理端构建时写入静态产物，修改后必须重新构建镜像

## 常用命令

```bash
# 后端依赖与构建
cd server && mvn clean package -DskipTests

# 管理端类型检查
cd admin-ui && npx vue-tsc --noEmit

# 管理端生产构建
cd admin-ui && npm run build:prod
```

## 文档

- `../CLAUDE.md`：工作区总览、技术约束与协作约定
- `../.agents/skills/system-details/`：后端模块、数据库、部署、AI、博客、点餐等落地细节
- `../docs/`：历史设计与实施记录；与代码冲突时以代码和 skill 文档为准

## License

管理端代码基于 RuoYi，遵循 MIT License。新增业务模块的许可证状态请按实际发布需求确认。
