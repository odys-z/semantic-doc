package io.oz.sdoc;

import io.odysz.anson.Anson;
import io.odysz.transact.sql.parts.Logic;
import io.odysz.transact.sql.parts.condition.Predicate;

/**
 * A predicate of a picking item, e.g. {@link DbPick#when}: {@link #op} of semantic.transact's
 * {@link Logic.op}, with its operands, built as a {@link Predicate} (never sql text).
 * <pre>
 * { "op": "isNotnull", "l": "builtAt" }    builtAt is not null
 * { "op": "eq", "l": "state", "r": "'ready'" }
 * </pre>
 *
 * @since 0.1.5
 * @author ody
 */
public class DbLogic extends Anson {
	/** name of {@link Logic.op}, e.g. "eq", "isNotnull" */
	public String op;
	/** left operand, an expression, e.g. a column */
	public String l;
	/** right operand, an expression, e.g. "'ready'"; null for unary ops */
	public String r;

	public DbLogic() { }

	public DbLogic(String op, String l, String r) {
		this.op = op;
		this.l = l;
		this.r = r;
	}

	public Predicate predicate() {
		return new Predicate(Logic.op.valueOf(op), l, r);
	}
}
