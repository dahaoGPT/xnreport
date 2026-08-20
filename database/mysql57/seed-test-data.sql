-- MySQL 5.7 deterministic fixtures for the efficiency report example.
-- Re-running this script only replaces rows whose identifiers start with TEST_.

SET NAMES utf8mb4;
START TRANSACTION;

DELETE FROM XN_API_DESIGN_FLOW_APPROVAL
WHERE PCS_NO LIKE 'TEST=_%' ESCAPE '=';

DELETE FROM xn_grp
WHERE empe_id LIKE 'TEST=_%' ESCAPE '=';

INSERT INTO xn_grp (empe_id, GRP_CTG, GRP_LNG, WTHR_ON_JOB) VALUES
('TEST_U01', '开发组', '测试组长甲', '1'),
('TEST_U02', '开发组', '测试组长甲', '1'),
('TEST_U03', '测试组', '测试组长乙', '1'),
('TEST_U04', '测试组', '测试组长乙', '0'),
('TEST_U05', '开发支持组', '测试组长丙', '0'),
('TEST_U06', '质量组', '测试组长丙', '1'),
('TEST_U07', '运维组', '测试组长丁', '0'),
('TEST_U08', '开发组', '测试组长丁', '1'),
('TEST_U09', '测试组', '测试组长戊', '0'),
('TEST_U10', '研发开发组', '测试组长戊', '1');

