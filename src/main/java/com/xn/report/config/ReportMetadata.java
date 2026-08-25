package com.xn.report.config;

/**
 * 报表基础元数据配置模型。
 * <p>
 * 对应报表主 YAML 配置文件中的 {@code report} 节点，包含报表业务编码（code）、
 * 报表中文名称（name）、Excel 模板与输出文件名、Word 模板与输出文件名，
 * 以及输出文件重名冲突处理策略（collisionPolicy）。
 * </p>
 */
public class ReportMetadata {

    /** 报表唯一业务编码（如 "api-design-efficiency"）。 */
    private String code;

    /** 报表中文展示名称（如 "API设计效能报表"）。 */
    private String name;

    /** Excel 模板文件名（如 "api-design-efficiency.xlsx"）。 */
    private String excelTemplate;

    /** Word 模板文件名（如 "api-design-efficiency.docx"）。 */
    private String wordTemplate;

    /** Excel 输出文件名模板（支持占位符，如 "${reportPeriod}_${reportCode}.xlsx"）。 */
    private String excelFileName;

    /** Word 输出文件名模板（支持占位符，如 "${reportPeriod}_${reportCode}.docx"）。 */
    private String wordFileName;

    /** 输出文件重名冲突解决策略（FAIL, OVERWRITE, VERSIONED，默认为 VERSIONED）。 */
    private String collisionPolicy;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExcelTemplate() {
        return excelTemplate;
    }

    public void setExcelTemplate(String excelTemplate) {
        this.excelTemplate = excelTemplate;
    }

    public String getWordTemplate() {
        return wordTemplate;
    }

    public void setWordTemplate(String wordTemplate) {
        this.wordTemplate = wordTemplate;
    }

    public String getExcelFileName() {
        return excelFileName;
    }

    public void setExcelFileName(String excelFileName) {
        this.excelFileName = excelFileName;
    }

    public String getWordFileName() {
        return wordFileName;
    }

    public void setWordFileName(String wordFileName) {
        this.wordFileName = wordFileName;
    }

    public String getCollisionPolicy() {
        return collisionPolicy;
    }

    public void setCollisionPolicy(String collisionPolicy) {
        this.collisionPolicy = collisionPolicy;
    }
}
