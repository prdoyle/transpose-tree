package org.vena.qb.util.block;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.ConcurrentModificationException;
import java.util.PrimitiveIterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.vena.qb.util.block.PrimitiveBlock.ValueBeyondLimitException;

/**
 * A red-black tree that keeps per-node data in arrays, thereby allocating a
 * small fixed number of objects regardless of how many items are inserted.
 * Useful in cases where the memory overhead of Java objects is prohibitive.
 * 
 * Users of TransposeTree keep their node data in one or more arrays, and use
 * this class to track which array element corresponds to which node in the
 * tree.  For example, if you wanted to keep two ints and a float per node,
 * you'd allocate two int arrays and one float arrays, and use this class to
 * keep track of which array elements correspond to which tree node.  That way
 * you can store many thousands of nodes using just a dozen Java objects.
 * 
 * Unfortunately, using this class is made awkward by the fact that it can never
 * manipulate keys directly. The result is the interface {@link NodeLocator} and
 * method {@link compare}, which are used to compare keys without ever naming
 * them. Users of this class must generally create a subclass, and use this
 * class as a facility to implement the desired functionality.  It's not as
 * simple as just using a TransposeTree out of the box.
 * 
 * The red-black balancing logic is the 2-3 left-leaning red-black tree
 * taken from this paper by Robert Sedgewick:
 * 
 *   https://www.cs.princeton.edu/~rs/talks/LLRB/LLRB.pdf
 * 
 * TODO:
 * - Improve {@link #lookup} so users can implement upsert without walking the
 * tree twice.  If an item is not present, lookup needs to position you at the
 * node that would be its parent.
 * - Implement deletion in some form, either by deleting nodes, or by marking
 * them invalid and offering a compaction operation (which would ideally be
 * parallelizable).
 * - Polish up all the methods and give a clean implementation recipe for users.
 * 
 * @author Patrick Doyle
 *
 */
public abstract class TransposeTree {

	abstract protected int compare(int index1, int index2);

	private int population = 0;
	private int root;
	
	//
	// Per-node data
	//
	private final PrimitiveBlock left;
	private final PrimitiveBlock right;
	private final BitSet blackNodes;
	
	protected static final int NULL = -1;

	protected TransposeTree(int initialCapacity, BlockFactory blockFactory) {
		this.population = 0;
		this.left = blockFactory.create(initialCapacity);
		this.right = blockFactory.create(initialCapacity);
		this.blackNodes = new BitSet(initialCapacity);
	}

	protected TransposeTree(int initialCapacity) {
		this(initialCapacity, c->new InflatableBlock(c));
	}

	protected TransposeTree(int initialCapacity, InflatableBlock.BlockFactory blockFactory) {
		this(initialCapacity, c->new InflatableBlock(c, blockFactory));
	}

	public static interface BlockFactory { PrimitiveBlock create(int initialCapacity); }

	protected int population(){ return population; }

	/**
	 * Subclasses should override this to shrink their arrays if possible.
	 * 
	 * @param roomPercentage is the additional head-room to leave for potential
	 * future growth before an expensive resize operation occurs.
	 */
	public void shrinkwrap(int roomPercentage) {
		left.shrinkwrap(roomPercentage);
		right.shrinkwrap(roomPercentage);
	}

	/**
	 * The index to be used to store data for the next item added to the tree.
	 */
	protected int insertionPoint(){ return population; }

	/**
	 * Treats the node whose index is {@link insertionPoint} as a newly created node,
	 * and inserts it in the correct location in the tree, increasing the population
	 * by 1.
	 */
	protected void insert() throws DuplicateKeyException {
		initializeNode(population);
		if (population == 0) {
			root = 0;
		} else {
			root = insert(root);
		}
		blackNodes.set(root);
		population += 1;
	
		if (RED_BLACK_BALANCING && EXPENSIVE_SELF_CHECKING) {
			long badNode;
			assert (badNode = invalidNode(root)) == NULL: "Node " + badNode + " should obey the red-black tree rules";
		}
	}

	/**
	 * Returns the new root of the subtree that was rooted at index before the insertion occurred.
	 */
	int insert(int index) throws DuplicateKeyException {
		PrimitiveBlock child;
		switch (Integer.signum(compare(population, index))) {
		case -1:
			child = left;
			break;
		case 1:
			child = right;
			break;
		default:
			throw new AssertionError("Unknown signum");
		case 0:
			throw new DuplicateKeyException(index);
		}
		try {
			if (child.get(index) == NULL) {
				child.set(index, population);
			} else {
				child.set(index, insert(toInt(child.get(index))));
			}
		} catch (ValueBeyondLimitException e) {
			throw new IndexOutOfBoundsException("Exceeded maximum capacity (" + population + ") of transpose tree backed by " + left.getClass().getSimpleName());
		}

		return rebalance(index);
	}

