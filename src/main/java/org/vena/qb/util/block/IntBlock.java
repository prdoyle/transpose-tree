/*
 * 
 * THIS IS A CUT+PASTE FROM ShortBlock.  If you want to modify this, you
 * probably want to modify that too, then copy it here.
 * 
 */

package org.vena.qb.util.block;

import java.util.Arrays;

public final class IntBlock extends PrimitiveBlockImpl {
	
	int[] storage;

	protected IntBlock(int initialCapacity) {
		storage = EMPTY;
		accommodateIndex(initialCapacity);
	}

	@Override
	public void clear() {
		super.clear();
		storage = EMPTY;
	}

	@Override
	public long get(int index) {
		return storage[index];
	}

	public int at(int index){ return storage[index]; }

	@Override
	public void set(int index, long value) throws ValueBeyondLimitException {
		set(index, (int)validate(value));
	}

	public void set(int index, int value) {
		if (index < 0 || index > population) {
			throw new IndexOutOfBoundsException(IntBlock.class.getSimpleName() + ": index " + index + " is outside of 0.." + population);
		}
		accommodateIndex(index);
		storage[index] = value;
		if (index == population) {
			population++;
		}
	}

	@Override
	protected void accommodateIndex(int index) {
		if (storage.length <= index) {
			storage = Arrays.copyOf(storage, newLengthFor(index));
		}
	}

	@Override protected boolean isValid(long value) { return ((int)value == value); }
	public static boolean  isValidValue(long value) { return ((int)value == value); }

	static final int[] EMPTY = new int[0];

	@Override
	public void shrinkwrap(int roomPercentage) {
		int newLength = shrinkwrappedLength(storage.length, population, roomPercentage);
		if (newLength != storage.length) {
			storage = Arrays.copyOf(storage, newLength);
		}
	}

}
