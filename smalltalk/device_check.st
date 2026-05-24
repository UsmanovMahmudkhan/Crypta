" 
  Sovereign Communication Platform
  System Governance & Policy Enforcement
  -------------------------------------------------------------
  Rule: Device Trust and Platform Security Posture Verification
  Filename: device_check.st
"

| verifyDeviceSecurity postureCheck |

verifyDeviceSecurity := [ :device |
    (device trust_state = 'REVOKED') ifTrue: [
        false
    ] ifFalse: [
        postureCheck value: device
    ]
].

postureCheck := [ :device |
    | isSecure |
    isSecure := false.
    (device platform = 'iOS') ifTrue: [
        " iOS devices must have Secure Enclave key protection enabled "
        (device strongbox_or_secure_enclave = true) ifTrue: [
            isSecure := true
        ]
    ].
    (device platform = 'Android') ifTrue: [
        " Android devices must have strongbox hardware backing or MDM compliance "
        (device strongbox_or_secure_enclave = true) ifTrue: [
            isSecure := true
        ] ifFalse: [
            (device mdm_compliant = true) ifTrue: [
                isSecure := true
            ]
        ]
    ].
    isSecure
].

" Execute validation against the active device context "
verifyDeviceSecurity value: activeDevice
