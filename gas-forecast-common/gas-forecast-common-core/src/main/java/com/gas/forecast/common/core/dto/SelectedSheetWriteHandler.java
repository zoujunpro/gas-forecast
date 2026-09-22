package com.gas.forecast.common.core.dto;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import java.util.Map;
import lombok.Data;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;

@Data
public class SelectedSheetWriteHandler implements SheetWriteHandler {

    private final Map<Integer, ExcelSelectedResolve> selectedMap;

    private final int columnSelectMaxLength = 255;

    @Override
    public void beforeSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {}

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        Sheet sheet = writeSheetHolder.getSheet();
        DataValidationHelper helper = sheet.getDataValidationHelper();
        selectedMap.forEach((columnIndex, selected) -> {
            String[] source = selected.getSource();
            String selectColumnName = selected.getColumnName();
            if (String.join("", source).length() > columnSelectMaxLength) {
                Workbook workbook = writeWorkbookHolder.getWorkbook();
                String sheetName = selectColumnName + columnIndex;
                Sheet tmpSheet = workbook.createSheet(sheetName);
                for (int i = 0, length = source.length; i < length; i++) {
                    tmpSheet.createRow(i).createCell(0).setCellValue(source[i]);
                }
                Name categoryName = workbook.createName();
                categoryName.setNameName(sheetName);
                categoryName.setRefersToFormula(sheetName + "!$A$1:$A$" + source.length);
                CellRangeAddressList addressList = new CellRangeAddressList(
                        selected.getFirstRow(), selected.getLastRow(), columnIndex, columnIndex);
                DataValidationConstraint constraint = helper.createFormulaListConstraint(sheetName);
                DataValidation validation = helper.createValidation(constraint, addressList);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.setShowErrorBox(true);
                validation.setSuppressDropDownArrow(true);
                validation.createErrorBox("提示", "请输入下拉选项中的内容");
                sheet.addValidationData(validation);
            } else {
                CellRangeAddressList rangeList = new CellRangeAddressList(
                        selected.getFirstRow(), selected.getLastRow(), columnIndex, columnIndex);
                DataValidationConstraint constraint = helper.createExplicitListConstraint(source);
                DataValidation validation = helper.createValidation(constraint, rangeList);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.setShowErrorBox(true);
                validation.setSuppressDropDownArrow(true);
                validation.createErrorBox("提示", "请输入下拉选项中的内容");
                sheet.addValidationData(validation);
            }
        });
    }
}
