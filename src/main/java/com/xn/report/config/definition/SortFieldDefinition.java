package com.xn.report.config.definition;

import com.xn.report.transform.Direction;
import com.xn.report.transform.NullOrder;

public class SortFieldDefinition {

    private String field;
    private Direction direction;
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
