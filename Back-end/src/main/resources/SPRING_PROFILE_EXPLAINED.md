# Spring Boot Profile 机制原理解释

## 1. 配置文件命名规则

Spring Boot 支持多环境配置文件，命名规则为：

```
application-{profile}.yml
或
application-{profile}.properties
```

在我们的项目中：
- `application.yml` - 默认配置文件（总是加载）
- `application-container.yml` - container profile 的配置文件

## 2. Profile 的激活方式

可以通过以下方式激活 Profile：

### 方式1：环境变量（推荐用于 Docker）
```bash
SPRING_PROFILES_ACTIVE=container
```

### 方式2：JVM 参数
```bash
java -jar app.jar --spring.profiles.active=container
```

### 方式3：application.yml 中配置
```yaml
spring:
  profiles:
    active: container
```

### 方式4：在 docker-compose.yml 中设置
```yaml
environment:
  - SPRING_PROFILES_ACTIVE=container
```

## 3. 配置文件加载顺序和优先级

Spring Boot 加载配置文件的顺序（**后面的会覆盖前面的**）：

1. **application.yml** （基础配置，总是加载）
2. **application-{profile}.yml** （当 profile 激活时加载）
3. **环境变量** （最高优先级，会覆盖配置文件）

### 示例流程

当设置 `SPRING_PROFILES_ACTIVE=container` 时：

```
步骤1: 加载 application.yml
  ├─ vector-db.url = jdbc:postgresql://localhost:5432/vector_db
  
步骤2: 加载 application-container.yml（因为 profile=container）
  └─ vector-db.url = jdbc:postgresql://pgvector-test:5432/vector_db  ← 覆盖步骤1

步骤3: 加载环境变量（如果存在）
  └─ VECTOR_DB_HOST=pgvector  ← 如果有，会再次覆盖步骤2
```

## 4. 为什么这样设计？

### 优点：
1. **代码不变，配置切换**：同一份代码可以运行在不同环境
2. **配置分层**：
   - `application.yml`：通用配置（日志、基础设置等）
   - `application-container.yml`：容器环境特有配置（服务名、端口等）
3. **优先级清晰**：环境变量 > Profile配置 > 默认配置

## 5. 实际使用示例

### 场景1：本地开发（Windows）
不设置 `SPRING_PROFILES_ACTIVE`，使用默认配置：
- MySQL: `localhost:3306`
- pgvector: `localhost:5432`
- Embedding: `localhost:10086`

### 场景2：Docker 容器部署
在 docker-compose.yml 中设置：
```yaml
backend-test:
  environment:
    - SPRING_PROFILES_ACTIVE=container
```
自动加载 `application-container.yml`：
- MySQL: `mysql-test:3306` （容器服务名）
- pgvector: `pgvector-test:5432` （容器服务名）
- Embedding: `embedding-service:9000` （容器服务名）

## 6. Spring Boot 源码层面

Spring Boot 在启动时会：
1. 读取 `spring.profiles.active` 属性
2. 遍历 `classpath:` 下的所有 `application-*.yml` 文件
3. 如果文件名匹配 `application-{active-profile}.yml`，则加载它
4. 合并配置（后面的覆盖前面的）
5. 用环境变量进行最终覆盖

这就是为什么设置 `SPRING_PROFILES_ACTIVE=container` 就能自动启用 `application-container.yml` 的原理！


