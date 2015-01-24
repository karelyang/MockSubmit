package com.vci.http;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

public class RWExcelFile {

    //缓存Excel中读取的数据
    public static final Map<Integer, String[]> cache_data = Collections.synchronizedMap(new HashMap<>());
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String FILE_NAME = "Template.xls";
    private static final String BAK_FILE_NAME = "Template_.xls";
    static DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

    public static Map<Integer, String[]> readFile() throws IOException {
        try (FileInputStream fis = new FileInputStream(FILE_NAME)) {
            HSSFWorkbook workbook = new HSSFWorkbook(fis);
            HSSFSheet sheet = workbook.getSheetAt(0);

            StreamSupport.stream(sheet.spliterator(), true)
                    .filter(row -> row.getRowNum() != 0 && row.getCell(0) != null && !row.getCell(0).getStringCellValue().equals(""))
                    .forEach(row -> {
                        String[] person = new String[6];

                        person[0] = getStringCellValue(row.getCell(0));
                        person[1] = getStringCellValue(row.getCell(1));
                        person[2] = getStringCellValue(row.getCell(2));
                        person[3] = getStringCellValue(row.getCell(3));
                        person[4] = getStringCellValue(row.getCell(4));
                        person[5] = getStringCellValue(row.getCell(5));

                        cache_data.put(row.getRowNum(), person);
                    });

        }
        return cache_data;
    }

    public static void writeResult(Map<Integer, String> results, int col) throws IOException {
        try (FileInputStream fis = new FileInputStream(FILE_NAME);
             FileOutputStream out = new FileOutputStream(BAK_FILE_NAME)) {

            HSSFWorkbook workbook = new HSSFWorkbook(fis);
            HSSFSheet sheet = workbook.getSheetAt(0);

            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setColor(HSSFColor.RED.index);
            style.setFont(font);
            style.setLocked(false);

            results.forEach((lineNum, String) -> {
                HSSFCell cell = sheet.getRow(lineNum).createCell(col);
                cell.setCellValue(String);
                cell.setCellStyle(style);
            });

            workbook.write(out);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //输入输出流不关闭时，不能正常重命名
            File file = new File(FILE_NAME);
            file.delete();

            File newFile = new File(BAK_FILE_NAME);
            newFile.renameTo(file);
        }
    }

    private static String getStringCellValue(Cell cell) {
        String strCell;
        switch (cell.getCellType()) {
            case HSSFCell.CELL_TYPE_STRING:
                strCell = cell.getRichStringCellValue().getString();
                break;
            case HSSFCell.CELL_TYPE_NUMERIC:
                if (HSSFDateUtil.isCellDateFormatted(cell))
                    strCell = dateFormat.format((HSSFDateUtil.getJavaDate(cell
                            .getNumericCellValue())));
                else
                    strCell = cell.getNumericCellValue() + "";
                break;
            case HSSFCell.CELL_TYPE_BOOLEAN:
                strCell = String.valueOf(cell.getBooleanCellValue());
                break;
            default:
                strCell = "";
                break;
        }
        return strCell.trim();
    }
}
