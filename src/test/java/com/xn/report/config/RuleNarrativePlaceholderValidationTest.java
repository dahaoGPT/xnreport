package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.support.TestFixtures;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class RuleNarrativePlaceholderValidationTest {

    @Test
    void acceptsKnownRuleContractAndRejectsUnknownRuleOrProperty() {
        ReportDefinition definition = TestFixtures.report(
                TestFixtures.dataset("pipeline"));
        definition.setRules(Collections.singletonList(
                TestFixtures.pipelineRule()));
        NarrativeDefinition narrative = fixed(
                "${rule.pipeline.matchedCount}/"
                        + "${rule.pipeline.summary.maxHours}");
        definition.setNarratives(Collections.singletonList(narrative));
        ReportDefinitionValidator validator = new ReportDefinitionValidator();

        assertThat(validator.validate(definition).issues())
                .noneMatch(issue -> "$.narratives[0].template"
                        .equals(issue.getPath()));

        narrative.setTemplate("${rule.missing.total}");
        assertThat(validator.validate(definition).issues())
                .anyMatch(issue -> "$.narratives[0].template"
                        .equals(issue.getPath())
                        && issue.getMessage().contains("rule placeholder"));
    }

    private static NarrativeDefinition fixed(String template) {
        NarrativeDefinition narrative = new NarrativeDefinition();
        narrative.setId("ruleText");
        narrative.setSourceType(NarrativeDefinition.SourceType.FIXED_TEMPLATE);
        narrative.setTemplate(template);
        return narrative;
    }
}
