package com.xn.report.config.definition;

import com.xn.report.policy.EmptyDataPolicy;
import com.xn.report.policy.MissingFieldPolicy;
import com.xn.report.policy.NullValuePolicy;
import com.xn.report.policy.TypeMismatchPolicy;

/**
 * 策略配置定义模型。
 * <p>
 * 声明在全局、报表、数据集、规则或组件层级生效的容错与降级策略：
 * <ul>
 *   <li>空数据策略（emptyData）与提示文本（emptyMessage）</li>
 *   <li>缺失字段策略（missingField：FAIL, USE_DEFAULT, WARN_AND_SKIP）</li>
 *   <li>类型不匹配策略（typeMismatch：FAIL, SAFE_CONVERT, WARN_AND_SKIP）</li>
 *   <li>空值（NULL）处理策略（nullValue：RULE_NOT_MATCHED, USE_DEFAULT, ALLOW, FAIL）</li>
 *   <li>空集合参数处理策略（emptyCollectionParameter：FAIL 或 EMPTY_IN）</li>
 *   <li>未解析占位符策略（unresolvedPlaceholder：FAIL, BLANK, KEEP）</li>
 * </ul>
 * </p>
 */
public class PolicyDefinition {

    /** 空数据处理策略。 */
    private EmptyDataPolicy emptyData;

    /** 空数据时展示的自定义提示文案。 */
    private String emptyMessage;

    /** 字段缺失处理策略。 */
    private MissingFieldPolicy missingField;

    /** 字段类型不匹配处理策略。 */
    private TypeMismatchPolicy typeMismatch;

    /** 字段值为 NULL 处理策略。 */
    private NullValuePolicy nullValue;

    /** SQL 集合入参为空时的处理策略（FAIL 或 EMPTY_IN）。 */
    private String emptyCollectionParameter;

    /** 文本占位符未解析时的处理策略（FAIL, BLANK, KEEP）。 */
    private String unresolvedPlaceholder;

    public EmptyDataPolicy getEmptyData() {
        return emptyData;
    }

    public void setEmptyData(EmptyDataPolicy emptyData) {
        this.emptyData = emptyData;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public void setEmptyMessage(String emptyMessage) {
        this.emptyMessage = emptyMessage;
    }

    public MissingFieldPolicy getMissingField() {
        return missingField;
    }

    public void setMissingField(MissingFieldPolicy missingField) {
        this.missingField = missingField;
    }

    public TypeMismatchPolicy getTypeMismatch() {
        return typeMismatch;
    }

    public void setTypeMismatch(TypeMismatchPolicy typeMismatch) {
        this.typeMismatch = typeMismatch;
    }

    public NullValuePolicy getNullValue() {
        return nullValue;
    }

    public void setNullValue(NullValuePolicy nullValue) {
        this.nullValue = nullValue;
    }

    public String getEmptyCollectionParameter() {
        return emptyCollectionParameter;
    }

    public void setEmptyCollectionParameter(String emptyCollectionParameter) {
        this.emptyCollectionParameter = emptyCollectionParameter;
    }

    public String getUnresolvedPlaceholder() {
        return unresolvedPlaceholder;
    }

    public void setUnresolvedPlaceholder(String unresolvedPlaceholder) {
        this.unresolvedPlaceholder = unresolvedPlaceholder;
    }

    /**
     * 获取系统全局兜底默认策略。
     *
     * @return 系统默认策略实例
     */
    public static PolicyDefinition systemDefaults() {
        PolicyDefinition definition = new PolicyDefinition();
        definition.setEmptyData(EmptyDataPolicy.OUTPUT_MESSAGE);
        definition.setMissingField(MissingFieldPolicy.FAIL);
        definition.setTypeMismatch(TypeMismatchPolicy.FAIL);
        definition.setNullValue(NullValuePolicy.RULE_NOT_MATCHED);
        definition.setEmptyCollectionParameter("FAIL");
        definition.setUnresolvedPlaceholder("FAIL");
        return definition;
    }

    public static PolicyDefinition component(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    public static PolicyDefinition rule(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    public static PolicyDefinition dataset(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    public static PolicyDefinition report(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    public static PolicyDefinition global(EmptyDataPolicy policy) {
        return withEmptyData(policy);
    }

    private static PolicyDefinition withEmptyData(EmptyDataPolicy policy) {
        PolicyDefinition definition = new PolicyDefinition();
        definition.setEmptyData(policy);
        return definition;
    }
}
