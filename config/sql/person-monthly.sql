SELECT
    t.NOD_NM AS nodeName,
    t.CENTR_NM AS centerName,
    t.APRV_PSN_NO AS approverId,
    t.APRV_PSN_NM AS approverName,
    DATE_FORMAT(t.APRV_END_TM, '%Y-%m') AS statMonth,
    ROUND(AVG(TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM)), 2) AS avgHours,
    MAX(b.baselineHours) AS baselineHours,
    MAX(g.GRP_CTG) AS groupCategory,
    MAX(g.GRP_LNG) AS groupLeader,
    MAX(g.WTHR_ON_JOB) AS onJob
FROM XN_API_DESIGN_FLOW_APPROVAL t
LEFT JOIN (
    SELECT
        NOD_NM,
        APRV_PSN_NO,
        AVG(TIMESTAMPDIFF(HOUR, APRV_BGN_TM, APRV_END_TM)) AS baselineHours
    FROM XN_API_DESIGN_FLOW_APPROVAL
    WHERE APRV_END_TM >= :baselineStartTime
      AND APRV_END_TM < :baselineEndTimeExclusive
      AND CENTR_NM IN (:centerNames)
      AND APRV_BGN_TM IS NOT NULL
      AND APRV_END_TM >= APRV_BGN_TM
    GROUP BY NOD_NM, APRV_PSN_NO
) b
  ON t.NOD_NM = b.NOD_NM
 AND t.APRV_PSN_NO = b.APRV_PSN_NO
LEFT JOIN xn_grp g ON g.empe_id = t.APRV_PSN_NO
WHERE t.APRV_END_TM >= :startTime
  AND t.APRV_END_TM < :endTimeExclusive
  AND t.CENTR_NM IN (:centerNames)
  AND t.APRV_BGN_TM IS NOT NULL
  AND t.APRV_END_TM >= t.APRV_BGN_TM
GROUP BY
    t.NOD_NM,
    t.CENTR_NM,
    t.APRV_PSN_NO,
    t.APRV_PSN_NM,
    DATE_FORMAT(t.APRV_END_TM, '%Y-%m')
ORDER BY t.APRV_PSN_NM, statMonth, avgHours DESC
