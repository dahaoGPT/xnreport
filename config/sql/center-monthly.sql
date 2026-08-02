SELECT
    t.NOD_NM AS nodeName,
    t.CENTR_NM AS centerName,
    DATE_FORMAT(t.APRV_END_TM, '%Y-%m') AS statMonth,
    ROUND(AVG(TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM)), 2) AS avgHours,
    MAX(b.baselineHours) AS baselineHours,
    SUM(CASE
        WHEN TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM)
             > COALESCE(b.baselineHours, 0)
        THEN 1 ELSE 0 END) AS overStandardCount,
    SUM(CASE
        WHEN TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM)
             <= COALESCE(b.baselineHours, 0)
        THEN 1 ELSE 0 END) AS withinStandardCount
FROM XN_API_DESIGN_FLOW_APPROVAL t
LEFT JOIN (
    SELECT
        NOD_NM,
        CENTR_NM,
        AVG(TIMESTAMPDIFF(HOUR, APRV_BGN_TM, APRV_END_TM)) AS baselineHours
    FROM XN_API_DESIGN_FLOW_APPROVAL
    WHERE APRV_END_TM >= :baselineStartTime
      AND APRV_END_TM < :baselineEndTimeExclusive
      AND CENTR_NM IN (:centerNames)
      AND APRV_BGN_TM IS NOT NULL
      AND APRV_END_TM >= APRV_BGN_TM
    GROUP BY NOD_NM, CENTR_NM
) b
  ON t.NOD_NM = b.NOD_NM
 AND t.CENTR_NM = b.CENTR_NM
WHERE t.APRV_END_TM >= :startTime
  AND t.APRV_END_TM < :endTimeExclusive
  AND t.CENTR_NM IN (:centerNames)
  AND t.APRV_BGN_TM IS NOT NULL
  AND t.APRV_END_TM >= t.APRV_BGN_TM
GROUP BY
    t.NOD_NM,
    t.CENTR_NM,
    DATE_FORMAT(t.APRV_END_TM, '%Y-%m')
ORDER BY FIELD(
    t.CENTR_NM,
    '开发一中心', '开发二中心', '开发三中心', '开发四中心', '开发五中心',
    '开发六中心', '开发七中心', '开发八中心', '开发九中心', '研发中心'
), statMonth, t.NOD_NM
