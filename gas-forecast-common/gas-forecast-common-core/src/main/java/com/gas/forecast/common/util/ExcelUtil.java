package com.gas.forecast.common.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.CellData;
import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.util.CollectionUtils;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.excel.write.style.column.AbstractColumnWidthStyleStrategy;
import com.alibaba.excel.write.style.row.AbstractRowHeightStyleStrategy;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.dto.ExcelData;
import com.gas.forecast.common.core.dto.ExcelHead;
import com.gas.forecast.common.core.dto.ExcelSelected;
import com.gas.forecast.common.core.dto.ExcelSelectedResolve;
import com.gas.forecast.common.core.dto.SelectedSheetWriteHandler;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ZouJun
 * @version V1.0
 * @Description: TODO
 * @Date: 2021/3/25 10:06
 * @ClassName: ExcelUtil
 */
@Slf4j
public class ExcelUtil {
    public static void main(String[] args) {
        String filePath = "D:/a.xlsx";
        List<ExcelHead> headList = new ArrayList();
        headList.add(new ExcelHead<String>("name", "名称"));
        headList.add(new ExcelHead("age", "年龄", -1));
        WriteSheet sheet = new WriteSheet();
        List<Map<String, Object>> dataList = new ArrayList();
        Map<String, Object> one = new HashMap();
        one.put("name", "张三 ");
        one.put("age", 20);
        dataList.add(one);

        Map<String, Object> two = new HashMap();
        two.put("name", "李四 ");
        two.put("age", 18);
        dataList.add(two);

        Map<String, Object> there = new HashMap();
        there.put("name", "不知道年龄");
        there.put("age", null);
        dataList.add(there);
        // write(filePath, headList, dataList);
        List<List<Object>> result = readExcel("C:\\Users\\zou'jun\\Downloads\\收获模板2025523132219.xlsx");
        result.stream().forEach(System.out::println);


    }


    public static List<List<Object>> readExcel(String filePath) {
        List<List<Object>> resulList = new ArrayList<List<Object>>();
        Map<Integer, Object> head = new HashMap<>();
        EasyExcel.read(filePath, new AnalysisEventListener<Map<Integer, Object>>() {
            @Override
            public void invoke(Map<Integer, Object> excelMap, AnalysisContext analysisContext) {
                List<Object> dataList = new ArrayList<>();
                log.info("读取当前行数据为{}", JSONUtil.toJsonStr(excelMap));
                if (excelMap.size() > head.size()) {
                    head.clear();
                    head.putAll(excelMap);
                }
                head.forEach((index, value) -> {
                    dataList.add(excelMap.get(index) == null ? "null" : excelMap.get(index));
                });
                resulList.add(dataList);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext analysisContext) {
                log.info("读取完毕");
            }

            @Override
            public void invokeHeadMap(Map headMap, AnalysisContext context) {
                log.info("读取表头{}", JSONUtil.toJsonStr(headMap));
                List<Object> headList = new ArrayList<>();
                headMap.forEach((index, value) -> {
                    headList.add(value);
                    head.put(Integer.parseInt(index.toString()), value);
                });

                resulList.add(headList);
            }
        }).sheet().doRead();
        return resulList;

    }


    public static ExcelData readExcelToExcelData(String filePath) {
        ExcelData excelData = new ExcelData();
        Map<Integer, Object> head = new HashMap<>();
        EasyExcel.read(filePath, new AnalysisEventListener<Map<Integer, Object>>() {
            @Override
            public void invoke(Map<Integer, Object> excelMap, AnalysisContext analysisContext) {
                List<ExcelData.Cell> lineCell = new ArrayList<ExcelData.Cell>();
                log.info("读取当前行数据为{}", JSONUtil.toJsonStr(excelMap));
                head.forEach((index, value) -> {
                    ExcelData.Cell cell = new ExcelData.Cell(null, value.toString(), excelMap.get(index));
                    lineCell.add(cell);
                });
                excelData.getCellList().add(lineCell);

            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext analysisContext) {
                log.info("读取完毕");
            }

            @Override
            public void invokeHeadMap(Map headMap, AnalysisContext context) {
                log.info("读取表头{}", JSONUtil.toJsonStr(headMap));
                List<Object> headList = new ArrayList<>();
                headMap.forEach((index, value) -> {
                    head.put(Integer.parseInt(index.toString()), value);
                });
            }
        }).sheet().doRead();
        return excelData;

    }

