-- 
-- Sovereign Communication Platform
-- Compliance Query Language (CQL) Script
-- -------------------------------------------------------------
-- Query: Out-of-compliance devices lacking active MDM control
-- Filename: mdm_non_compliant_devices.cql
-- 

-- Select active devices failing standard corporate posture requirements
SELECT 
    id, 
    user_id, 
    organization_id, 
    device_name, 
    platform, 
    trust_state 
FROM 
    DEVICES 
WHERE 
    mdm_compliant = 'false';
