" Sovereign Communication Platform - Device Compliance Policy Rule "
[ :device | 
    (device platform = 'iOS') ifTrue: [ 
        device strongbox_or_secure_enclave = true 
    ] ifFalse: [ 
        device mdm_compliant = true 
    ]
]
