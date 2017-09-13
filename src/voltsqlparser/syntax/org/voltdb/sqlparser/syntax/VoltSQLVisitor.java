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

package org.voltdb.sqlparser.syntax;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.voltdb.sqlparser.syntax.grammar.ICatalogAdapter;
import org.voltdb.sqlparser.syntax.grammar.IColumnIdent;
import org.voltdb.sqlparser.syntax.grammar.IGeographyType;
import org.voltdb.sqlparser.syntax.grammar.IIndex;
import org.voltdb.sqlparser.syntax.grammar.IInsertStatement;
import org.voltdb.sqlparser.syntax.grammar.IOperator;
import org.voltdb.sqlparser.syntax.grammar.ISelectQuery;
import org.voltdb.sqlparser.syntax.grammar.ISemantino;
import org.voltdb.sqlparser.syntax.grammar.IStringType;
import org.voltdb.sqlparser.syntax.grammar.QuerySetOp;
import org.voltdb.sqlparser.syntax.grammar.SQLParserBaseVisitor;
import org.voltdb.sqlparser.syntax.grammar.SQLParserParser;
import org.voltdb.sqlparser.syntax.grammar.SQLParserParser.Join_operatorContext;
import org.voltdb.sqlparser.syntax.grammar.SelectQueryQuantifier;
import org.voltdb.sqlparser.syntax.symtab.IColumn;
import org.voltdb.sqlparser.syntax.symtab.IExpressionParser;
import org.voltdb.sqlparser.syntax.symtab.IParserFactory;
import org.voltdb.sqlparser.syntax.symtab.ISourceLocation;
import org.voltdb.sqlparser.syntax.symtab.ISymbolTable;
import org.voltdb.sqlparser.syntax.symtab.ITable;
import org.voltdb.sqlparser.syntax.symtab.IType;
import org.voltdb.sqlparser.syntax.symtab.IndexType;
import org.voltdb.sqlparser.syntax.util.ErrorMessage;
import org.voltdb.sqlparser.syntax.util.ErrorMessageSet;

public class VoltSQLVisitor<T> extends SQLParserBaseVisitor<T> implements ANTLRErrorListener {

    private static final int DEFAULT_STRING_SIZE = 64;

    private T m_state;
    private List<IExpressionParser> m_expressionStack = new ArrayList<>();

    private ISymbolTable m_symbolTable;
    private IParserFactory m_factory;
    private ICatalogAdapter m_catalog;
    private ErrorMessageSet m_errorMessages;
    private List<ISelectQuery> m_selectQueryStack = new ArrayList<>();

    /*
     * Temporary data needed for table creation
     * and also for constraint definitions.
     */
    private ITable m_currentlyCreatedTable = null;
    private IColumn m_currentlyCreatedColumn = null;
    private String m_defaultValue;
    private boolean m_hasDefaultValue;
    private boolean m_isNullable;
    private boolean m_isNull;
    private IndexType m_indexType;
    private IType   m_columnType;


    /*
     * Temporary data needed for defining types.
     */
    private List<String> m_constantIntegerValues = null;

    /*
     * Temporary data needed for defining insert/upsert statements.
     */
    boolean m_isUpsert;
    private IInsertStatement m_insertStatement = null;

    public VoltSQLVisitor(IParserFactory aFactory, T aState) {
        m_state = aState;
        m_factory = aFactory;
        m_symbolTable = aFactory.getStandardPrelude();
        m_catalog = aFactory.getCatalog();
        m_insertStatement = null;
        m_errorMessages = aFactory.getErrorMessages();
        pushExpressionStack(aFactory.makeExpressionParser(m_symbolTable));
    }

    public boolean hasErrors() {
        return m_errorMessages.numberErrors() > 0;
    }

    private final void addError(ISourceLocation location, String errorMessageFormat, Object ... args) {
        m_errorMessages.addError(location, errorMessageFormat, args);
    }

    private final void addWarning(ISourceLocation location, String errorMessageFormat, Object ... args) {
        m_errorMessages.addWarning(location, errorMessageFormat, args);
    }

