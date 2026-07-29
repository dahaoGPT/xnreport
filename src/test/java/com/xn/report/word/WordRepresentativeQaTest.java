package com.xn.report.word;

import com.xn.report.chart.RenderedChart;
import com.xn.report.config.definition.WordComponentDefinition;
import com.xn.report.config.definition.WordCoverDefinition;
import com.xn.report.config.definition.WordSectionDefinition;
import com.xn.report.dataset.DatasetContext;
import com.xn.report.dataset.DatasetResult;
import com.xn.report.support.TestFixtures;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class WordRepresentativeQaTest {

    @Test
    void generatesRepresentativeDocumentForVisualQa() throws Exception {
        Path directory = Paths.get("target", "qa-word");
        Files.createDirectories(directory);
        Path imagePath = directory.resolve("approval-trend.png");
        createChart(imagePath);
        RenderedChart chart = new RenderedChart(
                imagePath, "image/png", 1600, 850, 180);

        DatasetResult people = TestFixtures.people(
                TestFixtures.row("personName", "张三",
                        "centerName", "开发一中心", "avgHours", 12.35),
                TestFixtures.row("personName", "李四",
                        "centerName", "开发二中心", "avgHours", 8.0),
                TestFixtures.row("personName", "王五",
                        "centerName", "研发中心", "avgHours", 23.64));
        DatasetContext datasets =
                DatasetContext.builder().put(people).build();

        try (XWPFDocument document = ReportTemplateFixtureBuilder.build()) {
            WordCoverDefinition cover = new WordCoverDefinition();
            cover.setTitle("研发效能报告");
            cover.setOrganization("软件开发二中心");
            cover.setReportPeriod("2026年6月");
            cover.setPreparedBy("效能小组");
            cover.setPreparedDate("2026年7月23日");
            WordRunTextReplacer replacer = new WordRunTextReplacer();
            new WordCoverBinder(replacer).bind(document, cover);
            new WordTocManager().configure(document, 3, true);
            replacer.replace(document,
                    "{{value:teamSummary.avgHours}}", "25.27");
            replacer.replace(document,
                    "{{text:approvalTimeout}}",
                    "说明：审批时长较去年基准值有所改善。");
            new WordTableWriter().bindPrototype(
                    document.getTables().get(0), people, "暂无明细");

            WordComponentDefinition scenario =
                    text("SCENARIO",
                            "场景说明：反映审核人员在周期内审批节点的时长。");
            WordComponentDefinition factors =
                    text("KEY_FACTORS",
                            "构成要素：API设计平台审批时长、数据库表设计平台审批时长。");
            WordComponentDefinition chartComponent =
                    new WordComponentDefinition();
            chartComponent.setType("CHART");
            chartComponent.setChartId("approvalTrend");
            chartComponent.setWidthInches(Double.valueOf(6.2));
            chartComponent.setCaption("图1 API设计平台审批时长");
            chartComponent.setAltText("API设计平台审批时长趋势图");
            WordComponentDefinition table = new WordComponentDefinition();
            table.setType("TABLE");
            table.setDataset("people");
            table.setEmptyMessage("暂无明细");
            WordComponentDefinition unit = text("UNIT", "单位：小时");
            WordComponentDefinition attachment =
                    new WordComponentDefinition();
            attachment.setType("ATTACHMENT");
            attachment.setTitle("附件信息");
            attachment.setDescription("本报告包含以下附件：");
            attachment.setItems(Collections.singletonList("API审批人员明细.xlsx"));

            WordSectionDefinition parent = section(
                    "delivery", "交付速率", 1);
            parent.setComponents(Arrays.asList(scenario, factors));
            WordSectionDefinition child = section(
                    "approval", "设计平台审批时长", 2);
            child.setComponents(Arrays.asList(
                    chartComponent, table, unit, attachment));
            parent.setChildren(Collections.singletonList(child));
            new WordSectionRenderer().render(
                    document, Collections.singletonList(parent),
                    WordRenderContext.builder()
                            .datasets(datasets)
                            .chart("approvalTrend", chart)
                            .build());

            WordComponentDefinition markerChart =
                    new WordComponentDefinition();
            markerChart.setWidthInches(Double.valueOf(5.4));
            markerChart.setAltText("模板图表标记替换示例");
            new WordTemplateChartBinder().bind(
                    document, "centerEventChart", chart, markerChart);

            Path output = directory.resolve("representative.docx");
            try (OutputStream stream = Files.newOutputStream(output)) {
                document.write(stream);
            }
            new WordOutputValidator().validate(
                    output, 3,
                    Arrays.asList("交付速率", "设计平台审批时长"),
                    2, 2);
        }
    }

    private static WordSectionDefinition section(
            String id, String title, int level) {
        WordSectionDefinition section = new WordSectionDefinition();
        section.setId(id);
        section.setTitle(title);
        section.setLevel(level);
        section.setEmptyStrategy("KEEP");
        return section;
    }

    private static WordComponentDefinition text(
            String type, String value) {
        WordComponentDefinition component = new WordComponentDefinition();
        component.setType(type);
        component.setText(value);
        return component;
    }

    private static void createChart(Path output) throws Exception {
        BufferedImage image = new BufferedImage(
                1600, 850, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(50, 90, 150));
        graphics.drawString("API DESIGN APPROVAL DURATION",
                560, 75);
        graphics.setColor(new Color(224, 122, 48));
        int[] values = {160, 70, 230, 350, 610, 280};
        for (int index = 0; index < values.length; index++) {
            int x = 180 + index * 220;
            graphics.fillRect(x, 720 - values[index],
                    75, values[index]);
            graphics.setColor(new Color(50, 90, 150));
            if (index > 0) {
                graphics.drawLine(
                        180 + (index - 1) * 220 + 37,
                        660 - values[index - 1] / 2,
                        x + 37, 660 - values[index] / 2);
            }
            graphics.setColor(new Color(224, 122, 48));
        }
        graphics.dispose();
        ImageIO.write(image, "png", output.toFile());
    }
}
