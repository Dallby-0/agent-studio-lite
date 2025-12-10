# Windows环境下后端连接Docker中MySQL的解决方案

## 问题分析

根据错误信息 `Communications link failure` 和 `Connection refused`，可以确定后端应用无法连接到MySQL数据库。这是因为：

1. **后端应用在Windows环境下直接启动**，而不是通过Docker启动
2. **MySQL数据库运行在Docker容器中**，通过端口映射暴露服务
3. **当前配置使用了Docker内部网络的服务名** `mysql-test:3306`，但Windows环境无法直接解析这个服务名

## 解决方案

### 方法1：使用环境变量配置（推荐）

在Windows环境下启动后端应用前，设置以下环境变量，覆盖默认配置：

#### 使用Windows CMD
```cmd
set MYSQL_HOST=localhost
set MYSQL_PORT=3307
set MYSQL_DATABASE=agent_workflow
set MYSQL_USER=workflow_user
set MYSQL_PASSWORD=workflow_pass_123
```

#### 使用Windows PowerShell
```powershell
$env:MYSQL_HOST="localhost"
$env:MYSQL_PORT="3307"
$env:MYSQL_DATABASE="agent_workflow"
$env:MYSQL_USER="workflow_user"
$env:MYSQL_PASSWORD="workflow_pass_123"
```

### 方法2：修改application.yml文件

直接修改 `Back-end/src/main/resources/application.yml` 文件，将MySQL配置改为：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/agent_workflow?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true
    username: workflow_user
    password: workflow_pass_123
```

### 方法3：使用IDE的运行配置

如果使用IDEA或Eclipse等IDE启动应用，可以在运行配置中添加环境变量：

1. 打开运行配置
2. 找到环境变量设置
3. 添加以下环境变量：
   - `MYSQL_HOST=localhost`
   - `MYSQL_PORT=3307`
   - `MYSQL_DATABASE=agent_workflow`
   - `MYSQL_USER=workflow_user`
   - `MYSQL_PASSWORD=workflow_pass_123`

## 验证连接

在应用启动前，可以使用以下命令验证MySQL容器是否可以从Windows访问：

### 方法1：使用MySQL客户端（如果已安装）
```cmd
mysql -h localhost -P 3307 -u workflow_user -p agent_workflow
```

### 方法2：使用telnet（如果已启用）
```cmd
telnet localhost 3307
```

### 方法3：使用PowerShell测试端口
```powershell
Test-NetConnection -ComputerName localhost -Port 3307
```

## 其他数据库配置

如果需要连接其他Docker中的数据库，也需要类似的配置：

### Redis连接
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6380
      password: workflow_redis123456
```

### 向量数据库连接
```yaml
vector-db:
  url: jdbc:postgresql://localhost:5433/vector_db
  username: vector_user
  password: vector_pass_123
```

## 启动应用

完成配置后，使用以下命令启动后端应用：

```cmd
cd Back-end
mvn spring-boot:run
```

或者使用IDE的运行按钮启动应用。

## 常见问题排查

### 1. 端口映射错误
- 检查Docker容器的端口映射：`docker port agent-workflow-mysql-test 3306`
- 确保映射的主机端口是3307

### 2. 防火墙问题
- 检查Windows防火墙是否允许3307端口的入站连接
- 可以临时关闭防火墙测试

### 3. 容器未运行
- 检查容器状态：`docker ps | grep mysql`
- 如果容器未运行，启动容器：`docker start agent-workflow-mysql-test`

### 4. 数据库用户权限问题
- 验证数据库用户权限：
  ```sql
  docker exec -it agent-workflow-mysql-test mysql -u root -proot123456 -e "GRANT ALL PRIVILEGES ON agent_workflow.* TO 'workflow_user'@'%' IDENTIFIED BY 'workflow_pass_123'; FLUSH PRIVILEGES;"
  ```

## 总结

在Windows环境下连接Docker中的MySQL数据库，关键是要使用正确的主机地址（localhost）和映射的端口（3307），而不是Docker内部的服务名和端口。通过设置环境变量或修改配置文件，可以轻松实现连接。