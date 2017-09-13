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
 package org.voltdb.sqlparser.syntax.grammar;

import java.util.List;

import org.voltdb.sqlparser.syntax.symtab.IAST;
import org.voltdb.sqlparser.syntax.symtab.IExpressionParser;
import org.voltdb.sqlparser.syntax.symtab.ISourceLocation;
import org.voltdb.sqlparser.syntax.symtab.ISymbolTable;
import org.voltdb.sqlparser.syntax.symtab.ITable;
import org.voltdb.sqlparser.syntax.symtab.IType;

/**
 * This holds all the parts of a select statement.
 *
 * @author bwhite
 */
public interface ISelectQuery {

    /**
     * Add a set of order by keys to a query.
     * @param aOrderByKeys
     */
    void addOrderBy(List<ISemantino> aOrderByKeys);
    /**
     * Add a limit to a query;
     */
    void addLimit(long aLimit);
    /**
     * Add an offset.
     */
    void addOffset(long aOffset);
    /**
     * Convert a string into an integer.
     */
    long convertConstantIntegerConstant(String aConstant);
    /**
     * FENCE.
     */
    /**
     * Add a projection.  This is a select list element.
     *
     * @param aTableName
     * @param aColumnName
     * @param aAlias
     * @param aLineNo
     * @param aColNo
     */
    void addProjection(ISourceLocation aLoc, ISemantino aSemantino, String aAlias);

    /**
     * Add a star projection.  This is also a select list element.
     * @param aLoc
     */
    void addStarProjection(ISourceLocation aLoc);

    void pushSemantino(ISemantino aColumnSemantino);

    ISemantino popSemantino();

    String printProjections();

    void addTable(ITable aITable, String aAlias);

    String printTables();

    boolean hasSemantinos();

    ISemantino getColumnSemantino(String aColumnName, String aTableName);

    ISemantino getConstantSemantino(Object value, IType type);

    ISemantino getSemantinoMath(IOperator aOperator, ISemantino aLeftoperand,
            ISemantino aRightoperand);

    ISemantino getSemantinoCompare(IOperator aOperator, ISemantino aLeftoperand,
            ISemantino aRightoperand);

    ISemantino getSemantinoBoolean(IOperator aOperator, ISemantino aLeftoperand,
            ISemantino aRightoperand);

    List<Projection> getProjections();

    void setWhereCondition(ISemantino aRet);

    IAST getWhereCondition();

    ISymbolTable getTables();

    void setAST(IAST aMakeQueryAST);

    boolean validate();

    IExpressionParser getExpressionParser();

    void setExpressionParser(IExpressionParser expr);

    /**
     * Add a join condition to the select query.
     * @param joinTree
     */
    void addJoinTree(IJoinTree joinTree);

    /**
     * Get the next display list alias for this
     * select statement.
     * @return
     */
    String getNextDisplayAlias();

    void setQuantifier(SelectQueryQuantifier q);

    boolean isSimpleTable();

    QuerySetOp getSetOp() throws Exception;

    ISelectQuery getLeftQuery();

    ISelectQuery getRightQuery();

    /**
     * Return the join condition of the current table_reference in the
     * select list.
     */
    IJoinTree getJoinCondition();
    /**
     * Set the join condition for the current table_reference.
     * @param aCondition
     */
    void setJoinCondition(IJoinTree aCondition);
}
