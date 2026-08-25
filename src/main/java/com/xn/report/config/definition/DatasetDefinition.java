package com.xn.report.config.definition;

import com.xn.report.dataset.DatasetType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集配置定义模型。
 * <p>
 * 声明一个独立的数据集（SQL 驱动型或 Transform 派生型）：
 * <ul>
 *   <li><b>标识与形态</b>：唯一标识（id）、预期结果形态（{@link DatasetType}：SINGLE 单行、LIST 多行列表、SCALAR 标量值）。</li>
 *   <li><b>SQL 数据源</b>：外部 SQL 文件路径（sqlFile）或内联只读 SQL（sql）。</li>
 *   <li><b>参数绑定与依赖</b>：SQL 命名参数绑定（parameters）、前置数据集依赖声明（dependsOn 用于拓扑排序）。</li>
 *   <li><b>Schema 契约与派生转换</b>：预期字段规格（expectedFields）、内存流水线转换（transforms：过滤、排序、去重、截断、计算派生列）。</li>
 *   <li><b>执行治理</b>：SQL 执行超时时间（timeoutSeconds）、最大返回行数限制（maxRows）及私有策略（policies）。</li>
 * </ul>
 * </p>
 */
public class DatasetDefinition {

    /** 数据集唯一标识。 */
    private String id;

    /** 导出到 Excel 时的工作表（Sheet）名称。 */
    private String sheetName;

    /** 外部 SQL 脚本文件路径（相对于 sqlRoot）。 */
    private String sqlFile;

    /** 内嵌 SQL 文本语句。 */
    private String sql;

    /** 预期查询结果形态（SINGLE, LIST, SCALAR）。 */
    private DatasetType resultType;

    /** 当前数据集依赖的前置数据集 ID 列表。 */
    private List<String> dependsOn = new ArrayList<String>();

    /** SQL 命名参数绑定规则映射（key 为 SQL 中的参数名）。 */
    private Map<String, ParameterBindingDefinition> parameters =
            new LinkedHashMap<String, ParameterBindingDefinition>();

    /** 预期字段元数据定义（key 为字段列名）。 */
    private Map<String, FieldDefinition> expectedFields =
            new LinkedHashMap<String, FieldDefinition>();

    /** 内存数据转换流水线配置列表。 */
    private List<TransformDefinition> transforms =
            new ArrayList<TransformDefinition>();

    /** SQL 查询超时秒数。 */
    private Integer timeoutSeconds;

    /** 允许的最大返回行数限制（防止大查询内存溢出）。 */
    private Integer maxRows;

    /** 数据集级别的异常降级策略。 */
    private PolicyDefinition policies = new PolicyDefinition();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getSqlFile() {
        return sqlFile;
    }

    public void setSqlFile(String sqlFile) {
        this.sqlFile = sqlFile;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public DatasetType getResultType() {
        return resultType;
    }

    public void setResultType(DatasetType resultType) {
        this.resultType = resultType;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn == null ? new ArrayList<String>() : dependsOn;
    }

    public Map<String, ParameterBindingDefinition> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, ParameterBindingDefinition> parameters) {
        this.parameters = parameters == null
                ? new LinkedHashMap<String, ParameterBindingDefinition>() : parameters;
    }

    public Map<String, FieldDefinition> getExpectedFields() {
        return expectedFields;
    }

    public void setExpectedFields(Map<String, FieldDefinition> expectedFields) {
        this.expectedFields = expectedFields == null
                ? new LinkedHashMap<String, FieldDefinition>() : expectedFields;
    }

    public List<TransformDefinition> getTransforms() {
        return transforms;
    }

    public void setTransforms(List<TransformDefinition> transforms) {
        this.transforms = transforms == null
                ? new ArrayList<TransformDefinition>() : transforms;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(Integer maxRows) {
        this.maxRows = maxRows;
    }

    public PolicyDefinition getPolicies() {
        return policies;
    }

    public void setPolicies(PolicyDefinition policies) {
        this.policies = policies == null ? new PolicyDefinition() : policies;
    }
}
