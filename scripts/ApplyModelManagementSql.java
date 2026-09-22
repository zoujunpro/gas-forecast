import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class ApplyModelManagementSql {
    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "root", "mysql2026");
                Statement statement = connection.createStatement()) {
            applySchema(statement);
            applyPermissions(statement);
            printColumns(statement, "model_config_tb");
            printColumns(statement, "model_config_scope_tb");
            printPermission(statement);
        }
    }

    private static void applySchema(Statement statement) throws Exception {
        statement.execute("""
                create table if not exists model_config_tb (
                    id bigint unsigned not null auto_increment comment '主键ID',
                    config_code varchar(64) not null comment '配置编码',
                    config_name varchar(128) not null comment '配置名称',
                    agent_code varchar(64) not null comment '智能体编码',
                    scene_code varchar(32) not null comment '场景编码：WINTER_SUPPLY/MONTHLY_SALES/SHORT_CUSTOMER',
                    enabled tinyint not null default 1 comment '是否启用',
                    description varchar(500) default null comment '描述',
                    create_time datetime not null default current_timestamp comment '创建时间',
                    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                    primary key (id),
                    unique key uk_config_code (config_code),
                    key idx_model_config_agent (agent_code, enabled),
                    key idx_model_config_scene (scene_code, enabled)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='模型配置表'
                """);
        statement.execute("""
                create table if not exists model_config_scope_tb (
                    id bigint unsigned not null auto_increment comment '主键ID',
                    model_config_id bigint unsigned not null comment '模型配置ID',
                    region_code varchar(32) default null comment '地区编码',
                    industry_code varchar(32) default null comment '行业编码',
                    customer_code varchar(64) default null comment '客户编码',
                    enabled tinyint not null default 1 comment '是否启用',
                    create_time datetime not null default current_timestamp comment '创建时间',
                    primary key (id),
                    key idx_model_config (model_config_id),
                    key idx_region (region_code),
                    key idx_customer (customer_code),
                    key idx_scope_lookup (region_code, industry_code, customer_code, enabled)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='模型配置作用范围表'
                """);
        ensureColumn(
                statement,
                "model_config_tb",
                "config_code",
                "alter table model_config_tb add column config_code varchar(64) null comment '配置编码' after id");
        ensureColumn(
                statement,
                "model_config_tb",
                "config_name",
                "alter table model_config_tb add column config_name varchar(128) null comment '配置名称' after config_code");
        ensureColumn(
                statement,
                "model_config_tb",
                "scene_code",
                "alter table model_config_tb add column scene_code varchar(32) null comment '场景编码：WINTER_SUPPLY/MONTHLY_SALES/SHORT_CUSTOMER' after agent_code");
        ensureColumn(
                statement,
                "model_config_tb",
                "create_time",
                "alter table model_config_tb add column create_time datetime not null default current_timestamp comment '创建时间'");
        ensureColumn(
                statement,
                "model_config_tb",
                "update_time",
                "alter table model_config_tb add column update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间'");
        copyIfColumnsExist(statement, "model_config_tb", "config_code", "model_code");
        copyIfColumnsExist(statement, "model_config_tb", "config_name", "model_name");
        copyIfColumnsExist(statement, "model_config_tb", "create_time", "created_at");
        copyIfColumnsExist(statement, "model_config_tb", "update_time", "updated_at");
        statement.execute(
                "update model_config_tb set scene_code = case agent_code when 'winter-supply' then 'WINTER_SUPPLY' when 'monthly-sales' then 'MONTHLY_SALES' when 'short-term' then 'SHORT_CUSTOMER' else scene_code end where scene_code is null or scene_code = ''");
        statement.execute(
                "update model_config_tb set config_code = concat('MODEL-', id) where config_code is null or config_code = ''");
        statement.execute(
                "update model_config_tb set config_name = config_code where config_name is null or config_name = ''");
        statement.execute("alter table model_config_tb modify column config_code varchar(64) not null comment '配置编码'");
        statement.execute("alter table model_config_tb modify column config_name varchar(128) not null comment '配置名称'");
        statement.execute(
                "alter table model_config_tb modify column scene_code varchar(32) not null comment '场景编码：WINTER_SUPPLY/MONTHLY_SALES/SHORT_CUSTOMER'");
        ensureIndex(
                statement,
                "model_config_tb",
                "uk_config_code",
                "alter table model_config_tb add unique key uk_config_code (config_code)");
        ensureIndex(
                statement,
                "model_config_tb",
                "idx_model_config_agent",
                "alter table model_config_tb add key idx_model_config_agent (agent_code, enabled)");
        ensureIndex(
                statement,
                "model_config_tb",
                "idx_model_config_scene",
                "alter table model_config_tb add key idx_model_config_scene (scene_code, enabled)");

        ensureColumn(
                statement,
                "model_config_scope_tb",
                "model_config_id",
                "alter table model_config_scope_tb add column model_config_id bigint unsigned null comment '模型配置ID' after id");
        ensureColumn(
                statement,
                "model_config_scope_tb",
                "enabled",
                "alter table model_config_scope_tb add column enabled tinyint not null default 1 comment '是否启用'");
        ensureColumn(
                statement,
                "model_config_scope_tb",
                "create_time",
                "alter table model_config_scope_tb add column create_time datetime not null default current_timestamp comment '创建时间'");
        copyIfColumnsExist(statement, "model_config_scope_tb", "model_config_id", "model_code");
        copyIfColumnsExist(statement, "model_config_scope_tb", "create_time", "created_at");
        statement.execute("update model_config_scope_tb set model_config_id = 0 where model_config_id is null");
        statement.execute(
                "alter table model_config_scope_tb modify column model_config_id bigint unsigned not null comment '模型配置ID'");
        ensureIndex(
                statement,
                "model_config_scope_tb",
                "idx_model_config",
                "alter table model_config_scope_tb add key idx_model_config (model_config_id)");
        ensureIndex(
                statement,
                "model_config_scope_tb",
                "idx_region",
                "alter table model_config_scope_tb add key idx_region (region_code)");
        ensureIndex(
                statement,
                "model_config_scope_tb",
                "idx_customer",
                "alter table model_config_scope_tb add key idx_customer (customer_code)");
        ensureIndex(
                statement,
                "model_config_scope_tb",
                "idx_scope_lookup",
                "alter table model_config_scope_tb add key idx_scope_lookup (region_code, industry_code, customer_code, enabled)");
    }

    private static void applyPermissions(Statement statement) throws Exception {
        statement.execute("""
                insert ignore into sys_permission_tb (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status) values
                    (60, null, '模型管理', '/model', null, 'MENU', null, 'DataAnalysis', 60, 0, 1),
                    (61, 60, '模型列表', '/model/list', 'views/model/ModelConfigManagement.vue', 'MENU', 'model:config:list', null, 61, 0, 1),
                    (6101, 61, '新增模型', null, null, 'BUTTON', 'model:config:create', null, 6101, 1, 1),
                    (6102, 61, '模型配置', null, null, 'BUTTON', 'model:config:update', null, 6102, 1, 1),
                    (6103, 61, '删除模型', null, null, 'BUTTON', 'model:config:delete', null, 6103, 1, 1)
                """);
        statement.execute(
                "update sys_permission_tb set permission_name = '模型管理', path = '/model', component = null, perms = null, icon = 'DataAnalysis', sort_no = 60, hidden = 0, status = 1 where id = 60");
        statement.execute(
                "update sys_permission_tb set parent_id = 60, permission_name = '模型列表', path = '/model/list', component = 'views/model/ModelConfigManagement.vue', permission_type = 'MENU', perms = 'model:config:list', sort_no = 61, hidden = 0, status = 1 where id = 61");
        statement.execute(
                "update sys_permission_tb set parent_id = 61, permission_name = '新增模型', permission_type = 'BUTTON', perms = 'model:config:create', hidden = 1, status = 1 where id = 6101");
        statement.execute(
                "update sys_permission_tb set parent_id = 61, permission_name = '模型配置', permission_type = 'BUTTON', perms = 'model:config:update', hidden = 1, status = 1 where id = 6102");
        statement.execute(
                "update sys_permission_tb set parent_id = 61, permission_name = '删除模型', permission_type = 'BUTTON', perms = 'model:config:delete', hidden = 1, status = 1 where id = 6103");
        statement.execute("""
                insert ignore into sys_role_permission_ref (role_id, permission_id)
                select r.id, p.id from sys_role_tb r join sys_permission_tb p
                where r.role_code = 'admin' and p.id in (60, 61, 6101, 6102, 6103)
                """);
    }

    private static void ensureColumn(Statement statement, String table, String column, String ddl) throws Exception {
        if (!exists(
                statement,
                "select 1 from information_schema.columns where table_schema = database() and table_name = '" + table
                        + "' and column_name = '" + column + "'")) {
            statement.execute(ddl);
        }
    }

    private static void ensureIndex(Statement statement, String table, String indexName, String ddl) throws Exception {
        if (!exists(
                statement,
                "select 1 from information_schema.statistics where table_schema = database() and table_name = '" + table
                        + "' and index_name = '" + indexName + "'")) {
            statement.execute(ddl);
        }
    }

    private static void copyIfColumnsExist(Statement statement, String table, String targetColumn, String sourceColumn)
            throws Exception {
        if (exists(
                        statement,
                        "select 1 from information_schema.columns where table_schema = database() and table_name = '"
                                + table + "' and column_name = '" + sourceColumn + "'")
                && exists(
                        statement,
                        "select 1 from information_schema.columns where table_schema = database() and table_name = '"
                                + table + "' and column_name = '" + targetColumn + "'")) {
            statement.execute("update " + table + " set " + targetColumn + " = " + sourceColumn + " where "
                    + targetColumn + " is null");
        }
    }

    private static boolean exists(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next();
        }
    }

    private static void printColumns(Statement statement, String table) throws Exception {
        System.out.println("TABLE " + table);
        try (ResultSet rs = statement.executeQuery("show columns from " + table)) {
            while (rs.next()) {
                System.out.println(rs.getString("Field") + "\t" + rs.getString("Type") + "\t" + rs.getString("Null")
                        + "\t" + rs.getString("Key") + "\t" + rs.getString("Default"));
            }
        }
    }

    private static void printPermission(Statement statement) throws Exception {
        System.out.println("MODEL PERMISSIONS");
        try (ResultSet rs = statement.executeQuery(
                "select id, parent_id, permission_name, path, component, permission_type, perms from sys_permission_tb where id in (60, 61, 6101, 6102, 6103) order by id")) {
            while (rs.next()) {
                System.out.println(
                        rs.getLong("id") + "\t" + rs.getObject("parent_id") + "\t" + rs.getString("permission_name")
                                + "\t" + rs.getString("path") + "\t" + rs.getString("component") + "\t"
                                + rs.getString("permission_type") + "\t" + rs.getString("perms"));
            }
        }
    }
}
