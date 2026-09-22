import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
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

public class ImportProcessedFeatureData {
    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "mysql2026";
    private static final Path DEFAULT_DIR = Path.of("/Users/zoujun/Documents/冬供_副本/江苏河北冬供旬预测智能体/data/processed_data1");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FeatureSpec {
        private String sourceColumn;

        private String featureCode;

        private String featureName;

        private String description;

        public String sourceColumn() {
            return sourceColumn;
        }

        public String featureCode() {
            return featureCode;
        }

        public String featureName() {
            return featureName;
        }

        public String description() {
            return description;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ImportRow {
        private String province;

        private String statDate;

        private BigDecimal gasSales;

        private Map<String, BigDecimal> features;

        public String province() {
            return province;
        }

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

    private static final List<FeatureSpec> FEATURES = List.of(
            new FeatureSpec("avg_temp", "avg_temp", "平均气温", "旬平均气温"),
            new FeatureSpec("max_temp", "max_temp", "最高气温", "旬最高气温"),
            new FeatureSpec("min_temp", "min_temp", "最低气温", "旬最低气温"),
            new FeatureSpec("HDD", "hdd", "采暖度日", "Heating Degree Days"),
            new FeatureSpec("extreme_cold_days", "extreme_cold_days", "极寒天数", "旬极寒天数"));

    public static void main(String[] args) throws Exception {
        Path dataDir = args.length > 0 ? Path.of(args[0]) : DEFAULT_DIR;
        List<Path> files;
        try (var stream = Files.list(dataDir)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".xlsx"))
                    .filter(path -> !path.getFileName().toString().startsWith(".~"))
                    .sorted()
                    .toList();
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("No xlsx files found in " + dataDir);
        }

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            connection.setAutoCommit(false);
            Set<String> regionColumns = columns(connection, "base_region_tb");
            Set<String> featureColumns = columns(connection, "model_feature_definition_tb");
            Set<String> trainColumns = columns(connection, "model_train_feature_data_tb");

            Map<String, String> featureSlotByCode = ensureFeatureDefinitions(connection, featureColumns);
            int inserted = 0;
            int updated = 0;
            int regionsCreated = 0;
            for (Path file : files) {
                String province = file.getFileName().toString().replaceFirst("\\.xlsx$", "");
                String regionCode = findRegionCode(connection, province);
                if (regionCode == null) {
                    regionCode = createRegion(connection, regionColumns, province);
                    regionsCreated++;
                }
                List<ImportRow> rows = readWorkbook(file, province);
                for (ImportRow row : rows) {
                    if (existsTrainRow(connection, row, regionCode)) {
                        updateTrainRow(connection, trainColumns, featureSlotByCode, row, regionCode);
                        updated++;
                    } else {
                        insertTrainRow(connection, trainColumns, featureSlotByCode, row, regionCode);
                        inserted++;
                    }
                }
                System.out.println(file.getFileName() + ": " + rows.size() + " rows");
            }
            connection.commit();
            System.out.println("regionsCreated=" + regionsCreated + ", inserted=" + inserted + ", updated=" + updated);
            printFeatureDefinitions(connection);
            printTrainCount(connection);
        }
    }

