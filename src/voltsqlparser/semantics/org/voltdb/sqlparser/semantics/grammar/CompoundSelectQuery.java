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

import java.util.List;

import org.voltdb.sqlparser.syntax.grammar.IJoinTree;
import org.voltdb.sqlparser.syntax.grammar.IOperator;
import org.voltdb.sqlparser.syntax.grammar.ISelectQuery;
import org.voltdb.sqlparser.syntax.grammar.ISemantino;
import org.voltdb.sqlparser.syntax.grammar.Projection;
import org.voltdb.sqlparser.syntax.grammar.QuerySetOp;
import org.voltdb.sqlparser.syntax.grammar.SelectQueryQuantifier;
import org.voltdb.sqlparser.syntax.symtab.IAST;
import org.voltdb.sqlparser.syntax.symtab.IExpressionParser;
import org.voltdb.sqlparser.syntax.symtab.ISourceLocation;
import org.voltdb.sqlparser.syntax.symtab.ISymbolTable;
import org.voltdb.sqlparser.syntax.symtab.ITable;
import org.voltdb.sqlparser.syntax.symtab.IType;

public class CompoundSelectQuery implements ISelectQuery, IDQLStatement {

    ISelectQuery m_left;
    ISelectQuery m_right;
    QuerySetOp   m_op;

    public CompoundSelectQuery(QuerySetOp op, ISelectQuery left, ISelectQuery right) {
        m_op = op;
        m_left = left;
        m_right = right;
    }

    @Override
    public boolean isSimpleTable() {
        return false;
    }

    @Override
    public QuerySetOp getSetOp() throws Exception {
        return m_op;
    }

    @Override
    public ISelectQuery getLeftQuery() {
        return m_left;
    }

    @Override
    public ISelectQuery getRightQuery() {
        // TODO Auto-generated method stub
        return m_right;
    }

    @Override
    public void pushSemantino(ISemantino aColumnSemantino) {
        assert(false);
    }

    @Override
    public ISemantino popSemantino() {
        assert(false);
        return null;
    }

    @Override
    public String printProjections() {
        assert(false);
        return null;
    }

    @Override
    public void addTable(ITable aITable, String aAlias) {
        assert(false);
    }

    @Override
    public String printTables() {
        assert(false);
        return null;
    }

    @Override
    public boolean hasSemantinos() {
        assert(false);
        return false;
    }

    @Override
    public ISemantino getColumnSemantino(String aColumnName, String aTableName) {
        assert(false);
        return null;
    }

    @Override
    public ISemantino getConstantSemantino(Object value, IType type) {
        assert(false);
        return null;
    }

    @Override
    public ISemantino getSemantinoMath(IOperator aOperator, ISemantino aLeftoperand, ISemantino aRightoperand) {
        assert(false);
        return null;
    }

    @Override
    public ISemantino getSemantinoCompare(IOperator aOperator, ISemantino aLeftoperand, ISemantino aRightoperand) {
        assert(false);
        return null;
    }

    @Override
    public ISemantino getSemantinoBoolean(IOperator aOperator, ISemantino aLeftoperand, ISemantino aRightoperand) {
        assert(false);
        return null;
    }

    @Override
    public List<Projection> getProjections() {
        assert(false);
        return null;
    }

    @Override
    public void setWhereCondition(ISemantino aRet) {
        assert(false);
    }

    @Override
    public IAST getWhereCondition() {
        assert(false);
        return null;
    }

    @Override
    public ISymbolTable getTables() {
        assert(false);
        return null;
    }

    @Override
    public void setAST(IAST aMakeQueryAST) {
        assert(false);
    }

    @Override
    public boolean validate() {
        assert(false);
        return false;
    }

    @Override
    public IExpressionParser getExpressionParser() {
        assert(false);
        return null;
    }

    @Override
    public void setExpressionParser(IExpressionParser expr) {
        assert(false);
    }

    @Override
    public void addJoinTree(IJoinTree joinTree) {
        assert(false);
    }

    @Override
    public void addProjection(ISourceLocation aLoc, ISemantino aSemantino, String aAlias) {
        assert(false);
    }

    @Override
    public void addStarProjection(ISourceLocation aLoc) {
        assert(false);
    }

    @Override
    public String getNextDisplayAlias() {
        assert(false);
        return null;
    }

    @Override
    public void setQuantifier(SelectQueryQuantifier q) {
        assert(false);
    }

}
