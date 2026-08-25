package com.xn.report.config.definition;

/**
 * Word 封面元数据绑定配置模型。
 * <p>
 * 声明 Word 模板封面页上的关键字段绑定（标题、编制单位、报告周期、编制人、编制日期）。
 * 支持直接配置静态文本或引用 `${runtime.param}` 运行时变量。
 * </p>
 */
public class WordCoverDefinition {

    /** 封面大标题。 */
    private String title;

    /** 编制单位/组织名称。 */
    private String organization;

    /** 统计报告周期描述（如 "2026年6月"）。 */
    private String reportPeriod;

    /** 报告编制人/责任人。 */
    private String preparedBy;

    /** 报告编制日期（如 "2026年7月23日"）。 */
    private String preparedDate;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getReportPeriod() {
        return reportPeriod;
    }

    public void setReportPeriod(String reportPeriod) {
        this.reportPeriod = reportPeriod;
    }

    public String getPreparedBy() {
        return preparedBy;
    }

    public void setPreparedBy(String preparedBy) {
        this.preparedBy = preparedBy;
    }

    public String getPreparedDate() {
        return preparedDate;
    }

    public void setPreparedDate(String preparedDate) {
        this.preparedDate = preparedDate;
    }
}
