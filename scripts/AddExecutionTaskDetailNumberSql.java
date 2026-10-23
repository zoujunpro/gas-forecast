import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Adds task primary-key linkage and readable detail numbering to the empty detail table. */
public class AddExecutionTaskDetailNumberSql {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "root", "mysql2026");
                Statement statement = connection.createStatement()) {
            if (!exists(statement, "columns", "column_name = 'task_id'"))
                statement.execute("alter table execution_task_detail_tb add column task_id bigint not null comment '父任务主键，关联execution_task_tb.id' after id, add column detail_seq int not null comment '父任务内明细序号' after task_no, add column detail_no varchar(96) not null comment '任务明细展示编号' after detail_seq");
            if (exists(statement, "statistics", "index_name = 'uk_task_target'"))
                statement.execute("alter table execution_task_detail_tb drop index uk_task_target");
            if (!exists(statement, "statistics", "index_name = 'uk_task_detail_seq'"))
                statement.execute("alter table execution_task_detail_tb add unique key uk_task_detail_seq (task_id, detail_seq)");
            if (!exists(statement, "statistics", "index_name = 'uk_task_detail_no'"))
                statement.execute("alter table execution_task_detail_tb add unique key uk_task_detail_no (detail_no)");
            if (!exists(statement, "statistics", "index_name = 'uk_task_target'"))
                statement.execute("alter table execution_task_detail_tb add unique key uk_task_target (task_id, dimension_type, region_code, industry_code, customer_code)");
            System.out.println("execution_task_detail_tb numbering ready");
        }
    }

    private static boolean exists(Statement statement, String table, String condition) throws Exception {
        try (ResultSet result = statement.executeQuery("select 1 from information_schema." + table
                + " where table_schema = database() and table_name = 'execution_task_detail_tb' and " + condition + " limit 1")) {
            return result.next();
        }
    }
}
