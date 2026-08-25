package com.xn.report.output;

import com.xn.report.error.ReportErrorCode;
import com.xn.report.error.ReportException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 报表输出文件名渲染与安全净化器。
 * <p>
 * 支持将文件名模板中的 `${param}` 占位符替换为运行时参数，并执行严格的文件系统安全合规检查：
 * <ul>
 *   <li>禁止路径遍历（..）与路径分隔符（/、\）。</li>
 *   <li>过滤 Windows 系统非法字符（{@code <>:"|?*} 与控制字符）。</li>
 *   <li>过滤尾部空格与句点，规避 Windows 保留设备名称（CON、PRN、AUX、NUL、COM1-9、LPT1-9）。</li>
 *   <li>限制文件名最大长度（默认 180 字符），防止超出文件系统限制。</li>
 *   <li>强制校验并保留正确的后缀名（{@code .xlsx} 或 {@code .docx}）。</li>
 * </ul>
 * </p>
 */
public final class OutputNameRenderer {

    /** 占位符正则：匹配 ${varName}。 */
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    /** Windows 非法文件名字符正则。 */
    private static final Pattern WINDOWS_ILLEGAL =
            Pattern.compile("[<>:\"|?*\\p{Cntrl}]");

    /** 文件名末尾的空格或句点正则。 */
    private static final Pattern TRAILING_SPACE_OR_DOT =
            Pattern.compile("[ .]+$");

    /** Windows 预留设备名称正则。 */
    private static final Pattern WINDOWS_RESERVED =
            Pattern.compile(
                    "(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\.|$)");

    /** 允许的最大文件名长度。 */
    private final int maxLength;

    /**
     * 使用默认最大长度（180）构造渲染器。
     */
    public OutputNameRenderer() {
        this(180);
    }

    /**
     * 指定最大长度构造渲染器。
     *
     * @param maxLength 允许的最大文件名长度（至少为 10）
     */
    public OutputNameRenderer(int maxLength) {
        if (maxLength < 10) {
            throw new IllegalArgumentException("maxLength must be at least 10");
        }
        this.maxLength = maxLength;
    }

    /**
     * 根据参数渲染并净化输出文件名。
     *
     * @param template 文件名模板（如 "${reportPeriod}_${reportCode}.xlsx"）
     * @param values 运行时参数 Map
     * @return 安全合规的最终文件名
     * @throws ReportException 如果占位符未解析、路径非法或文件名为空
     */
    public String render(String template, Map<String, ?> values) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(values, "values");
        rejectPathSyntax(template);

        // 替换占位符
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!values.containsKey(key)) {
                throw invalid("unresolved output name placeholder: " + key);
            }
            Object value = values.get(key);
            matcher.appendReplacement(
                    rendered,
                    Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(rendered);

        String value = rendered.toString();
        if (value.contains("${")) {
            throw invalid("unresolved output name placeholder remains after rendering");
        }
        rejectPathSyntax(value);

        // 校验扩展名
        String extension = extension(value);
        if (!".xlsx".equalsIgnoreCase(extension)
                && !".docx".equalsIgnoreCase(extension)) {
            throw invalid("output file extension must be .xlsx or .docx");
        }

        // 净化基本名称
        String base = value.substring(0, value.length() - extension.length());
        base = WINDOWS_ILLEGAL.matcher(base).replaceAll("_");
        base = TRAILING_SPACE_OR_DOT.matcher(base).replaceAll("");
        if (base.isEmpty()) {
            throw invalid("output file name is empty after sanitization");
        }

        // 避开 Windows 保留设备名称
        if (WINDOWS_RESERVED.matcher(base).find()) {
            base = "_" + base;
        }

        // 截断超长名称
        int allowedBaseLength = maxLength - extension.length();
        if (base.length() > allowedBaseLength) {
            base = base.substring(0, allowedBaseLength);
            base = TRAILING_SPACE_OR_DOT.matcher(base).replaceAll("");
        }
        if (base.isEmpty()) {
            throw invalid("output file name is empty after truncation and sanitization");
        }
        return base + extension.toLowerCase();
    }

    /** 检查是否包含路径穿越或路径分隔符。 */
    private static void rejectPathSyntax(String value) {
        if (value.contains("..")
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0) {
            throw invalid("output path traversal or directory separator is not allowed");
        }
    }

    /** 提取文件扩展名。 */
    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private static ReportException invalid(String message) {
        return new ReportException(ReportErrorCode.OUT_001, message);
    }
}
