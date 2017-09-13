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
package org.voltdb.sqlparser.semantics.grammar;

import java.util.ArrayList;
import java.util.List;

import org.voltdb.sqlparser.semantics.symtab.ExpressionParser;
import org.voltdb.sqlparser.semantics.symtab.JoinTree;
import org.voltdb.sqlparser.semantics.symtab.Semantino;
import org.voltdb.sqlparser.semantics.symtab.SymbolTable;
import org.voltdb.sqlparser.semantics.symtab.Table;
import org.voltdb.sqlparser.syntax.grammar.IJoinTree;
import org.voltdb.sqlparser.syntax.grammar.IOperator;
import org.voltdb.sqlparser.syntax.grammar.ISelectQuery;
import org.voltdb.sqlparser.syntax.grammar.ISemantino;
import org.voltdb.sqlparser.syntax.grammar.Projection;
import org.voltdb.sqlparser.syntax.grammar.QuerySetOp;
import org.voltdb.sqlparser.syntax.grammar.SelectQueryQuantifier;
import org.voltdb.sqlparser.syntax.symtab.IAST;
import org.voltdb.sqlparser.syntax.symtab.IExpressionParser;
import org.voltdb.sqlparser.syntax.symtab.IParserFactory;
import org.voltdb.sqlparser.syntax.symtab.ISourceLocation;
import org.voltdb.sqlparser.syntax.symtab.ITable;
import org.voltdb.sqlparser.syntax.symtab.IType;
import org.voltdb.sqlparser.syntax.util.ErrorMessageSet;



public class SimpleTableSelectQuery implements ISelectQuery, IDQLStatement {
    List<Projection> m_projections = new ArrayList<>();

    private ExpressionParser m_expressionParser;
    private SymbolTable m_tables;
    private Semantino m_whereCondition = null;
    private IAST m_ast;
    private ErrorMessageSet m_errorMessages;
    private JoinTree m_joinTree = null;

    public SimpleTableSelectQuery(ISourceLocation aLoc,
                                  SymbolTable aParent,
                                  IParserFactory aFactory,
                                  ErrorMessageSet aErrorMessages) {
        m_tables = new SymbolTable(aParent);
        m_errorMessages = aErrorMessages;
        m_expressionParser = new ExpressionParser(aFactory, aParent);
    }

    @Override
    public List<Projection> getProjections() {
        return m_projections;
    }

    @Override
    public void addTable(ITable aTable, String aAlias) {
        if (aAlias != null)
            m_tables.addTable((Table)aTable, aAlias);
        else
            m_tables.addTable((Table)aTable, aTable.getName());
    }

    public ITable getTableByName(String aName) {
        return m_tables.getTable(aName);
    }

    @Override
    public void pushSemantino(ISemantino aColumnSemantino) {
            m_expressionParser.pushSemantino(aColumnSemantino);
    }

    @Override
    public ISemantino popSemantino() {
        return m_expressionParser.popSemantino();
    }

    @Override
    public Semantino getSemantinoMath(IOperator aOperator,
                                       ISemantino aLeftoperand,
                                       ISemantino aRightoperand) {
        return m_expressionParser.getSemantinoMath(aOperator, aLeftoperand, aRightoperand);
    }

    @Override
    public ISemantino getSemantinoCompare(IOperator aOperator,
                                       ISemantino aLeftoperand,
                                       ISemantino aRightoperand) {
        return m_expressionParser.getSemantinoCompare(aOperator, aLeftoperand, aRightoperand);
    }

    @Override
    public ISemantino getSemantinoBoolean(IOperator aOperator,
                                       ISemantino aLeftoperand,
                                       ISemantino aRightoperand) {
        return m_expressionParser.getSemantinoBoolean(aOperator, aLeftoperand, aRightoperand);
    }

    @Override
    public ISemantino getColumnSemantino(String aColumnName, String aTableName) {
        return m_expressionParser.getColumnSemantino(aColumnName, aTableName);
    }

    @Override
    public String printProjections() {
        String out = "projections: ";
        for (int i=0;i<m_projections.size();i++) {
                out += "["+m_projections.get(i).toString()+"]";
        }
        return out;
    }

    @Override
    public String printTables() {
        String out = "Tables: ";
        out += m_tables.toString();
        return out;
    }

    @Override
    public boolean hasSemantinos() {
        return !m_expressionParser.isEmpty();
    }

    @Override
    public void setWhereCondition(ISemantino aSemantino) {
        m_whereCondition = (Semantino) aSemantino;
    }

    @Override
    public IAST getWhereCondition() {
        if (m_whereCondition != null) {
            return m_whereCondition.getAST();
        }
        return null;
    }

    @Override
    public SymbolTable getTables() {
        return m_tables;
    }

    @Override
    public void setAST(IAST aMakeQueryAST) {
        m_ast = aMakeQueryAST;
    }

    public IAST getAST() {
        return m_ast;
    }

    @Override
    public boolean validate() {
        if (m_errorMessages.size() > 0) {
            return false;
        }
        return true;
    }

    @Override
    public ISemantino getConstantSemantino(Object value, IType type) {
        return m_expressionParser.getConstantSemantino(value, type);
    }

    @Override
    public final IExpressionParser getExpressionParser() {
        return m_expressionParser;
    }

    @Override
    public final void setExpressionParser(IExpressionParser aExpressionParser) {
        assert(aExpressionParser == null || aExpressionParser instanceof ExpressionParser);
        m_expressionParser = (ExpressionParser)aExpressionParser;
    }

    @Override
    public boolean isSimpleTable() {
        return true;
    }

    @Override
    public QuerySetOp getSetOp() throws Exception {
        return null;
    }

    @Override
    public ISelectQuery getLeftQuery() {
        return null;
    }

    @Override
    public ISelectQuery getRightQuery() {
        return null;
    }

    @Override
    public void addJoinTree(IJoinTree joinTree) {
        // TODO Auto-generated method stub

    }

    @Override
    public void addProjection(ISourceLocation aLoc, ISemantino aSemantino, String aAlias) {
        // TODO Auto-generated method stub

    }

    @Override
    public void addStarProjection(ISourceLocation aLoc) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getNextDisplayAlias() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setQuantifier(SelectQueryQuantifier q) {
        // TODO Auto-generated method stub

    }
}
