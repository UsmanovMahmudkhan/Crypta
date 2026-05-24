-- 
-- Sovereign Communication Platform
-- Compliance Query Language (CQL) Script
-- -------------------------------------------------------------
-- Query: Critical emergency platform lockdown history
-- Filename: emergency_lockdown_events.cql
-- 

-- Select audit logs generated during critical platform shutdowns
SELECT 
    id, 
    organization_id, 
    actor_user_id, 
    event_type, 
    target_id, 
    created_at 
FROM 
    AUDIT_EVENTS 
WHERE 
    event_type = 'EMERGENCY_LOCKDOWN_TRIGGERED';
