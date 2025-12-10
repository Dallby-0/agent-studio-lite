# 本地启动后端连接Docker中MySQL的配置方法

## Docker中MySQL的配置信息

从`docker-compose.yml`和`docker ps`命令结果，可以看到MySQL服务的配置如下：

### MySQL服务基本信息
- **容器名称**: agent-workflow-mysql-test
- **主机端口映射**: 0.0.0.0:3307->3306/tcp
- **网络**: agent-network-test

### MySQL认证信息
- **数据库名称**: agent_workflow
- **用户名**: workflow_user
- **密码**: workflow_pass_123
- **root密码**: root123456

## 本地后端连接配置

### 1. 使用环境变量配置（推荐）

在本地启动后端应用时，需要设置以下环境变量，覆盖默认配置：

```bash
# Windows CMD
set MYSQL_HOST=localhost
set MYSQL_PORT=3307
set MYSQL_DATABASE=agent_workflow
set MYSQL_USER=workflow_user
set MYSQL_PASSWORD=workflow_pass_123

# Windows PowerShell
$env:MYSQL_HOST="localhost"
$env:MYSQL_PORT="3307"
$env:MYSQL_DATABASE="agent_workflow"
$env:MYSQL_USER="workflow_user"
$env:MYSQL_PASSWORD="workflow_pass_123"

# Linux/Mac
MYSQL_HOST=localhost MYSQL_PORT=3307 MYSQL_DATABASE=agent_workflow MYSQL_USER=workflow_user MYSQL_PASSWORD=workflow_pass_123 java -jar backend.jar
```

### 2. 直接修改application.yml（不推荐，仅用于测试）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/agent_workflow?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true
    username: workflow_user
    password: workflow_pass_123
```

## 连接验证

### 使用MySQL客户端测试连接

```bash
# 使用docker exec进入容器内部测试
mysql -h localhost -P 3307 -u workflow_user -p agent_workflow

# 输入密码: workflow_pass_123
```

### 使用phpMyAdmin测试连接

通过浏览器访问：http://localhost:8083
- **服务器**: mysql-test
- **用户名**: workflow_user
- **密码**: workflow_pass_123
- **数据库**: agent_workflow

## 连接原理说明

1. **端口映射**: Docker将容器内部的3306端口映射到主机的3307端口，因此本地应用通过localhost:3307访问Docker中的MySQL
2. **网络隔离**: 虽然MySQL容器运行在独立的Docker网络中，但通过端口映射，本地应用可以直接访问
3. **环境变量覆盖**: Spring Boot应用会优先使用环境变量的值，覆盖application.yml中的默认配置
4. **认证信息**: 必须使用docker-compose.yml中配置的用户名和密码，否则无法认证成功

## 常见问题排查

### 1. 连接超时
- 检查MySQL容器是否正在运行：`docker ps | grep mysql`
- 检查端口映射是否正确：`docker port agent-workflow-mysql-test 3306`

### 2. 认证失败
- 检查用户名和密码是否与docker-compose.yml中的配置一致
- 检查数据库名称是否正确
- 检查MySQL是否使用了正确的认证插件：docker-compose.yml中已配置`--default-authentication-plugin=mysql_native_password`

### 3. 无法访问
- 检查防火墙是否允许3307端口访问
- 检查Docker是否运行正常：`docker info`

## 最佳实践

1. 始终使用环境变量配置数据库连接，避免硬编码
2. 为不同环境（开发、测试、生产）使用不同的环境变量文件
3. 定期更新数据库密码，避免使用弱密码
4. 限制数据库用户的权限，遵循最小权限原则
5. 本地开发时，使用docker-compose启动所有依赖服务，确保环境一致性

## 其他数据库连接配置

### Redis连接
- **主机**: localhost
- **端口**: 6380
- **密码**: workflow_redis123456

### PostgreSQL向量数据库连接
- **主机**: localhost
- **端口**: 5433
- **数据库**: vector_db
- **用户名**: vector_user
- **密码**: vector_pass_123

## 总结

本地后端应用连接Docker中的MySQL主要通过端口映射和环境变量配置实现。确保使用正确的主机地址、端口、用户名、密码和数据库名称，即可成功建立连接。