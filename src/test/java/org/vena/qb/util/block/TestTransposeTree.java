package org.vena.qb.util.block;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vena.qb.HeapDumper;
import org.vena.qb.util.block.PrimitiveBlock.ValueBeyondLimitException;
import org.vena.qb.util.block.ReferenceBlock;
import org.vena.qb.util.block.TransposeTree;

@RunWith(Parameterized.class)
public class TestTransposeTree {
	
	final TransposeTree.BlockFactory blockFactory;
	
	public TestTransposeTree(TransposeTree.BlockFactory blockFactory) {
		this.blockFactory = blockFactory;
	}
	
	@Parameters
	public static Object[][] parameters() {
		return new TransposeTree.BlockFactory[][] {
			{ c->new InflatableBlock(c) },
			{ c->new InflatableBlock(c, InflatableBlock.JAVA_PRIMITIVE_BLOCKS) },
			{ c->new InflatableBlock(c, InflatableBlock.BITWISE_GROWTH) },
		};
	}
	
	class StringTree extends TransposeTree {
		
		final ReferenceBlock<String> keys, values;

		protected StringTree() {
			super(2, blockFactory);
			keys = new ReferenceBlock<>();
			values = new ReferenceBlock<>();
		}

		@Override
		protected int compare(int index1, int index2) {
			return keys.get(index1).compareTo(keys.get(index2));
		}

		private void insert(String key, String value) throws DuplicateKeyException {
			int index = insertionPoint();
			keys.set(index, key);
			values.set(index, value);
			insert();
		}
		
		public String get(String key) {
			int index = lookup(candidate->key.compareTo(keys.get(candidate)));
			if (index == NULL)
				return null;
			else
				return values.get(index);
		}
		
		public String put(String key, String value) {
			int index = lookup(candidate->key.compareTo(keys.get(candidate)));
			if (index == NULL) {
				try {
					insert(key, value);
				} catch (DuplicateKeyException e) {
					throw new AssertionError("Impossible", e);
				}
				return null;
			} else {
				return values.set(index, value);
			}
		}

		protected String nodeToString(short index) {
			return super.nodeToString(index) + " " + keys.get(index) + ":" + values.get(index);
		}

	}

	@Test
	public void testNames() throws Exception {
		StringTree tree = new StringTree();
		for (String[] name: NAMES) {
			tree.insert(name[1], name[0]);
		}
		for (String[] name: NAMES) {
			assertEquals(name[0], tree.get(name[1]));
		}
		assertEquals(null, tree.get("Doyle"));
	}

	@Test
	public void testIteration() throws Exception {
		StringTree tree = new StringTree();
		String[] keys = new String[NAMES.length];
		int i=0;
		for (String[] name: NAMES) {
			tree.insert(name[1], name[0]);
			keys[i++] = name[1];
		}
		Arrays.sort(keys);
		i=0;
		int indexes[] = tree.allIndexes().toArray();
		for (int index: indexes) {
			assertEquals(keys[i++], tree.keys.get(index));
		}
	}

	@Test
	public void testRandomNumbers() throws Exception {
		Random random = new Random(123);
		testWith(IntStream.generate(()->random.nextInt(999_999_999)));
	}

	@Test
	public void testNumbersInOrder() throws Exception {
		testWith(IntStream.iterate(1_000_000, n->n+1));
	}

	void testWith(IntStream ints) {
		StringTree tree = new StringTree();
		int[] numbers = ints.limit(NUM_ENTRIES).toArray();
		for (int rep = 0; rep < REPS; rep++) {
			for (int number: numbers) {
				String decimal = Integer.toString(number);
				String hex = Integer.toHexString(number);
				tree.put(decimal, hex);
				//tree.dumpTo(System.out, tree.root, 1);
				//System.out.println("");
			}
			//System.out.println("Population after " + numbers.length + " puts is " + tree.population());
			//tree.dumpTo(System.out, tree.root, 1);
			for (int number: numbers) {
				assertEquals(Integer.toHexString(number), tree.get(Integer.toString(number)));
			}
			assertEquals(null, tree.get("Huh?"));

			numbers = IntStream.of(numbers)
					.mapToObj(i -> Integer.toString(i))
					.sorted().distinct()
					.mapToInt(s -> Integer.parseInt(s))
					.toArray();
			int i = 0;
			for (int index: tree.allIndexes().toArray()) {
				assertEquals(Integer.toString(numbers[i++]), tree.keys.get(index));
			}
		}
	}
	
	static final int REPS = 1;
	static final int NUM_ENTRIES = 35_000; // Just enough to inflate past short

	static final String[][] NAMES = {
			{ "Albert", "Einstein" },
			{ "Michael", "Jordan" },
			{ "Barack", "Obama" },
			{ "Charles", "Darwin" },
	};
	
	//
	// Performance testing
	//
	
	static class NumberStatsTree extends TransposeTree {

		final PrimitiveBlock values, finalDigit, numDigits, signBit;

		protected NumberStatsTree(int initialCapacity) {
			super(initialCapacity);
			this.values = new LongBlock(1);
			this.finalDigit = new BitBlock.Unsigned(4, 1);
			this.numDigits = new BitBlock.Unsigned(5, 1);
			this.signBit = new BitBlock.Unsigned(1, 1);
		}

		@Override
		protected int compare(int index1, int index2) {
			return Long.compare(values.get(index1), values.get(index2));
		}

