package com.xn.report.config;

public class ReportMetadata {

    private String code;
    private String name;
    private String excelTemplate;
    private String wordTemplate;
    private String excelFileName;
    private String wordFileName;
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
