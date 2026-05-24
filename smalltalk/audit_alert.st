" Sovereign Communication Platform - Audit Event Alert Filter Rule "
[ :event | 
    (event event_type = 'AUDIT_EXPORT_REQUESTED') ifTrue: [ 
        event organization_id notNil 
    ]
]
