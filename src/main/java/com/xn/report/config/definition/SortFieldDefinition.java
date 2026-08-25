package com.xn.report.config.definition;

import com.xn.report.transform.Direction;
import com.xn.report.transform.NullOrder;

/**
 * 排序字段规格定义模型。
 * <p>
 * 声明排序字段名（field）、排序方向（{@link Direction}：ASC, DESC）以及空值排序策略（{@link NullOrder}：FIRST, LAST）。
 * </p>
 */
public class SortFieldDefinition {

    /** 排序字段名称。 */
    private String field;

    /** 排序方向（ASC 升序, DESC 降序）。 */
    private Direction direction;

    /** NULL 值排在前或后（FIRST 排在最前, LAST 排在最后）。 */
    private NullOrder nullOrder;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public NullOrder getNullOrder() {
        return nullOrder;
    }

    public void setNullOrder(NullOrder nullOrder) {
        this.nullOrder = nullOrder;
    }
}
