import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.IntStream;

/** 用日销量标准数据全量刷新训练特征宽表；暂不填充特征槽位。 */
public class RefreshDailyTrainingData {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !("--apply".equals(args[0]) || "--verify".equals(args[0]))) {
            throw new IllegalArgumentException("Use --apply or --verify");
        }
        try (Connection connection = DriverManager.getConnection(URL, "root", "mysql2026")) {
            if ("--verify".equals(args[0])) {
                verify(connection);
                return;
            }
            validateSource(connection);
            expandCodeColumns(connection);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int deleted = statement.executeUpdate("delete from model_train_feature_data_tb");
                int inserted = statement.executeUpdate("""
                        insert into model_train_feature_data_tb (
                            stat_date, time_granularity,
                            region_code, region_name,
                            customer_code, customer_name,
                            industry_code, industry_name,
                            gas_sales, create_time, update_time
                        )
                        select
                            trim(stat_date), 'DAY',
                            trim(region_code), trim(region_name),
                            trim(customer_code), trim(customer_name),
                            trim(industry_code), trim(industry_name),
                            cast(trim(gas_sales) as decimal(18,4)), now(), now()
                        from data_daily_sales_tb
                        order by id
                        """);
                long sourceCount = scalar(connection, "select count(*) from data_daily_sales_tb");
                long targetCount = scalar(connection, "select count(*) from model_train_feature_data_tb");
                long populatedFeatures = scalar(connection, "select count(*) from model_train_feature_data_tb where feature_001 is not null or feature_200 is not null");
                if (inserted != sourceCount || targetCount != sourceCount || populatedFeatures != 0) {
                    throw new IllegalStateException(
                            "Refresh verification failed: source=" + sourceCount + ", inserted=" + inserted + ", target=" + targetCount + ", populatedFeatures=" + populatedFeatures);
                }
                connection.commit();
                System.out.println("deleted=" + deleted + ", inserted=" + inserted + ", featuresNonNull=" + populatedFeatures);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void verify(Connection connection) throws Exception {
        long sourceCount = scalar(connection, "select count(*) from data_daily_sales_tb");
        long targetCount = scalar(connection, "select count(*) from model_train_feature_data_tb");
        long nonDayRows = scalar(connection, "select count(*) from model_train_feature_data_tb where time_granularity <> 'DAY' or time_granularity is null");
        String featurePredicate = IntStream.rangeClosed(1, 200).mapToObj(index -> String.format("feature_%03d is not null", index)).reduce((left, right) -> left + " or " + right).orElseThrow();
        long populatedFeatures = scalar(connection, "select count(*) from model_train_feature_data_tb where " + featurePredicate);
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("select min(stat_date), max(stat_date) from model_train_feature_data_tb")) {
            resultSet.next();
            System.out.println("source=" + sourceCount + ", target=" + targetCount + ", nonDayRows=" + nonDayRows + ", rowsWithFeatures=" + populatedFeatures + ", minDate=" + resultSet.getString(1)
                    + ", maxDate=" + resultSet.getString(2));
        }
        if (sourceCount != targetCount || nonDayRows != 0 || populatedFeatures != 0) {
            throw new IllegalStateException("Verification failed");
        }
    }

    private static void validateSource(Connection connection) throws Exception {
        long invalid = scalar(connection, """
                select count(*) from data_daily_sales_tb
                where str_to_date(trim(stat_date), '%Y-%m-%d') is null
                   or trim(gas_sales) not regexp '^-?[0-9]+([.][0-9]+)?$'
                   or stat_date is null or trim(stat_date) = ''
                   or region_code is null or trim(region_code) = ''
                   or industry_code is null or trim(industry_code) = ''
                   or customer_code is null or trim(customer_code) = ''
                """);
        long duplicates = scalar(connection, """
                select count(*) from (
                    select 1 from data_daily_sales_tb
                    group by trim(stat_date), trim(region_code), trim(industry_code), trim(customer_code)
                    having count(*) > 1
                ) duplicate_keys
                """);
        if (invalid != 0 || duplicates != 0) {
            throw new IllegalStateException("Source validation failed: invalid=" + invalid + ", duplicateGroups=" + duplicates);
        }
    }

    private static void expandCodeColumns(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "alter table model_train_feature_data_tb " + "modify column region_code varchar(64) null comment '区域编码', " + "modify column industry_code varchar(64) null comment '行业编码'");
        }
    }

    private static long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
