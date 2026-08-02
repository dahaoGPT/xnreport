SELECT
    '1天之内' AS durationRange,
    COUNT(*) AS approvalCount,
    1 AS rangeOrder
FROM XN_API_DESIGN_FLOW_APPROVAL t
WHERE t.APRV_END_TM >= :startTime
  AND t.APRV_END_TM < :endTimeExclusive
  AND t.CENTR_NM IN (:centerNames)
  AND t.APRV_BGN_TM IS NOT NULL
  AND t.APRV_END_TM >= t.APRV_BGN_TM
  AND TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM) <= 24
UNION ALL
SELECT
    '7天之内' AS durationRange,
    COUNT(*) AS approvalCount,
    2 AS rangeOrder
FROM XN_API_DESIGN_FLOW_APPROVAL t
WHERE t.APRV_END_TM >= :startTime
  AND t.APRV_END_TM < :endTimeExclusive
  AND t.CENTR_NM IN (:centerNames)
  AND t.APRV_BGN_TM IS NOT NULL
  AND t.APRV_END_TM >= t.APRV_BGN_TM
  AND TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM) > 24
  AND TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM) <= 168
UNION ALL
SELECT
    '7天以上' AS durationRange,
    COUNT(*) AS approvalCount,
    3 AS rangeOrder
FROM XN_API_DESIGN_FLOW_APPROVAL t
WHERE t.APRV_END_TM >= :startTime
  AND t.APRV_END_TM < :endTimeExclusive
  AND t.CENTR_NM IN (:centerNames)
  AND t.APRV_BGN_TM IS NOT NULL
  AND t.APRV_END_TM >= t.APRV_BGN_TM
  AND TIMESTAMPDIFF(HOUR, t.APRV_BGN_TM, t.APRV_END_TM) > 168
ORDER BY rangeOrder
