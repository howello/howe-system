# 管理端前端镜像：构建静态资源并由 Nginx 托管
FROM node:22-alpine AS builder
WORKDIR /build

# 使用 npm：pnpm 新版默认拦截构建脚本，Docker 中易失败
COPY package.json package-lock.json ./
RUN npm install --no-audit --no-fund

COPY . .

# 站点标题：构建期变量，经 compose 的 build.args 从 .env 传入
ARG VITE_APP_TITLE=后台管理系统
ENV VITE_APP_TITLE=${VITE_APP_TITLE}

# 生产接口走同源 /api，由 Nginx 反代到后端容器。
# 此值与 docker/default.conf.template 的 location /api/ 硬耦合，故不做成可配开关
ENV VITE_APP_BASE_API=/api

RUN npm run build:prod

FROM nginx:1.27-alpine
WORKDIR /app

# 放入 templates 目录，nginx 官方 entrypoint 启动时用 envsubst 渲染到 conf.d
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=builder /build/dist /app/dist

EXPOSE 9528
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:${WEB_PORT}/ || exit 1
