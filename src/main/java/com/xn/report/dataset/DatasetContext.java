package com.xn.report.dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DatasetContext {

    private final Map<String, DatasetResult> results;
    private final List<String> ids;

    private DatasetContext(Map<String, DatasetResult> source) {
        LinkedHashMap<String, DatasetResult> copy =
                new LinkedHashMap<String, DatasetResult>(source);
        this.results = Collections.unmodifiableMap(copy);
        this.ids = Collections.unmodifiableList(
                new ArrayList<String>(copy.keySet()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public DatasetResult get(String id) {
        DatasetResult result = results.get(id);
        if (result == null) {
            throw new IllegalArgumentException("Missing dataset: " + id);
        }
        return result;
    }

    public boolean contains(String id) {
        return results.containsKey(id);
    }

    public List<String> ids() {
        return ids;
    }

    public Map<String, DatasetResult> asMap() {
        return results;
    }

    public static final class Builder {

        private final Map<String, DatasetResult> results =
                new LinkedHashMap<String, DatasetResult>();

        public Builder put(DatasetResult result) {
            if (result == null) {
                throw new IllegalArgumentException("Dataset result must not be null");
            }
            if (results.containsKey(result.id())) {
                throw new IllegalArgumentException(
                        "Duplicate dataset id: " + result.id());
            }
            results.put(result.id(), result);
            return this;
        }

        public DatasetContext buildView() {
            return new DatasetContext(results);
        }

        public DatasetContext build() {
            return new DatasetContext(results);
        }
    }
}
