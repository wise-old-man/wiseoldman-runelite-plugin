package net.wiseoldman.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoSyncSafetyTest
{
	@Test
	public void allowsSmallRemoval()
	{
		assertTrue(AutoSyncSafety.isRemovalSafe(100, 5));
	}

	@Test
	public void blocksLargeRemovalCount()
	{
		assertFalse(AutoSyncSafety.isRemovalSafe(100, 11));
	}

	@Test
	public void blocksLargeRemovalRatio()
	{
		assertFalse(AutoSyncSafety.isRemovalSafe(20, 6));
	}

	@Test
	public void allowsNoRemovals()
	{
		assertTrue(AutoSyncSafety.isRemovalSafe(0, 0));
	}
}
