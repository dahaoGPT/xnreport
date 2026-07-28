package com.xn.report.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TextArrayValues {

    private TextArrayValues() {
    }

    static List<Object> copy(Object value) {
        List<Object> values = new ArrayList<Object>();
        if (value instanceof Object[]) {
            Collections.addAll(values, (Object[]) value);
        } else if (value instanceof byte[]) {
            for (byte item : (byte[]) value) {
                values.add(item);
            }
        } else if (value instanceof short[]) {
            for (short item : (short[]) value) {
                values.add(item);
            }
        } else if (value instanceof int[]) {
            for (int item : (int[]) value) {
                values.add(item);
            }
        } else if (value instanceof long[]) {
            for (long item : (long[]) value) {
                values.add(item);
            }
        } else if (value instanceof float[]) {
            for (float item : (float[]) value) {
                values.add(item);
            }
        } else if (value instanceof double[]) {
            for (double item : (double[]) value) {
                values.add(item);
            }
        } else if (value instanceof char[]) {
            for (char item : (char[]) value) {
                values.add(item);
            }
        } else if (value instanceof boolean[]) {
            for (boolean item : (boolean[]) value) {
                values.add(item);
            }
        } else {
            return null;
        }
        return values;
    }
}
