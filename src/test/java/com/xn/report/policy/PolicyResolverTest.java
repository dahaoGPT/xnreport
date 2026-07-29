package com.xn.report.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.definition.PolicyDefinition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyResolverTest {

    @Test
    void resolvesComponentBeforeRuleDatasetReportAndSystemDefault() {
        PolicyResolver resolver = new PolicyResolver(PolicyDefinition.systemDefaults());

        EmptyDataPolicy result = resolver.resolveEmptyData(
                PolicyDefinition.component(EmptyDataPolicy.USE_DEFAULT),
                PolicyDefinition.rule(EmptyDataPolicy.FAIL),
                PolicyDefinition.dataset(EmptyDataPolicy.SKIP),
                PolicyDefinition.report(EmptyDataPolicy.OUTPUT_MESSAGE));

        assertThat(result).isEqualTo(EmptyDataPolicy.USE_DEFAULT);
    }

    @Test
    void resolvesEachPolicyFromFirstNonNullScopeAndFallsBackToDefaults() {
        PolicyDefinition system = PolicyDefinition.systemDefaults();
        PolicyResolver resolver = new PolicyResolver(system);
        PolicyDefinition component = new PolicyDefinition();
        PolicyDefinition rule = new PolicyDefinition();
        PolicyDefinition dataset = new PolicyDefinition();
        PolicyDefinition report = new PolicyDefinition();
        dataset.setMissingField(MissingFieldPolicy.WARN_AND_SKIP);
        rule.setTypeMismatch(TypeMismatchPolicy.SAFE_CONVERT);
        component.setNullValue(NullValuePolicy.ALLOW);

        assertThat(resolver.resolveMissingField(component, rule, dataset, report))
                .isEqualTo(MissingFieldPolicy.WARN_AND_SKIP);
        assertThat(resolver.resolveTypeMismatch(component, rule, dataset, report))
                .isEqualTo(TypeMismatchPolicy.SAFE_CONVERT);
        assertThat(resolver.resolveNullValue(component, rule, dataset, report))
                .isEqualTo(NullValuePolicy.ALLOW);
        assertThat(resolver.resolveEmptyData(component, rule, dataset, report))
                .isEqualTo(EmptyDataPolicy.OUTPUT_MESSAGE);
    }

    @Test
    void skipAndDefaultActionsEmitLowCouplingWarnings() {
        List<ReportWarning> warnings = new ArrayList<ReportWarning>();
        PolicyResolver resolver = new PolicyResolver(
                PolicyDefinition.systemDefaults(), warnings::add);

        resolver.recordApplied(
                MissingFieldPolicy.WARN_AND_SKIP,
                "dataset",
                "monthly",
                "missing avgHours");
        resolver.recordApplied(
                EmptyDataPolicy.USE_DEFAULT,
                "component",
                "chart-1",
                "using configured default");
        resolver.recordApplied(
                NullValuePolicy.RULE_NOT_MATCHED,
                "rule",
                "slow-rule",
                "null does not match");

        assertThat(warnings).extracting(ReportWarning::getAction)
                .containsExactly("WARN_AND_SKIP", "USE_DEFAULT");
        assertThat(warnings).extracting(ReportWarning::getScopeId)
                .containsExactly("monthly", "chart-1");
    }
}
