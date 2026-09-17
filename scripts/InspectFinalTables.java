import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class InspectFinalTables {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(url, "root", "mysql2026");
             Statement statement = connection.createStatement()) {
            printRows(statement, "show columns from base_region_tb where Field = 'id'");
            printRows(statement, "show columns from base_customer_tb where Field = 'id'");
            printRows(statement, "show columns from base_industry_tb where Field = 'id'");
            printRows(statement, "show columns from model_train_batch_tb where Field = 'id'");
            printRows(statement, "show columns from model_forecast_batch_tb where Field = 'id'");
            printRows(statement, "show columns from model_predict_winter_result_tb where Field = 'id'");
            printRows(statement, "show tables like 'gas_%'");
            printRows(statement, "select region_code, region_name, region_type, created_by_name from base_region_tb where remark like 'winner-agent%' order by id limit 12");
            printRows(statement, "select customer_code, customer_name, industry_name, region_name, created_by_name from base_customer_tb order by id limit 5");
            printRows(statement, "select batch_no, region_name, best_model, mape, wmape, rmse, created_by_name from model_train_batch_tb where batch_no like 'WGTRAIN-%' order by id");
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
