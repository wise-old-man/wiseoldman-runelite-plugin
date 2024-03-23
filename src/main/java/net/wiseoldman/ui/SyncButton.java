package net.wiseoldman.ui;

import com.google.common.util.concurrent.Runnables;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.clan.ClanRank;
import net.wiseoldman.beans.GroupMembership;
import net.wiseoldman.beans.RoleIndex;
import net.wiseoldman.web.WomClient;
import net.wiseoldman.beans.Member;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.*;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.widgets.*;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.List;

public class SyncButton
{
	private final Client client;
	private final ClientThread clientThread;
	private final WomClient womClient;
	private final ChatboxPanelManager chatboxPanelManager;
	private final Widget parent;

	private final List<Widget> cornersAndEdges = new ArrayList<>();
	private final ClanSettings clanSettings;
	private final Map<String, GroupMembership> groupMembers;
	private final List<String> ignoredRanks;
	private final List<String> alwaysIncludedOnSync;
	private Widget textWidget;

	private Set<RoleIndex> roleOrders = new HashSet<>();


	private final List<ClanRank> roleOrder = Arrays.asList(
		ClanRank.OWNER, ClanRank.DEPUTY_OWNER, ClanRank.CLAN_RANK_14, ClanRank.CLAN_RANK_13, ClanRank.CLAN_RANK_12,
		ClanRank.CLAN_RANK_11, ClanRank.ADMINISTRATOR, ClanRank.CLAN_RANK_10, ClanRank.CLAN_RANK_9,
		ClanRank.CLAN_RANK_8, ClanRank.CLAN_RANK_7, ClanRank.CLAN_RANK_6, ClanRank.CLAN_RANK_5, ClanRank.CLAN_RANK_4,
		ClanRank.CLAN_RANK_3, ClanRank.CLAN_RANK_2, ClanRank.CLAN_RANK_1
	);

	public SyncButton(Client client, ClientThread clientThread, WomClient womClient, ChatboxPanelManager chatboxPanelManager,
	                  int parent, Map<String, GroupMembership> groupMembers, List<String> ignoredRanks,
	                  List<String> alwaysIncludedOnSync)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.womClient = womClient;
		this.chatboxPanelManager = chatboxPanelManager;
		this.parent = client.getWidget(parent);
		this.clanSettings = client.getClanSettings();
		this.groupMembers = groupMembers;
		this.ignoredRanks = ignoredRanks;
		this.alwaysIncludedOnSync = alwaysIncludedOnSync;

		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_TOP_LEFT, 6, 6, 9, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_TOP_RIGHT, 97, 6, 9, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_BOTTOM_LEFT, 6, 20, 9, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_BOTTOM_RIGHT, 97, 20, 9, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_EDGE_LEFT, 6, 15, 9, 5);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_EDGE_TOP, 15, 6, 82, 9);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_EDGE_RIGHT, 97, 15, 9, 5);
		this.createWidgetWithSprite(SpriteID.EQUIPMENT_BUTTON_EDGE_BOTTOM, 15, 20, 82, 9);
		this.textWidget = this.createWidgetWithText();
	}

	private void createWidgetWithSprite(int spriteId, int x, int y, int width, int height)
	{
		Widget w = this.parent.createChild(-1, WidgetType.GRAPHIC);
		w.setSpriteId(spriteId);
		w.setOriginalX(x);
		w.setOriginalY(y);
		w.setOriginalWidth(width);
		w.setOriginalHeight(height);
		w.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		w.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		w.revalidate();
		cornersAndEdges.add(w);
	}

	private Widget createWidgetWithText()
	{
		Widget textWidget = this.parent.createChild(-1, WidgetType.TEXT);
		textWidget.setOriginalX(6);
		textWidget.setOriginalY(6);
		textWidget.setOriginalWidth(100);
		textWidget.setOriginalHeight(23);
		textWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		textWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		textWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
		textWidget.setYTextAlignment(WidgetTextAlignment.CENTER);
		textWidget.setText("<col=9f9f9f>" + "Sync WOM Group" + "</col>");
		textWidget.setFontId(FontID.PLAIN_11);
		textWidget.setTextShadowed(true);

		textWidget.setHasListener(true);
		textWidget.setAction(0, "Sync WOM Group");
		textWidget.setOnMouseOverListener((JavaScriptCallback) e -> update(true));
		textWidget.setOnMouseLeaveListener((JavaScriptCallback) e -> update(false));

		textWidget.revalidate();

		return textWidget;
	}

	private void update(boolean hovered)
	{
		for (Widget w : cornersAndEdges)
		{
			int spriteId = w.getSpriteId();
			w.setSpriteId(hovered ? spriteId + 8 : spriteId - 8);
			w.revalidate();
		}
	}

	private void syncMembers()
	{
		syncMembers(true);
	}

	private void syncMembers(boolean overwrite)
	{
		Map<String, Member> clanMembers = new HashMap<>();

		if (!overwrite)
		{
			groupMembers.forEach((k, v) -> clanMembers.put(k, new Member(v.getPlayer().getDisplayName(), v.getRole())));
		}

		for (ClanMember clanMember : clanSettings.getMembers())
		{
			if (clanMember.getName().startsWith("[#"))
			{
				continue;
			}

			String memberName = Text.toJagexName(clanMember.getName());
			ClanTitle memberTitle = clanSettings.titleForRank(clanMember.getRank());

			String role = memberTitle == null ? "member" : memberTitle.getName().toLowerCase().replaceAll("[-\\s]", "_");

			if (memberTitle != null)
			{
				int roleIndex = roleOrder.indexOf(clanMember.getRank());
				roleOrders.add(new RoleIndex(role, roleIndex));
			}

			if (ignoredRanks.contains(role))
			{
				continue;
			}

			clanMembers.put(memberName.toLowerCase(), new Member(memberName, role));
		}

		for (String name : alwaysIncludedOnSync)
		{
			String nameLower = name.toLowerCase();
			if (!clanMembers.containsKey(nameLower))
			{
				clanMembers.put(nameLower, new Member(name, "member"));
			}
		}

		womClient.syncClanMembers(new ArrayList<>(clanMembers.values()), roleOrders);
	}

	public void setEnabled()
	{
		this.textWidget.setText("<col=ffffff>" + "Sync WOM Group" + "</col>");
		textWidget.setOnOpListener((JavaScriptCallback) e -> {
			chatboxPanelManager.openTextMenuInput(
					"Any members not in your clan will be removed" +
						"<br>from your WOM group. Proceed?")
				.option("1. Yes, overwrite WOM group", () -> clientThread.invoke(() -> syncMembers()))
				.option("2. No, only add new members", () -> clientThread.invoke(() -> syncMembers(false)))
				.option("3. Cancel", Runnables.doNothing())
				.build();
		});
	}
}
