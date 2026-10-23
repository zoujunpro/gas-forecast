import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Adds the task_no business key to the local empty execution_task_tb table. */
public class ApplyExecutionTaskNoSql {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "root", "mysql2026");
                Statement statement = connection.createStatement()) {
            if (!exists(statement, "columns", "column_name = 'task_no'"))
                statement.execute("alter table execution_task_tb add column task_no varchar(64) not null comment '执行任务唯一编号' after request_id");
            if (!exists(statement, "statistics", "index_name = 'uk_execution_task_no'"))
                statement.execute("alter table execution_task_tb add unique key uk_execution_task_no (task_no)");
            System.out.println("execution_task_tb.task_no ready");
        }
    }

    private static boolean exists(Statement statement, String table, String condition) throws Exception {
        try (ResultSet result = statement.executeQuery("select 1 from information_schema." + table
                + " where table_schema = database() and table_name = 'execution_task_tb' and " + condition + " limit 1")) {
            return result.next();
        }
    }
}
