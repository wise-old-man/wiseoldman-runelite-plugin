package net.wiseoldman.util;

public final class AutoSyncSafety
{
	private static final int MAX_REMOVALS = 10;
	private static final double MAX_REMOVAL_RATIO = 0.25;

	private AutoSyncSafety()
	{
	}

	public static boolean isRemovalSafe(int groupSize, int membersRemoved)
	{
		if (membersRemoved <= 0)
		{
			return true;
		}

		if (groupSize <= 0)
		{
			return false;
		}

		double removalRatio = (double) membersRemoved / groupSize;
		return membersRemoved <= MAX_REMOVALS && removalRatio <= MAX_REMOVAL_RATIO;
	}
}
