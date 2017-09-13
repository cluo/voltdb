/* This file is part of VoltDB.
 * Copyright (C) 2008-2017 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * Open questions:
 *   1.) Is it true that constraints defined in a create
 *       table statement must be column names?
 *   2.) Is it true that the order by expressions in a
 *       delete statement must be column names?
 */
grammar SQLParser;
import SQLLexer;

@header {
package org.voltdb.sqlparser.syntax.grammar;
}

dml_statement_list:
       dml_statement ';' ( dml_statement ';' )* EOF
    ;       
ddl_statement_list:
       ddl_statement ';' ( ddl_statement ';' )* EOF
    ;
    
dql_statement_list:
        dql_statement ';' ( dql_statement ';' )* EOF
    ;
    
dml_statement:
        insert_statement
    |   
        update_statement
    |
        delete_statement
    |
        /* empty */
    ;

ddl_statement:
        alter_table
    |
        create_index
    |
        create_table
    |
        create_view
    |
        create_procedure
    |
        create_role
    |
        dr_statement
    |
        drop_statement
    |
        export_statement
    |
        import_statement
    |
        partition_statement
    |
        truncate_table_statement
    |
    	set_statement
    |
        /* empty */
    ;

dql_statement:
        cursor_specification
    |
        /* empty */
    ;


/*************************************************************************
 * DDL Statements.
 *************************************************************************/
/*
 * Alter Table.
 */
alter_table:
        ALTER TABLE table_name
        (
            alter_table_drop
        |
            alter_table_add
        |
            alter_table_alter
        )
    ;

alter_table_drop:
        DROP
        (
            CONSTRAINT constraint_name
        |
            ( COLUMN )? column_name ( CASCADE )?
        |
            ( PRIMARY KEY | LIMIT PARTITION ROWS )
        )
    ;

alter_table_add:
        ADD
        (
            constraint_definition
        |
            ( COLUMN )? column_definition ( BEFORE column_name )?
        )
    ;

alter_table_alter:
        ALTER
           ( COLUMN )? column_name
           (
               column_definition_metadata ( CASCADE )?
            |
               column_definition_settings
           )
    ;

column_definition_metadata:
        datatype (
        		      column_default_value
        		  |
                      column_not_null
                  |
                      index_type
                  )*
    ;

column_default_value:
		DEFAULT default_string
	;

column_not_null:
		NOT NULL
	;

column_definition_settings:
        SET ( DEFAULT default_string | ( NOT )? NULL )
    ;

default_string:
		STRING
	;

column_definition:
        column_name column_definition_metadata
    ;

constraint_definition:
        ( CONSTRAINT constraint_name )? ( index_definition | limit_definition )
    ;

index_definition:
        index_type '(' index_column (',' index_column )* ')'
    ;

limit_definition:
        LIMIT PARTITION ROWS row_count
    ;

row_count:
        constant_integer_value
    ;
    
index_type:
        ( PRIMARY KEY )
    |
        UNIQUE
    |
        ASSUMEUNIQUE
    ;
/*
 * Create index.
 */
create_index:
        CREATE (UNIQUE | ASSUMEUNIQUE )? INDEX index_name
        ON ( table_name | view_name )
        '(' index_column ( ',' index_column )* ')'
        WHERE where_boolean_expression
    ;

//
// Until we get full expressions implemented, this is the
// best we can do.
//
index_column:
        column_name
    ;

/*
 * Create Procedure
 */
create_procedure:
        CREATE PROCEDURE procedure_name
        ( PARTITION ON TABLE table_name COLUMN column_name ( PARAMETER position )? )?
        ( ALLOW role_name ( ',' role_name )* )?
        (
            from_class_procedure
        |
            as_single_statement
        )
    ;

position:
        constant_integer_value
    ;

from_class_procedure:
        FROM CLASS class_name
    ;

as_single_statement:
        AS
        (
            SOURCE_CODE LANGUAGE GROOVY
        |
            query_expression
        )
    ;

/*
 * Create Role
 */
create_role:
        CREATE ROLE role_name
        ( WITH permission_name (',' permission_name )* )?
    ;

/*
 * Create stream.
 */
create_stream:
        CREATE STREAM stream_name 
        ( PARTITION ON COLUMN column_name )?
        ( EXPORT TO TARGET export_target_name )?
        '(' 
            column_definition ( ',' column_definition )*
        ')'
    ;

/*
 * Create Table.
 */
