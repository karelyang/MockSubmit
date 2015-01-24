package com.vci.convert;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Convert2Check {

    private static final String XLS_NAME = "Template.xls";
    private static final String CHECK_XLS_NAME = "Book1.xls";
    private static final String CHECK_RESULT_XLS_NAME = "检查.xls";
    public static final int PAGE_SIZE = 22;

    public static void main(String[] args) throws IOException {
        POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(XLS_NAME));
        HSSFWorkbook wb = new HSSFWorkbook(fs);
        HSSFSheet sheet = wb.getSheetAt(0);
        int size = sheet.getLastRowNum();

        POIFSFileSystem checkFS = new POIFSFileSystem(new FileInputStream(CHECK_XLS_NAME));
        HSSFWorkbook checkWB = new HSSFWorkbook(checkFS);

        int pageAt = 1/*使用的sheet位置*/, row_num = 2;//每页开始行
        HSSFSheet data_sheet = null;
        for (int i = 0; i < size; i++) {
            HSSFRow row = sheet.getRow(i + 1);
            //排除空行
            if (row.getCell(0) == null || row.getCell(0).getStringCellValue().trim().equals(""))
                continue;

            if (i % PAGE_SIZE == 0) {
                row_num = 2;
                checkWB.cloneSheet(0);
                checkWB.setSheetName(pageAt, "第" + pageAt + "页");
                data_sheet = checkWB.getSheetAt(pageAt++);
            }

            HSSFRow data_row = data_sheet.getRow(row_num);

            data_row.getCell(1).setCellValue(row.getCell(0).getStringCellValue());
            data_row.getCell(2).setCellValue("C1");
            data_row.getCell(3).setCellValue(row.getCell(2).getStringCellValue());
            row_num++;
        }
        checkWB.removeSheetAt(0);

        try (FileOutputStream fileOut = new FileOutputStream(CHECK_RESULT_XLS_NAME)){
            checkWB.write(fileOut);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }
}
