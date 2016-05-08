package org.vena.qb.util.block;

/**
 * TODO: Perhaps I should have treated the smaller integers as unsigned.  If
 * these data are usually indexes into other data structures, negative values
 * are useless.  It's just that unsigned integers are error-prone because of the
 * discontinuity right near zero, so I tend to avoid them on principle, but I'm
 * not sure it's wise in this case.
 * 
 * In particular, this makes a ShortBlock behave differently from a BitBlock
 * with 16-bit chunks, because the latter is unsigned.  (BitBlock is unsigned
 * only because I couldn't stomach the prospect of having 1-bit chunks getting
 * the values  "0" and "-1".  That was just too counterintuitive.)
 * 
 * @author Patrick Doyle
 *
 */
abstract class PrimitiveBlockImpl extends PrimitiveBlock {

	protected int population = 0;
	public int size() { return population; }
	protected void clear() { population = 0; } // Subclasses should override this and free up storage as appropriate

}
