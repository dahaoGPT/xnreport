SELECT
    '全部部门' AS nodeName,
    DATE_FORMAT(t.APRV_END_TM, '%Y-%m') AS statMonth,
    ROUND(AVG(TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM)), 2) AS avgHours,
    MAX(b.baselineHours) AS baselineHours
FROM XN_API_DESIGN_FLOW_APPROVAL t
LEFT JOIN (
    SELECT
        AVG(TIMESTAMPDIFF(HOUR, APRV_BGN_TM, APRV_END_TM)) AS baselineHours
    FROM XN_API_DESIGN_FLOW_APPROVAL
    WHERE APRV_END_TM >= :baselineStartTime
      AND APRV_END_TM < :baselineEndTimeExclusive
      AND CENTR_NM IN (:centerNames)
      AND APRV_BGN_TM IS NOT NULL
      AND APRV_END_TM >= APRV_BGN_TM
) b ON 1 = 1
WHERE t.APRV_END_TM >= :startTime
  AND t.APRV_END_TM < :endTimeExclusive
  AND t.CENTR_NM IN (:centerNames)
  AND t.APRV_BGN_TM IS NOT NULL
  AND t.APRV_END_TM >= t.APRV_BGN_TM
GROUP BY DATE_FORMAT(t.APRV_END_TM, '%Y-%m')
ORDER BY statMonth
