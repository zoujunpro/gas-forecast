import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DescribeExistingTables {
    public static void main(String[] args) throws Exception {
        String url =
                "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        String[] tables = {
            "base_region_tb",
            "base_customer_tb",
            "base_industry_tb",
            "model_forecast_config_tb",
            "model_forecast_record_tb",
            "model_train_record_tb",
            "model_train_batch_tb",
            "model_forecast_result_tb",
            "model_predict_winter_result_tb",
            "model_train_backtest_tb",
            "data_winter_tenday_dataset_tb"
        };
        try (Connection connection = DriverManager.getConnection(url, "root", "mysql2026");
                Statement statement = connection.createStatement()) {
            for (String table : tables) {
                System.out.println("TABLE " + table);
                try {
                    try (ResultSet rs = statement.executeQuery("show columns from " + table)) {
                        while (rs.next()) {
                            System.out.println(
                                    rs.getString("Field") + "\t" + rs.getString("Type") + "\t" + rs.getString("Null")
                                            + "\t" + rs.getString("Key") + "\t" + rs.getString("Default"));
                        }
                    }
                } catch (Exception exception) {
                    System.out.println("NOT FOUND");
                }
            }
        }
    }
}