		void add(long value) {
			long abs = Math.abs(value);
			try {
				values.set(insertionPoint(), value);
				finalDigit.set(insertionPoint(), abs % 10);
				numDigits.set(insertionPoint(), Long.toString(abs).length());
				long sign = value >>> 63;
				LOGGER.trace("{} sign bit is {}", value, sign);
				signBit.set(insertionPoint(), sign);
				insert();
			} catch (ValueBeyondLimitException e) {
				throw new AssertionError("All values we're trying to add should fit", e);
			} catch (DuplicateKeyException e) {
				LOGGER.trace("Duplicate value {}", value);
			}
		}

		public Stream<Stats> statsFor(NodeLocator locator) {
			return super.allIndexesMatching(locator).mapToObj(i->
				new Stats(values.get(i), (byte)finalDigit.get(i), (byte)numDigits.get(i), signBit.get(i) == 1)
			);
		}
		
		public Stream<Stats> allStats() {
			return statsFor(index->0);
		}

		@Override
		public void shrinkwrap(int roomPercentage) {
			super.shrinkwrap(roomPercentage);
			values.shrinkwrap(roomPercentage);
			finalDigit.shrinkwrap(roomPercentage);
			numDigits.shrinkwrap(roomPercentage);
			signBit.shrinkwrap(roomPercentage);
		}

	}

	static class Stats {
		final byte finalDigit, numDigits;
		final boolean signBit;

		Stats(long value, byte finalDigit, byte numDigits, boolean signBit) {
			//this.value = value;
			this.finalDigit = finalDigit;
			this.numDigits = numDigits;
			this.signBit = signBit;
		}

		public static Stats from(long value) {
			long abs   = Math.abs(value);
			byte finalDigit = (byte)(abs % 10);
			byte numDigits = (byte)(Long.toString(abs).length());
			boolean signBit = (value < 0);
			return new Stats(value, finalDigit, numDigits, signBit);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + finalDigit;
			result = prime * result + numDigits;
			result = prime * result + (signBit ? 1231 : 1237);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof Stats))
				return false;
			Stats other = (Stats) obj;
			if (finalDigit != other.finalDigit)
				return false;
			if (numDigits != other.numDigits)
				return false;
			if (signBit != other.signBit)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Stats [finalDigit=" + finalDigit + ", numDigits=" + numDigits + ", signBit=" + signBit + "]";
		}

	}

	static void stressTest(String[] args) throws Exception {
		long freeMemory = Runtime.getRuntime().freeMemory();
		long count = 0;
		long withoutData, withData=0xbad;
		Random random = new Random(123);
		try {
			NumberStatsTree tree = new NumberStatsTree(1);
			//TreeMap<Long, Stats> bigTree = new TreeMap<>();
			//LongBlock block = new LongBlock(1);
			try {
				System.gc();
				freeMemory = Runtime.getRuntime().freeMemory();
				LOGGER.warn("{} bytes free", freeMemory);
				for (long value = random.nextLong(); ; value = random.nextLong()) {
					//block.add(value); // Measured 19 bytes each, hprof 8 bytes each.
					tree.add(value); // Measured 38 bytes each, hprof 
					//bigTree.put(value, new Stats(value)); // 121 bytes each
					count++;
				}
			} finally {
				System.gc();
				withData = Runtime.getRuntime().freeMemory();
			}
		} catch (OutOfMemoryError e) {
			LOGGER.error("Out of memory after processing {} numbers ({} bytes each)", count, ((float)freeMemory)/count);
			System.gc();
			withoutData = Runtime.getRuntime().freeMemory();
			long delta = withData - withoutData;
			LOGGER.error("{} bytes each after gc()", ((float)delta)/count);
			throw e;
		}
		/*
		tree.forEach((TransposeTreeBlock<NumberStatsTree> block, int index)->0, (String key, long finalDigit, long numDigits, long signBit)->{
			LOGGER.info("{}: {} {} {}", key, finalDigit, numDigits, signBit);
		});
		 */
	}
	
	static class ThingsToMeasure {
		TreeMap<Long, Stats> treeMap = new TreeMap<>();
		NumberStatsTree transposeTree = new NumberStatsTree(1);
		
		void build() {
			// Fill up a traditional TreeMap
			//
			numbers().forEach(number->treeMap.put(number, Stats.from(number)));

			// Fill up a transpose tree
			//
			numbers().forEach(number->transposeTree.add(number));
			transposeTree.shrinkwrap(0);

			// Verify
			//
			Iterator<Stats> treeMapIter = treeMap.values().iterator();
			transposeTree.allStats().forEachOrdered(fromTransposeTree->{
				Stats fromTreeMap = treeMapIter.next();
				if (!fromTransposeTree.equals(fromTreeMap)) {
					System.err.println("fromTransposeTree: " + fromTransposeTree);
					System.err.println("fromTreeMap: " + fromTreeMap);
					throw new AssertionError();
				}
			});
		}
	}
	
	
	public static void main(String[] args) throws Exception {
		ThingsToMeasure t = new ThingsToMeasure();
		t.build();
		HeapDumper.dumpHeap("c:/tmp/heapdumps/transpose.hprof", true);
		System.out.println(t.toString());
	}
	
	static LongStream numbers() {
		return new Random(123).longs().limit(1_000_000);
	}
	
	static final Logger LOGGER = LoggerFactory.getLogger(TestTransposeTree.class);
			

}
