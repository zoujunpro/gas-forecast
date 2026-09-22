import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SqlImporter {
    public static void main(String[] args) throws Exception {
        String url =
                "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(url, "root", "mysql2026")) {
            if (args.length == 0) {
                executeFile(connection, Path.of("forecast-bussiness-dao/src/main/resources/sql/00_schema.sql"));
                executeFile(
                        connection, Path.of("forecast-bussiness-dao/src/main/resources/sql/01_seed_winner_agent.sql"));
            } else {
                for (String arg : args) {
                    executeFile(connection, Path.of(arg));
                }
            }
            printCount(connection, "gas_data.base_region_tb");
            printCount(connection, "gas_data.base_customer_tb");
            printCount(connection, "gas_data.base_industry_tb");
            printCount(connection, "gas_data.data_monthly_sales_tb");
            printCount(connection, "gas_data.data_daily_sales_tb");
            printCount(connection, "gas_data.data_winter_tenday_dataset_tb");
            printCount(connection, "gas_data.model_train_batch_tb");
            printCount(connection, "gas_data.model_forecast_record_tb");
            printCount(connection, "gas_data.model_forecast_result_tb");
            printCount(connection, "gas_data.model_predict_winter_result_tb");
            printCount(connection, "gas_data.model_train_backtest_tb");
            printCount(connection, "gas_data.gas_area");
            printCount(connection, "gas_data.gas_province");
            printCount(connection, "gas_data.gas_customer");
        }
    }

    private static void executeFile(Connection connection, Path path) throws Exception {
        String sql = Files.readString(path, StandardCharsets.UTF_8);
        List<String> statements = splitSql(sql);
        try (Statement statement = connection.createStatement()) {
            for (int i = 0; i < statements.size(); i++) {
                String item = statements.get(i);
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        statement.execute(trimmed);
                    } catch (Exception exception) {
                        System.err.println("failed statement #" + (i + 1));
                        System.err.println(trimmed.substring(0, Math.min(trimmed.length(), 500)));
                        throw exception;
                    }
                }
            }
        }
        System.out.println("executed: " + path + " (" + statements.size() + " statements)");
    }

    private static List<String> splitSql(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            current.append(ch);
            if (ch == '\'') {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append(sql.charAt(++i));
                } else {
                    inSingleQuote = !inSingleQuote;
                }
            } else if (ch == ';' && !inSingleQuote) {
                statements.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.toString().trim().isEmpty()) {
            statements.add(current.toString());
        }
        return statements;
    }

    private static void printCount(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("select count(*) from " + tableName)) {
            resultSet.next();
            System.out.println(tableName + ": " + resultSet.getLong(1));
        } catch (Exception exception) {
            System.out.println(tableName + ": not found");
        }
    }
}
