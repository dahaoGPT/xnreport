package com.xn.report.word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Word 生成产物质检预期领域模型。
 * <p>
 * 封装在生成前依据报表定义与数据计算得出的预期特征（期望封面字段、TOC 最大层级、DFS 标题层级与文字序列、表格数量与特征文本、附录结构、插入图片总数等），供 {@link WordOutputValidator} 执行严格比对。
 * </p>
 */
public final class WordOutputExpectation {

    private final List<String> coverValues;
    private final int tocMaxLevel;
    private final boolean requireUpdateFields;
    private final List<Heading> headings;
    private final List<Table> tables;
    private final List<Attachment> attachments;
    private final int pictureInstances;

    private WordOutputExpectation(Builder builder) {
        this.coverValues = immutable(builder.coverValues);
        this.tocMaxLevel = builder.tocMaxLevel;
        this.requireUpdateFields = builder.requireUpdateFields;
        this.headings = Collections.unmodifiableList(
                new ArrayList<Heading>(builder.headings));
        this.tables = Collections.unmodifiableList(
                new ArrayList<Table>(builder.tables));
        this.attachments = Collections.unmodifiableList(
                new ArrayList<Attachment>(builder.attachments));
        this.pictureInstances = builder.pictureInstances;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> getCoverValues() {
        return coverValues;
    }

    public int getTocMaxLevel() {
        return tocMaxLevel;
    }

    public boolean isRequireUpdateFields() {
        return requireUpdateFields;
    }

    public List<Heading> getHeadings() {
        return headings;
    }

    public List<Table> getTables() {
        return tables;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public int getPictureInstances() {
        return pictureInstances;
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(
                new ArrayList<String>(values));
    }

    /**
     * 预期标题项。
     */
    public static final class Heading {
        private final int level;
        private final String text;

        private Heading(int level, String text) {
            this.level = level;
            this.text = text;
        }

        public int getLevel() {
            return level;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 预期表格项。
     */
    public static final class Table {
        private final int index;
        private final int rowCount;
        private final List<String> expectedValues;

        private Table(
                int index, int rowCount, List<String> expectedValues) {
            this.index = index;
            this.rowCount = rowCount;
            this.expectedValues = immutable(expectedValues);
        }

        public int getIndex() {
            return index;
        }

        public int getRowCount() {
            return rowCount;
        }

        public List<String> getExpectedValues() {
            return expectedValues;
        }
    }

    /**
     * 预期附录项。
     */
    public static final class Attachment {
        private final String title;
        private final String description;
        private final List<String> items;

        private Attachment(
                String title, String description, List<String> items) {
            this.title = title;
            this.description = description;
            this.items = immutable(items);
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getItems() {
            return items;
        }
    }

    /**
     * 期望模型建造者。
     */
    public static final class Builder {
        private final List<String> coverValues =
                new ArrayList<String>();
        private int tocMaxLevel = 3;
        private boolean requireUpdateFields = true;
        private final List<Heading> headings =
                new ArrayList<Heading>();
        private final List<Table> tables = new ArrayList<Table>();
        private final List<Attachment> attachments =
                new ArrayList<Attachment>();
        private int pictureInstances;

        public Builder cover(
                String title,
                String organization,
                String reportPeriod,
                String preparedBy,
                String preparedDate) {
            coverValues.clear();
            coverValues.add(required(title, "cover title"));
            coverValues.add(required(organization, "cover organization"));
            coverValues.add(required(reportPeriod, "cover reportPeriod"));
            coverValues.add(required(preparedBy, "cover preparedBy"));
            coverValues.add(required(preparedDate, "cover preparedDate"));
            return this;
        }

        public Builder tocMaxLevel(int value) {
            if (value < 1 || value > 4) {
                throw new IllegalArgumentException(
                        "TOC max level must be between 1 and 4");
            }
            this.tocMaxLevel = value;
            return this;
        }

        public Builder requireUpdateFields(boolean value) {
            this.requireUpdateFields = value;
            return this;
        }

        public Builder heading(int level, String text) {
            if (level < 1 || level > 4) {
                throw new IllegalArgumentException(
                        "Heading level must be between 1 and 4");
            }
            headings.add(new Heading(level, required(text, "heading text")));
            return this;
        }

        public Builder table(
                int index,
                int rowCount,
                List<String> expectedValues) {
            if (index < 0 || rowCount < 0 || expectedValues == null) {
                throw new IllegalArgumentException(
                        "Table index, row count, and values are required");
            }
            tables.add(new Table(index, rowCount, expectedValues));
            return this;
        }

        public Builder tablePresence(
                int index, List<String> expectedValues) {
            if (index < 0 || expectedValues == null) {
                throw new IllegalArgumentException(
                        "Table index and values are required");
            }
            tables.add(new Table(index, -1, expectedValues));
            return this;
        }

        public Builder attachmentSequence(List<String> values) {
            if (values == null || values.size() < 2) {
                throw new IllegalArgumentException(
                        "Attachment sequence requires title and description");
            }
            return attachment(values.get(0), values.get(1),
                    values.subList(2, values.size()));
        }

        public Builder attachment(
                String title,
                String description,
                List<String> items) {
            List<String> safeItems = items == null
                    ? Collections.<String>emptyList() : items;
            String safeTitle = optional(title);
            String safeDescription = optional(description);
            List<String> checked = new ArrayList<String>();
            for (String item : safeItems) {
                checked.add(required(item, "attachment item"));
            }
            if (safeTitle == null && safeDescription == null
                    && checked.isEmpty()) {
                throw new IllegalArgumentException(
                        "Attachment structure must not be empty");
            }
            attachments.add(new Attachment(
                    safeTitle, safeDescription, checked));
            return this;
        }

        public Builder pictureInstances(int value) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        "Picture instances must not be negative");
            }
            this.pictureInstances = value;
            return this;
        }

        public WordOutputExpectation build() {
            return new WordOutputExpectation(this);
        }

        private static String required(String value, String name) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value;
        }

        private static String optional(String value) {
            return value == null || value.trim().isEmpty() ? null : value;
        }
    }
}
