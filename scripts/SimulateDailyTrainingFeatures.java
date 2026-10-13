import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** 按参考工作簿为日训练样本填充模拟天气特征 feature_006..feature_022。 */
public class SimulateDailyTrainingFeatures {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final Path DEFAULT_WORKBOOK = Path.of("/Users/zoujun/Documents/江苏发电/城市燃气/test.xlsx");
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final List<String> FEATURE_CODES = List.of("tempmax", "tempmin", "temp", "feelslikemax", "feelslikemin", "feelslike", "dew", "humidity", "precip", "windgust", "windspeed",
            "sealevelpressure", "cloudcover", "visibility", "solarradiation", "solarenergy", "uvindex");

    record FeatureRow(LocalDate date, double[] values) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !"--apply".equals(args[0])) {
            throw new IllegalArgumentException("Destructive feature update requires --apply [workbook]");
        }
        Path workbook = args.length > 1 ? Path.of(args[1]) : DEFAULT_WORKBOOK;
        List<FeatureRow> rows = readWorkbook(workbook);
        if (rows.isEmpty()) {
            throw new IllegalStateException("No reference feature rows found");
        }
        try (Connection connection = DriverManager.getConnection(URL, "root", "mysql2026")) {
            validateFeatureMappings(connection);
            connection.setAutoCommit(false);
            try {
                stageReferenceRows(connection, rows);
                int updated = updateTrainingRows(connection);
                verify(connection, rows.size(), updated);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static List<FeatureRow> readWorkbook(Path path) throws Exception {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            List<String> sharedStrings = sharedStrings(zip);
            Document sheet = xml(zip, "xl/worksheets/sheet1.xml");
            NodeList rowNodes = sheet.getElementsByTagNameNS("*", "row");
            Map<String, Integer> headers = new HashMap<>();
            List<FeatureRow> result = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < rowNodes.getLength(); rowIndex++) {
                Element row = (Element) rowNodes.item(rowIndex);
                Map<Integer, String> cells = new HashMap<>();
                NodeList cellNodes = row.getElementsByTagNameNS("*", "c");
                for (int cellIndex = 0; cellIndex < cellNodes.getLength(); cellIndex++) {
                    Element cell = (Element) cellNodes.item(cellIndex);
                    NodeList valueNodes = cell.getElementsByTagNameNS("*", "v");
                    if (valueNodes.getLength() == 0)
                        continue;
                    String raw = valueNodes.item(0).getTextContent();
                    if ("s".equals(cell.getAttribute("t")))
                        raw = sharedStrings.get(Integer.parseInt(raw));
                    cells.put(columnIndex(cell.getAttribute("r")), raw);
                }
                if (rowIndex == 0) {
                    cells.forEach((column, value) -> headers.put(value.trim(), column));
                    requireHeaders(headers);
                    continue;
                }
                String dateValue = cells.get(headers.get("ds"));
                if (dateValue == null || dateValue.isBlank())
                    continue;
                LocalDate date = dateValue.matches("-?\\d+(\\.\\d+)?") ? EXCEL_EPOCH.plusDays((long) Double.parseDouble(dateValue)) : LocalDate.parse(dateValue.trim());
                double[] values = new double[FEATURE_CODES.size()];
                for (int i = 0; i < FEATURE_CODES.size(); i++) {
                    String value = cells.get(headers.get(FEATURE_CODES.get(i)));
                    if (value == null || value.isBlank()) {
                        throw new IllegalStateException("Missing " + FEATURE_CODES.get(i) + " at date " + date);
                    }
                    values[i] = Double.parseDouble(value);
                }
                result.add(new FeatureRow(date, values));
            }
            return result;
        }
    }

    private static void requireHeaders(Map<String, Integer> headers) {
        if (!headers.containsKey("ds"))
            throw new IllegalStateException("Missing ds column");
        for (String code : FEATURE_CODES) {
            if (!headers.containsKey(code))
                throw new IllegalStateException("Missing feature column: " + code);
        }
    }

    private static List<String> sharedStrings(ZipFile zip) throws Exception {
        Document document = xml(zip, "xl/sharedStrings.xml");
        NodeList items = document.getElementsByTagNameNS("*", "si");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            NodeList textNodes = item.getElementsByTagNameNS("*", "t");
            StringBuilder value = new StringBuilder();
            for (int j = 0; j < textNodes.getLength(); j++)
                value.append(textNodes.item(j).getTextContent());
            result.add(value.toString());
        }
        return result;
    }

    private static Document xml(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null)
            throw new IllegalStateException("Missing workbook entry: " + name);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream input = zip.getInputStream(entry)) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private static int columnIndex(String reference) {
        int value = 0;
        for (int i = 0; i < reference.length() && Character.isLetter(reference.charAt(i)); i++) {
            value = value * 26 + Character.toUpperCase(reference.charAt(i)) - 'A' + 1;
        }
        return value - 1;
    }

    private static void validateFeatureMappings(Connection connection) throws Exception {
        String sql = "select feature_code, feature_column from model_feature_definition_tb where feature_code in (" + String.join(",", FEATURE_CODES.stream().map(code -> "?").toList())
                + ") and enabled = 1";
        Map<String, String> mappings = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < FEATURE_CODES.size(); i++)
                statement.setString(i + 1, FEATURE_CODES.get(i));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next())
                    mappings.put(rs.getString(1), rs.getString(2));
            }
        }
        for (int i = 0; i < FEATURE_CODES.size(); i++) {
            String expected = String.format("feature_%03d", i + 6);
            if (!expected.equals(mappings.get(FEATURE_CODES.get(i)))) {
                throw new IllegalStateException("Unexpected mapping for " + FEATURE_CODES.get(i) + ": " + mappings.get(FEATURE_CODES.get(i)));
            }
        }
    }

    private static void stageReferenceRows(Connection connection, List<FeatureRow> rows) throws Exception {
        StringBuilder ddl = new StringBuilder("create temporary table tmp_daily_feature_reference (stat_date varchar(10) primary key");
        for (int i = 6; i <= 22; i++)
            ddl.append(", feature_").append(String.format("%03d", i)).append(" double not null");
        ddl.append(") engine=InnoDB");
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl.toString());
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(18, "?"));
        try (PreparedStatement statement = connection.prepareStatement("insert into tmp_daily_feature_reference values (" + placeholders + ")")) {
            for (FeatureRow row : rows) {
                statement.setString(1, row.date().toString());
                for (int i = 0; i < row.values().length; i++)
                    statement.setDouble(i + 2, row.values()[i]);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("create temporary table tmp_daily_feature_reference_prior like tmp_daily_feature_reference");
            statement.executeUpdate("insert into tmp_daily_feature_reference_prior select * from tmp_daily_feature_reference");
        }
    }

    private static int updateTrainingRows(Connection connection) throws Exception {
        StringBuilder sets = new StringBuilder();
        for (int i = 6; i <= 22; i++) {
            if (sets.length() > 0)
                sets.append(", ");
            String column = String.format("feature_%03d", i);
            sets.append("t.").append(column).append(" = coalesce(current_ref.").append(column).append(", prior_ref.").append(column).append(")");
        }
        sets.append(", t.update_time = now()");
        String sql = "update model_train_feature_data_tb t " + "left join tmp_daily_feature_reference current_ref on current_ref.stat_date = t.stat_date "
                + "left join tmp_daily_feature_reference_prior prior_ref on prior_ref.stat_date = date_format(date_sub(str_to_date(t.stat_date, '%Y-%m-%d'), interval 1 year), '%Y-%m-%d') " + "set "
                + sets + " where t.time_granularity = 'DAY'";
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static void verify(Connection connection, int referenceRows, int updatedRows) throws Exception {
        long targetRows = scalar(connection, "select count(*) from model_train_feature_data_tb where time_granularity = 'DAY'");
        long missingRows = scalar(connection,
                "select count(*) from model_train_feature_data_tb where time_granularity = 'DAY' and (feature_006 is null or feature_007 is null or feature_008 is null or feature_009 is null or feature_010 is null or feature_011 is null or feature_012 is null or feature_013 is null or feature_014 is null or feature_015 is null or feature_016 is null or feature_017 is null or feature_018 is null or feature_019 is null or feature_020 is null or feature_021 is null or feature_022 is null)");
        long tendayFeatures = scalar(connection,
                "select count(*) from model_train_feature_data_tb where feature_001 is not null or feature_002 is not null or feature_003 is not null or feature_004 is not null or feature_005 is not null");
        if (targetRows != updatedRows || missingRows != 0 || tendayFeatures != 0) {
            throw new IllegalStateException("Verification failed: target=" + targetRows + ", updated=" + updatedRows + ", missing=" + missingRows + ", tendayFeatures=" + tendayFeatures);
        }
        System.out.println("referenceRows=" + referenceRows + ", updatedRows=" + updatedRows + ", missingDailyFeatures=" + missingRows + ", populatedTendayFeatures=" + tendayFeatures);
    }

    private static long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
