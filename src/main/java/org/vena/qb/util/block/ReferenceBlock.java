package org.vena.qb.util.block;

import java.util.ArrayList;

/**
 * Exactly like an ArrayList, except you're allowed to call {@link #set} with an
 * index equal to {@link #size()}, having the same effect as {@link #add}.
 * 
 * @author Patrick Doyle
 *
 * @param <E> Element type
 */
public class ReferenceBlock<E> extends ArrayList<E> {

	@Override
	public E set(int index, E element) {
		if (index == size()) {
			// TODO: Grow more slowly when memory is tight
			super.add(element);
			return null;
		} else {
			return super.set(index, element);
		}
	}
	
	private static final long serialVersionUID = 2172610343721307492L;

}
