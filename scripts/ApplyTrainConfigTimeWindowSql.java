import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class ApplyTrainConfigTimeWindowSql {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "root", "mysql2026");
             Statement statement = connection.createStatement()) {
            ensureBaseColumns(statement);
            ensureColumn(statement, "train_mode",
                    "alter table model_train_config_tb add column train_mode varchar(32) not null default 'RECENT' comment '训练方式：RECENT最近时间/RANGE指定时间范围' after train_end_date");
            ensureColumn(statement, "time_granularity",
                    "alter table model_train_config_tb add column time_granularity varchar(32) not null default 'MONTH' comment '时间格式：DAY日/TENDAY旬/MONTH月' after train_mode");
            ensureColumn(statement, "recent_periods",
                    "alter table model_train_config_tb add column recent_periods int default 36 comment '最近周期数' after time_granularity");
            backfillBaseColumns(statement);
            statement.execute("update model_train_config_tb set train_mode = 'RECENT' where train_mode is null or train_mode = ''");
            statement.execute("update model_train_config_tb set time_granularity = 'MONTH' where time_granularity is null or time_granularity = ''");
            statement.execute("update model_train_config_tb set recent_periods = 36 where train_mode = 'RECENT' and recent_periods is null");
            ensureIndex(statement, "uk_train_config_code", "alter table model_train_config_tb add unique key uk_train_config_code (train_code)");
            ensureIndex(statement, "uk_train_code", "alter table model_train_config_tb add unique key uk_train_code (train_code)");
            ensureIndex(statement, "idx_train_config_agent", "alter table model_train_config_tb add key idx_train_config_agent (agent_code, enabled)");
            ensureIndex(statement, "idx_train_config_model", "alter table model_train_config_tb add key idx_train_config_model (model_code)");
            ensureIndex(statement, "idx_train_config_scope", "alter table model_train_config_tb add key idx_train_config_scope (agent_code, scope_type, region_code, customer_code, industry_code)");
            printColumns(statement);
        }
    }

    private static void ensureBaseColumns(Statement statement) throws Exception {
        ensureColumn(statement, "train_code", "alter table model_train_config_tb add column train_code varchar(64) null comment '训练配置编码' after id");
        ensureColumn(statement, "train_name", "alter table model_train_config_tb add column train_name varchar(128) null comment '训练配置名称' after train_code");
        ensureColumn(statement, "agent_code", "alter table model_train_config_tb add column agent_code varchar(64) null comment '智能体编码' after train_name");
        ensureColumn(statement, "model_code", "alter table model_train_config_tb add column model_code varchar(64) null comment '所属模型编码' after agent_code");
        ensureColumn(statement, "model_name", "alter table model_train_config_tb add column model_name varchar(128) null comment '所属模型名称' after model_code");
        ensureColumn(statement, "scope_type", "alter table model_train_config_tb add column scope_type varchar(32) not null default 'ALL' comment '作用范围：REGION/CUSTOMER/INDUSTRY/ALL' after model_name");
        ensureColumn(statement, "region_code", "alter table model_train_config_tb add column region_code varchar(64) null comment '区域编号' after scope_type");
        ensureColumn(statement, "region_name", "alter table model_train_config_tb add column region_name varchar(64) null comment '区域名称' after region_code");
        ensureColumn(statement, "industry_code", "alter table model_train_config_tb add column industry_code varchar(64) null comment '行业编号' after region_name");
        ensureColumn(statement, "industry_name", "alter table model_train_config_tb add column industry_name varchar(64) null comment '行业名称' after industry_code");
        ensureColumn(statement, "customer_code", "alter table model_train_config_tb add column customer_code varchar(64) null comment '客户编号' after industry_name");
        ensureColumn(statement, "customer_name", "alter table model_train_config_tb add column customer_name varchar(128) null comment '客户名称' after customer_code");
        ensureColumn(statement, "train_start_date", "alter table model_train_config_tb add column train_start_date varchar(32) null comment '训练数据开始日期' after customer_name");
        ensureColumn(statement, "train_end_date", "alter table model_train_config_tb add column train_end_date varchar(32) null comment '训练数据结束日期' after train_start_date");
        ensureColumn(statement, "enabled", "alter table model_train_config_tb add column enabled tinyint not null default 1 comment '是否启用'");
        ensureColumn(statement, "remark", "alter table model_train_config_tb add column remark varchar(512) null comment '备注'");
        ensureColumn(statement, "created_by", "alter table model_train_config_tb add column created_by varchar(64) null comment '创建人'");
        ensureColumn(statement, "created_by_name", "alter table model_train_config_tb add column created_by_name varchar(64) null comment '创建人名称'");
        ensureColumn(statement, "created_at", "alter table model_train_config_tb add column created_at datetime not null default current_timestamp comment '创建时间'");
        ensureColumn(statement, "updated_at", "alter table model_train_config_tb add column updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间'");
    }

    private static void backfillBaseColumns(Statement statement) throws Exception {
        copyIfColumnsExist(statement, "train_code", "config_code");
        copyIfColumnsExist(statement, "train_name", "config_name");
        copyIfColumnsExist(statement, "train_code", "model_code");
        copyIfColumnsExist(statement, "train_name", "model_name");
        copyIfColumnsExist(statement, "created_at", "create_time");
        copyIfColumnsExist(statement, "updated_at", "update_time");
        statement.execute("update model_train_config_tb set train_code = concat('TRAIN-', id) where train_code is null or train_code = ''");
        statement.execute("update model_train_config_tb set train_name = train_code where train_name is null or train_name = ''");
        statement.execute("update model_train_config_tb set agent_code = 'winter-supply' where agent_code is null or agent_code = ''");
        statement.execute("update model_train_config_tb set scope_type = 'ALL' where scope_type is null or scope_type = ''");
        statement.execute("alter table model_train_config_tb modify column train_code varchar(64) not null comment '训练配置编码'");
        statement.execute("alter table model_train_config_tb modify column train_name varchar(128) not null comment '训练配置名称'");
        statement.execute("alter table model_train_config_tb modify column agent_code varchar(64) not null comment '智能体编码'");
        relaxLegacyRequiredColumn(statement, "config_code", "varchar(64)", "旧配置编码");
        relaxLegacyRequiredColumn(statement, "config_name", "varchar(128)", "旧配置名称");
    }

    private static void ensureColumn(Statement statement, String column, String ddl) throws Exception {
        if (!exists(statement, "select 1 from information_schema.columns where table_schema = database() and table_name = 'model_train_config_tb' and column_name = '" + column + "'")) {
            statement.execute(ddl);
            System.out.println("added column: " + column);
        } else {
            System.out.println("column exists: " + column);
        }
    }

    private static void ensureIndex(Statement statement, String indexName, String ddl) throws Exception {
        if (!exists(statement, "select 1 from information_schema.statistics where table_schema = database() and table_name = 'model_train_config_tb' and index_name = '" + indexName + "'")) {
            statement.execute(ddl);
            System.out.println("added index: " + indexName);
        } else {
            System.out.println("index exists: " + indexName);
        }
    }

    private static void copyIfColumnsExist(Statement statement, String targetColumn, String sourceColumn) throws Exception {
        if (exists(statement, "select 1 from information_schema.columns where table_schema = database() and table_name = 'model_train_config_tb' and column_name = '" + sourceColumn + "'")
                && exists(statement, "select 1 from information_schema.columns where table_schema = database() and table_name = 'model_train_config_tb' and column_name = '" + targetColumn + "'")) {
            statement.execute("update model_train_config_tb set " + targetColumn + " = " + sourceColumn + " where " + targetColumn + " is null");
        }
    }

    private static void relaxLegacyRequiredColumn(Statement statement, String column, String type, String comment) throws Exception {
        if (exists(statement, "select 1 from information_schema.columns where table_schema = database() and table_name = 'model_train_config_tb' and column_name = '" + column + "'")) {
            statement.execute("alter table model_train_config_tb modify column " + column + " " + type + " null comment '" + comment + "'");
            System.out.println("relaxed legacy column: " + column);
        }
    }

    private static boolean exists(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next();
        }
    }

    private static void printColumns(Statement statement) throws Exception {
        try (ResultSet rs = statement.executeQuery("show columns from model_train_config_tb")) {
            while (rs.next()) {
                System.out.println(rs.getString("Field") + "\t" + rs.getString("Type") + "\t" + rs.getString("Null") + "\t" + rs.getString("Default"));
            }
        }
    }
}
