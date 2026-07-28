package com.xn.report.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.support.TestFixtures;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetPlannerTest {

    private final DatasetPlanner planner = new DatasetPlanner();

    @Test
    void keepsConfigurationOrderInsideSameDependencyLevel() {
        List<DatasetDefinition> input = Arrays.asList(
                TestFixtures.dataset("summary"),
                TestFixtures.dataset("detail"),
                TestFixtures.dataset("analysis", new String[]{"summary", "detail"}));

        assertThat(planner.plan(input))
                .extracting(DatasetDefinition::getId)
                .containsExactly("summary", "detail", "analysis");
    }

    @Test
    void keepsConfigurationOrderWhenDependenciesUnlockInDifferentOrder() {
        List<DatasetDefinition> input = Arrays.asList(
                TestFixtures.dataset("rootA"),
                TestFixtures.dataset("laterSecond", "rootB"),
                TestFixtures.dataset("rootB"),
                TestFixtures.dataset("laterFirst", "rootA"));

        assertThat(planner.plan(input))
                .extracting(DatasetDefinition::getId)
                .containsExactly("rootA", "rootB", "laterSecond", "laterFirst");
    }

    @Test
    void rejectsUnknownDependencies() {
        assertThatThrownBy(() -> planner.plan(Arrays.asList(
                TestFixtures.dataset("analysis", "missing"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("analysis")
                .hasMessageContaining("missing");
    }

    @Test
    void rejectsDuplicateIdsWithoutRelyingOnDefinitionValidation() {
        assertThatThrownBy(() -> planner.plan(Arrays.asList(
                TestFixtures.dataset("summary"),
                TestFixtures.dataset("summary"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summary");
    }

    @Test
    void reportsIdsThatParticipateInACycle() {
        assertThatThrownBy(() -> planner.plan(Arrays.asList(
                TestFixtures.dataset("first", "second"),
                TestFixtures.dataset("second", "third"),
                TestFixtures.dataset("third", "first"),
                TestFixtures.dataset("independent"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first")
                .hasMessageContaining("second")
                .hasMessageContaining("third");
    }

    @Test
    void doesNotModifyInputOrDependencyLists() {
        DatasetDefinition child = TestFixtures.dataset("child", "root");
        List<String> dependenciesBefore = new ArrayList<String>(child.getDependsOn());
        List<DatasetDefinition> input = new ArrayList<DatasetDefinition>(Arrays.asList(
                child,
                TestFixtures.dataset("root")));
        List<DatasetDefinition> inputBefore = new ArrayList<DatasetDefinition>(input);

        planner.plan(input);

        assertThat(input).containsExactlyElementsOf(inputBefore);
        assertThat(child.getDependsOn()).containsExactlyElementsOf(dependenciesBefore);
    }
}