    private static Map<String, String> ensureFeatureDefinitions(Connection connection, Set<String> tableColumns)
            throws Exception {
        Set<String> usedSlots = new HashSet<>();
        Map<String, String> slotByCode = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "select feature_code, feature_column from model_feature_definition_tb")) {
            while (rs.next()) {
                String code = rs.getString("feature_code");
                String column = rs.getString("feature_column");
                if (column != null && !column.isBlank()) {
                    usedSlots.add(column);
                }
                if (code != null && column != null) {
                    slotByCode.put(code, column);
                }
            }
        }

        for (FeatureSpec feature : FEATURES) {
            String slot = slotByCode.get(feature.featureCode());
            if (slot == null || slot.isBlank()) {
                slot = nextFeatureSlot(usedSlots);
                insertFeatureDefinition(connection, tableColumns, feature, slot);
                usedSlots.add(slot);
            } else {
                updateFeatureDefinition(connection, tableColumns, feature, slot);
            }
            slotByCode.put(feature.featureCode(), slot);
        }
        return slotByCode;
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

    private static void insertFeatureDefinition(
            Connection connection, Set<String> tableColumns, FeatureSpec feature, String slot) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", nextId(connection, "model_feature_definition_tb"));
        values.put("feature_code", feature.featureCode());
        values.put("feature_name", feature.featureName());
        values.put("feature_column", slot);
        values.put("time_granularity", "TENDAY");
        values.put("enabled", 1);
        values.put("description", feature.description());
        values.put("created_at", RawSql.NOW);
        values.put("created_by", 0L);
        values.put("updated_by_name", "系统");
        insertByColumns(connection, "model_feature_definition_tb", tableColumns, values);
    }

    private static void updateFeatureDefinition(
            Connection connection, Set<String> tableColumns, FeatureSpec feature, String slot) throws Exception {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        setIfColumn(tableColumns, sets, params, "feature_name", feature.featureName());
        setIfColumn(tableColumns, sets, params, "feature_column", slot);
        setIfColumn(tableColumns, sets, params, "time_granularity", "TENDAY");
        setIfColumn(tableColumns, sets, params, "enabled", 1);
        setIfColumn(tableColumns, sets, params, "description", feature.description());
        if (sets.isEmpty()) {
            return;
        }
        params.add(feature.featureCode());
        try (PreparedStatement ps = connection.prepareStatement(
                "update model_feature_definition_tb set " + String.join(", ", sets) + " where feature_code = ?")) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static String findRegionCode(Connection connection, String province) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select region_code
                from base_region_tb
                where region_name = ?
                order by case when region_code regexp '^REG[0-9]{6}$' then 0 else 1 end, id
                limit 1
                """)) {
            ps.setString(1, province);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static String createRegion(Connection connection, Set<String> tableColumns, String province)
            throws Exception {
        String code = nextRegionCode(connection);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", nextId(connection, "base_region_tb"));
        values.put("region_code", code);
        values.put("region_name", province);
        values.put("remark", "processed_data1导入省份");
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

    private static String nextRegionCode(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "select coalesce(max(cast(substr(region_code, 4) as unsigned)), 0) from base_region_tb where region_code regexp '^REG[0-9]+$'")) {
            rs.next();
            long next = rs.getLong(1) + 1;
            return "REG" + String.format("%06d", next);
        }
    }

    private static boolean existsTrainRow(Connection connection, ImportRow row, String regionCode) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select id from model_train_feature_data_tb
                where region_code = ? and stat_date = ? and time_granularity = 'TENDAY'
                limit 1
                """)) {
            ps.setString(1, regionCode);
            ps.setString(2, row.statDate());
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
            String regionCode)
            throws Exception {
        Map<String, Object> values = trainValues(featureSlotByCode, row, regionCode);
        values.put("id", nextId(connection, "model_train_feature_data_tb"));
        insertByColumns(connection, "model_train_feature_data_tb", tableColumns, values);
    }

    private static void updateTrainRow(
            Connection connection,
            Set<String> tableColumns,
            Map<String, String> featureSlotByCode,
            ImportRow row,
            String regionCode)
            throws Exception {
        Map<String, Object> values = trainValues(featureSlotByCode, row, regionCode);
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String column = entry.getKey();
            if (!tableColumns.contains(column)
                    || column.equals("region_code")
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
        params.add(row.statDate());
        try (PreparedStatement ps =
                connection.prepareStatement("update model_train_feature_data_tb set " + String.join(", ", sets)
                        + " where region_code = ? and stat_date = ? and time_granularity = 'TENDAY'")) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static Map<String, Object> trainValues(
            Map<String, String> featureSlotByCode, ImportRow row, String regionCode) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("stat_date", row.statDate());
        values.put("time_granularity", "TENDAY");
        values.put("region_code", regionCode);
        values.put("region_name", row.province());
        values.put("gas_sales", row.gasSales());
        values.put("create_time", RawSql.NOW);
        values.put("update_time", RawSql.NOW);
        values.put("created_at", RawSql.NOW);
        values.put("updated_at", RawSql.NOW);
        for (FeatureSpec feature : FEATURES) {
            values.put(
                    featureSlotByCode.get(feature.featureCode()), row.features().get(feature.sourceColumn()));
        }
        return values;
    }

    private static List<ImportRow> readWorkbook(Path file, String province) throws Exception {
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
                if (cells.isEmpty() || blank(cells, headers, "date")) {
                    continue;
                }
                Map<String, String> byHeader = new HashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    byHeader.put(headers.get(c), c < cells.size() ? cells.get(c) : "");
                }
                Map<String, BigDecimal> features = new LinkedHashMap<>();
                for (FeatureSpec feature : FEATURES) {
                    features.put(feature.sourceColumn(), decimal(byHeader.get(feature.sourceColumn())));
                }
                rows.add(new ImportRow(
                        province, excelDate(byHeader.get("date")), decimal(byHeader.get("gas_sales")), features));
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

    private static long nextId(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("select coalesce(max(id), 0) + 1 from " + table)) {
            rs.next();
            return rs.getLong(1);
        }
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

    private static void printFeatureDefinitions(Connection connection) throws Exception {
        System.out.println("FEATURE DEFINITIONS");
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "select feature_code, feature_name, feature_column from model_feature_definition_tb where feature_code in ('avg_temp','max_temp','min_temp','hdd','extreme_cold_days') order by feature_column")) {
            while (rs.next()) {
                System.out.println(rs.getString("feature_code") + "\t" + rs.getString("feature_name") + "\t"
                        + rs.getString("feature_column"));
            }
        }
    }

    private static void printTrainCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "select region_name, count(*) rows_count, min(stat_date) min_date, max(stat_date) max_date from model_train_feature_data_tb where time_granularity = 'TENDAY' group by region_name order by region_name")) {
            System.out.println("TRAIN FEATURE DATA");
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                System.out.println(rs.getString("region_name") + "\t" + rs.getLong("rows_count") + "\t"
                        + rs.getString("min_date") + "\t" + rs.getString("max_date"));
            }
        }
    }

    private enum RawSql {
        NOW
    }
}
