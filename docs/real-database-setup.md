# 真实数据库配置指南

本文用于在**新机器**上跑真实 MySQL + Oracle 模式。
如果只是体验系统，直接使用 H2 快速启动，不需要本文：

```bash
bash scripts/start-all.sh
```

## 1. 前置条件

- JDK 17+
- Maven 3.9+
- Git Bash（Windows）或 Bash（macOS/Linux）
- MySQL 8.x
- Oracle 23ai/19c，包含可连接的 PDB（默认示例为 `FREEPDB1`）

确认工具：

```bash
java -version
mvn -version
```

## 2. MySQL 建库和账号

1. 确认 MySQL 实际端口。默认是 `3306`，工程示例常用 `3308`。
2. 编辑 `database/mysql/create-accounts.sql`，把 `CHANGE_ME` 替换成统一密码。
3. 用 MySQL 管理员执行：

```bash
mysql -u root -p < database/mysql/create-accounts.sql
```

脚本会创建：

```text
airline_a
airline_b
airline_central
```

以及同名三个账号。**四个账号必须使用同一个密码**，因为 `start-real-db.sh` 只接收一个 `DB_PASSWORD`。

## 3. Oracle 建账号

1. 进入目标 PDB。以 `FREEPDB1` 为例：

```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
```

2. 执行 `database/oracle/create-accounts.sql`。执行前把 `CHANGE_ME` 替换成与 MySQL 相同的密码。
3. 确认 `airline_c` 可以登录：

```bash
sqlplus airline_c/<密码>@//localhost:1521/FREEPDB1
```

如果使用 Oracle 19c 的 `XEPDB1`，后续启动时设置 `ORACLE_SERVICE=XEPDB1`。

## 4. 启动

在项目根目录执行：

```bash
MYSQL_PORT=3306 \
ORACLE_SERVICE=FREEPDB1 \
DB_PASSWORD='你的统一密码' \
bash scripts/start-real-db.sh
```

- MySQL 是 3308 时可省略 `MYSQL_PORT`。
- Oracle 是默认 `localhost:1521/FREEPDB1` 时可省略 `ORACLE_SERVICE`。
- 脚本会启动 A/B/Central 在 `18081/18082/18083/8080`。

首次连接空 Oracle schema 时执行：

```bash
ORACLE_SCHEMA_INIT=true \
MYSQL_PORT=3306 \
ORACLE_SERVICE=FREEPDB1 \
DB_PASSWORD='你的统一密码' \
bash scripts/start-real-db.sh
```

**第二次启动必须去掉 `ORACLE_SCHEMA_INIT`**，否则 Oracle 会报 `ORA-00955`。MySQL 的建表使用 `CREATE TABLE IF NOT EXISTS`，重复执行没有问题。

## 5. 验证

```bash
curl --noproxy '*' http://localhost:8080/health
python3 scripts/smoke_test.py
```

演示日期固定为 `2026-11-15`，查询 `WUH → PEK`。

厚客户端：

```bash
bash scripts/run-thick-client.sh
```

## 6. 常见错误

| 错误 | 原因 | 处理 |
|---|---|---|
| `Access denied for user ...` | 账号密码不一致，或账号没有对应库权限 | 四个账号使用同一密码，确认 `GRANT` 已执行 |
| `ORA-12541: 没有监听程序` | Oracle 实例/监听器没启动 | 启动 Oracle 服务或监听器 |
| `ORA-00955: 名称已由现有对象使用` | 对已有表重复执行 Oracle DDL | 去掉 `ORACLE_SCHEMA_INIT` |
| `Connection refused` 3308 | MySQL 实际是 3306 | 设置 `MYSQL_PORT=3306` |
| 端口 8080/18081 被占用 | 旧进程未停止 | `bash scripts/stop-all.sh` 或检查端口 |

不要提交真实数据库密码。密码只通过 `DB_PASSWORD` 环境变量或脚本隐藏输入传入。
