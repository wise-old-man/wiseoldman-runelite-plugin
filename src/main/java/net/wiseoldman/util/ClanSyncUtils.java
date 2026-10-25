package net.wiseoldman.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.util.Text;
import net.wiseoldman.beans.GroupMembership;
import net.wiseoldman.beans.Member;
import net.wiseoldman.beans.RoleIndex;

public final class ClanSyncUtils
{
	private static final String DEFAULT_ROLE = "member";

	private static final List<ClanRank> ROLE_ORDER = Arrays.asList(
		ClanRank.OWNER, ClanRank.DEPUTY_OWNER, new ClanRank(124), new ClanRank(120),
		new ClanRank(115), new ClanRank(110), new ClanRank(105), new ClanRank(104),
		new ClanRank(103), new ClanRank(102), new ClanRank(101), ClanRank.ADMINISTRATOR,
		new ClanRank(99), new ClanRank(98), new ClanRank(97), new ClanRank(96),
		new ClanRank(95), new ClanRank(90), new ClanRank(80), new ClanRank(70),
		new ClanRank(60), new ClanRank(50), new ClanRank(40), new ClanRank(30),
		new ClanRank(20), new ClanRank(10), new ClanRank(0)
	);

	private ClanSyncUtils()
	{
	}

	public static SyncData buildSyncData(
		ClanSettings clanSettings,
		Map<String, GroupMembership> groupMembers,
		Collection<String> ignoredRanks,
		Collection<String> alwaysIncludedOnSync,
		boolean overwrite)
	{
		Map<String, Member> members = new HashMap<>();
		Set<RoleIndex> roleOrders = new HashSet<>();

		if (!overwrite)
		{
			groupMembers.forEach((k, v) -> members.put(
				k.toLowerCase(),
				new Member(v.getPlayer().getDisplayName(), v.getRole(), null)
			));
		}

		for (ClanMember clanMember : clanSettings.getMembers())
		{
			if (clanMember.getName().startsWith("[#"))
			{
				continue;
			}

			String memberName = Text.toJagexName(clanMember.getName());
			ClanTitle memberTitle = clanSettings.titleForRank(clanMember.getRank());
			String role = memberTitle == null
				? DEFAULT_ROLE
				: memberTitle.getName().toLowerCase().replaceAll("[-\\s]", "_");

			if (memberTitle != null)
			{
				int roleIndex = ROLE_ORDER.indexOf(clanMember.getRank());
				roleOrders.add(new RoleIndex(role, roleIndex));
			}

			if (ignoredRanks.contains(role))
			{
				continue;
			}

			members.put(
				memberName.toLowerCase(),
				new Member(memberName, role, clanMember.getJoinDate().toString())
			);
		}

		for (String name : alwaysIncludedOnSync)
		{
			String nameLower = name.toLowerCase();
			members.putIfAbsent(nameLower, new Member(name, DEFAULT_ROLE, null));
		}

		return new SyncData(new ArrayList<>(members.values()), roleOrders);
	}

	public static final class SyncData
	{
		private final ArrayList<Member> members;
		private final Set<RoleIndex> roleOrders;

		private SyncData(ArrayList<Member> members, Set<RoleIndex> roleOrders)
		{
			this.members = members;
			this.roleOrders = roleOrders;
		}

		public ArrayList<Member> getMembers()
		{
			return members;
		}

		public Set<RoleIndex> getRoleOrders()
		{
			return roleOrders;
		}
	}
}
