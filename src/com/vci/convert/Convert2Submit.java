package com.vci.convert;

import com.vci.ValidateIDCard;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.*;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Convert2Submit {

    private static final String TXT_NAME = "报名人员.txt";
    private static final String XLS_NAME = "Template.xls";
    private static Set<String> schoolSet = new HashSet<>();
    private static Set<String> idSet = new HashSet<>();

    public static void main(String[] args) throws IOException {
        File file = new File(TXT_NAME);
        LinkedList<String[]> result = new LinkedList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "GBK"))) {
            List<String[]> stringList = reader.lines().parallel()
                    .filter(line -> line.trim().length() > 0)
                    .map(line -> line.trim().toUpperCase()
                            .replace("：", ":")
                            .replace("、", "/").replaceAll("\\s|;|；", "").split(":"))
                    .collect(Collectors.toList());

            String regex = "([\\u4e00-\\u9fa5]+)[\\.|/]*([\\d]*[\\d|X])[\\.|/]*([\\d]*)";
            Pattern pattern = Pattern.compile(regex);

            stringList.parallelStream().forEach(temp -> {
                Matcher m = pattern.matcher(temp[1]);
                while (m.find()) {
                    String[] personLine = new String[4];
                    for (int i = 1; i <= m.groupCount(); i++) {
                        personLine[i - 1] = m.group(i);
                    }
                    personLine[3] = temp[0];
                    result.add(personLine);
                }
            });
            writeXLS(result);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    private static void writeXLS(LinkedList<String[]> result) throws IOException {
        POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(XLS_NAME));
        HSSFWorkbook wb = new HSSFWorkbook(fs);
        HSSFSheet sheet = wb.getSheetAt(0);

        //加载全部的驾校
        getInitData(wb.getSheetAt(0));
        int row_num = 1;
        for (String[] person : result) {
            if (idSet.contains(person[1]))//过滤已经存在于Excel中的数据
                continue;

            HSSFRow row = sheet.getRow(row_num);
            while (row != null && row.getCell(0) != null && !row.getCell(0).getStringCellValue().trim().equals("")) {
                row_num++;
                row = sheet.getRow(row_num);
            }

            if (row == null)
                row = sheet.createRow(row_num);
            //姓名
            row.createCell(0).setCellValue(person[0]);
            //驾校
            row.createCell(1).setCellValue(schoolSet.parallelStream()
                    .filter(schoolName -> schoolName.contains(person[3]))
                    .findFirst().get());
            //身份证号
            ValidateIDCard.isValidIDCardNum(person[0], person[1]);
            row.createCell(2).setCellValue(person[1]);
            //电话号码
            row.createCell(3).setCellValue(person[2]);
            row.createCell(4).setCellValue("EK001:邢台市北环考试点");
        }

        try (FileOutputStream fileOut = new FileOutputStream(XLS_NAME)) {
            wb.write(fileOut);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    private static void getInitData(HSSFSheet sheet) {
        int size = sheet.getLastRowNum();
        HSSFRow row;
        for (int i = 0; i < size; i++) {
            row = sheet.getRow(i);
            if (row.getCell(2) != null && !row.getCell(2).getStringCellValue().equals(""))
                idSet.add(row.getCell(2).getStringCellValue());
            if (row.getCell(25) != null && !row.getCell(25).getStringCellValue().equals(""))
                schoolSet.add(row.getCell(25).getStringCellValue());
        }
    }

}
