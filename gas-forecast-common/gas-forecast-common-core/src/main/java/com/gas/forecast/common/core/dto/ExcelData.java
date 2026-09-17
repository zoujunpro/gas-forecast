package com.gas.forecast.common.core.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExcelData {

    private List<List<Cell>> cellList = new ArrayList<>();

    @Data
    public static class Cell {

        private String eName;

        private String cName;

        private Object value;

        public Cell(String eName, String cName, Object value) {
            this.eName = eName;
            this.cName = cName;
            this.value = value;
        }
    }
}
