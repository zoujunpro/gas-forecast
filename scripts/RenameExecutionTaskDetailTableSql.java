import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Renames the empty execution task detail table without changing its rows or indexes. */
public class RenameExecutionTaskDetailTableSql {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "root", "mysql2026");
                Statement statement = connection.createStatement()) {
            if (exists(statement, "execution_task_batch_relation_tb") && !exists(statement, "execution_task_detail_tb"))
                statement.execute("rename table execution_task_batch_relation_tb to execution_task_detail_tb");
            System.out.println("execution_task_detail_tb ready");
        }
    }

    private static boolean exists(Statement statement, String tableName) throws Exception {
        try (ResultSet result = statement.executeQuery("select 1 from information_schema.tables where table_schema = database() and table_name = '"
                + tableName + "' limit 1")) {
            return result.next();
        }
    }
}
