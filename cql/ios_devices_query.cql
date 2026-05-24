-- Sovereign Communication Platform
-- CQL Compliance Rule: Query all iOS devices
SELECT id, device_name, trust_state 
FROM DEVICES 
WHERE platform = 'iOS';
