package com.xn.report.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.PolicyDefinition;
import com.xn.report.config.definition.WordCoverDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WordCoverValueResolverTest {

    private final WordCoverValueResolver resolver =
            new WordCoverValueResolver();

    @Test
    void resolvesRuntimePlaceholdersWithoutMutatingSharedDefinition() {
        WordCoverDefinition source = cover(
                "研发效能报告",
                "${runtime.organization}",
                "${runtime.reportPeriod}",
                "效能小组",
                "${runtime.preparedDate}");
        Map<String, Object> runtime = new LinkedHashMap<String, Object>();
        runtime.put("organization", "软件开发二中心");
        runtime.put("reportPeriod", "2026年6月");
        runtime.put("preparedDate", "2026年7月23日");

        WordCoverDefinition resolved = resolver.resolve(
                source, runtime, PolicyDefinition.systemDefaults());

        assertThat(resolved.getOrganization()).isEqualTo("软件开发二中心");
        assertThat(resolved.getReportPeriod()).isEqualTo("2026年6月");
        assertThat(resolved.getPreparedDate()).isEqualTo("2026年7月23日");
        assertThat(source.getOrganization())
                .isEqualTo("${runtime.organization}");
        assertThat(source.getReportPeriod())
                .isEqualTo("${runtime.reportPeriod}");
        assertThat(source.getPreparedDate())
                .isEqualTo("${runtime.preparedDate}");
    }

    @Test
    void failsClearlyWhenRuntimeCoverValueCannotBeResolved() {
        WordCoverDefinition source = cover(
                "研发效能报告", "软件开发二中心",
                "${runtime.reportPeriod}", "效能小组",
                "${runtime.preparedDate}");

        assertThatThrownBy(() -> resolver.resolve(
                source,
                java.util.Collections.singletonMap(
                        "reportPeriod", "2026年6月"),
                PolicyDefinition.systemDefaults()))
                .isInstanceOf(WordTemplateException.class)
                .hasMessageContaining("preparedDate")
                .hasMessageContaining("Unresolved placeholder");
    }

    private static WordCoverDefinition cover(
            String title,
            String organization,
            String reportPeriod,
            String preparedBy,
            String preparedDate) {
        WordCoverDefinition cover = new WordCoverDefinition();
        cover.setTitle(title);
        cover.setOrganization(organization);
        cover.setReportPeriod(reportPeriod);
        cover.setPreparedBy(preparedBy);
        cover.setPreparedDate(preparedDate);
        return cover;
    }
}