create_table:
        CREATE TABLE table_name '(' 
            ( column_definition ( ',' column_definition )* )?
            (',' constraint_definition ( ',' constraint_definition )* )?
        ')'
    ;

/*
 * Create View.
 *
 * There are many non contextfree rules here
 * which the grammar does not reflect.
 */
create_view:
        CREATE VIEW view_name '(' view_column_name (',' view_column_name )* ')'
        AS
        view_query_expression
        ;

view_query_expression:
        SELECT project_item ( ',' project_item )*
        FROM table_expression
        ( WHERE where_boolean_expression )?
        ( GROUP BY value_expression
                   ( ',' value_expression )* )?
    ;
// Note: Only stream and table can have CASCADE.
drop_statement:
        DROP dropped_kind dropped_name ( IF EXISTS )? ( CASCADE )?
    ;

dropped_kind:
        INDEX
    |
        TABLE
    |
        ROLE
    |
        STREAM
    |
        PROCEDURE
    |
        VIEW
    ;

/*
 * Partition Statement.
 */
partition_statement:
       partition_procedure_statement
    |
       partition_table_statement
    ;

partition_procedure_statement:
        PARTITION PROCEDURE procedure_name
        ON TABLE table_name
        COLUMN column_name
        ( PARAMETER position )?
    ;
partition_table_statement:
        PARTITION TABLE table_name ON COLUMN column_name
    ;

/*
 * DR Statement.
 */
dr_statement:
        DR TABLE table_name ( DISABLE )?
    ;

/*************************************************************************
 * DML Statements
 *************************************************************************/
/*
 * Delete statement.
 */
delete_statement:
        DELETE FROM table_name
        ( WHERE where_boolean_expression )?
        ( ORDER BY ( delete_sort_specification ( ',' delete_sort_specification ) )? )
    ;

//
// These may be only column names, and not more
// general expressions.  This may not be right.
// See the questions above.
//
delete_sort_specification:
        column_name ( ordering_specification )? ( null_ordering )?
    ;

/*
 * Insert statement.
 */
insert_statement:
        ( INSERT | UPSERT ) INTO table_name
        ( '(' column_name (',' column_name)* ')' )?
        (
            insert_values
        |
            insert_select
        )
    ;

insert_values:
        VALUES '(' constant_value_expression (',' constant_value_expression )* ')'
    ;

insert_select:
        query_expression
    ;

/*
 * Select Statement.
 *
 * Apparently the LIMIT and OFFSET are not standard.
 */
cursor_specification returns [ ISelect query ]:
        query_expression
        { query = query_expression.query; }
        ( order_by_clause 
            { query.addOrderBy(order_by_clause.answer); }
        )?
        ( limit_clause 
            { query.addLimit(limit_clause.answer); }
        )?
        ( offset_clause 
            { query.addOffset(offset_clause.answer); }
        )?
    ;

order_by_clause returns [List<ISemantino> answer]:
        ORDER BY sort_specification_list
        { answer = sort_specification_list.answer; }
    ;

limit_clause returns [ long answer ]:
		LIMIT constant_value_expression
		{ answer = m_factory.convertConstantIntegerExpression(constant_value_expression.text); }
	;
	
offset_clause returns [ long answer ]:
		OFFSET constant_value_expression
		{ answer = m_factory.convertConstantIntegerExpression(constant_value_expression.text); }
	;

query_expression returns [ ISelect query ]:
        query_expression_body
        { query = query_expression_body.answer; }
    ;

with_clause:
        WITH with_list_element ( ',' with_list_element )*
    ;

//
// The standard is more complicated, so as to get
// the precedence right.  We lean on Antlr to bind
// intersection more tightly than union/except.
//
query_expression_body returns [ISelect query]:
        query_primary
        { query = query_primary.query; }
    |
    	left=query_expression_body op=query_intersect_op right=query_expression_body
    	{ query = m_factory.makeCompoundQuery(op.setop, left.query, right.query); }
    |
        left=query_expression_body op=query_union_op right=query_expression_body
        { query = m_factory.makeCompoundQuery(intersect_op.setop, left.query, right.query); }
    ;

query_union_op returns [ QuerySetOp op ]:
        ( UNION 
        { op = QuerySetOp.UNION_OP; }
           ( ALL { op = QuerySetOp.UNION_ALL_OP; } )?
        )
    |
    	EXCEPT
    	{ op = QuerySetOp.EXCEPT_OP; }
    ;

query_intersect_op:
		INTERSECT
		{ op = QuerySetOp.INTERSECT_OP; }
	;

