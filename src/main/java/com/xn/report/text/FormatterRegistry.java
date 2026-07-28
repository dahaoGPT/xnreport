package com.xn.report.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class FormatterRegistry {

    private final Map<String, ValueFormatter> formatters =
            new LinkedHashMap<String, ValueFormatter>();
    private final Locale locale;
    private final ZoneId zoneId;

    public FormatterRegistry(Locale locale, ZoneId zoneId) {
        this.locale = locale == null ? Locale.ROOT : locale;
        this.zoneId = zoneId == null ? ZoneId.of("UTC") : zoneId;
    }

    public static FormatterRegistry defaults() {
        FormatterRegistry registry =
                new FormatterRegistry(Locale.ROOT, ZoneId.of("UTC"));
        registry.registerDefaults();
        return registry;
    }

    public FormatterRegistry register(String name, ValueFormatter formatter) {
        if (name == null || name.trim().isEmpty() || formatter == null) {
            throw new IllegalArgumentException(
                    "Formatter name and implementation are required");
        }
        if (formatters.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate formatter: " + name);
        }
        formatters.put(name, formatter);
        return this;
    }

    public String format(String name, Object value, String argument) {
        ValueFormatter formatter = formatters.get(name);
        if (formatter == null) {
            throw new TextRenderException("Unknown formatter: " + name);
        }
        try {
            return formatter.format(value, argument);
        } catch (TextRenderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TextRenderException(
                    "Formatter " + name + " failed", exception);
        }
    }

    private void registerDefaults() {
        register("number", new ValueFormatter() {
            @Override
            public String format(Object value, String argument) {
                if (value == null) {
                    return "";
                }
                BigDecimal decimal;
                try {
                    decimal = value instanceof BigDecimal
                            ? (BigDecimal) value
                            : new BigDecimal(String.valueOf(value));
                } catch (NumberFormatException exception) {
                    throw new TextRenderException("Value is not numeric: " + value);
                }
                return decimalFormat(
                        argument == null || argument.isEmpty() ? "0.##########" : argument)
                        .format(decimal);
            }
        });
        register("percent", new ValueFormatter() {
            @Override
            public String format(Object value, String argument) {
                if (value == null) {
                    return "";
                }
                BigDecimal decimal;
                try {
                    decimal = value instanceof BigDecimal
                            ? (BigDecimal) value
                            : new BigDecimal(String.valueOf(value));
                } catch (NumberFormatException exception) {
                    throw new TextRenderException("Value is not numeric: " + value);
                }
                String pattern =
                        argument == null || argument.isEmpty() ? "0.00" : argument;
                return decimalFormat(pattern)
                        .format(decimal.multiply(new BigDecimal("100"))) + "%";
            }
        });
        register("date", temporalFormatter(false));
        register("datetime", temporalFormatter(true));
        register("durationHours", new ValueFormatter() {
            @Override
            public String format(Object value, String argument) {
                BigDecimal hours;
                if (value instanceof Duration) {
                    Duration duration = (Duration) value;
                    hours = BigDecimal.valueOf(duration.getSeconds())
                            .add(BigDecimal.valueOf(duration.getNano(), 9))
                            .divide(new BigDecimal("3600"), 10, RoundingMode.HALF_UP);
                } else {
                    try {
                        hours = new BigDecimal(String.valueOf(value));
                    } catch (RuntimeException exception) {
                        throw new TextRenderException(
                                "Value is not a duration or number: " + value);
                    }
                }
                return decimalFormat(
                        argument == null || argument.isEmpty() ? "0.00" : argument)
                        .format(hours);
            }
        });
        register("default", new ValueFormatter() {
            @Override
            public String format(Object value, String argument) {
                return value == null || String.valueOf(value).isEmpty()
                        ? (argument == null ? "" : argument)
                        : String.valueOf(value);
            }
        });
        register("join", new ValueFormatter() {
            @Override
            public String format(Object value, String argument) {
                if (value == null) {
                    return "";
                }
                String delimiter = argument == null ? "," : argument;
                StringBuilder output = new StringBuilder();
                if (value instanceof Iterable<?>) {
                    Iterator<?> iterator = ((Iterable<?>) value).iterator();
                    while (iterator.hasNext()) {
                        appendJoined(output, iterator.next(), delimiter);
                    }
                    return output.toString();
                }
                Collection<?> array = TextArrayValues.copy(value);
                if (array != null) {
                    for (Object element : array) {
                        appendJoined(output, element, delimiter);
                    }
                    return output.toString();
                }
                throw new TextRenderException("join requires an array or collection");
            }
        });
    }

    private ValueFormatter temporalFormatter(final boolean dateTime) {
        return new ValueFormatter() {
            @Override
            public String format(Object value, String argument) {
                String fallback = dateTime
                        ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd";
                DateTimeFormatter formatter = DateTimeFormatter
                        .ofPattern(argument == null || argument.isEmpty()
                                ? fallback : argument, locale);
                TemporalAccessor temporal = toTemporal(value, dateTime);
                return formatter.format(temporal);
            }
        };
    }

    private TemporalAccessor toTemporal(Object value, boolean dateTime) {
        if (value instanceof Instant) {
            return ((Instant) value).atZone(zoneId);
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(zoneId);
        }
        if (value instanceof LocalDate
                || value instanceof LocalDateTime
                || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime) {
            return (TemporalAccessor) value;
        }
        if (value instanceof CharSequence) {
            String text = value.toString();
            try {
                return dateTime
                        ? LocalDateTime.parse(text)
                        : LocalDate.parse(text);
            } catch (RuntimeException exception) {
                try {
                    return Instant.parse(text).atZone(zoneId);
                } catch (RuntimeException ignored) {
                    throw new TextRenderException("Value is not an ISO date: " + text);
                }
            }
        }
        throw new TextRenderException("Value is not a supported date: " + value);
    }

    private DecimalFormat decimalFormat(String pattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setParseBigDecimal(true);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    private static void appendJoined(
            StringBuilder output, Object value, String delimiter) {
        if (output.length() > 0) {
            output.append(delimiter);
        }
        if (value != null) {
            output.append(value);
        }
    }
}
