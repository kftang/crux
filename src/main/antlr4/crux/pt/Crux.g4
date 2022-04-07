grammar Crux;
program: declarationList EOF;

statementBlock: '{' statementList '}';
statementList: statement*;
statement
 : variableDeclaration
 | callStatement
 | assignmentStatement
 | ifStatement
 | forStatement
 | breakStatement
 | returnStatement
 ;

returnStatement: 'return' expression0 Semicolon;
breakStatement: 'break' Semicolon;
callStatement: callExpression Semicolon;
assignmentStatement: designator '=' expression0 Semicolon;
assignmentStatementNoSemi: designator '=' expression0;

ifStatement: 'if' expression0 statementBlock ('else' statementBlock)?;
forStatement: 'for' '(' assignmentStatement expression0 ';' assignmentStatementNoSemi ')' statementBlock;

declarationList: declaration*;
declaration
 : variableDeclaration
 | arrayDeclaration
 | functionDefinition
 ;

functionDefinition: type Identifier '(' parameterList ')' statementBlock;
arrayDeclaration: type Identifier '[' Integer ']' Semicolon;
variableDeclaration: type Identifier Semicolon;

parameterList: (parameter (',' parameter)*)?;
parameter: type Identifier;

expressionList: (expression0 (',' expression0)*)?;
callExpression: Identifier '(' expressionList ')';

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
designator: Identifier ('[' expression0 ']')?;

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
