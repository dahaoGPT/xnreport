package com.xn.report.execution;

import java.nio.file.Path;

/**
 * 生成产物文件有效性校验函数式接口。
 */
@FunctionalInterface
public interface GeneratedOutputValidator {

    /**
     * 校验生成的 Excel 与 Word 文件有效性。
     *
     * @param excel Excel 产物文件路径
     * @param word Word 产物文件路径
     */
    void validate(Path excel, Path word);
}