    public static List<Map<String, Object>> readExcel(String filePath, List<ExcelHead> excelHeadList) {
        if (CollectionUtil.isEmpty(excelHeadList)) {
            throw new BusinessException("excel表头配置缺失");
        }
        List<Map<String, Object>> mapList = new ArrayList<>();
        EasyExcel.read(filePath, new AnalysisEventListener<Map<Integer, Object>>() {
            @Override
            public void invoke(Map<Integer, Object> excelMap, AnalysisContext analysisContext) {
                Map<String, Object> resultMap = new HashMap<>();
                Map<Integer, String> excelFieldMap = excelHeadList.stream().collect(Collectors.toMap(ExcelHead::getPosition, ExcelHead::getFieldName));
                log.info("读取当前行数据为{}", JSONUtil.toJsonStr(excelMap));
                excelMap.forEach((key, value) -> {
                    resultMap.put(excelFieldMap.get(key), value);
                });
                mapList.add(resultMap);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext analysisContext) {
                log.info("读取完毕");
            }

            @Override
            public void invokeHeadMap(Map headMap, AnalysisContext context) {
                log.info("读取表头{}", JSONUtil.toJsonStr(headMap));
                Map<String, Integer> headerMap = reversalHead(headMap);

                for (ExcelHead excelHead : excelHeadList) {
                    Integer position = headerMap.get(excelHead.getTitle());
                    if (position == null) {
                        throw new BusinessException("excel中缺失数据：" + excelHead.getTitle());
                    }
                    excelHead.setPosition(position);
                }

            }
        }).sheet().doRead();
        return mapList;

    }