    public final ErrorMessageSet getErrorMessages() {
        return m_errorMessages;
    }

    public String getErrorMessagesAsString() {
        StringBuffer sb = new StringBuffer();
        int nerrs = getErrorMessages().size();
        sb.append(String.format("\nOh, dear, there seem%s to be %serror%s here.\n",
                                nerrs > 1 ? "" : "s",
                                nerrs > 1 ? "" : "an ",
                                nerrs > 1 ? "s" : ""));
        for (ErrorMessage em : getErrorMessages()) {
            sb.append(String.format("line %d, column %d: %s: %s\n",
                                    em.getLine(),
                                    em.getCol(),
                                    em.getSeverity(),
                                    em.getMsg()));
        }
        return sb.toString();
    }

    /**
     * This convenience function makes the code slightly cleaner.
     *
     * @param aLineNumber
     * @param aColumnNumber
     * @return
     */
    private final ISourceLocation newSourceLocation(Token aToken) {
        return m_factory.newSourceLocation(aToken.getLine(), aToken.getCharPositionInLine());
    }
    ///**
    // * {@inheritDoc}
    // * /
    @Override public T visitCreate_table(SQLParserParser.Create_tableContext ctx) {
        String tableName = ctx.table_name().IDENTIFIER().getText();
        assert(m_currentlyCreatedTable == null);
        m_currentlyCreatedTable = m_factory.newTable(newSourceLocation(ctx.table_name().start),
                                                     tableName);
        //
        // Walk the subtree.
        //
        super.visitCreate_table(ctx);
        m_catalog.addTable(m_currentlyCreatedTable);
        m_currentlyCreatedTable = null;
        return m_state;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Define a column.</p>
     */
    @Override public T visitColumn_definition(SQLParserParser.Column_definitionContext ctx) {
        m_defaultValue       = null;
        m_hasDefaultValue    = false;
        m_isNullable         = true;
        m_isNull             = false;
        m_indexType          = IndexType.INVALID;
        assert(m_currentlyCreatedColumn == null);
        //
        // Walk the subtree.
        //
        super.visitColumn_definition(ctx);
        if (m_columnType == null) {
            addError(newSourceLocation(ctx.column_name().start), "Type expected");
            m_columnType = m_factory.getErrorType();
        }
        String colName = ctx.column_name().IDENTIFIER().getText();
        IColumn column = m_factory.newColumn(newSourceLocation(ctx.column_name().start),
                                             colName, m_columnType);
        column.setHasDefaultValue(m_hasDefaultValue);
        column.setDefaultValue(m_defaultValue);
        column.setIsPrimaryKey(m_indexType == IndexType.PRIMARY_KEY);
        column.setIsUniqueConstraint(m_indexType == IndexType.UNIQUE_KEY);
        column.setIsAssumedUnique(m_indexType == IndexType.ASSUMED_UNIQUE_KEY);
        column.setIsNullable(m_isNullable);
        column.setIsNull(m_isNull);
        m_currentlyCreatedTable.addColumn(colName, column);
        if (m_indexType != IndexType.INVALID) {
            // The factory knows how to compute the
            // name of the index since we don't actually
            // have a name.
            IIndex index = m_factory.newIndex(newSourceLocation(ctx.column_name().start),
                                              null,
                                              m_currentlyCreatedTable,
                                              column,
                                              m_indexType);
            m_currentlyCreatedTable.addIndex(index.getName(), index);
        }
        return m_state;
    }


    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override public T visitColumn_definition_metadata(SQLParserParser.Column_definition_metadataContext ctx) {
        //
        // Visit the children.
        //
        super.visitColumn_definition_metadata(ctx);
        return m_state;
    }

    /**
     * {@inheritDoc}
     */
    @Override public T visitColumn_default_value(SQLParserParser.Column_default_valueContext ctx) {
        super.visitColumn_default_value(ctx);
        if (m_hasDefaultValue) {
            addError(newSourceLocation(ctx.start),
                     "DEFAULT value specified more than once.");
        }
        else {
            m_hasDefaultValue = true;
            m_defaultValue = ctx.default_string().getText();
        }
        return m_state;
    }
    /**
     * {@inheritDoc}
     */
    @Override public T visitColumn_not_null(SQLParserParser.Column_not_nullContext ctx) {
        visitChildren(ctx);
        if ( ! m_isNullable) {
            addError(newSourceLocation(ctx.start),
                     "NOT NULL specified more than once.");
        }
        else {
            m_isNullable = false;
        }
        return m_state;
    }

    private String convertDefaultString(String string) {
        int startIdx = 0;
        int endIdx = string.length();
        boolean changed = false;
        if (string.startsWith("'")) {
            startIdx = 1;
            changed = true;
        }
        if (string.endsWith("'")) {
            endIdx = string.length();
            changed = true;
        }
        if (changed) {
            string = string.substring(startIdx, endIdx);
        }
        return string;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override public T visitIndex_type(SQLParserParser.Index_typeContext ctx) {
        super.visitChildren(ctx);
        if (ctx.PRIMARY() != null && ctx.KEY() != null) {
            if (m_indexType == IndexType.PRIMARY_KEY) {
                addError(newSourceLocation(ctx.start),
                         "PRIMARY KEY specified twice.");
            }
            else if (m_indexType != IndexType.INVALID) {
                addError(newSourceLocation(ctx.start),
                         "PRIMARY KEY and %s both specified.",
                         m_indexType.syntaxName());
            }
            else {
                m_indexType = IndexType.PRIMARY_KEY;
            }
        }
        if (ctx.UNIQUE() != null) {
            if (m_indexType == IndexType.UNIQUE_KEY) {
                addError(newSourceLocation(ctx.start),
                         "UNIQUE KEY specified twice.");
            }
            else if (m_indexType != IndexType.INVALID) {
                addError(newSourceLocation(ctx.start),
                         "UNIQUE and %s both specified.",
                         m_indexType.syntaxName());
            }
            m_indexType = IndexType.PRIMARY_KEY;
            m_indexType = IndexType.UNIQUE_KEY;
        }
        if (ctx.ASSUMEUNIQUE() != null) {
            if (m_indexType != IndexType.INVALID) {
                addError(newSourceLocation(ctx.start),
                         "ASSUMEUNIQUE and %s both specified.",
                         m_indexType.syntaxName());
            }
            else {
                m_indexType = IndexType.PRIMARY_KEY;
            }
            m_indexType = IndexType.ASSUMED_UNIQUE_KEY;
        }
        return m_state;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Parse a data type and leave it in m_type.</p>
     */
    @Override public T visitDatatype(SQLParserParser.DatatypeContext ctx) {
        assert(m_constantIntegerValues == null);
        m_constantIntegerValues = new ArrayList<>();
        super.visitChildren(ctx);
        String typeName = ctx.datatype_name().IDENTIFIER().getText();
        m_columnType = m_symbolTable.getType(typeName);
        if (m_columnType == null) {
            addError(newSourceLocation(ctx.start),
                     "The type name %s is not defined.",
                     typeName);
        }
        if (m_columnType.getTypeKind().isFixedPoint()) {
            //
            // Warn that we are ignoring scale and
            // precision here.
            //
            if (m_constantIntegerValues.size() > 0) {
                addWarning(newSourceLocation(ctx.start),
                           "The fixed point type %s has a fixed scale and precision.  These arguments will be ignored.",
                           m_columnType.getName().toUpperCase());
            }
        }
        else if (m_columnType instanceof IStringType
                 || m_columnType instanceof IGeographyType) {
            long size;
            //
            // We should get zero or one argument.  It is an
            // error otherwise.
            //
            if (m_columnType instanceof IStringType
                    && m_constantIntegerValues.size() == 0) {
                size = DEFAULT_STRING_SIZE;
            } else if (m_constantIntegerValues.size() == 1) {
                size = Long.parseLong(m_constantIntegerValues.get(0));
                m_columnType = m_columnType.makeInstance(newSourceLocation(ctx.start), size);
            } else {
                addError(newSourceLocation(ctx.start),
                         "The type %s takes only one size parameter.",
                         m_columnType.getName().toUpperCase());
                m_columnType = m_factory.getErrorType();
            }
        }
        else if (m_constantIntegerValues.size() > 0) {
            addError(newSourceLocation(ctx.start),
                     "The type %s takes no parameters.",
                     m_columnType.getName());
        }
        m_constantIntegerValues = null;
        return m_state;
    }

    /**
     * {@inheritDoc}
     */
    @Override public T visitConstant_integer_value(SQLParserParser.Constant_integer_valueContext ctx) {
        assert(m_constantIntegerValues != null);
        m_constantIntegerValues.add(ctx.getText());
        return m_state;
    }

    /**
     * {@inheritDoc}
     */
    @Override public T visitInsert_statement(SQLParserParser.Insert_statementContext ctx) {
        //
        // Walk the subtree.
        //
        m_constantIntegerValues = new ArrayList<>();
        super.visitInsert_statement(ctx);
        m_isUpsert = (ctx.UPSERT() != null);
        try {
            String tableName = ctx.table_name().IDENTIFIER().getText();
            ITable table = m_catalog.getTableByName(tableName);
            if (table == null) {
                addError(newSourceLocation(ctx.table_name().start),
                         "Undefined table name %s",
                         tableName);
                return m_state;
            }
            //
            // Calculate names and values.  Don't do any semantic checking here.
            // We'll do it all later.
            //
            if (ctx.insert_values().constant_value_expression() == null
                    || ctx.insert_values().constant_value_expression().size() == 0) {
                addError(newSourceLocation(ctx.start),
                         "No values specified.");
                return m_state;
            }
            List<IColumnIdent> columns = new ArrayList<>();
            if (ctx.column_name() != null && ctx.column_name().size() > 0) {
                for (SQLParserParser.Column_nameContext cnctx : ctx.column_name()) {
                    String colName = cnctx.IDENTIFIER().getText();
                    columns.add(m_factory.makeColumnRef(colName, newSourceLocation(cnctx.start)));
                }
            } else {
                for (int colIdx = 0; colIdx < table.getColumnCount(); colIdx += 1) {
                    IColumn col = table.getColumnByIndex(colIdx);
                    assert(col != null);
                    String cname = col.getName();
                    assert(cname != null);
                    columns.add(m_factory.makeColumnRef(cname, -1, -1));
                }
            }

            m_insertStatement = m_factory.newInsertStatement();
            m_insertStatement.addTable(table);
            List<String> colVals = new ArrayList<>();
            for (SQLParserParser.Constant_value_expressionContext val : ctx.insert_values().constant_value_expression()) {
                //
                // TODO: This is not right.  These are expressions in general.  We
                // need to traffic in Semantinos here.
                //
                String valStr = val.constant_integer_value().NUMBER().getText();
                colVals.add(valStr);
            }
            m_insertStatement.addColumns(newSourceLocation(ctx.start),
                                         m_errorMessages,
                                         columns,
                                         colVals);
            return m_state;
        } finally {
            m_constantIntegerValues = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override public T visitQuery_expression_body(SQLParserParser.Query_expression_bodyContext ctx) {
        if ( ctx.query_primary() != null) {
            visitQuery_primary(ctx.query_primary());
        } else {
            QuerySetOp qSetOp;
            if (ctx.query_intersect_op() != null) {
                qSetOp = QuerySetOp.INTERSECT_OP;
            } else if (ctx.query_union_op().UNION() != null) {
                if (ctx.query_union_op().ALL() != null) {
                    qSetOp = QuerySetOp.UNION_ALL_OP;
                } else {
                    qSetOp = QuerySetOp.UNION_OP;
                }
            } else {
                qSetOp = QuerySetOp.EXCEPT_OP;
            }
            visitQuery_expression_body(ctx.query_expression_body(1));
            visitQuery_expression_body(ctx.query_expression_body(0));
            ISelectQuery leftOp = popSelectQueryStack();
            ISelectQuery rightOp = popSelectQueryStack();
            pushSelectQuery(m_factory.newCompoundQuery(qSetOp, leftOp, rightOp));
        }
        return m_state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T visitSimple_table(SQLParserParser.Simple_tableContext ctx) {
        if (ctx.explicit_table() != null) {
            // This seems stingy.  Maybe we should implement this.
            m_errorMessages.addError(newSourceLocation(ctx.explicit_table().start),
                                     "Explicit tables are not allowed in select statements.");
        } else {
            pushSelectQuery(m_factory.newSimpleTableSelectQuery(newSourceLocation(ctx.start),
                                                                m_symbolTable,
                                                                m_factory,
                                                                m_errorMessages));
            assert(ctx.query_specification() != null);
            visitQuery_specification(ctx.query_specification());
        }
        return m_state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T visitQuery_specification(SQLParserParser.Query_specificationContext ctx) {
        SelectQueryQuantifier q = ctx.set_quantifier().quantifier;
        getTopSelectQuery().setQuantifier(q);
        // We have to visit the table expression before the
        // select list, because the select list will have references
        // to tables.
        visitTable_expression(ctx.table_expression());
        visitSelect_list(ctx.select_list());
        return m_state;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     */
    @Override
    public T visitTable_reference(SQLParserParser.Table_referenceContext ctx) {
        assert(ctx.table_factor(0) != null);
        visitTable_factor(ctx.table_factor(0));
        if (ctx.join_operator() == null) {
            assert(ctx.join_condition() == null);
        } else {
            assert(ctx.join_operator().size() == ctx.join_condition().size());
            for (int idx = 0; idx < ctx.join_operator().size(); idx += 1) {
                ISelectQuery query = getTopSelectQuery();
                query.setJoinCondition(
                        m_factory.newJoinTree(ctx.join_operator(idx).operator,
                                              query.getJoinCondition(),
                                              ctx.join_condition(idx).semantino));

            }
        return visitChildren(ctx);
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public T visitDerived_column(SQLParserParser.Derived_columnContext ctx) {
        assert(m_expressionStack.size() == 0);
        super.visitDerived_column(ctx);
        ISelectQuery topQuery = getTopSelectQuery();
        if (ctx.ASTERISK() != null) {
            getTopSelectQuery().addStarProjection(newSourceLocation(ctx.ASTERISK().getSymbol()));
        } else {
            String alias = (ctx.column_alias_name() != null)
                                ? ctx.column_alias_name().getText()
                                : topQuery.getNextDisplayAlias();
            getTopSelectQuery().addProjection(newSourceLocation(ctx.start),
                                              getTopExpressionParser().popSemantino(),
                                              alias);
        }
        return m_state;
    }

    private void binOp(String opString, ISourceLocation aLoc) {
        IOperator op = m_factory.getExpressionOperator(opString);
        if (op == null) {
            addError(aLoc,
                     "Unknown operator \"%s\"",
                     opString);
            return;
        }

        //
        // Now, given the kind of operation, calculate the output.
        //
        ISemantino rightoperand = getTopExpressionParser().popSemantino();
        ISemantino leftoperand = getTopExpressionParser().popSemantino();
        ISemantino answer;
        if (op.isArithmetic()) {
            answer = getTopExpressionParser().getSemantinoMath(op,
                                                              leftoperand,
                                                              rightoperand);
        } else if (op.isRelational()) {
            answer = getTopExpressionParser().getSemantinoCompare(op,
                                                                 leftoperand,
                                                                 rightoperand);
        } else if (op.isBoolean()) {
            answer = getTopExpressionParser().getSemantinoBoolean(op,
                                                                 leftoperand,
                                                                 rightoperand);
        } else {
            addError(aLoc,
                    "Internal Error: Unknown operation kind for operator \"%s\"",
                    opString);
            return;
        }
        if (answer == null) {
            addError(aLoc,
                     "Incompatible argument types %s and %s",
                     leftoperand.getType().getName(),
                     rightoperand.getType().getName());
            return;
        }
        getTopExpressionParser().pushSemantino(answer);
    }

    private void unaryOp(String aOpString, ISourceLocation aLoc) {
        IOperator op = m_factory.getExpressionOperator(aOpString);
        if (op == null) {
            addError(aLoc,
                     "Unknown operator \"%s\"",
                     aOpString);
            return;
        }

        //
        // Now, given the kind of operation, calculate the output.
        //
        ISemantino operand = getTopExpressionParser().popSemantino();
        ISemantino answer;
        if (op.isBoolean()) {
            answer = getTopExpressionParser().getSemantinoBoolean(op,
                                                                  operand);
        } else {
            addError(aLoc,
                    "Internal Error: Unknown operation kind for operator \"%s\"",
                    aOpString);
            answer = m_factory.getErrorSemantino();
        }
        getTopExpressionParser().pushSemantino(answer);
    }

    ///**
    // * {@inheritDoc}
    // *
    // * <p>Combine two Semantinos with a product op.</p>
    // * /
    @Override
    public T visitTimes_expr(SQLParserParser.Times_exprContext ctx) {
        //
        // Walk the subtree.
        //
        super.visitTimes_expr(ctx);

        binOp(ctx.mop.getText(),
              newSourceLocation(ctx.mop.start));
        return m_state;
    }

    ///**
    // * {@inheritDoc}
    // *
    // * <p>Combine two Semantinos with an add op.</p>
    // * /
    @Override public T visitAdd_expr(SQLParserParser.Add_exprContext ctx) {
        //
        // Walk the subtree.
        //
        super.visitAdd_expr(ctx);
        binOp(ctx.sop.getText(),
              newSourceLocation(ctx.sop.start));
        return m_state;
    }

    ///**
    // * {@inheritDoc}
    // *
    // * <p>Combine two Semantinos with a relational op.</p>
    // * /
    @Override public T visitRel_expr(SQLParserParser.Rel_exprContext ctx) {
        //
        // Walk the subtree.
        //
        super.visitRel_expr(ctx);

        binOp(ctx.rop.getText(),
              newSourceLocation(ctx.rop.start));
        return m_state;
    }

    ///**
    // * {@inheritDoc}
    // *
    // * /
    @Override public T visitDisjunction_expr(SQLParserParser.Disjunction_exprContext ctx) {
        //
        // Walk the subtree.
        //
        super.visitDisjunction_expr(ctx);

        binOp(ctx.OR().getText(),
              newSourceLocation(ctx.OR().getSymbol()));
        return m_state;
    }

    ///**
    // * {@inheritDoc}
    // *
    // * /
    @Override public T visitConjunction_expr(SQLParserParser.Conjunction_exprContext ctx) {
        //
        // Walk the subtree.
        //
        super.visitConjunction_expr(ctx);

        binOp(ctx.AND().getText(),
              newSourceLocation(ctx.AND().getSymbol()));
        return m_state;
    }
    ///**
    // * {@inheritDoc}
    // * /
    @Override public T visitNot_expr(org.voltdb.sqlparser.syntax.grammar.SQLParserParser.Not_exprContext ctx) {
        //
        // Walk the subtree.
        //
        super.visitNot_expr(ctx);
        unaryOp(ctx.NOT().getText(),
                newSourceLocation(ctx.NOT().getSymbol()));
        return m_state;
    };

    ///**
    // * {@inheritDoc}
    // *
    // * <p>Push a true semantino</p>
    // * /
    @Override public T visitTrue_expr(SQLParserParser.True_exprContext ctx) {
        super.visitTrue_expr(ctx);
        IType boolType = m_factory.getBooleanType();
        getTopExpressionParser().pushSemantino(getTopExpressionParser().getConstantSemantino(Boolean.valueOf(true), boolType));
        return m_state;
    }

    ///**
    // * {@inheritDoc}
    // *
    // * <p>Push a False Semantino.</p>
    // * /
    @Override public T visitFalse_expr(SQLParserParser.False_exprContext ctx) {
        super.visitFalse_expr(ctx);
        IType boolType = m_factory.getBooleanType();
        getTopSelectQuery().pushSemantino(getTopExpressionParser().getConstantSemantino(Boolean.valueOf(false), boolType));
        return m_state;
    }

    /*
    ///**
    // * {@inheritDoc}
    // * /
    @Override public T visitColref_expr(SQLParserParser.Colref_exprContext ctx) {
        //
        // Walk the subtree.
        //
        super.visitColref_expr(ctx);

        SQLParserParser.Column_refContext crctx = ctx.column_ref();
        String tableName = (crctx.table_name() != null) ? crctx.table_name().IDENTIFIER().getText() : null;
        String columnName = crctx.column_name().IDENTIFIER().getText();
        ISemantino crefSemantino = getTopExpressionParser().getColumnSemantino(columnName, tableName);
        getTopExpressionParser().pushSemantino(crefSemantino);
        return m_state;
    }
    ///**
    // * {@inheritDoc}
    // * /
    @Override public T visitNumeric_expr(SQLParserParser.Numeric_exprContext ctx) {
        super.visitNumeric_expr(ctx);
        IType intType = m_symbolTable.getType("integer");
        getTopExpressionParser().pushSemantino(getTopExpressionParser().getConstantSemantino(Integer.valueOf(ctx.NUMBER().getText()),
                                                                     intType));
        return m_state;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     * /
    @Override public T visitString_expr(SQLParserParser.String_exprContext ctx) { return visitChildren(ctx); }
    */

    @Override
    public void reportAmbiguity(Parser aArg0, DFA aArg1, int aArg2, int aArg3,
                                boolean aArg4, java.util.BitSet aArg5, ATNConfigSet aArg6) {
        // Nothing to be done here.
    }

    @Override
    public void reportAttemptingFullContext(Parser aArg0, DFA aArg1, int aArg2,
                                            int aArg3, java.util.BitSet aArg4, ATNConfigSet aArg5) {
        // Nothing to be done here.
    }

    @Override
    public void reportContextSensitivity(Parser aArg0, DFA aArg1, int aArg2,
                                         int aArg3, int aArg4, ATNConfigSet aArg5) {
        // Nothing to be done here.
    }

    @Override
    public void syntaxError(Recognizer<?, ?> aArg0, Object aTokObj, int aLine,
                            int aCol, String msg, RecognitionException aArg5) {
        addError(m_factory.newSourceLocation(aLine, aCol), msg);
    }

    public final ISelectQuery getSelectQuery() {
        return getTopSelectQuery();
    }

    public final IInsertStatement getInsertStatement() {
        return m_insertStatement;
    }

    public ICatalogAdapter getCatalogAdapter() {
        return m_catalog;
    }

    protected final IParserFactory getFactory() {
        return m_factory;
    }
    private IExpressionParser getTopExpressionParser() {
        assert(m_expressionStack.size() > 0);
        return m_expressionStack.get(m_expressionStack.size() - 1);
    }
    private void pushExpressionStack(IExpressionParser aParser) {
        m_expressionStack.add(aParser);
    }
    private IExpressionParser popExpressionStack() {
        assert(m_expressionStack.size() > 0);
        return m_expressionStack.remove(m_expressionStack.size() - 1);
    }

    private ISelectQuery getTopSelectQuery() {
        assert(m_selectQueryStack.size() > 0);
        return m_selectQueryStack.get(m_selectQueryStack.size() - 1);
    }

    private void pushSelectQuery(ISelectQuery aQuery) {
        m_selectQueryStack.add(aQuery);
    }
    private ISelectQuery popSelectQueryStack() {
        assert(m_selectQueryStack.size() > 0);
        return m_selectQueryStack.remove(m_selectQueryStack.size() - 1);
    }

    protected ISemantino getResultSemantino() {
        assert (m_selectQueryStack.size() == 0);
        assert (m_expressionStack.size() == 1);
        return (m_expressionStack.get(m_expressionStack.size() - 1).popSemantino());
    }
}
