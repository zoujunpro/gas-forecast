import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class SqlInspector {
    public static void main(String[] args) throws Exception {
        String url =
                "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(url, "root", "mysql2026");
                Statement statement = connection.createStatement()) {
            printSingle(statement, "select @@hostname, @@port, @@version, database()");
            printRows(statement, "show tables");
            printRows(statement, "select area_code, area_name from gas_area order by id");
            printRows(statement, "select province_code, province_name, area_code from gas_province order by id");
            printRows(
                    statement,
                    "select customer_code, customer_name, customer_type from gas_customer order by id limit 5");
        }
    }

    private static void printSingle(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= columns; i++) {
                    System.out.print(rs.getMetaData().getColumnLabel(i) + "=" + rs.getString(i));
                    System.out.print(i == columns ? "\n" : ", ");
                }
            }
        }
    }

    private static void printRows(Statement statement, String sql) throws Exception {
        System.out.println("SQL> " + sql);
        try (ResultSet rs = statement.executeQuery(sql)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= columns; i++) {
                    System.out.print(rs.getMetaData().getColumnLabel(i) + "=" + rs.getString(i));
                    System.out.print(i == columns ? "\n" : ", ");
                }
            }
        }
    }
}