	int rebalance(int index) {
		if (RED_BLACK_BALANCING) {
			// This is called a lot, so it's a little ugly for the sake of performance:
			// we try to minimize the calls to get() by caching them in locals.
			// Must remember to reload them whenever they might become obsolete.
			//
			long myLeft  = left.get(index);
			long myRight = right.get(index);

			if (isRed(myRight) && !isRed(myLeft)) {
				index = rotate(index, right, left);
				myLeft  = left.get(index);
				myRight = right.get(index);
			}
			if (isRed(myLeft) && isRed(left.get(toInt(myLeft)))) {
				index = rotate(index, left, right);
				myLeft  = left.get(index);
				myRight = right.get(index);
			}
			if (isRed(myLeft) && isRed(myRight)) {
				blackNodes.flip(toInt(index));
				blackNodes.flip(toInt(myLeft));
				blackNodes.flip(toInt(myRight));
			}
		}
		return index;
	}
	
	int rotate(long longIndex, PrimitiveBlock fromChild, PrimitiveBlock toChild) {
		int index = toInt(longIndex);

		// Shuffle
		int result;
		try {
			result = toInt(fromChild.get(index));
			fromChild.set(index, toChild.get(result));
			toChild.set(result, index);
		} catch (ValueBeyondLimitException e) {
			// We're only putting values that we just got from the underlying blocks,
			// so that can't possibly overflow.
			throw new AssertionError( e);
		}
		
		// Recolour
		blackNodes.set(result, blackNodes.get(index));
		blackNodes.set(index, false);
		
		return result;
	}
	
	long invalidNode(long longIndex) {
		int index = toInt(longIndex);
		if (index == NULL)
			return NULL;
		
		// Never two consecutive reds
		if (isRed(index)) {
			if (isRed(left.get(index)) || isRed(right.get(index))) {
				return index;
			}
		}
		
		// All paths have the same number of blacks
		// TODO
		
		// All children are valid
		long badness = invalidNode(left.get(index));
		if (badness != NULL)
			return badness;
		badness = invalidNode(right.get(index));
		if (badness != NULL)
			return badness;
		
		// All is well
		return NULL;
	}

	/**
	 * @param locator must be consistent with the tree's compare order.
	 * @return Index of matching node, or NULL_NODE if none.
	 */
	protected int lookup(NodeLocator locator) {
		if (population == 0)
			return NULL;
		int result = locate(root, locator);
		if (result == NULL)
			return NULL;
		else if (locator.compareWith(result) == 0) // sad -- this is redundant.  We've already done this comparison inside locate()
			return result;
		else
			return NULL;
	}

	/**
	 * @param locator must be consistent with the tree's compare order.
	 * @return Index of matching node, or its parent if there is none, or NULL_NODE if the tree is empty.
	 */
	protected int locate(NodeLocator locator) {
		if (population == 0)
			return NULL;
		else
			return locate(root, locator);
	}

	private int locate(int index, NodeLocator locator) {
		while (true) {
			PrimitiveBlock child;
			switch (Integer.signum(locator.compareWith(index))) {
			case 0:
				return index;
			case -1:
				child = left;
				break;
			case 1:
				child = right;
				break;
			default:
				throw new AssertionError("Unknown signum!");
			}
			if (child.get(index) == NULL) {
				return index;
			} else {
				index = toInt(child.get(index));
			}
		}
	}

	protected IntStream allIndexes() {
		return StreamSupport.intStream(Spliterators.spliteratorUnknownSize(new Walker(i->0), 0), false);
	}
	
	protected IntStream allIndexesMatching(NodeLocator locator) {
		return StreamSupport.intStream(Spliterators.spliteratorUnknownSize(new Walker(locator), 0), false);
	}

	private class Walker implements PrimitiveIterator.OfInt {
		final int originalPopulation = population; // To help detect concurrent modifications
		final NodeLocator locator;
		final ArrayDeque<Integer> resumeStack = new ArrayDeque<>();

		int nextIndex;

		Walker(NodeLocator locator) {
			this.locator = locator;
			this.nextIndex = dive(root, left, locator);
		}

		public boolean hasNext() {
			return nextIndex != NULL;
		}

