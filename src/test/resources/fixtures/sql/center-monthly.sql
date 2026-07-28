SELECT
    center_name AS centerName,
    DATE_FORMAT(approved_at, '%Y-%m') AS statMonth,
    AVG(approval_hours) AS avgHours
FROM approval_record
WHERE approved_at >= :startTime
  AND approved_at < :endTimeExclusive
  AND center_name IN (:centerNames)
GROUP BY center_name, DATE_FORMAT(approved_at, '%Y-%m')
ORDER BY center_name, statMonth
