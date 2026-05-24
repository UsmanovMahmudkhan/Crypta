" 
  Sovereign Communication Platform
  System Governance & Policy Enforcement
  -------------------------------------------------------------
  Rule: Room Access Control and Classification Policy
  Filename: room_access_control.st
"

| validateRoomAccess isClassificationMatch isOrganizationMatched |

isOrganizationMatched := [ :user :room |
    user organization_id = room organization_id
].

isClassificationMatch := [ :user :room |
    (room classification = 'TOP_SECRET') ifTrue: [
        user clearance_level = 'TOP_SECRET'
    ] ifFalse: [
        (room classification = 'SECRET') ifTrue: [
            (user clearance_level = 'TOP_SECRET') or: [ user clearance_level = 'SECRET' ]
        ] ifFalse: [
            true " General public or default classification "
        ]
    ]
].

validateRoomAccess := [ :user :room |
    (isOrganizationMatched value: user value: room) ifTrue: [
        (room lockdown_state = 'LOCKED') ifTrue: [
            " During lockdown, only organizational operators are permitted room entry "
            user is_operator = true
        ] ifFalse: [
            isClassificationMatch value: user value: room
        ]
    ] ifFalse: [
        false " Non-matching organizations are strictly rejected under zero-trust "
    ]
].

" Perform evaluation on context arguments "
validateRoomAccess value: activeUser value: activeRoom