query_primary returns [ISelect query]:
        simple_table
        { query = simple_table.query; }
    |
        '(' query_expression_body ')'
        { query = query_expression_body.query; }
    ;

simple_table returns [ISelect query]:
        query_specification
        { query = query_specification.query; }
    |
        explicit_table
        { query = explicit_table.query; }
    ;

explicit_table returns [ISelect query]:
        TABLE table_or_query_name
        { query = m_factory.makeErrorQuery(); }   
    ;

query_specification returns [ ISelect query ]:
        /* Note: set_quantifier can be empty, so it's optional. */
        SELECT set_quantifier select_list table_expression
    ;

set_quantifier returns [SelectQueryQuantifier quantifier]:
        ALL
        { quantifier = SelectQueryQuantifier.ALL_QUANTIFIER; } 
    |
        DISTINCT
        { quantifier = SelectQueryQuantifier.DISTINCT_QUANTIFIER; }
    |
        /* empty */
        { quantifier = SelectQueryQuantifier.ALL_QUANTIFIER; }
    ;

select_list:
        select_sublist ( ',' select_sublist )*
    ;

with_list_element:
        query_name
        ( '(' column_name (',' column_name )* ')' )?
        AS '(' query_expression ')'
    ;

select_sublist:
        derived_column
    ;

derived_column:
        ASTERISK
    |
        value_expression ( AS column_alias_name )?
    ;

sort_specification_list returns [ List<ISortSemantino> answer]:
        sortspecs += sort_specification (',' sortspecs += sort_specification)*
        { answer = new ArrayList<ISortSemantino>();
          for (Sort_specification_context sortspec : sortspecs) {
              answer.add(sortspec.answer);
          }
        }
    ;

sort_specification returns [ ISortSemantino answer ]:
        sort_key
        { answer = m_factory.makeSortSemantino(sort_key.answer); } 
        ( 
            ordering_specification
            { answer.setSortOrder(ordering_specification.answer); } 
        )? 
        ( 
            null_ordering
            { answer.setSortOrder(ordering_specification.answer); }
        )?
    ;

sort_key returns [ISemantino answer]:
        value_expression
        { answer = value_expression.answer; }
    ;

ordering_specification:
        ASC | DESC
    ;
    
null_ordering:
        ( NULLS FIRST) | (NULLS LAST )
    ;

project_item:
        value_expression ( ( AS )? column_alias_name )?
    ;

table_expression:
		from_clause
		( where_clause )?
		( group_by_clause )?
		( having_clause )?
   ;
   
from_clause:
		FROM table_reference ( ',' table_reference )*
	;

table_reference:
		table_factor
		( join_operator
          table_factor
          join_condition )*
    ;

table_factor:
		table_name ( AS table_alias_name )?
	| 
		derived_table AS table_alias_name
	|
		'(' table_reference ')'
	;

derived_table:
		table_subquery
	;

join_operator returns [JoinOperator operator]:
        (
            ( INNER )?
            { $operator = JoinOperator.INNER_JOIN; }
         |
            ( LEFT { $operator = JoinOperator.LEFT_OUTER_JOIN; }
                | 
              RIGHT { $operator = JoinOperator.RIGHT_OUTER_JOIN; } 
                | 
              FULL  { $operator = JoinOperator.LEFT_OUTER_JOIN; }
            )
            ( OUTER )?
        )
        JOIN
    ;

join_condition:
        ON join_boolean_expression
    |
        USING '(' column_name (',' column_name )* ')'
    ;

table_subquery:
        subquery
    ;

scalar_subquery:
        subquery
    ;
    
subquery:
        '(' query_specification ')'
    ;

where_clause:
		boolean_expression
	;
	

group_by_clause:
		GROUP BY group_item ( ',' group_item )*
	;
	
group_item:
        value_expression
    ;

having_clause:
		HAVING boolean_expression
	;
	
window_ref:
        window_name
    |
        window_spec
    ;

window_spec:
        ( window_name )?
        '('
        ( ORDER BY sort_specification_list )?
        ( PARTITION BY partition_specification_list )?
        ')'
    ;

partition_specification_list:
        partition_specification (',' partition_specification )*
    ;

partition_specification:
        value_expression
    ;

limit_value:
        constant_integer_value
    ;

offset_value:
        constant_integer_value
    ;

/*
 * Update statement.
 *
 */
update_statement:
        UPDATE table_name
        SET assign (',' assign )*
        ( WHERE boolean_expression )?
    ;