    public static <T> List<T> readExcel(String absoluteFile, Class clazz) {
        List<T> resultList = new ArrayList<T>();
        EasyExcel.read(absoluteFile, clazz, new AnalysisEventListener<T>() {
            //读取除表头外的每行数据执行该方法
            @Override
            public void invoke(T data, AnalysisContext context) {
                log.info("读取当前行数据为{}", JSONUtil.toJsonStr(data));
                resultList.add(data);
            }

            //获取表头数据
            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                log.info("读取表头{}", JSONUtil.toJsonStr(headMap));

            }

            //读取结束执行该方法
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                log.info("读取完毕");

            }
        }).sheet().doRead();
        return resultList;
    }

    public static <T> void writeExcel(String absoluteFile, String sheetName, List<T> list, Class<T> clazz) {

        File file = new File(absoluteFile);
        if (file.exists()) {
            log.info("{}文件删除", absoluteFile);
            file.delete();
        }
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        log.info("absoluteFile:{}, t.getClass():{}", absoluteFile, clazz);
        log.info("sheetName:{}", sheetName);
        log.info("list:{}", list);

        EasyExcel.write(absoluteFile, clazz).sheet(TextUtils.hasText(sheetName) ? sheetName : "sheet1").head(clazz)
                .registerWriteHandler(getHorizontalCellStyleStrategy())
                .registerWriteHandler(new CustomCellWriteHeightConfig())
                .registerWriteHandler(new SelectedSheetWriteHandler(resolveSelectedAnnotation(clazz)))
                .registerWriteHandler(new CustomCellWriteWidthConfig()).doWrite(list);
        log.info("{}文件生成完毕", absoluteFile);
    }

    public static <T> void writeExcel(String fileName, String sheetName, List<T> list, Class<T> clazz, HttpServletResponse response) {
        ServletOutputStream out = null;
        try {
            out = response.getOutputStream();
            //设置文件类型
            response.setContentType("application/vnd.ms-excel");
            //设置编码格式
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8") + ".xlsx");
            //创建excel
            EasyExcel.write(response.getOutputStream(), clazz).sheet(TextUtils.hasText(sheetName) ? sheetName : "sheet1")
                    .head(clazz)
                    .registerWriteHandler(getHorizontalCellStyleStrategy())
                    .registerWriteHandler(new CustomCellWriteHeightConfig())
                    .registerWriteHandler(new SelectedSheetWriteHandler(resolveSelectedAnnotation(clazz)))
                    .registerWriteHandler(new CustomCellWriteWidthConfig()).doWrite(list);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static <T> void fillExcel(String filePath, String excelTemplatePath, List<T> list, Class<T> clazz) {
        File file = new File(excelTemplatePath);
        if (!file.exists()) {
            throw new RuntimeException("模板不存在");
        }
        log.info("excelTemplatePath:{}, t.getClass():{}", excelTemplatePath, clazz);
        log.info("list:{}", list);
        EasyExcel.write(filePath, clazz).withTemplate(file).sheet().doFill(list);

    }

    public static <T> void fillExcel(String excelTemplatePath, List<T> list, Class<T> clazz, HttpServletResponse response) {

        File file = new File(excelTemplatePath);
        if (!file.exists()) {
            throw new RuntimeException("模板不存在");
        }
        log.info("excelTemplatePath:{}, t.getClass():{}", excelTemplatePath, clazz);
        log.info("list:{}", list);
        ServletOutputStream out = null;
        try {
            out = response.getOutputStream();
            //设置文件类型
            response.setContentType("application/vnd.ms-excel");
            //设置编码格式
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(file.getName(), "UTF-8"));
            EasyExcel.write(out, clazz).withTemplate(file).sheet().doFill(list);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * 写excel
     */
    public static void write(HttpServletResponse response, List<ExcelHead> headList, List<Map<String, Object>> dataList, String fileName) {
        ServletOutputStream out = null;
        try {
            out = response.getOutputStream();
            //设置文件类型
            response.setContentType("application/vnd.ms-excel");
            //设置编码格式
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            ExcelWriterBuilder writerBuilder = EasyExcel.write();
            writerBuilder.registerWriteHandler(getHorizontalCellStyleStrategy())
                    .registerWriteHandler(new CustomCellWriteHeightConfig())
                    .registerWriteHandler(new CustomCellWriteWidthConfig())
                    .excelType(ExcelTypeEnum.XLSX);
            writerBuilder.autoCloseStream(true);
            writerBuilder.file(out);
            writerBuilder.head(convertHead(headList)).sheet("sheet1")
                    .doWrite(convertData(headList, dataList));
        } catch (IOException e) {
            throw new BusinessException("文件生成异常");
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * 写excel
     *
     * @param filePath 保存的路径名
     * @param headList
     * @param dataList
     */
    public static void write(String filePath, List<ExcelHead> headList, List<Map<String, Object>> dataList) {
        ExcelWriterBuilder writerBuilder = EasyExcel.write();
        writerBuilder.file(filePath);
        writerBuilder.excelType(ExcelTypeEnum.XLSX);
        writerBuilder.autoCloseStream(true);

        writerBuilder.registerWriteHandler(getHorizontalCellStyleStrategy())
                .registerWriteHandler(new CustomCellWriteHeightConfig())
                .registerWriteHandler(new CustomCellWriteWidthConfig())
                .head(convertHead(headList)).sheet("sheet1")
                .doWrite(convertData(headList, dataList));
    }


    private static Map<String, Integer> reversalHead(Map headMap) {
        Map<String, Integer> map = new HashMap<>();
        for (Object key : headMap.keySet()) {
            if (headMap.get(key) != null) {
                map.put(headMap.get(key).toString(), Integer.valueOf(key.toString()));
            }
        }
        return map;
    }


    private static List<List<String>> convertHead(List<ExcelHead> headList) {
        List<List<String>> list = new ArrayList<>();
        for (ExcelHead head : headList) {
            list.add(Arrays.asList(head.getTitle()));
        }
        //沒有搞清楚head的参数为List<List<String>>,用List<String>就OK了
        return list;
    }


    /**
     * @param headList
     * @param dataList key为head里的fieldName
     * @return
     */
    private static List<List<Object>> convertData(List<ExcelHead> headList, List<Map<String, Object>> dataList) {
        List<List<Object>> result = new ArrayList();
        //对dataList转为easyExcel的数据格式
        for (Map<String, Object> data : dataList) {
            List<Object> row = new ArrayList();
            for (ExcelHead h : headList) {
                Object o = data.get(h.getFieldName());
                //需要对null的处理，比如age的null，要转为-1
                row.add(handler(o, h.getNullValue()));
            }
            result.add(row);
        }
        return result;
    }

    /**
     * null值处理
     *
     * @param o
     * @param nullValue
     * @return
     */
    private static Object handler(Object o, Object nullValue) {
        return o != null ? o : nullValue;
    }


    private static HorizontalCellStyleStrategy getHorizontalCellStyleStrategy() {

        // 创建写出Excel的字体对象
        WriteFont contentWriteFont = new WriteFont();
        contentWriteFont.setFontHeightInPoints((short) 12);
        contentWriteFont.setFontName("宋体");


        // 创建一个写出的单元格样式对象
        WriteCellStyle contentWriteCellStyle = new WriteCellStyle();
        // 这里需要指定 FillPatternType 为FillPatternType.SOLID_FOREGROUND
        // 不然无法显示背景颜色.头默认了 FillPatternType所以可以不指定
        contentWriteCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        contentWriteCellStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        contentWriteCellStyle.setBorderTop(BorderStyle.THIN);
        contentWriteCellStyle.setBorderBottom(BorderStyle.THIN);
        contentWriteCellStyle.setBorderLeft(BorderStyle.THIN);
        contentWriteCellStyle.setBorderRight(BorderStyle.THIN);
        contentWriteCellStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        contentWriteCellStyle.setWriteFont(contentWriteFont);


        WriteCellStyle headWriteCellStyle = new WriteCellStyle();
        headWriteCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        headWriteCellStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
        headWriteCellStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headWriteCellStyle.setWriteFont(contentWriteFont);
        HorizontalCellStyleStrategy horizontalCellStyleStrategy =
                new HorizontalCellStyleStrategy(headWriteCellStyle, contentWriteCellStyle);
        return horizontalCellStyleStrategy;
    }

    private static class CustomCellWriteWidthConfig extends AbstractColumnWidthStyleStrategy {
        private final Map<Integer, Map<Integer, Integer>> CACHE = new HashMap<>();

        /**
         * 计算长度
         *
         * @param list
         * @param cell
         * @param isHead
         * @return
         */
        private Integer dataLength(List<CellData> list, Cell cell, Boolean isHead) {
            if (isHead) {
                return cell.getStringCellValue().getBytes().length;
            } else {
                CellData<?> cellData = list.get(0);
                CellDataTypeEnum type = cellData.getType();
                if (type == null) {
                    return -1;
                } else {
                    switch (type) {
                        case STRING:
                            // 换行符（数据需要提前解析好）
                            int index = cellData.getStringValue().indexOf("\n");
                            return index != -1 ?
                                    cellData.getStringValue().substring(0, index).getBytes().length + 1 : cellData.getStringValue().getBytes().length + 1;
                        case BOOLEAN:
                            return cellData.getBooleanValue().toString().getBytes().length;
                        case NUMBER:
                            return cellData.getNumberValue().toString().getBytes().length;
                        default:
                            return -1;
                    }
                }
            }
        }

        @Override
        protected void setColumnWidth(WriteSheetHolder writeSheetHolder, List<CellData> list, Cell cell, Head head, Integer integer, Boolean isHead) {
            boolean needSetWidth = isHead || !CollectionUtils.isEmpty(list);
            if (needSetWidth) {
                Map<Integer, Integer> maxColumnWidthMap = CACHE.computeIfAbsent(writeSheetHolder.getSheetNo(), k -> new HashMap<>());

                Integer columnWidth = this.dataLength(list, cell, isHead);
                // 单元格文本长度大于60换行
                if (columnWidth >= 0) {
                    if (columnWidth > 60) {
                        columnWidth = 60;
                    }
                    Integer maxColumnWidth = maxColumnWidthMap.get(cell.getColumnIndex());
                    if (maxColumnWidth == null || columnWidth > maxColumnWidth) {
                        maxColumnWidthMap.put(cell.getColumnIndex(), columnWidth);
                        Sheet sheet = writeSheetHolder.getSheet();
                        sheet.setColumnWidth(cell.getColumnIndex(), columnWidth * 256);
                    }
                }
            }
        }
    }


    private static class CustomCellWriteHeightConfig extends AbstractRowHeightStyleStrategy {
        /**
         * 默认高度
         */
        private static final Integer DEFAULT_HEIGHT = 300;

        @Override
        protected void setHeadColumnHeight(Row row, int relativeRowIndex) {
        }

        @Override
        protected void setContentColumnHeight(Row row, int relativeRowIndex) {
            Iterator<Cell> cellIterator = row.cellIterator();
            if (!cellIterator.hasNext()) {
                return;
            }
            // 默认为 1行高度
            int maxHeight = 1;
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                if (cell.getCellTypeEnum() == CellType.STRING) {
                    String value = cell.getStringCellValue();
                    int len = value.length();
                    int num = 0;
                    if (len > 50) {
                        num = len % 50 > 0 ? len / 50 : len / 2 - 1;
                    }
                    if (num > 0) {
                        for (int i = 0; i < num; i++) {
                            value = value.substring(0, (i + 1) * 50 + i) + "\n" + value.substring((i + 1) * 50 + i, len + i);
                        }
                    }
                    if (value.contains("\n")) {
                        int length = value.split("\n").length;
                        maxHeight = Math.max(maxHeight, length) + 1;
                    }
                }
            }
            row.setHeight((short) ((maxHeight) * DEFAULT_HEIGHT));
        }
    }



    /**
     * 解析表头类中的下拉注解
     *
     * @param head 表头类
     * @param <T>  泛型
     * @return Map<下拉框列索引, 下拉框内容> map
     */
    public static <T> Map<Integer, ExcelSelectedResolve> resolveSelectedAnnotation(Class<T> head) {
        Map<Integer, ExcelSelectedResolve> selectedMap = new HashMap<>();
        List<Field> fields = getExcelFields(head);
        int columnIndex = 0;
        for (Field field : fields) {
            if (field.getAnnotation(ExcelIgnore.class) != null) {
                continue;
            }
            ExcelProperty property = field.getAnnotation(ExcelProperty.class);
            if (property == null) {
                continue;
            }
            // 解析注解信息
            ExcelSelected selected = field.getAnnotation(ExcelSelected.class);
            if (selected != null) {
                ExcelSelectedResolve excelSelectedResolve = new ExcelSelectedResolve();
                excelSelectedResolve.setColumnName(String.join("-", property.value()));
                // 处理下拉框内容
                String[] source = excelSelectedResolve.resolveSelectedSource(selected);
                if (source != null && source.length > 0) {
                    excelSelectedResolve.setSource(source);
                    excelSelectedResolve.setFirstRow(selected.firstRow());
                    excelSelectedResolve.setLastRow(selected.lastRow());
                    if (property.index() >= 0) {
                        selectedMap.put(property.index(), excelSelectedResolve);
                    } else {
                        selectedMap.put(columnIndex, excelSelectedResolve);
                    }
                }
            }
            columnIndex++;
        }
        return selectedMap;
    }

    private static List<Field> getExcelFields(Class<?> head) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = head;
        while (currentClass != null && currentClass != Object.class) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        return fields;
    }


}
