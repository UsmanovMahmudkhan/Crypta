grammar CQL;

query : selectClause fromClause whereClause? EOF ;

selectClause : SELECT fields ;
fields : ALL | field (COMMA field)* ;
field : IDENTIFIER ;

fromClause : FROM source ;
source : AUDIT_EVENTS | DEVICES | ROOMS ;

whereClause : WHERE condition ;
condition : field operator value ;
operator : EQ | NEQ | CONTAINS ;
value : STRING | NUMBER | UUID ;

SELECT : 'SELECT' | 'select' ;
FROM : 'FROM' | 'from' ;
WHERE : 'WHERE' | 'where' ;
ALL : '*' ;
COMMA : ',' ;

EQ : '=' ;
NEQ : '!=' | '<>' ;
CONTAINS : 'CONTAINS' | 'contains' ;

AUDIT_EVENTS : 'AUDIT_EVENTS' | 'audit_events' ;
DEVICES : 'DEVICES' | 'devices' ;
ROOMS : 'ROOMS' | 'rooms' ;

fragment HEX : [0-9a-fA-F] ;
UUID : HEX HEX HEX HEX HEX HEX HEX HEX '-' HEX HEX HEX HEX '-' HEX HEX HEX HEX '-' HEX HEX HEX HEX '-' HEX HEX HEX HEX HEX HEX HEX HEX HEX HEX HEX HEX ;

IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* ;
STRING : '\'' (~'\'' | '\'\'')* '\'' ;
NUMBER : [0-9]+ ;
WS : [ \t\r\n]+ -> skip ;