		/**
		 * Concurrent modification not supported.  It's pretty hard when you can
		 * have balancing of the tree, thereby changing the depths of existing
		 * nodes and invalidating resumeStack.  We could achieve it by using
		 * parent pointers instead of a stack, if we don't mind the storage cost.
		 */
		public int nextInt() {
			if (population != originalPopulation) {
				throw new ConcurrentModificationException(TransposeTree.class.getSimpleName() + " modified during iteration");
			}
			int result = nextIndex;
			if (result != NULL) {
				// Bump the pointer
				if (right.get(result) != NULL) {
					nextIndex = dive(right.get(result), left, locator);
				} else if (!resumeStack.isEmpty()) {
					nextIndex = resumeStack.pop();
				} else {
					nextIndex = NULL;
				}
				if (locator.compareWith(nextIndex) != 0) {
					nextIndex = NULL;
				}
			}
			return result;
		}

		int dive(long longIndex, PrimitiveBlock directionIfEqual, NodeLocator locator) {
			int index = toInt(longIndex);
			while (true) {
				PrimitiveBlock child;
				switch (Integer.signum(locator.compareWith(index))) {
				case 0:
					child = directionIfEqual;
					break;
				case -1:
					child = left;
					break;
				case 1:
					child = right;
					break;
				default:
					throw new AssertionError("Unknown signum!");
				}
				if (child.get(index) == NULL) {
					return index;
				} else {
					resumeStack.push(index);
					index = toInt(child.get(index));
				}
			}
		}

	}
	
	protected int extremeNode(PrimitiveBlock direction) {
		int result = root;
		for (int peek = result; peek != NULL; peek = toInt(direction.get(peek))) {
			result = peek;
		}
		return result;
	}
	
	protected int leftmost(){ return extremeNode(left); }
	protected int rightmost(){ return extremeNode(right); }
	
	protected static int newSize(int currentSize, int index) {
		assert index <= Short.MAX_VALUE;
		return Math.min(Short.MAX_VALUE+1, Math.max(currentSize*2, index+1));
	}

	void initializeNode(int index) {
		assert 0 <= index && index <= insertionPoint();
		try {
			left.set(index, NULL);
			right.set(index, NULL);
		} catch (ValueBeyondLimitException e) {
			// We can always put NULL in any block, so this can't overflow.
			throw new AssertionError(e);
		}
		blackNodes.clear(index); // New nodes are always red
	}
	
	boolean isRed(long index) {
		if (index == NULL)
			return false;
		else
			return !blackNodes.get(toInt(index));
	}
	
	int toInt(long value) {
		return Math.toIntExact(value);
	}

	@SuppressWarnings("serial")
	public static class DuplicateKeyException extends Exception {

		int existingNodeIndex;

		public int getExistingNodeIndex(){ return existingNodeIndex; }

		DuplicateKeyException(int existingNodeIndex) {
			super(messageFor(existingNodeIndex));
			this.existingNodeIndex = existingNodeIndex;
		}

		DuplicateKeyException(int existingNodeIndex, Throwable cause) {
			super(messageFor(existingNodeIndex), cause);
			this.existingNodeIndex = existingNodeIndex;
		}

		private static String messageFor(int index) {
			return "Cannot insert duplicate key at index " + index;
		}

	}

	public static interface NodeLocator {
		/**
		 * Returns what a hypothetical Comparator would return for
		 * compare(desiredKey, keyAt(index)).  When searching, this
		 * indicates what direction to go: negative means to go left,
		 * positive means to go right, and zero means a match.
		 * 
		 * In other words, a {@link NodeLocator} divides the universe
		 * of keys into three regions:
		 * 
		 *    too low    |  matching node  |  too high
		 *   (go right)  |                 |  (go left)
		 *        +               0               -
		 *        
		 * This is also used to indicate a range of matching nodes, by returning
		 * zero for multiple values.  This can be achieved by returning something like
		 * "compare(lowBound, keyAt(index)) & compare(highBound, keyAt(index))".
		 * 
		 * @param index of the node whose key is being examined.
		 * @return Negative if the desired key is below the one at index; positive if it's above; and zero if it's equal.
		 */
		int compareWith(int index);
	}
	
	void dumpTo(PrintStream out, long longIndex, int indent) {
		int index = toInt(longIndex);
		if (index != NULL) {
			dumpTo(out, right.get(index), indent+1);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < indent; i++) {
				sb.append(" ");
			}
			out.println(sb.append(nodeToString(index)).toString());
			dumpTo(out, left.get(index), indent+1);
		}
	}
	
	protected String nodeToString(int index) {
		return String.format("%s@%d", isRed(index)?"R":"B", index);
	}

	private static boolean RED_BLACK_BALANCING = true;
	private static boolean EXPENSIVE_SELF_CHECKING = false;

}
