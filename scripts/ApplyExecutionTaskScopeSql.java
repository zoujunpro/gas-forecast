import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Rebuilds the empty task-target relation table to its final schema. */
public class ApplyExecutionTaskScopeSql {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "root", "mysql2026");
                Statement statement = connection.createStatement()) {
            statement.execute("drop table execution_task_detail_tb");
            statement.execute("""
                    create table execution_task_detail_tb (
                        id bigint unsigned not null auto_increment comment '主键ID',
                        task_id bigint not null comment '父任务主键，关联execution_task_tb.id',
                        task_no varchar(64) not null comment '父任务展示编号',
                        detail_seq int not null comment '父任务内明细序号',
                        detail_no varchar(96) not null comment '任务明细展示编号',
                        agent_code varchar(64) not null comment '执行智能体编码',
                        dimension_type varchar(32) not null comment 'CUSTOMER客户、REGION地区、INDUSTRY行业',
                        region_code varchar(64) not null default '' comment '地区编码；不适用时为空串',
                        industry_code varchar(64) not null default '' comment '行业编码；不适用时为空串',
                        customer_code varchar(64) not null default '' comment '客户编码；不适用时为空串',
                        train_batch_no varchar(64) default null comment '训练批次号',
                        forecast_batch_no varchar(64) default null comment '预测批次号',
                        status varchar(32) not null default 'PENDING' comment '最小目标执行状态',
                        created_at datetime not null default current_timestamp comment '创建时间',
                        updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                        primary key (id),
                        unique key uk_task_detail_seq (task_id, detail_seq),
                        unique key uk_task_detail_no (detail_no),
                        unique key uk_task_target (task_id, dimension_type, region_code, industry_code, customer_code),
                        key idx_train_batch (train_batch_no),
                        key idx_forecast_batch (forecast_batch_no)
                    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='任务与训练预测批次关联表'
                    """);
            printColumns(statement, "execution_task_detail_tb");
        }
    }

    private static void printColumns(Statement statement, String table) throws Exception {
        System.out.println("TABLE " + table);
        try (ResultSet rs = statement.executeQuery("show columns from " + table)) {
            while (rs.next()) {
                System.out.println(rs.getString("Field") + "\t" + rs.getString("Type") + "\t" + rs.getString("Null") + "\t" + rs.getString("Key"));
            }
        }
    }
}