assign:
        IDENTIFIER '=' value_expression
    ;

truncate_table_statement:
        TRUNCATE TABLE table_name
    ;

set_statement:
        SET assign
    ;

/*
 * Export and Import Statements
 */
export_statement:
        EXPORT TABLE table_name TO STREAM stream_name
    ;

import_statement:
        IMPORT CLASS class_name
    ;

/*************************************************************************
 * Datatypes.
 *************************************************************************/
datatype:
        datatype_name ( '(' constant_integer_value (',' constant_integer_value )? ')' )?
    ;

/*************************************************************************
 * Expressions.
 *************************************************************************/
value_expression returns [ ISemantino answer ]:
                '(' value_expression ')'
                { answer = value_expression.answer; }
        |
                left=value_expression top=timesop right=value_expression
                { answer = m_factory.makeBinOp(op.answer, left.answer, right.answer); } 
        |
                left=value_expression sop=addop right=value_expression
                { answer = m_factory.makeBinOp(op.answer, left.answer, right.answer); }
        |
                left=value_expression rop=relop right=value_expression
                { answer = m_factory.makeBinOp(op.answer, left.answer, right.answer); }
        |
                NOT left=value_expression
                { 
                    answer = m_factory.makeUnaryOp(m_factory.getNotExpressionOperator(),
                                                   left.answer);
                                                 
                }
        |
                left=value_expression bop=AND right=value_expression
                {
                    answer = m_factory.makeBinOp(m_factory.getAndExpressionOperator(),
                                                 left.answer,
                                                 right.answer);
                }
        |
                left=value_expression bop=OR right=value_expression
                {
                    answer = m_factory.makeBinOp(m_factory.getOrExpressionOperator(),
                                                 left.answer,
                                                 right.answer);
                }
        |
                boolconst=TRUE
        |
                boolconst=FALSE
        |
                col=column_reference
        |
                num=NUMBER
        |
        		str=STRING
        |
        		scalar_function_name '(' ( value_expression (',' value_expression )* )? ')'
        |
        		aggregate_function_name '(' ( value_expression ( ',' value_expression )? )? ')'
        		( OVER window_ref )
       	//
       	// There are some other time/date functions with
       	// eccentric syntax which need to be put here.
        ;
        
timesop:
                '*'|'/'
        ;
        
addop:
                '+'|'-'
        ;
        
relop:
                '='|'<'|'>'|'<='|'>='|'!='
        ;

column_reference:
        IDENTIFIER ( '.' IDENTIFIER )?
    ;

boolean_expression:
        value_expression
    ;

// Better if we have full expressions.
constant_value_expression:
        constant_integer_value
    ;

having_boolean_expression:
        value_expression
    ;

join_boolean_expression:
        value_expression
    ;

numeric_expression:
        value_expression
    ;

numeric_or_interval_expression:
        value_expression
    ;

where_boolean_expression:
        value_expression
    ;

constant_value:
        constant_value_expression
    ;

constant_integer_value:
       	NUMBER
    ;

/*************************************************************************
 * Names.
 *************************************************************************/
 aggregate_function_name:
 		IDENTIFIER
 	;
 
catalog_name:
        IDENTIFIER
    ;
    
class_name:
        IDENTIFIER
    ;
    
column_alias_name:
        IDENTIFIER
    ;
    
column_name:
        IDENTIFIER
    ;
    
constraint_name:
        IDENTIFIER
    ;
    
database_name:
        IDENTIFIER
    ;
    
datatype_name:
		IDENTIFIER
	;

dropped_name:
        IDENTIFIER
    ;

export_target_name:
        IDENTIFIER
    ;
    
index_name:
        IDENTIFIER
    ;
    
permission_name:
        IDENTIFIER
    ;
    
procedure_name:
        IDENTIFIER
    ;
    
query_name:
        IDENTIFIER
    ;
    
role_name:
        IDENTIFIER
    ;
    
scalar_function_name:
		IDENTIFIER
	;

schema_name:
        IDENTIFIER
    ;
    
stream_name:
        IDENTIFIER
    ;
    
table_alias_name:
        IDENTIFIER
    ;
    
table_name:
        IDENTIFIER
    ;
    
table_or_query_name:
        IDENTIFIER
    ;
    
view_column_name:
        IDENTIFIER
    ;
    
view_name:
        IDENTIFIER
    ;
    
window_name:
        IDENTIFIER
    ;

