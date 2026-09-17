# gas-forecast

天然气冬供旬预测展示项目。

## 模块

- `gas-forecast-web`：Vue 3 前端，渲染区域/省份/客户筛选、预测、模型排名、特征排名和迁移图表。
- `gas-forecast-bussiness`：Java 后端接口，按 `controller/service/dto` 分层。
- `gas-forecast-dao`：数据库实体、MyBatis Mapper、XML 和 `gas_data` 初始化 SQL。

## 数据库

本地 MySQL 库名：`gas_data`。

按顺序执行：

```bash
mysql -uroot -p < gas-forecast-dao/src/main/resources/sql/00_schema.sql
mysql -uroot -p gas_data < gas-forecast-dao/src/main/resources/sql/01_seed_winner_agent.sql
mysql -uroot -p gas_data < gas-forecast-dao/src/main/resources/sql/04_create_config_tables.sql
mysql -uroot -p gas_data < gas-forecast-dao/src/main/resources/sql/05_seed_standard_sales.sql
mysql -uroot -p gas_data < gas-forecast-dao/src/main/resources/sql/06_create_auth_tables.sql
mysql -uroot -p gas_data < gas-forecast-dao/src/main/resources/sql/07_add_data_file_info_menu.sql
```

`05_seed_standard_sales.sql` 由 `scripts/generate_standard_sales_seed.py` 根据本地 A13 分月原始数据生成，包含月销量标准数据和按月量拆分的日销量标准数据。

当前后端默认连接本地 MySQL：

```text
database: gas_data
host: 127.0.0.1:3306
username: root
password: mysql2026
```

## 后端

```bash
mvn -pl gas-forecast-bussiness -am spring-boot:run
```

接口：

- `GET /api/forecast/provinces`
- `GET /api/forecast/summaries`
- `GET /api/forecast/dashboard?province=北京`

## 前端 Vue 3

安装依赖并启动：

```bash
cd gas-forecast-web
npm install
npm run dev
```

Vite 已配置 `/api` 代理到 `http://localhost:8080`。

构建：

```bash
cd gas-forecast-web
npm run build
```
