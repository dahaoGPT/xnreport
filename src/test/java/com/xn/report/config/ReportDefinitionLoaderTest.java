package com.xn.report.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xn.report.config.definition.DatasetDefinition;
import com.xn.report.config.definition.DistributionDefinition;
import com.xn.report.config.definition.NarrativeDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.dataset.DatasetType;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class ReportDefinitionLoaderTest {

    private final ReportDefinitionLoader loader = ReportDefinitionLoader.createDefault();

    @Test
    void loadsYamlAndJsonIntoEquivalentDefinitions() {
        ReportDefinition yaml = loader.load(Paths.get(
                "src/test/resources/fixtures/configs/minimal-report.yml"));
        ReportDefinition json = loader.load(Paths.get(
                "src/test/resources/fixtures/configs/minimal-report.json"));

        assertThat(yaml.getSchemaVersion()).isEqualTo("1.0");
        assertThat(json.getSchemaVersion()).isEqualTo(yaml.getSchemaVersion());
        assertThat(json.getReport().getCode()).isEqualTo(yaml.getReport().getCode());
        assertThat(json.getParameters().keySet()).containsExactlyElementsOf(
                yaml.getParameters().keySet());

        DatasetDefinition yamlDataset = yaml.getDatasets().get(0);
        DatasetDefinition jsonDataset = json.getDatasets().get(0);
        assertThat(yamlDataset.getId()).isEqualTo("summary");
        assertThat(yamlDataset.getSheetName()).isEqualTo("汇总");
        assertThat(yamlDataset.getResultType()).isEqualTo(DatasetType.SINGLE);
        assertThat(jsonDataset.getId()).isEqualTo(yamlDataset.getId());
        assertThat(jsonDataset.getSheetName()).isEqualTo(yamlDataset.getSheetName());
        assertThat(jsonDataset.getSqlFile()).isEqualTo(yamlDataset.getSqlFile());
        assertThat(jsonDataset.getResultType()).isEqualTo(yamlDataset.getResultType());

        assertThat(yaml.getWord().getCover().getTitle()).isEqualTo("示例报表");
        assertThat(json.getWord().getCover().getTitle())
                .isEqualTo(yaml.getWord().getCover().getTitle());
        assertThat(yaml.getWord().getToc().isEnabled()).isTrue();
        assertThat(json.getWord().getToc().getMaxLevel())
                .isEqualTo(yaml.getWord().getToc().getMaxLevel());

        WordSectionDefinition yamlSection = yaml.getWord().getSections().get(0);
        WordSectionDefinition jsonSection = json.getWord().getSections().get(0);
        assertThat(yamlSection.getId()).isEqualTo("summarySection");
        assertThat(jsonSection.getTitle()).isEqualTo(yamlSection.getTitle());
        assertThat(yamlSection.getChildren()).hasSize(1);
        assertThat(jsonSection.getChildren()).hasSize(1);
        assertThat(jsonSection.getChildren().get(0).getComponents().get(0).getText())
                .isEqualTo(yamlSection.getChildren().get(0).getComponents().get(0).getText());

        NarrativeDefinition yamlNarrative = yaml.getNarratives().get(0);
        NarrativeDefinition jsonNarrative = json.getNarratives().get(0);
        assertThat(yamlNarrative.getSourceType())
                .isEqualTo(NarrativeDefinition.SourceType.RULE_GENERATED);
        assertThat(jsonNarrative.getAnalyzer()).isEqualTo(yamlNarrative.getAnalyzer());
        assertThat(jsonNarrative.getDataset()).isEqualTo(yamlNarrative.getDataset());
        assertThat(jsonNarrative.getBaseline()).isEqualTo(yamlNarrative.getBaseline());
        assertThat(jsonNarrative.getFormat()).isEqualTo(yamlNarrative.getFormat());
        assertThat(jsonNarrative.getSentence()).isEqualTo(yamlNarrative.getSentence());

        DistributionDefinition yamlDistribution = yamlNarrative.getDistribution();
        DistributionDefinition jsonDistribution = jsonNarrative.getDistribution();
        assertThat(yamlDistribution.getField()).isEqualTo("approvalHours");
        assertThat(jsonDistribution.getLabelMode())
                .isEqualTo(yamlDistribution.getLabelMode());
        assertThat(jsonDistribution.getBins()).hasSize(1);
        assertThat(jsonDistribution.getBins().get(0).getMax())
                .isEqualByComparingTo(yamlDistribution.getBins().get(0).getMax());
        assertThat(jsonDistribution.getBins().get(0).isMaxInclusive()).isTrue();
    }

    @Test
    void suppliesNonNullDefaultsForOmittedObjectsAndCollections() {
        ReportDefinition definition = loader.load(Paths.get(
                "src/test/resources/fixtures/configs/minimal-report.yml"));
        DatasetDefinition dataset = definition.getDatasets().get(0);
        WordSectionDefinition child = definition.getWord().getSections()
                .get(0).getChildren().get(0);

        assertThat(definition.getParameters()).isNotNull();
        assertThat(definition.getDatasets()).isNotNull();
        assertThat(definition.getWord()).isNotNull();
        assertThat(dataset.getDependsOn()).isNotNull().isEmpty();
        assertThat(dataset.getParameters()).isNotNull().isEmpty();
        assertThat(dataset.getExpectedFields()).isNotNull().isEmpty();
        assertThat(definition.getWord().getSections()).isNotNull();
        assertThat(child.getChildren()).isNotNull().isEmpty();
        assertThat(child.getComponents()).isNotNull().hasSize(1);
        assertThat(new NarrativeDefinition().getParameters()).isNotNull().isEmpty();
        assertThat(new NarrativeDefinition().getDistribution()).isNotNull();
        assertThat(new DistributionDefinition().getBins()).isNotNull().isEmpty();
    }

    @Test
    void rejectsUnknownProperty() {
        assertThatThrownBy(() -> loader.load(Paths.get(
                "src/test/resources/fixtures/configs/unknown-property.yml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknownField");
    }
}
