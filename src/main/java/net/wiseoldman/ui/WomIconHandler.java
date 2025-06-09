package net.wiseoldman.ui;

import net.runelite.api.gameval.InterfaceID;
import net.wiseoldman.WomUtilsConfig;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import net.wiseoldman.beans.GroupMembership;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class WomIconHandler
{
	private final Client client;
	private final ClientThread clientThread;
	private final WomUtilsConfig config;

	private int modIconsStart = -1;

	private String currentLayouting;

	@Inject
	WomIconHandler(Client client, ClientThread clientThread, WomUtilsConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
	}

	private boolean loadCountryIcons()
	{
		final IndexedSprite[] modIcons = client.getModIcons();
		if (modIconsStart != -1 || modIcons == null)
		{
			return false;
		}

		final CountryIcon[] countryIcons = CountryIcon.values();
		final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + countryIcons.length);
		modIconsStart = modIcons.length;

		for (int i = 0; i < countryIcons.length; i++)
		{
			CountryIcon icon = countryIcons[i];
			BufferedImage image = icon.loadImage();
			IndexedSprite sprite = ImageUtil.getImageIndexedSprite(image, client);
			newModIcons[modIconsStart + i] = sprite;
		}

		client.setModIcons(newModIcons);
		return true;
	}

	public boolean iconsAreLoaded()
	{
		return modIconsStart != -1;
	}

	public void loadIcons()
	{
		clientThread.invokeLater(this::loadCountryIcons);
	}

	public void handleScriptEvent(ScriptCallbackEvent event, Map<String, GroupMembership> groupMembers)
	{
		switch (event.getEventName())
		{
			case "friendsChatSetText":
				Object[] objectStack = client.getObjectStack();
				int objectStackSize = client.getObjectStackSize();
				final String rsn = (String) objectStack[objectStackSize - 1];
				final String sanitized = Text.toJagexName(Text.removeTags(rsn).toLowerCase());
				currentLayouting = sanitized;
				GroupMembership member = groupMembers.get(sanitized);

				if (member != null)
				{
					String country = member.getPlayer().getCountry() != null &&
						config.showFlags() ? member.getPlayer().getCountry().toLowerCase() : "default";
					CountryIcon icon = CountryIcon.getIcon(country);
					int iconIdx = modIconsStart + icon.ordinal();
					objectStack[objectStackSize - 1] = rsn + " <img=" + iconIdx + ">";
				}
				break;
			case "friendsChatSetPosition":
				if (currentLayouting == null || !groupMembers.containsKey(currentLayouting))
				{
					return;
				}

				int[] intStack = client.getIntStack();
				int intStackSize = client.getIntStackSize();
				int xpos = intStack[intStackSize - 4];
				xpos += CountryIcon.ICON_WIDTH + 1;
				intStack[intStackSize - 4] = xpos;
				break;
		}
	}

	public void rebuildMemberList(boolean disable, Map<String, GroupMembership> groupMembers, int componentId)
	{
		Widget containerWidget = client.getWidget(componentId);
		if (containerWidget == null)
		{
			return;
		}

		Widget[] children = containerWidget.getChildren();
		if (children == null)
		{
			return;
		}

		for (int i = 0; i < children.length; i++)
		{
			// Username child widget uniquely has fontId and XTextAlignment.
			if (children[i].getFontId() == -1 || children[i].getXTextAlignment() != 0)
			{
				continue;
			}

			String name = Text.removeTags(children[i].getText());
			String sanitized = Text.toJagexName(name);
			GroupMembership member = groupMembers.get(sanitized.toLowerCase());

			if (!disable && member != null)
			{
				String country = member.getPlayer().getCountry() != null &&
					config.showFlags() ? member.getPlayer().getCountry().toLowerCase() : "default";
				CountryIcon icon = CountryIcon.getIcon(country);
				int iconIdx = modIconsStart + icon.ordinal();
				String spacer = name.charAt(name.length() - 1) != ' ' ? " " : ""; // Stupid
				String newName = name + spacer + "<img=" + iconIdx + ">";
				children[i].setText(newName);
			}
			else
			{
				children[i].setText(name);
			}
		}
	}

	public void rebuildSettingsMemberList(boolean disable, Map<String, GroupMembership> groupMembers)
	{
		Widget containerWidget = client.getWidget(InterfaceID.ClansMembers.NAME);
		if (containerWidget == null)
		{
			return;
		}

		Widget[] children = containerWidget.getChildren();
		if (children == null)
		{
			return;
		}

		for (int i = 1; i < children.length; i += 3)
		{
			String name = Text.removeTags(children[i].getText());
			String sanitized = Text.toJagexName(name);
			GroupMembership member = groupMembers.get(sanitized.toLowerCase());

			if (!disable && member != null)
			{
				String oldText = children[i].getText();

				String country = member.getPlayer().getCountry() != null
					&& config.showFlags() ? member.getPlayer().getCountry().toLowerCase() : "default";
				CountryIcon icon = CountryIcon.getIcon(country);
				int iconIdx = modIconsStart + icon.ordinal();
				String spacer = name.charAt(name.length() - 1) != ' ' ? " " : ""; // Stupid
				String newName = name + spacer + "<img=" + iconIdx + ">";
				children[i].setText(newName);

				if (!oldText.contains("<img"))
				{
					Widget rankIcon = children[i + 1];
					rankIcon.setOriginalX(rankIcon.getOriginalX() - 6);
					rankIcon.revalidate();
				}
			}
			else
			{
				String oldText = children[i].getText();
				if (oldText.contains("<img"))
				{
					// If the sequence have img in it it's an old and removed member so we have to move
					// its rank icon back
					Widget rankIcon = children[i + 1];
					rankIcon.setOriginalX(rankIcon.getOriginalX() + 6);
					rankIcon.revalidate();
				}
				children[i].setText(name);
			}
		}
	}

	public void rebuildLists(Map<String, GroupMembership> members, boolean showIcons)
	{
		clientThread.invokeLater(() ->
		{
			rebuildFriendsList();
			rebuildMemberList(!showIcons, members, InterfaceID.ChatchannelCurrent.LIST);
			rebuildMemberList(!showIcons, members, InterfaceID.ClansSidepanel.PLAYERLIST);
			rebuildMemberList(!showIcons, members, InterfaceID.ClansGuestSidepanel.PLAYERLIST);
		});
	}

	private void rebuildFriendsList()
	{
		client.runScript(
			ScriptID.FRIENDS_UPDATE,
			InterfaceID.Friends.LIST_CONTAINER,
			InterfaceID.Friends.SORT_NAME,
			InterfaceID.Friends.SORT_RECENT,
			InterfaceID.Friends.SORT_WORLD,
			InterfaceID.Friends.SORT_LEGACY,
			InterfaceID.Friends.LIST,
			InterfaceID.Friends.SCROLLBAR,
			InterfaceID.Friends.LOADING,
			InterfaceID.Friends.TOOLTIP
		);
	}
}
