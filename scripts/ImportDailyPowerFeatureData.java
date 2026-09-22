import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ImportDailyPowerFeatureData {
    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "mysql2026";
    private static final Path DEFAULT_FILE = Path.of("/Users/zoujun/Documents/江苏发电/发电/test.xlsx");
    private static final String REGION_NAME = "江苏";
    private static final String INDUSTRY_NAME = "发电";
    private static final String TIME_GRANULARITY = "DAY";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ImportRow {
        private String statDate;

        private BigDecimal gasSales;

        private Map<String, BigDecimal> features;

        public String statDate() {
            return statDate;
        }

        public BigDecimal gasSales() {
            return gasSales;
        }

        public Map<String, BigDecimal> features() {
            return features;
        }
    }

    public static void main(String[] args) throws Exception {
        Path file = args.length > 0 ? Path.of(args[0]) : DEFAULT_FILE;
        List<ImportRow> rows = readWorkbook(file);
        if (rows.isEmpty()) {
            throw new IllegalStateException("No data rows found in " + file);
        }

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            connection.setAutoCommit(false);
            Set<String> featureColumns = columns(connection, "model_feature_definition_tb");
            Set<String> trainColumns = columns(connection, "model_train_feature_data_tb");
            String regionCode = findOrCreateRegion(connection, REGION_NAME);
            String industryCode = findOrCreateIndustry(connection, INDUSTRY_NAME);
            Map<String, String> featureSlotByCode = ensureFeatureDefinitions(
                    connection, featureColumns, rows.get(0).features().keySet());

            int inserted = 0;
            int updated = 0;
            for (ImportRow row : rows) {
                if (existsTrainRow(connection, row, regionCode, industryCode)) {
                    updateTrainRow(connection, trainColumns, featureSlotByCode, row, regionCode, industryCode);
                    updated++;
                } else {
                    insertTrainRow(connection, trainColumns, featureSlotByCode, row, regionCode, industryCode);
                    inserted++;
                }
            }
            connection.commit();
            System.out.println("file=" + file);
            System.out.println("region=" + REGION_NAME + "(" + regionCode + "), industry=" + INDUSTRY_NAME + "("
                    + industryCode + ")");
            System.out.println("rows=" + rows.size() + ", inserted=" + inserted + ", updated=" + updated);
            printImportedRange(connection, regionCode, industryCode);
        }
    }

    private static Map<String, String> ensureFeatureDefinitions(
            Connection connection, Set<String> tableColumns, Set<String> featureCodes) throws Exception {
        Set<String> usedSlots = new HashSet<>();
        Map<String, String> slotByCode = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "select feature_code, feature_column, time_granularity from model_feature_definition_tb")) {
            while (rs.next()) {
                String column = rs.getString("feature_column");
                if (column != null && !column.isBlank()) {
                    usedSlots.add(column);
                }
                if (TIME_GRANULARITY.equals(rs.getString("time_granularity"))) {
                    slotByCode.put(rs.getString("feature_code"), column);
                }
            }
        }

        for (String featureCode : featureCodes) {
            String slot = slotByCode.get(featureCode);
            if (slot == null || slot.isBlank()) {
                slot = nextFeatureSlot(usedSlots);
                insertFeatureDefinition(connection, tableColumns, featureCode, slot);
                usedSlots.add(slot);
            } else {
                updateFeatureDefinition(connection, tableColumns, featureCode, slot);
            }
            slotByCode.put(featureCode, slot);
        }
        return slotByCode;
    }

    private static void insertFeatureDefinition(
            Connection connection, Set<String> tableColumns, String featureCode, String slot) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", nextId(connection, "model_feature_definition_tb"));
        values.put("feature_code", featureCode);
        values.put("feature_name", featureName(featureCode));
        values.put("feature_column", slot);
        values.put("time_granularity", TIME_GRANULARITY);
        values.put("enabled", 1);
        values.put("description", "江苏发电日粒度导入特征");
        values.put("created_at", RawSql.NOW);
        values.put("created_by", 0L);
        values.put("updated_by_name", "系统");
        insertByColumns(connection, "model_feature_definition_tb", tableColumns, values);
    }

    private static void updateFeatureDefinition(
            Connection connection, Set<String> tableColumns, String featureCode, String slot) throws Exception {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        setIfColumn(tableColumns, sets, params, "feature_name", featureName(featureCode));
        setIfColumn(tableColumns, sets, params, "feature_column", slot);
        setIfColumn(tableColumns, sets, params, "enabled", 1);
        if (sets.isEmpty()) {
            return;
        }
        params.add(featureCode);
        params.add(TIME_GRANULARITY);
        try (PreparedStatement ps = connection.prepareStatement("update model_feature_definition_tb set "
                + String.join(", ", sets) + " where feature_code = ? and time_granularity = ?")) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static String featureName(String featureCode) {
        return switch (featureCode) {
            case "tempmax" -> "最高气温";
            case "tempmin" -> "最低气温";
            case "temp" -> "平均气温";
            case "feelslikemax" -> "最高体感温度";
            case "feelslikemin" -> "最低体感温度";
            case "feelslike" -> "平均体感温度";
            case "dew" -> "露点温度";
            case "humidity" -> "湿度";
            case "precip" -> "降水量";
            case "windgust" -> "阵风风速";
            case "windspeed" -> "风速";
            case "sealevelpressure" -> "海平面气压";
            case "cloudcover" -> "云量";
            case "visibility" -> "能见度";
            case "solarradiation" -> "太阳辐射";
            case "solarenergy" -> "太阳能量";
            case "uvindex" -> "紫外线指数";
            default -> featureCode;
        };
    }

    private static String nextFeatureSlot(Set<String> usedSlots) {
        for (int i = 1; i <= 200; i++) {
            String slot = String.format("feature_%03d", i);
            if (!usedSlots.contains(slot)) {
                return slot;
            }
        }
        throw new IllegalStateException("No free feature slot in feature_001..feature_200");
    }

    private static boolean existsTrainRow(Connection connection, ImportRow row, String regionCode, String industryCode)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select id from model_train_feature_data_tb
                where region_code = ? and industry_code = ? and stat_date = ? and time_granularity = ?
                limit 1
                """)) {
            ps.setString(1, regionCode);
            ps.setString(2, industryCode);
            ps.setString(3, row.statDate());
            ps.setString(4, TIME_GRANULARITY);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void insertTrainRow(
            Connection connection,
            Set<String> tableColumns,
            Map<String, String> featureSlotByCode,
            ImportRow row,
            String regionCode,
            String industryCode)
            throws Exception {
        Map<String, Object> values = trainValues(featureSlotByCode, row, regionCode, industryCode);
        values.put("id", nextId(connection, "model_train_feature_data_tb"));
        insertByColumns(connection, "model_train_feature_data_tb", tableColumns, values);
    }

    private static void updateTrainRow(
            Connection connection,
            Set<String> tableColumns,
            Map<String, String> featureSlotByCode,
            ImportRow row,
            String regionCode,
            String industryCode)
            throws Exception {
        Map<String, Object> values = trainValues(featureSlotByCode, row, regionCode, industryCode);
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String column = entry.getKey();
            if (!tableColumns.contains(column)
                    || column.equals("region_code")
                    || column.equals("industry_code")
                    || column.equals("stat_date")
                    || column.equals("time_granularity")
                    || column.equals("create_time")) {
                continue;
            }
            sets.add(column + " = " + (entry.getValue() == RawSql.NOW ? "now()" : "?"));
            if (entry.getValue() != RawSql.NOW) {
                params.add(entry.getValue());
            }
        }
        params.add(regionCode);
        params.add(industryCode);
        params.add(row.statDate());
        params.add(TIME_GRANULARITY);
        try (PreparedStatement ps =
                connection.prepareStatement("update model_train_feature_data_tb set " + String.join(", ", sets)
                        + " where region_code = ? and industry_code = ? and stat_date = ? and time_granularity = ?")) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static Map<String, Object> trainValues(
            Map<String, String> featureSlotByCode, ImportRow row, String regionCode, String industryCode) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("stat_date", row.statDate());
        values.put("time_granularity", TIME_GRANULARITY);
        values.put("region_code", regionCode);
        values.put("region_name", REGION_NAME);
        values.put("industry_code", industryCode);
        values.put("industry_name", INDUSTRY_NAME);
        values.put("gas_sales", row.gasSales());
        values.put("create_time", RawSql.NOW);
        values.put("update_time", RawSql.NOW);
        values.put("created_at", RawSql.NOW);
        values.put("updated_at", RawSql.NOW);
        for (Map.Entry<String, BigDecimal> feature : row.features().entrySet()) {
            values.put(featureSlotByCode.get(feature.getKey()), feature.getValue());
        }
        return values;
    }

    private static List<ImportRow> readWorkbook(Path file) throws Exception {
        try (ZipFile zipFile = new ZipFile(file.toFile())) {
            List<String> sharedStrings = readSharedStrings(zipFile);
            String sheetPath = firstSheetPath(zipFile);
            Document sheet = parse(zipFile.getInputStream(zipFile.getEntry(sheetPath)));
            NodeList rowNodes =
                    sheet.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "row");
            List<String> headers = null;
            List<ImportRow> rows = new ArrayList<>();
            for (int i = 0; i < rowNodes.getLength(); i++) {
                Element rowElement = (Element) rowNodes.item(i);
                List<String> cells = readRow(rowElement, sharedStrings);
                if (headers == null) {
                    headers = cells;
                    continue;
                }
                if (cells.isEmpty() || blank(cells, headers, "日期")) {
                    continue;
                }
                Map<String, String> byHeader = new HashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    byHeader.put(headers.get(c), c < cells.size() ? cells.get(c) : "");
                }
                Map<String, BigDecimal> features = new LinkedHashMap<>();
                for (String header : headers) {
                    if ("日期".equals(header) || "y".equals(header)) {
                        continue;
                    }
                    features.put(header, decimal(byHeader.get(header)));
                }
                rows.add(new ImportRow(excelDate(byHeader.get("日期")), decimal(byHeader.get("y")), features));
            }
            return rows;
        }
    }

    private static List<String> readSharedStrings(ZipFile zipFile) throws Exception {
        ZipEntry entry = zipFile.getEntry("xl/sharedStrings.xml");
        if (entry == null) {
            return List.of();
        }
        Document document = parse(zipFile.getInputStream(entry));
        NodeList items =
                document.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "si");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            NodeList texts =
                    item.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "t");
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < texts.getLength(); j++) {
                text.append(texts.item(j).getTextContent());
            }
            values.add(text.toString());
        }
        return values;
    }

    private static String firstSheetPath(ZipFile zipFile) throws Exception {
        Document workbook = parse(zipFile.getInputStream(zipFile.getEntry("xl/workbook.xml")));
        Document rels = parse(zipFile.getInputStream(zipFile.getEntry("xl/_rels/workbook.xml.rels")));
        Element firstSheet = (Element)
                workbook.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "sheet")
                        .item(0);
        String relId =
                firstSheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
        NodeList relationships = rels.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
        for (int i = 0; i < relationships.getLength(); i++) {
            Element relationship = (Element) relationships.item(i);
            if (relId.equals(relationship.getAttribute("Id"))) {
                String target = relationship.getAttribute("Target").replaceFirst("^/", "");
                return target.startsWith("xl/") ? target : "xl/" + target;
            }
        }
        throw new IllegalStateException("No worksheet relationship found");
    }

    private static List<String> readRow(Element rowElement, List<String> sharedStrings) {
        NodeList cells =
                rowElement.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "c");
        List<String> values = new ArrayList<>();
        int lastIndex = -1;
        for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            int index = columnIndex(cell.getAttribute("r"));
            while (lastIndex + 1 < index) {
                values.add("");
                lastIndex++;
            }
            values.add(cellValue(cell, sharedStrings));
            lastIndex = index;
        }
        return values;
    }

    private static String cellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            return cell.getTextContent();
        }
        NodeList values = cell.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "v");
        if (values.getLength() == 0) {
            return "";
        }
        String raw = values.item(0).getTextContent();
        if ("s".equals(type)) {
            int index = Integer.parseInt(raw);
            return index < sharedStrings.size() ? sharedStrings.get(index) : raw;
        }
        return raw;
    }

    private static int columnIndex(String reference) {
        int value = 0;
        for (int i = 0; i < reference.length(); i++) {
            char ch = reference.charAt(i);
            if (!Character.isLetter(ch)) {
                break;
            }
            value = value * 26 + Character.toUpperCase(ch) - 'A' + 1;
        }
        return value - 1;
    }

    private static Document parse(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(inputStream);
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private static String excelDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("date is blank");
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d+(\\.0+)?")) {
            long serial = new BigDecimal(trimmed).longValue();
            return LocalDate.of(1899, 12, 30).plusDays(serial).format(DATE_FORMATTER);
        }
        return LocalDate.parse(trimmed.substring(0, 10)).format(DATE_FORMATTER);
    }

    private static boolean blank(List<String> cells, List<String> headers, String column) {
        int index = headers.indexOf(column);
        return index < 0
                || index >= cells.size()
                || cells.get(index) == null
                || cells.get(index).isBlank();
    }

    private static String findOrCreateRegion(Connection connection, String regionName) throws Exception {
        String code = findCode(connection, "base_region_tb", "region_name", "region_code", regionName);
        if (code != null) {
            return code;
        }
        code = nextCode(connection, "base_region_tb", "region_code", "REG");
        Set<String> tableColumns = columns(connection, "base_region_tb");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", nextId(connection, "base_region_tb"));
        values.put("region_code", code);
        values.put("region_name", regionName);
        values.put("remark", "江苏发电日粒度导入自动创建");
        values.put("region_type", "PROVINCE");
        values.put("status", 1);
        values.put("enabled", 1);
        values.put("deleted", 0);
        values.put("sort_no", 0);
        values.put("created_by", "system");
        values.put("created_by_name", "系统");
        values.put("updated_by", "system");
        values.put("updated_by_name", "系统");
        values.put("created_at", RawSql.NOW);
        values.put("updated_at", RawSql.NOW);
        insertByColumns(connection, "base_region_tb", tableColumns, values);
        return code;
    }

    private static String findOrCreateIndustry(Connection connection, String industryName) throws Exception {
        String code = findCode(connection, "base_industry_tb", "industry_name", "industry_code", industryName);
        if (code != null) {
            return code;
        }
        code = nextCode(connection, "base_industry_tb", "industry_code", "IND");
        Set<String> tableColumns = columns(connection, "base_industry_tb");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", nextId(connection, "base_industry_tb"));
        values.put("industry_code", code);
        values.put("industry_name", industryName);
        values.put("created_at", RawSql.NOW);
        values.put("updated_at", RawSql.NOW);
        values.put("created_by", 0L);
        values.put("created_by_name", "系统");
        values.put("updated_by", 0L);
        values.put("updated_by_name", "系统");
        insertByColumns(connection, "base_industry_tb", tableColumns, values);
        return code;
    }

    private static String findCode(
            Connection connection, String table, String nameColumn, String codeColumn, String name) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select " + codeColumn + " from " + table + " where " + nameColumn + " = ? order by id limit 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private static String nextCode(Connection connection, String table, String codeColumn, String prefix)
            throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("select coalesce(max(cast(substr(" + codeColumn + ", "
                        + (prefix.length() + 1) + ") as unsigned)), 0) + 1 from " + table + " where " + codeColumn
                        + " regexp '^" + prefix + "[0-9]+$'")) {
            rs.next();
            return prefix + String.format("%06d", rs.getLong(1));
        }
    }

    private static long nextId(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("select coalesce(max(id), 0) + 1 from " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Set<String> columns(Connection connection, String table) throws Exception {
        Set<String> columns = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                select column_name from information_schema.columns
                where table_schema = database() and table_name = ?
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("Table not found or has no columns: " + table);
        }
        return columns;
    }

    private static void insertByColumns(
            Connection connection, String table, Set<String> tableColumns, Map<String, Object> values)
            throws Exception {
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!tableColumns.contains(entry.getKey())) {
                continue;
            }
            columns.add(entry.getKey());
            if (entry.getValue() == RawSql.NOW) {
                placeholders.add("now()");
            } else {
                placeholders.add("?");
                params.add(entry.getValue());
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("insert into " + table + " ("
                + String.join(", ", columns) + ") values (" + String.join(", ", placeholders) + ")")) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static void setIfColumn(
            Set<String> tableColumns, List<String> sets, List<Object> params, String column, Object value) {
        if (tableColumns.contains(column)) {
            sets.add(column + " = ?");
            params.add(value);
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws Exception {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static void printImportedRange(Connection connection, String regionCode, String industryCode)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select count(*) rows_count, min(stat_date) min_date, max(stat_date) max_date
                from model_train_feature_data_tb
                where region_code = ? and industry_code = ? and time_granularity = ?
                """)) {
            ps.setString(1, regionCode);
            ps.setString(2, industryCode);
            ps.setString(3, TIME_GRANULARITY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("importedRange rows=" + rs.getLong("rows_count") + ", minDate="
                            + rs.getString("min_date") + ", maxDate=" + rs.getString("max_date"));
                }
            }
        }
    }

    private enum RawSql {
        NOW
    }
}
