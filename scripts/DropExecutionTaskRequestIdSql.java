import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Removes the obsolete task-level request_id; AgentRun keeps the client idempotency key. */
public class DropExecutionTaskRequestIdSql {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "root", "mysql2026"); Statement statement = connection.createStatement()) {
            if (exists(statement)) statement.execute("alter table execution_task_tb drop column request_id");
            System.out.println(exists(statement) ? "request_id still exists" : "execution_task_tb.request_id dropped");
        }
    }
    private static boolean exists(Statement statement) throws Exception {
        try (ResultSet result = statement.executeQuery("select 1 from information_schema.columns where table_schema=database() and table_name='execution_task_tb' and column_name='request_id' limit 1")) { return result.next(); }
    }
}
