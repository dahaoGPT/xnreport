package com.xn.report.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报表配置校验结果收集器。
 * <p>
 * 在流水线的配置校验阶段（{@code VALIDATE_CONFIG}）收集所有 {@link ValidationIssue} 问题项。
 * 支持判断是否合法、获取全部错误码列表、以及在存在错误时抛出聚合异常。
 * </p>
 */
public final class ValidationResult {

    /** 校验问题列表。 */
    private final List<ValidationIssue> issues = new ArrayList<ValidationIssue>();

    /**
     * 判断配置是否完全有效（无任何校验问题）。
     *
     * @return true 表示配置合法，false 表示存在校验错误
     */
    public boolean isValid() {
        return issues.isEmpty();
    }

    /**
     * 获取不可变的问题列表副本。
     *
     * @return 问题列表
     */
    public List<ValidationIssue> issues() {
        return Collections.unmodifiableList(new ArrayList<ValidationIssue>(issues));
    }

    /**
     * 提取所有问题的错误码列表。
     *
     * @return 错误码字符串列表
     */
    public List<String> codes() {
        List<String> codes = issues.stream()
                .map(ValidationIssue::getCode)
                .collect(Collectors.toList());
        return Collections.unmodifiableList(codes);
    }

    /**
     * 如果配置校验失败，则抛出带有聚合错误详情的 {@link IllegalArgumentException}。
     *
     * @throws IllegalArgumentException 如果 isValid() 为 false
     */
    public void throwIfInvalid() {
        if (!isValid()) {
            String summary = issues.stream()
                    .map(ValidationIssue::toString)
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Invalid report definition: " + summary);
        }
    }

    /**
     * 内部添加一个校验问题。
     *
     * @param code 错误码
     * @param path 配置路径
     * @param message 错误信息
     */
    void add(String code, String path, String message) {
        issues.add(new ValidationIssue(code, path, message));
    }
}
