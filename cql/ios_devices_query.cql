-- 
-- Sovereign Communication Platform
-- Compliance Query Language (CQL) Script
-- -------------------------------------------------------------
-- Query: Platform-specific trusted mobile device inventory
-- Filename: ios_devices_query.cql
-- 

-- Select hardware-backed iOS devices for compliance checking
SELECT 
    id, 
    user_id, 
    organization_id, 
    device_name, 
    trust_state, 
    hardware_backed, 
    strongbox_or_secure_enclave 
FROM 
    DEVICES 
WHERE 
    platform = 'iOS';
