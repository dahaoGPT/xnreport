package com.xn.report.excel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel 模板文件加载器。
 * <p>
 * 从磁盘路径安全读取模板文件流并初始化构造 {@link XSSFWorkbook} 对象。
 * </p>
 */
public final class ExcelTemplateLoader {

    /**
     * 读取并加载 Excel 模板工作簿。
     *
     * @param template 模板文件绝对路径
     * @return XSSFWorkbook 实例
     * @throws IOException 若 IO 读取失败
     */
    public XSSFWorkbook load(Path template) throws IOException {
        if (template == null || !Files.isRegularFile(template)) {
            throw new IllegalArgumentException(
                    "Excel template must be a readable file: " + template);
        }
        try (InputStream stream = Files.newInputStream(template)) {
            return new XSSFWorkbook(stream);
        }
    }
}
