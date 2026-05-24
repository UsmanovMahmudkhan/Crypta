" 
  Sovereign Communication Platform
  System Governance & Policy Enforcement
  -------------------------------------------------------------
  Rule: Real-time Compliance Audit Event Alert Filtering
  Filename: audit_alert.st
"

| processAuditEvent alertRequired isCriticalEvent |

isCriticalEvent := [ :event |
    | type |
    type := event event_type.
    (type = 'EMERGENCY_LOCKDOWN_TRIGGERED')
        or: [ (type = 'KEY_TRANSPARENCY_FAILURE')
        or: [ (type = 'DEVICE_REVOKED')
        or: [ (type = 'UNAUTHORIZED_PLAINTEXT_ATTEMPT') ] ] ]
].

alertRequired := [ :event |
    (isCriticalEvent value: event) ifTrue: [
        true
    ] ifFalse: [
        " Standard export audits require alerts if triggered out-of-band "
        (event event_type = 'AUDIT_EXPORT_REQUESTED') ifTrue: [
            (event sink_config_ref = 'configured-out-of-band')
        ] ifFalse: [
            false
        ]
    ]
].

processAuditEvent := [ :event |
    (alertRequired value: event) ifTrue: [
        " Raise compliance alert to the Governance monitor "
        event put: 'alertStatus' value: 'RAISED'.
        true
    ] ifFalse: [
        event put: 'alertStatus' value: 'SKIPPED'.
        false
    ]
].

processAuditEvent value: currentEvent
