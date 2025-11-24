# Agent Studio Lite

一个轻量级的Agent工作流管理系统，支持任务配置、执行和监控。

## 技术栈

- **后端**: Java
- **前端**: Vue.js
- **数据库**: MySQL
- **缓存**: Redis
- **容器化**: Docker

## 项目结构

```
├── Back-end/       # Java后端代码
├── Front-end/      # Vue.js前端代码
├── Database/       # 数据库初始化脚本
```

## 快速开始

### 前置条件

- Linux Ubuntu 22.04
- Docker 环境
- Docker Compose

- 测试环境完成安装后，git clone本项目
- 修改后端CORS
- 输入：docker compose -f docker-compose.yml up -d 即可自动一键容器化部署上线整个项目


## 服务访问

测试环境:
- 前端: http://localhost:81
- 后端API: http://localhost:8082
- phpMyAdmin: http://localhost:8083


生产环境:（暂不可用，Nginx和Dockerfile文件未和测试环境统一）
- 前端: http://localhost
- 后端API: http://localhost:8082