-- One 2025 baseline row per center and approver.
INSERT INTO XN_API_DESIGN_FLOW_APPROVAL (
    PCS_NO, PCS_NM, NOD_NO, NOD_NM, DEMD_NO,
    ITTR_ID, ITTR_NM, ITTR_SYS_NM,
    APRV_PSN_NO, APRV_PSN_NM, CENTR_NM, SYS_NM,
    APRV_BGN_TM, APRV_END_TM, APRV_STS
) VALUES
('TEST_2025_C01_01', '测试基准流程-开发一中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C01', 'TEST_I01', '测试发起人01', '测试系统', 'TEST_U01', '测试审批人01', '开发一中心', 'API设计平台', DATE_SUB('2025-06-15 18:00:00', INTERVAL 20 HOUR), '2025-06-15 18:00:00', '1'),
('TEST_2025_C02_01', '测试基准流程-开发二中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C02', 'TEST_I02', '测试发起人02', '测试系统', 'TEST_U02', '测试审批人02', '开发二中心', 'API设计平台', DATE_SUB('2025-06-16 18:00:00', INTERVAL 22 HOUR), '2025-06-16 18:00:00', '1'),
('TEST_2025_C03_01', '测试基准流程-开发三中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C03', 'TEST_I03', '测试发起人03', '测试系统', 'TEST_U03', '测试审批人03', '开发三中心', 'API设计平台', DATE_SUB('2025-06-17 18:00:00', INTERVAL 18 HOUR), '2025-06-17 18:00:00', '1'),
('TEST_2025_C04_01', '测试基准流程-开发四中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C04', 'TEST_I04', '测试发起人04', '测试系统', 'TEST_U04', '测试审批人04', '开发四中心', 'API设计平台', DATE_SUB('2025-06-18 18:00:00', INTERVAL 24 HOUR), '2025-06-18 18:00:00', '1'),
('TEST_2025_C05_01', '测试基准流程-开发五中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C05', 'TEST_I05', '测试发起人05', '测试系统', 'TEST_U05', '测试审批人05', '开发五中心', 'API设计平台', DATE_SUB('2025-06-19 18:00:00', INTERVAL 16 HOUR), '2025-06-19 18:00:00', '1'),
('TEST_2025_C06_01', '测试基准流程-开发六中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C06', 'TEST_I06', '测试发起人06', '测试系统', 'TEST_U06', '测试审批人06', '开发六中心', 'API设计平台', DATE_SUB('2025-06-20 18:00:00', INTERVAL 20 HOUR), '2025-06-20 18:00:00', '1'),
('TEST_2025_C07_01', '测试基准流程-开发七中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C07', 'TEST_I07', '测试发起人07', '测试系统', 'TEST_U07', '测试审批人07', '开发七中心', 'API设计平台', DATE_SUB('2025-06-21 18:00:00', INTERVAL 30 HOUR), '2025-06-21 18:00:00', '1'),
('TEST_2025_C08_01', '测试基准流程-开发八中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C08', 'TEST_I08', '测试发起人08', '测试系统', 'TEST_U08', '测试审批人08', '开发八中心', 'API设计平台', DATE_SUB('2025-06-22 18:00:00', INTERVAL 12 HOUR), '2025-06-22 18:00:00', '1'),
('TEST_2025_C09_01', '测试基准流程-开发九中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C09', 'TEST_I09', '测试发起人09', '测试系统', 'TEST_U09', '测试审批人09', '开发九中心', 'API设计平台', DATE_SUB('2025-06-23 18:00:00', INTERVAL 26 HOUR), '2025-06-23 18:00:00', '1'),
('TEST_2025_C10_01', '测试基准流程-研发中心', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2025_C10', 'TEST_I10', '测试发起人10', '测试系统', 'TEST_U10', '测试审批人10', '研发中心', 'API设计平台', DATE_SUB('2025-06-24 18:00:00', INTERVAL 15 HOUR), '2025-06-24 18:00:00', '1');

-- Thirty current-period rows. Durations intentionally span all report bins.
INSERT INTO XN_API_DESIGN_FLOW_APPROVAL (
    PCS_NO, PCS_NM, NOD_NO, NOD_NM, DEMD_NO,
    ITTR_ID, ITTR_NM, ITTR_SYS_NM,
    APRV_PSN_NO, APRV_PSN_NM, CENTR_NM, SYS_NM,
    APRV_BGN_TM, APRV_END_TM, APRV_STS
) VALUES
('TEST_2026_C01_01', '测试当期流程-开发一中心-01', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C01_01', 'TEST_I01', '测试发起人01', '测试系统', 'TEST_U01', '测试审批人01', '开发一中心', 'API设计平台', DATE_SUB('2026-01-20 18:00:00', INTERVAL 25 HOUR), '2026-01-20 18:00:00', '1'),
('TEST_2026_C01_03', '测试当期流程-开发一中心-03', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C01_03', 'TEST_I01', '测试发起人01', '测试系统', 'TEST_U01', '测试审批人01', '开发一中心', 'API设计平台', DATE_SUB('2026-03-20 18:00:00', INTERVAL 30 HOUR), '2026-03-20 18:00:00', '1'),
('TEST_2026_C01_05', '测试当期流程-开发一中心-05', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C01_05', 'TEST_I01', '测试发起人01', '测试系统', 'TEST_U01', '测试审批人01', '开发一中心', 'API设计平台', DATE_SUB('2026-05-20 18:00:00', INTERVAL 35 HOUR), '2026-05-20 18:00:00', '1'),
('TEST_2026_C02_01', '测试当期流程-开发二中心-01', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C02_01', 'TEST_I02', '测试发起人02', '测试系统', 'TEST_U02', '测试审批人02', '开发二中心', 'API设计平台', DATE_SUB('2026-01-20 18:00:00', INTERVAL 10 HOUR), '2026-01-20 18:00:00', '1'),
('TEST_2026_C02_03', '测试当期流程-开发二中心-03', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C02_03', 'TEST_I02', '测试发起人02', '测试系统', 'TEST_U02', '测试审批人02', '开发二中心', 'API设计平台', DATE_SUB('2026-03-20 18:00:00', INTERVAL 18 HOUR), '2026-03-20 18:00:00', '1'),
('TEST_2026_C02_05', '测试当期流程-开发二中心-05', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C02_05', 'TEST_I02', '测试发起人02', '测试系统', 'TEST_U02', '测试审批人02', '开发二中心', 'API设计平台', DATE_SUB('2026-05-20 18:00:00', INTERVAL 20 HOUR), '2026-05-20 18:00:00', '1'),
('TEST_2026_C03_01', '测试当期流程-开发三中心-01', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C03_01', 'TEST_I03', '测试发起人03', '测试系统', 'TEST_U03', '测试审批人03', '开发三中心', 'API设计平台', DATE_SUB('2026-01-20 18:00:00', INTERVAL 20 HOUR), '2026-01-20 18:00:00', '1'),
('TEST_2026_C03_03', '测试当期流程-开发三中心-03', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C03_03', 'TEST_I03', '测试发起人03', '测试系统', 'TEST_U03', '测试审批人03', '开发三中心', 'API设计平台', DATE_SUB('2026-03-20 18:00:00', INTERVAL 22 HOUR), '2026-03-20 18:00:00', '1'),
('TEST_2026_C03_05', '测试当期流程-开发三中心-05', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C03_05', 'TEST_I03', '测试发起人03', '测试系统', 'TEST_U03', '测试审批人03', '开发三中心', 'API设计平台', DATE_SUB('2026-05-20 18:00:00', INTERVAL 24 HOUR), '2026-05-20 18:00:00', '1'),
('TEST_2026_C04_01', '测试当期流程-开发四中心-01', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C04_01', 'TEST_I04', '测试发起人04', '测试系统', 'TEST_U04', '测试审批人04', '开发四中心', 'API设计平台', DATE_SUB('2026-01-20 18:00:00', INTERVAL 30 HOUR), '2026-01-20 18:00:00', '1'),
('TEST_2026_C04_03', '测试当期流程-开发四中心-03', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C04_03', 'TEST_I04', '测试发起人04', '测试系统', 'TEST_U04', '测试审批人04', '开发四中心', 'API设计平台', DATE_SUB('2026-03-20 18:00:00', INTERVAL 40 HOUR), '2026-03-20 18:00:00', '1'),
('TEST_2026_C04_05', '测试当期流程-开发四中心-05', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C04_05', 'TEST_I04', '测试发起人04', '测试系统', 'TEST_U04', '测试审批人04', '开发四中心', 'API设计平台', DATE_SUB('2026-05-20 18:00:00', INTERVAL 50 HOUR), '2026-05-20 18:00:00', '1'),
('TEST_2026_C05_01', '测试当期流程-开发五中心-01', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C05_01', 'TEST_I05', '测试发起人05', '测试系统', 'TEST_U05', '测试审批人05', '开发五中心', 'API设计平台', DATE_SUB('2026-01-20 18:00:00', INTERVAL 40 HOUR), '2026-01-20 18:00:00', '1'),
('TEST_2026_C05_03', '测试当期流程-开发五中心-03', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C05_03', 'TEST_I05', '测试发起人05', '测试系统', 'TEST_U05', '测试审批人05', '开发五中心', 'API设计平台', DATE_SUB('2026-03-20 18:00:00', INTERVAL 50 HOUR), '2026-03-20 18:00:00', '1'),
('TEST_2026_C05_05', '测试当期流程-开发五中心-05', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C05_05', 'TEST_I05', '测试发起人05', '测试系统', 'TEST_U05', '测试审批人05', '开发五中心', 'API设计平台', DATE_SUB('2026-05-20 18:00:00', INTERVAL 60 HOUR), '2026-05-20 18:00:00', '1'),
('TEST_2026_C06_02', '测试当期流程-开发六中心-02', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C06_02', 'TEST_I06', '测试发起人06', '测试系统', 'TEST_U06', '测试审批人06', '开发六中心', 'API设计平台', DATE_SUB('2026-02-20 18:00:00', INTERVAL 8 HOUR), '2026-02-20 18:00:00', '1'),
('TEST_2026_C06_04', '测试当期流程-开发六中心-04', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C06_04', 'TEST_I06', '测试发起人06', '测试系统', 'TEST_U06', '测试审批人06', '开发六中心', 'API设计平台', DATE_SUB('2026-04-20 18:00:00', INTERVAL 10 HOUR), '2026-04-20 18:00:00', '1'),
('TEST_2026_C06_06', '测试当期流程-开发六中心-06', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C06_06', 'TEST_I06', '测试发起人06', '测试系统', 'TEST_U06', '测试审批人06', '开发六中心', 'API设计平台', DATE_SUB('2026-06-20 18:00:00', INTERVAL 12 HOUR), '2026-06-20 18:00:00', '1'),
('TEST_2026_C07_02', '测试当期流程-开发七中心-02', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C07_02', 'TEST_I07', '测试发起人07', '测试系统', 'TEST_U07', '测试审批人07', '开发七中心', 'API设计平台', DATE_SUB('2026-02-20 18:00:00', INTERVAL 200 HOUR), '2026-02-20 18:00:00', '1'),
('TEST_2026_C07_04', '测试当期流程-开发七中心-04', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C07_04', 'TEST_I07', '测试发起人07', '测试系统', 'TEST_U07', '测试审批人07', '开发七中心', 'API设计平台', DATE_SUB('2026-04-20 18:00:00', INTERVAL 180 HOUR), '2026-04-20 18:00:00', '1'),
('TEST_2026_C07_06', '测试当期流程-开发七中心-06', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C07_06', 'TEST_I07', '测试发起人07', '测试系统', 'TEST_U07', '测试审批人07', '开发七中心', 'API设计平台', DATE_SUB('2026-06-20 18:00:00', INTERVAL 190 HOUR), '2026-06-20 18:00:00', '1'),
('TEST_2026_C08_02', '测试当期流程-开发八中心-02', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C08_02', 'TEST_I08', '测试发起人08', '测试系统', 'TEST_U08', '测试审批人08', '开发八中心', 'API设计平台', DATE_SUB('2026-02-20 18:00:00', INTERVAL 15 HOUR), '2026-02-20 18:00:00', '1'),
('TEST_2026_C08_04', '测试当期流程-开发八中心-04', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C08_04', 'TEST_I08', '测试发起人08', '测试系统', 'TEST_U08', '测试审批人08', '开发八中心', 'API设计平台', DATE_SUB('2026-04-20 18:00:00', INTERVAL 20 HOUR), '2026-04-20 18:00:00', '1'),
('TEST_2026_C08_06', '测试当期流程-开发八中心-06', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C08_06', 'TEST_I08', '测试发起人08', '测试系统', 'TEST_U08', '测试审批人08', '开发八中心', 'API设计平台', DATE_SUB('2026-06-20 18:00:00', INTERVAL 25 HOUR), '2026-06-20 18:00:00', '1'),
('TEST_2026_C09_02', '测试当期流程-开发九中心-02', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C09_02', 'TEST_I09', '测试发起人09', '测试系统', 'TEST_U09', '测试审批人09', '开发九中心', 'API设计平台', DATE_SUB('2026-02-20 18:00:00', INTERVAL 20 HOUR), '2026-02-20 18:00:00', '1'),
('TEST_2026_C09_04', '测试当期流程-开发九中心-04', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C09_04', 'TEST_I09', '测试发起人09', '测试系统', 'TEST_U09', '测试审批人09', '开发九中心', 'API设计平台', DATE_SUB('2026-04-20 18:00:00', INTERVAL 24 HOUR), '2026-04-20 18:00:00', '1'),
('TEST_2026_C09_06', '测试当期流程-开发九中心-06', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C09_06', 'TEST_I09', '测试发起人09', '测试系统', 'TEST_U09', '测试审批人09', '开发九中心', 'API设计平台', DATE_SUB('2026-06-20 18:00:00', INTERVAL 28 HOUR), '2026-06-20 18:00:00', '1'),
('TEST_2026_C10_02', '测试当期流程-研发中心-02', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C10_02', 'TEST_I10', '测试发起人10', '测试系统', 'TEST_U10', '测试审批人10', '研发中心', 'API设计平台', DATE_SUB('2026-02-20 18:00:00', INTERVAL 60 HOUR), '2026-02-20 18:00:00', '1'),
('TEST_2026_C10_04', '测试当期流程-研发中心-04', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C10_04', 'TEST_I10', '测试发起人10', '测试系统', 'TEST_U10', '测试审批人10', '研发中心', 'API设计平台', DATE_SUB('2026-04-20 18:00:00', INTERVAL 80 HOUR), '2026-04-20 18:00:00', '1'),
('TEST_2026_C10_06', '测试当期流程-研发中心-06', 'TEST_NODE_API', 'API设计', 'TEST_DEMD_2026_C10_06', 'TEST_I10', '测试发起人10', '测试系统', 'TEST_U10', '测试审批人10', '研发中心', 'API设计平台', DATE_SUB('2026-06-20 18:00:00', INTERVAL 100 HOUR), '2026-06-20 18:00:00', '1');

COMMIT;

-- Verification output: 40 total, 10 baseline, 30 current.
SELECT
    COUNT(*) AS totalCount,
    SUM(APRV_END_TM >= '2025-01-01' AND APRV_END_TM < '2026-01-01') AS baselineCount,
    SUM(APRV_END_TM >= '2026-01-01' AND APRV_END_TM < '2026-07-01') AS currentCount
FROM XN_API_DESIGN_FLOW_APPROVAL
WHERE PCS_NO LIKE 'TEST=_%' ESCAPE '=';

SELECT CENTR_NM AS centerName, COUNT(*) AS recordCount
FROM XN_API_DESIGN_FLOW_APPROVAL
WHERE PCS_NO LIKE 'TEST=_%' ESCAPE '='
GROUP BY CENTR_NM
ORDER BY FIELD(
    CENTR_NM,
    '开发一中心', '开发二中心', '开发三中心', '开发四中心', '开发五中心',
    '开发六中心', '开发七中心', '开发八中心', '开发九中心', '研发中心'
);

-- Verification output: 13 within one day, 14 within seven days, 3 over seven days.
SELECT
    SUM(hours <= 24) AS within1Day,
    SUM(hours > 24 AND hours <= 168) AS within7Days,
    SUM(hours > 168) AS over7Days
FROM (
    SELECT TIMESTAMPDIFF(HOUR, APRV_BGN_TM, APRV_END_TM) AS hours
    FROM XN_API_DESIGN_FLOW_APPROVAL
    WHERE PCS_NO LIKE 'TEST=_%' ESCAPE '='
      AND APRV_END_TM >= '2026-01-01'
      AND APRV_END_TM < '2026-07-01'
) d;

-- Verification output: 10 people.
SELECT COUNT(*) AS testPersonCount
FROM xn_grp
WHERE empe_id LIKE 'TEST=_%' ESCAPE '=';
