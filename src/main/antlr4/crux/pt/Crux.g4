grammar Crux;
program: declarationList EOF;

statementBlock: '{' statementList '}';
statementList: statements+=statement*;
statement
 : variableDeclaration
 | callStatement
 | assignmentStatement
 | ifStatement
 | forStatement
 | breakStatement
 | returnStatement
 ;

returnStatement: 'return' value=expression0 Semicolon;
breakStatement: 'break' Semicolon;
callStatement: callExpression Semicolon;
assignmentStatement: name=designator '=' value=expression0 Semicolon;
assignmentStatementNoSemi: name=designator '=' value=expression0;

ifStatement: 'if' condition=expression0 thenBody=statementBlock ('else' elseBody=statementBlock)?;
forStatement: 'for' '(' init=assignmentStatement condition=expression0 ';' update=assignmentStatementNoSemi ')' body=statementBlock;

declarationList: declaration*;
declaration
 : variableDeclaration
 | arrayDeclaration
 | functionDefinition
 ;

functionDefinition: type name=Identifier '(' parameterList ')' body=statementBlock;
arrayDeclaration: type name=Identifier '[' length=Integer ']' Semicolon;
variableDeclaration: type name=Identifier Semicolon;

parameterList: (parameters+=parameter (',' parameters+=parameter)*)?;
parameter: type name=Identifier;

expressionList: (expressions+=expression0 (',' expressions+=expression0)*)?;
callExpression: functionName=Identifier '(' expressionList ')';

expression3
 : '!' expression3
 | '(' expression0 ')'
 | designator
 | callExpression
 | literal
 ;

expression2
 : expression3
 | expression2 op2 expression3
 ;

expression1
 : expression2
 | expression1 op1 expression2
 ;

expression0
 : expression1
 | expression1 op0 expression1
 ;

op2
 : '*'
 | '/'
 | '&&'
 ;

op1
 : '+'
 | '-'
 | '||'
 ;

op0
 : '>='
 | '<='
 | '!='
 | '=='
 | '>'
 | '<'
 ;

type: Identifier;
designator: name=Identifier ('[' index=expression0 ']')?;

literal
 : Integer
 | True
 | False
 ;

Integer
 : '0'
 | [1-9] [0-9]*
 ;

True: 'true';
False: 'false';
Semicolon: ';';

Identifier: [a-zA-Z] [a-zA-Z0-9_]*;

WhiteSpaces: [ \t\r\n]+ -> skip;

Comment: '//' ~[\r\n]* -> skip;
