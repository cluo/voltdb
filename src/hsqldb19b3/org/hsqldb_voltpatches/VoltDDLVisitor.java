package org.hsqldb_voltpatches;

import org.voltdb.sqlparser.semantics.grammar.InsertStatement;
import org.voltdb.sqlparser.semantics.symtab.ParserFactory;
import org.voltdb.sqlparser.syntax.VoltSQLState;
import org.voltdb.sqlparser.syntax.VoltSQLVisitor;
import org.voltdb.sqlparser.syntax.grammar.IInsertStatement;
import org.voltdb.sqlparser.syntax.grammar.ISemantino;
import org.voltdb.sqlparser.syntax.symtab.IAST;

public class VoltDDLVisitor extends VoltSQLVisitor<VoltSQLState> {
    public VoltDDLVisitor(ParserFactory aFactory, VoltSQLState state) {
        super(aFactory, state);
    }

    /**
     * Figure out what kind of statement this is and
     * calculate the VoltXMLElement for it.  This needs to be moved
     * someplace else, probably to the factory object.
     *
     * @return
     */
    public VoltXMLElement getVoltXML() {
        IInsertStatement istat = getInsertStatement();
        if (istat != null) {
            return getVoltXML(istat);
        }
        ISemantino iresult = getResultSemantino();
        if (iresult != null) {
        	if (iresult.getType().isVoidType()) {
        		IAST  resAST  = iresult.getAST();
        		if (resAST instanceof VoltXMLElement) {
        			return (VoltXMLElement)resAST;
        		}
        	}
        }
        return null;
    }

	/**
     * Calculate the VoltXMLElement for an insert statement.  This
     * probably needs to be moved to the factory object.
     *
     * @param aInsertStatement
     * @return
     */
    private VoltXMLElement getVoltXML(IInsertStatement aInsertStatement) {
        assert(aInsertStatement instanceof InsertStatement);
        return (VoltXMLElement)getFactory().makeInsertAST(aInsertStatement);
    }
}
