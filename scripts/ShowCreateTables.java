import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class ShowCreateTables {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        String[] tables = {"base_region_tb", "base_customer_tb", "base_industry_tb", "model_predict_winter_result_tb", "data_winter_tenday_dataset_tb"};
        try (Connection connection = DriverManager.getConnection(url, "root", "mysql2026"); Statement statement = connection.createStatement()) {
            for (String table : tables) {
                try (ResultSet rs = statement.executeQuery("show create table " + table)) {
                    rs.next();
                    System.out.println("TABLE " + table);
                    System.out.println(rs.getString(2));
                }
            }
        }
    }
}
