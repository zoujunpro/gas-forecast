import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;

public class InspectSimulationSystem {
    private static final List<String> TABLES = List.of("sys_user", "sys_role", "sys_user_role", "sys_permission", "sys_role_permission", "sys_depart", "sys_user_depart", "sys_permission_data_rule",
            "sys_depart_permission", "sys_log");

    public static void main(String[] args) throws Exception {
        String password = args.length > 0 ? args[0] : "root";
        String url = "jdbc:mysql://127.0.0.1:3306/simulation_system?characterEncoding=UTF-8&useUnicode=true&useSSL=false&tinyInt1isBit=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(url, "root", password); Statement statement = connection.createStatement()) {
            printRows(statement, "select database() db, @@version version");
            for (String table : TABLES) {
                printRows(statement, "select '" + table + "' table_name, count(*) row_count from " + table);
            }
            printRows(statement, "select id, username, realname, org_code, status, del_flag, work_no from sys_user order by create_time desc limit 10");
            printRows(statement, "select id, role_name, role_code from sys_role order by create_time desc limit 20");
            printRows(statement, "select id, parent_id, name, url, component, menu_type, perms, sort_no, hidden, status from sys_permission where del_flag = 0 order by sort_no limit 30");
            printRows(statement,
                    "select u.username, r.role_code, r.role_name from sys_user u join sys_user_role ur on ur.user_id = u.id join sys_role r on r.id = ur.role_id order by u.username, r.role_code limit 50");
            printRows(statement,
                    "select u.username, d.depart_name, d.org_code from sys_user u join sys_user_depart ud on ud.user_id = u.id join sys_depart d on d.id = ud.dep_id order by u.username limit 50");
        }
    }

    private static void printRows(Statement statement, String sql) throws Exception {
        System.out.println("SQL> " + sql);
        try (ResultSet rs = statement.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int columns = md.getColumnCount();
            int row = 0;
            while (rs.next()) {
                row++;
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    if (i > 1) {
                        line.append(", ");
                    }
                    line.append(md.getColumnLabel(i)).append("=").append(rs.getString(i));
                }
                System.out.println(line);
            }
            if (row == 0) {
                System.out.println("(empty)");
            }
        }
    }
}
