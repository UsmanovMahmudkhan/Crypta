-- Sovereign Communication Platform
-- CQL Compliance Rule: Query all requested audit exports
SELECT * 
FROM AUDIT_EVENTS 
WHERE event_type = 'AUDIT_EXPORT_REQUESTED';
