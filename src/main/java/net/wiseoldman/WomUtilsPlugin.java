package net.wiseoldman;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Runnables;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.WorldType;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetUtil;
import net.wiseoldman.beans.CanvasCompetition;
import net.wiseoldman.beans.Competition;
import net.wiseoldman.beans.NameChangeEntry;
import net.wiseoldman.beans.ParticipantWithStanding;
import net.wiseoldman.beans.GroupMembership;
import net.wiseoldman.beans.ParticipantWithCompetition;
import net.wiseoldman.events.WomGroupSynced;
import net.wiseoldman.events.WomOngoingPlayerCompetitionsFetched;
import net.wiseoldman.events.WomRequestFailed;
import net.wiseoldman.events.WomUpcomingPlayerCompetitionsFetched;
import net.wiseoldman.panel.CompetitionCardPanel;
import net.wiseoldman.panel.NameAutocompleter;
import net.wiseoldman.panel.WomPanel;
import net.wiseoldman.ui.CodeWordOverlay;
import net.wiseoldman.ui.CompetitionInfoBox;
import net.wiseoldman.ui.SyncButton;
import net.wiseoldman.util.DelayedAction;
import net.wiseoldman.web.WomRequestType;
import net.wiseoldman.web.WomClient;
import net.wiseoldman.web.WomCommand;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Nameable;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NameableNameChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.xpupdater.XpUpdaterConfig;
import net.runelite.client.plugins.xpupdater.XpUpdaterPlugin;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;

@Slf4j
@PluginDependency(XpUpdaterPlugin.class)
@PluginDescriptor(
	name = "Wise Old Man",
	tags = {"wom", "utils", "group", "xp"},
	description = "Helps you manage your wiseoldman.net group and track your competitions."
)
public class WomUtilsPlugin extends Plugin
{
	static final String CONFIG_GROUP = "womutils";
	private static final File WORKING_DIR;
	private static final String NAME_CHANGES = "name-changes.json";

	private static final String IMPORT_MEMBERS = "Import";
	private static final String BROWSE_GROUP = "Browse";
	private static final String MENU_TARGET = "WOM group";
	private static final String LOOKUP = "WOM lookup";
	private static final String IGNORE_RANK = "Ignore rank";
	private static final String UNIGNORE_RANK = "Unignore rank";

	private static final String KICK_OPTION = "Kick";

	private static final ImmutableList<String> AFTER_OPTIONS = ImmutableList.of("Message", "Add ignore", "Remove friend", "Delete", KICK_OPTION);

	private final ImmutableList<WidgetMenuOption> WIDGET_IMPORT_MENU_OPTIONS =
		new ImmutableList.Builder<WidgetMenuOption>()
			.add(new WidgetMenuOption(IMPORT_MEMBERS,
				MENU_TARGET, InterfaceID.Toplevel.STONE7))
			.add(new WidgetMenuOption(IMPORT_MEMBERS,
				MENU_TARGET, InterfaceID.ToplevelOsrsStretch.STONE7))
			.add(new WidgetMenuOption(IMPORT_MEMBERS,
				MENU_TARGET, InterfaceID.ToplevelPreEoc.STONE7))
			.build();

	private final ImmutableList<WidgetMenuOption> WIDGET_BROWSE_MENU_OPTIONS =
		new ImmutableList.Builder<WidgetMenuOption>()
			.add(new WidgetMenuOption(BROWSE_GROUP,
				MENU_TARGET, InterfaceID.Toplevel.STONE7))
			.add(new WidgetMenuOption(BROWSE_GROUP,
				MENU_TARGET, InterfaceID.ToplevelOsrsStretch.STONE7))
			.add(new WidgetMenuOption(BROWSE_GROUP,
				MENU_TARGET, InterfaceID.ToplevelPreEoc.STONE7))
			.build();

	private static final int XP_THRESHOLD = 10_000;

	private static final Color DEFAULT_CLAN_SETTINGS_TEXT_COLOR = new Color(0xff981f);

	private static final Splitter SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

	private boolean levelupThisSession = false;

	private static String MESSAGE_PREFIX = "WOM: ";

	public boolean isSeasonal = false;

	private String DEFAULT_ROLE = "member";

	public Double SAME_CLAN_TOLERANCE = 0.5;

	@Inject
	private Client client;

	@Inject
	private WomUtilsConfig config;

	@Inject
	private MenuManager menuManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private Gson gson;

	@Inject
	private JsonParser jsonParser;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private WomClient womClient;

	@Inject
	private XpUpdaterConfig xpUpdaterConfig;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private ScheduledExecutorService scheduledExecutorService;

	@Inject
	private Notifier notifier;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CodeWordOverlay codeWordOverlay;

	private WomPanel womPanel;

	@Inject
	ClientToolbar clientToolbar;

	private Map<String, String> nameChanges = new HashMap<>();
	private LinkedBlockingQueue<NameChangeEntry> queue = new LinkedBlockingQueue<>();
	private Map<String, GroupMembership> groupMembers = new HashMap<>();
	private List<ParticipantWithStanding> playerCompetitionsOngoing = new ArrayList<>();
	private List<ParticipantWithCompetition> playerCompetitionsUpcoming = new ArrayList<>();
	private Map<Integer, CompetitionInfoBox> competitionInfoBoxes = new HashMap<>();
	public List<CanvasCompetition> competitionsOnCanvas = new ArrayList<>();
	private List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();
	private List<String> ignoredRanks = new ArrayList<>();
	private List<String> alwaysIncludedOnSync = new ArrayList<>();

	private boolean fetchXp;
	private long lastXp;
	private boolean visitedLoginScreen = true;
	private boolean recentlyLoggedIn;
	private String playerName;
	private long accountHash;
	private boolean namechangesSubmitted = false;
	private SyncButton syncButton;
	public boolean fetchedOngoingCompetitions = false;
	public boolean fetchedUpcomingCompetitions = false;

	private NavigationButton navButton;

	private final Map<Skill, Integer> previousSkillLevels = new EnumMap<>(Skill.class);

	private boolean comparedClanMembers = false;
	private int tickCounter = 0;

	@Getter
	private static String pluginVersion = "0.0.0";

	static
	{
		WORKING_DIR = new File(RuneLite.RUNELITE_DIR, "wom-utils");
		WORKING_DIR.mkdirs();

		try (InputStream inputStream = WomUtilsPlugin.class.getResourceAsStream("/version.ini"))
		{
			Properties props = new Properties();
			props.load(inputStream);
			pluginVersion = props.getProperty("pluginVersion");
		}
		catch (IOException e)
		{
			log.error("Failed to read version.ini", e);
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Wise Old Man started! (v{})", pluginVersion);

		// This will work, idk why really, but ok
		womPanel = injector.getInstance(WomPanel.class);
		try
		{
			loadFile();
		}
		catch (IOException e)
		{
			log.error("Could not load previous name changes");
		}

		womClient.importGroupMembers();

		if (config.playerLookupOption())
		{
			menuManager.addPlayerMenuItem(LOOKUP);
		}

		if (config.importGroup())
		{
			addGroupImportOptions();
		}

		if (config.browseGroup())
		{
			addGroupBrowseOptions();
		}


		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// Set this to true here so when the plugin is enabled after the player has logged in
			// the player name is set correctly for fetching competitions in onGameTick.
			recentlyLoggedIn = true;

			clientThread.invokeLater(() -> {
				Player local = client.getLocalPlayer();
				if (local != null)
				{
					womClient.fetchOngoingPlayerCompetitions(client.getLocalPlayer().getName());
					womClient.fetchUpcomingPlayerCompetitions(client.getLocalPlayer().getName());
					return true;
				}
				return false;
			});
		}

		for (WomCommand c : WomCommand.values())
		{
			chatCommandManager.registerCommandAsync(c.getCommand(), this::commandHandler);
		}

		ignoredRanks = new ArrayList<>(Arrays.asList(gson.fromJson(config.ignoredRanks(), String[].class)));
		competitionsOnCanvas = new ArrayList<>(Arrays.asList(gson.fromJson(config.competitionsOnCanvas(), CanvasCompetition[].class)));


		String ignoreRanksDisplayText = ignoredRanks.stream()
			.map(Object::toString)
			.collect(Collectors.joining(", "));

		// update the ignored ignoreRanksDisplayed text on load if it was modified, it's meant to be read only.
		if (!config.ignoredRanksDisplay().equals(ignoreRanksDisplayText))
		{
			config.ignoreRanksDisplay(ignoreRanksDisplayText);
		}


		alwaysIncludedOnSync.addAll(SPLITTER.splitToList(config.alwaysIncludedOnSync()));

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "wom-icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Wise Old Man")
			.icon(icon)
			.priority(5)
			.panel(womPanel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(codeWordOverlay);

		clientThread.invoke(this::saveCurrentLevels);
	}

	@Override
	protected void shutDown() throws Exception
	{
		removeGroupMenuOptions();
		menuManager.removePlayerMenuItem(LOOKUP);

		for (WomCommand c : WomCommand.values())
		{
			chatCommandManager.unregisterCommand(c.getCommand());
		}
		clientToolbar.removeNavigation(navButton);
		womPanel.shutdown();
		clearInfoBoxes();
		competitionsOnCanvas.clear();
		cancelNotifications();
		previousSkillLevels.clear();
		ignoredRanks.clear();
		alwaysIncludedOnSync.clear();
		levelupThisSession = false;
		overlayManager.remove(codeWordOverlay);
		log.info("Wise Old Man stopped!");
	}

	private void addGroupBrowseOptions()
	{
		addGroupMenuOptions(WIDGET_BROWSE_MENU_OPTIONS, ev -> {
			openGroupInBrowser();
		});
	}

	private void addGroupImportOptions()
	{
		addGroupMenuOptions(WIDGET_IMPORT_MENU_OPTIONS, ev -> {
			womClient.importGroupMembers();
		});
	}

	private void saveCurrentLevels()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		for (Skill s : Skill.values())
		{
			previousSkillLevels.put(s, client.getRealSkillLevel(s));
		}
	}

	private void commandHandler(ChatMessage chatMessage, String s)
	{
		// TODO: Handle individual ehp/ehbs.

		WomCommand cmd = WomCommand.fromCommand(s);

		if (cmd == null)
		{
			return;
		}

		commandLookup(cmd, chatMessage);
	}

	private void commandLookup(WomCommand command, ChatMessage chatMessage)
	{
		ChatMessageType type = chatMessage.getType();

		String player;

		if (type == ChatMessageType.PRIVATECHATOUT)
		{
			player = client.getLocalPlayer().getName();
		}
		else
		{
			player = Text.sanitize(chatMessage.getName());
		}

		womClient.commandLookup(player, command, chatMessage);
	}

	private boolean isValidNameChange(String prev, String curr)
	{
		return !(Strings.isNullOrEmpty(prev)
			|| curr.equals(prev)
			|| prev.startsWith("[#")
			|| curr.startsWith("[#"));
	}

	@Subscribe
	public void onNameableNameChanged(NameableNameChanged nameableNameChanged)
	{
		final Nameable nameable = nameableNameChanged.getNameable();

		String name = nameable.getName();
		String prev = nameable.getPrevName();

		if (!isValidNameChange(prev, name))
		{
			return;
		}

		NameChangeEntry entry = new NameChangeEntry(Text.toJagexName(prev), Text.toJagexName(name));

		if (isChangeAlreadyRegistered(entry))
		{
			return;
		}

		registerNameChange(entry);
	}

	private boolean isChangeAlreadyRegistered(NameChangeEntry entry)
	{
		String expected = nameChanges.get(entry.getNewName());
		// We can't just check the key because people can change back and forth between names
		return expected != null && expected.equals(entry.getOldName());
	}

	private void registerNameChange(NameChangeEntry entry)
	{
		nameChanges.put(entry.getNewName(), entry.getOldName());
		queue.add(entry);
	}

	@Schedule(
		period = 30,
		unit = ChronoUnit.MINUTES
	)
	public void sendUpdate()
	{
		if (queue.isEmpty())
		{
			if (syncButton != null)
			{
				syncButton.setEnabled();
			}
			namechangesSubmitted = true;

			return;
		}

		List<Nameable> friendIgnore = new ArrayList<>();
		friendIgnore.addAll(Arrays.asList(client.getFriendContainer().getMembers()));
		friendIgnore.addAll(Arrays.asList(client.getIgnoreContainer().getMembers()));

		// List of current valid name changes in our friends/ignore list.
		List<NameChangeEntry> validNameChanges = friendIgnore.stream()
			.filter(nameable -> isValidNameChange(nameable.getPrevName(), nameable.getName()))
			.map(nameable -> new NameChangeEntry(Text.toJagexName(nameable.getPrevName()), Text.toJagexName(nameable.getName())))
			.collect(Collectors.toList());

		// Remove a name change from the queue if it is no longer in our friends/ignore list at the time
		// of submission.
		queue.removeIf(entry -> {
			if (!validNameChanges.contains(entry))
			{
				nameChanges.remove(entry.getNewName(), entry.getOldName());
				return true;
			}
			return false;
		});

		womClient.submitNameChanges(queue.toArray(new NameChangeEntry[0]));
		clientThread.invoke(queue::clear);

		try
		{
			saveFile();

			if (syncButton != null)
			{
				syncButton.setEnabled();
			}
			namechangesSubmitted = true;
		}
		catch (IOException e)
		{
			log.error("Could not write name changes to filesystem");
		}
	}

	private void loadFile() throws IOException
	{
		File file = new File(WORKING_DIR, NAME_CHANGES);
		if (file.exists())
		{
			try
			{
				String json = Files.asCharSource(file, Charsets.UTF_8).read();
				JsonObject jsonObject = jsonParser.parse(json).getAsJsonObject();

				for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet())
				{
					nameChanges.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
			catch (JsonSyntaxException | IllegalStateException e)
			{
				nameChanges.clear();
				log.debug("name-changes.json file was malformed");
			}
		}
	}

	private void saveFile() throws IOException
	{
		String changes = gson.toJson(this.nameChanges);
		File file = new File(WORKING_DIR, NAME_CHANGES);
		Files.asCharSink(file, Charsets.UTF_8).write(changes);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.menuLookupOption())
		{
			return;
		}

		int groupId = WidgetUtil.componentToInterface(event.getActionParam1());
		String option = event.getOption();

		if (!AFTER_OPTIONS.contains(option)
			// prevent duplicate menu options in friends list
			|| (option.equals("Delete") && groupId != InterfaceID.IGNORE))
		{
			return;
		}

		String name = Text.toJagexName(Text.removeTags(event.getTarget()));

		if (config.menuLookupOption())
		{
			boolean addMenuLookup = (groupId == InterfaceID.FRIENDS
				|| groupId == InterfaceID.CHATCHANNEL_CURRENT
				|| groupId == InterfaceID.CLANS_SIDEPANEL
				|| groupId == InterfaceID.CLANS_GUEST_SIDEPANEL
				// prevent from adding for Kick option (interferes with the raiding party one)
				|| groupId == InterfaceID.CHATBOX && !KICK_OPTION.equals(option)
				|| groupId == InterfaceID.RAIDS_SIDEPANEL
				|| groupId == InterfaceID.PM_CHAT
				|| groupId == InterfaceID.IGNORE);

			if (addMenuLookup)
			{
				client.getMenu().createMenuEntry(-2)
					.setTarget(event.getTarget())
					.setOption(LOOKUP)
					.setType(MenuAction.RUNELITE)
					.setIdentifier(event.getIdentifier())
					.onClick(e -> lookupPlayer(name));
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() == MenuAction.RUNELITE_PLAYER && event.getMenuOption().equals(LOOKUP))
		{
			IndexedObjectSet<? extends Player> players = client.getTopLevelWorldView().players();
			Player player = players.byIndex(event.getId());

			if (player == null)
			{
				return;
			}
			String target = player.getName();
			lookupPlayer(target);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill s = event.getSkill();
		int levelAfter = client.getRealSkillLevel(s);
		int levelBefore = previousSkillLevels.getOrDefault(s, -1);

		if (levelBefore != -1 && levelAfter > levelBefore)
		{
			levelupThisSession = true;
		}
		previousSkillLevels.put(s, levelAfter);
	}

	private void openGroupInBrowser()
	{
		String url = new HttpUrl.Builder()
			.scheme("https")
			.host(isSeasonal ? "league.wiseoldman.net" : "wiseoldman.net")
			.addPathSegment("groups")
			.addPathSegment("" + config.groupId())
			.build()
			.toString();

		SwingUtilities.invokeLater(() -> LinkBrowser.browse(url));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP))
		{
			return;
		}

		menuManager.removePlayerMenuItem(LOOKUP);
		if (config.playerLookupOption())
		{
			menuManager.addPlayerMenuItem(LOOKUP);
		}

		removeGroupMenuOptions();
		if (config.groupId() > 0)
		{
			if (config.browseGroup())
			{
				addGroupBrowseOptions();
			}

			if (config.importGroup())
			{
				addGroupImportOptions();
			}
		}

		if (event.getKey().equals("sendCompetitionNotification"))
		{
			updateScheduledNotifications();
		}

		if (event.getKey().equals("alwaysIncludedOnSync"))
		{
			alwaysIncludedOnSync.clear();
			alwaysIncludedOnSync.addAll(SPLITTER.splitToList(config.alwaysIncludedOnSync()));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		if (widgetLoaded.getGroupId() != InterfaceID.CLANS_INFO && widgetLoaded.getGroupId() != InterfaceID.CLANS_MEMBERS)
		{
			return;
		}


		switch (widgetLoaded.getGroupId())
		{
			case InterfaceID.CLANS_MEMBERS:
				clientThread.invoke(() ->
				{
					createSyncButton(InterfaceID.ClansMembers.FRAME);
					if (syncButton != null)
					{
						syncButton.setEnabled();
					}
					clientThread.invokeLater(this::updateIgnoredRankColors);
				});
				break;
			case InterfaceID.CLANS_INFO:
				clientThread.invoke(() ->
				{
					createSyncButton(InterfaceID.ClansInfo.FRAME);
					if (namechangesSubmitted)
					{
						if (syncButton != null)
						{
							syncButton.setEnabled();
						}
					}
					else
					{
						clientThread.invokeLater(this::sendUpdate);
					}
				});

				break;
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (event.getMenuEntries().length < 2)
		{
			return;
		}

		final MenuEntry entry = event.getMenuEntries()[event.getMenuEntries().length - 1];
		Widget clanWidgetTitleLeftSide = client.getWidget(InterfaceID.ClansMembers.HEADER1);
		boolean leftSideRanks = false;
		if (clanWidgetTitleLeftSide != null)
		{
			if (clanWidgetTitleLeftSide.getDynamicChildren().length == 5)
			{
				leftSideRanks = entry.getParam1() == InterfaceID.ClansMembers.COLUMN1 && clanWidgetTitleLeftSide.getDynamicChildren()[4].getText().equals("Rank");
			}
		}
		boolean rightSideRanks = false;
		Widget clanWidgetTitleRightSide = client.getWidget(InterfaceID.ClansMembers.HEADER2);
		if (clanWidgetTitleRightSide != null)
		{
			if (clanWidgetTitleRightSide.getDynamicChildren().length == 5)
			{
				rightSideRanks = entry.getParam1() == InterfaceID.ClansMembers.COLUMN2 && clanWidgetTitleRightSide.getDynamicChildren()[4].getText().equals("Rank");
			}
		}

		if (entry.getType() != MenuAction.CC_OP)
		{
			return;
		}

		if (!leftSideRanks && !rightSideRanks)
		{
			return;
		}

		ClanSettings clanSettings = client.getClanSettings();
		String targetPlayer = Text.removeTags(entry.getTarget());
		ClanRank rank = clanSettings.findMember(targetPlayer).getRank();
		String rankTitle = clanSettings.titleForRank(rank).getName();
		String targetRank = ColorUtil.wrapWithColorTag(rankTitle, new Color(0xff9040));
		String standardisedRankTitle = rankTitle.toLowerCase().replaceAll("[-\\s]", "_");
		boolean rankIsIgnored = ignoredRanks.contains(standardisedRankTitle);

		client.getMenu().createMenuEntry(-1)
			.setOption(!rankIsIgnored ? IGNORE_RANK : UNIGNORE_RANK)
			.setType(MenuAction.RUNELITE)
			.setTarget(targetRank)
			.onClick(e -> {
				if (!rankIsIgnored)
				{
					chatboxPanelManager.openTextMenuInput("Are you sure you want to ignore " + rankTitle + " from WOM Sync?")
						.option("Yes", () -> addIgnoredRank(standardisedRankTitle))
						.option("No", Runnables.doNothing())
						.build();
				}
				else
				{
					removeIgnoreRank(standardisedRankTitle);
				}
			});
	}

	private void addIgnoredRank(String rankTitle)
	{
		ignoredRanks.add(rankTitle);
		updateIgnoredRanks();
	}

	private void removeIgnoreRank(String rankTitle)
	{
		ignoredRanks.removeIf(r -> r.equals(rankTitle));
		updateIgnoredRanks();
	}

	private void updateIgnoredRanks()
	{
		config.ignoredRanks(gson.toJson(ignoredRanks));
		config.ignoreRanksDisplay(ignoredRanks.stream()
			.map(Object::toString)
			.collect(Collectors.joining(", ")));
		updateIgnoredRankColors();
	}

	private void updateIgnoredRankColors()
	{
		updateIgnoredRankColorsByID(InterfaceID.ClansMembers.COLUMN1);
		updateIgnoredRankColorsByID(InterfaceID.ClansMembers.COLUMN2);
	}

	private void updateIgnoredRankColorsByID(int widgetID)
	{
		Widget parent = client.getWidget(widgetID);
		if (parent == null)
		{
			return;
		}

		Widget[] children = parent.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return;
		}

		for (Widget child : children)
		{
			if (ignoredRanks.contains(child.getText().toLowerCase().replaceAll("[-\\s]", "_")))
			{
				child.setTextColor(Color.RED.getRGB());
			}
			else
			{
				child.setTextColor(DEFAULT_CLAN_SETTINGS_TEXT_COLOR.getRGB());
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState state = gameStateChanged.getGameState();
		switch (state)
		{
			case LOGGED_IN:
				if (accountHash != client.getAccountHash())
				{
					fetchXp = true;
				}

				recentlyLoggedIn = true;
				isSeasonal = client.getWorldType().contains(WorldType.SEASONAL);
				womClient.importGroupMembers();
				break;
			case LOGIN_SCREEN:
				// When a player logs out we want to set these variables
				// and also submit update request
				visitedLoginScreen = true;
				namechangesSubmitted = false;
				comparedClanMembers = false;
				womPanel.resetCompetitionsPanel();
				womPanel.resetGroupFilter();
				clearInfoBoxes();
			case HOPPING:
				Player local = client.getLocalPlayer();
				if (local == null)
				{
					return;
				}

				long totalXp = client.getOverallExperience();
				// Don't submit update unless xp threshold is reached
				if (Math.abs(totalXp - lastXp) > XP_THRESHOLD || levelupThisSession)
				{
					updateMostRecentPlayer();
					lastXp = totalXp;
					levelupThisSession = false;
				}
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (fetchXp)
		{
			lastXp = client.getOverallExperience();
			fetchXp = false;
		}

		Player local = client.getLocalPlayer();

		if (visitedLoginScreen && recentlyLoggedIn && local != null)
		{
			playerName = local.getName();
			accountHash = client.getAccountHash();
			womClient.fetchOngoingPlayerCompetitions(playerName);
			womClient.fetchUpcomingPlayerCompetitions(playerName);
			recentlyLoggedIn = false;
			visitedLoginScreen = false;
		}

		if (!womPanel.noCompetitionsErrorPanel.isVisible() &&
			fetchedUpcomingCompetitions &&
			fetchedOngoingCompetitions &&
			playerCompetitionsUpcoming.isEmpty() &&
			playerCompetitionsOngoing.isEmpty())
		{
			womPanel.showNoCompetitionsError();
		}

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// Delay comparing the clan members list for a little until clan settings have loaded
			if (tickCounter >= 5 && !comparedClanMembers)
			{
				ClanSettings clanSettings = client.getClanSettings();
				if (clanSettings != null && config.groupId() > 0)
				{
					compareClanMembersList(clanSettings);
				}
				comparedClanMembers = true;
			}
			else
			{
				tickCounter += 1;
			}
		}

		if (womPanel.active)
		{
			womPanel.updateCompetitionCountdown();
		}
	}

	public boolean isSameClan(Set<String> clanMemberNames, Set<String> groupMemberNames, double tolerance)
	{
		Set<String> onlyInClan = new HashSet<>(clanMemberNames);
		onlyInClan.removeAll(groupMemberNames);

		Set<String> onlyInGroup = new HashSet<>(groupMemberNames);
		onlyInGroup.removeAll(clanMemberNames);

		int totalDifference = onlyInClan.size() + onlyInGroup.size();

		Set<String> combinedLists = new HashSet<>(clanMemberNames);
		combinedLists.addAll(groupMemberNames);
		int totalUniqueNames = combinedLists.size();

		return ((double) totalDifference / totalUniqueNames) <= tolerance;
	}

	private void compareClanMembersList(ClanSettings clanSettings)
	{
		List<ClanMember> clanMembers = clanSettings.getMembers();

		Set<String> clanMemberNames = clanMembers.stream().map(clanMember -> clanMember.getName().toLowerCase()).collect(Collectors.toSet());
		Set<String> groupMemberNames = groupMembers.keySet();

		// Don't send the out of sync chat message so we don't encourage syncing
		// when it's not the same clan.
		if (!isSameClan(clanMemberNames, groupMemberNames, SAME_CLAN_TOLERANCE))
		{
			return;
		}

		Set<String> onlyInClan = new HashSet<>(clanMemberNames);
		onlyInClan.removeAll(groupMemberNames);

		Set<String> onlyInGroup = new HashSet<>(groupMemberNames);
		onlyInGroup.removeAll(clanMemberNames);

		boolean outOfSync = false;
		String outOfSyncMessage = "Your group is out of sync: ";
		if (!onlyInClan.isEmpty())
		{
			outOfSyncMessage += onlyInClan.size() + " player" + (onlyInClan.size() > 1 ? "s" : "") + "joined";
			outOfSync = true;
		}

		if (!onlyInClan.isEmpty() && !onlyInGroup.isEmpty())
		{
			outOfSyncMessage += " and ";
		}

		if (!onlyInGroup.isEmpty())
		{
			outOfSyncMessage += onlyInGroup.size() + " player" + (onlyInGroup.size() > 1 ? "s" : "") + " left";
			outOfSync = true;
		}

		if (onlyInClan.isEmpty() && onlyInGroup.isEmpty())
		{
			// check if ranks differ when the member lists are the same
			int ranksChanged = 0;
			for (ClanMember cm : clanMembers)
			{
				ClanTitle clanTitle = clanSettings.titleForRank(cm.getRank());
				String groupRole = groupMembers.get(cm.getName().toLowerCase()).getRole();

				// clanTitle=null syncs to default role "member" on WOM.
				if (clanTitle != null && !clanTitle.getName().toLowerCase().replaceAll(" ", "_").equals(groupRole) || clanTitle == null && !groupRole.equals(DEFAULT_ROLE))
				{
					ranksChanged += 1;
				}
			}

			if (ranksChanged > 0)
			{
				outOfSyncMessage += ranksChanged + " rank" + (ranksChanged > 1 ? "s" : "") + " changed";
				outOfSync = true;
			}
		}

		outOfSyncMessage += ".";

		if (outOfSync)
		{
			sendResponseToChat(outOfSyncMessage, womClient.ERROR);
		}
	}

	private void addGroupMenuOptions(List<WidgetMenuOption> menuOptions, Consumer<MenuEntry> callback)
	{
		for (WidgetMenuOption option : menuOptions)
		{
			menuManager.addManagedCustomMenu(option, callback);
		}
	}

	private void removeGroupMenuOptions()
	{
		for (WidgetMenuOption option : WIDGET_BROWSE_MENU_OPTIONS)
		{
			menuManager.removeManagedCustomMenu(option);
		}

		for (WidgetMenuOption option : WIDGET_IMPORT_MENU_OPTIONS)
		{
			menuManager.removeManagedCustomMenu(option);
		}
	}

	private void updateMostRecentPlayer()
	{
		updateMostRecentPlayer(false);
	}

	private void updateMostRecentPlayer(boolean always)
	{
		boolean coreUpdaterIsOff = pluginManager
			.getPlugins().stream()
			.noneMatch(p -> p instanceof XpUpdaterPlugin && pluginManager.isPluginEnabled(p));

		if (always || !xpUpdaterConfig.wiseoldman() || coreUpdaterIsOff)
		{
			log.debug("Submitting update for {}", playerName);
			// Send update requests even if the user has forgotten to enable player updates in the core plugin
			womClient.updatePlayer(playerName, accountHash);
		}
	}

	private void lookupPlayer(String playerName)
	{
		SwingUtilities.invokeLater(() ->
		{
			clientToolbar.openPanel(navButton);
			womPanel.lookup(playerName);
		});
	}

	@Subscribe
	public void onWomGroupSynced(WomGroupSynced event)
	{
		Map<String, GroupMembership> old = new HashMap<>(groupMembers);

		groupMembers.clear();
		for (GroupMembership member : event.getGroupInfo().getMemberships())
		{
			groupMembers.put(member.getPlayer().getUsername(), member);
		}

		if (!event.isSilent())
		{
			String message = compareChanges(old, groupMembers);
			sendResponseToChat(message, getSuccessColor());
		}
	}

	@Subscribe
	public void onWomOngoingPlayerCompetitionsFetched(WomOngoingPlayerCompetitionsFetched event)
	{
		// Filter out competitions with null metrics
		playerCompetitionsOngoing = Arrays.stream(event.getCompetitions())
			.filter(pws -> pws.getCompetition().getMetric() != null)
			.collect(Collectors.toList());

		log.debug("Fetched {} ongoing competitions for player {}", event.getCompetitions().length, event.getUsername());
		for (ParticipantWithStanding pws : playerCompetitionsOngoing)
		{
			Competition c = pws.getCompetition();
			if (config.competitionLoginMessage())
			{
				sendHighlightedMessage(c.getStatus());
			}
		}
		updateScheduledNotifications();
		womPanel.addOngoingCompetitions(playerCompetitionsOngoing);
		womPanel.addGroupFilters(playerCompetitionsOngoing.stream().map(ParticipantWithStanding::getCompetition).toArray(Competition[]::new));
		fetchedOngoingCompetitions = true;
	}

	@Subscribe
	public void onWomUpcomingPlayerCompetitionsFetched(WomUpcomingPlayerCompetitionsFetched event)
	{
		// Filter out competitions with null metrics
		playerCompetitionsUpcoming = Arrays.stream(event.getCompetitions())
			.filter(pwc -> pwc.getCompetition().getMetric() != null)
			.collect(Collectors.toList());

		log.debug("Fetched {} upcoming competitions for player {}", event.getCompetitions().length, event.getUsername());
		updateScheduledNotifications();
		womPanel.addUpcomingCompetitions(playerCompetitionsUpcoming);
		womPanel.addGroupFilters(playerCompetitionsUpcoming.stream().map(ParticipantWithCompetition::getCompetition).toArray(Competition[]::new));
		fetchedUpcomingCompetitions = true;
	}

	@Subscribe
	public void onWomRequestFailed(WomRequestFailed event)
	{
		if (event.getType() == WomRequestType.COMPETITIONS_ONGOING || event.getType() == WomRequestType.COMPETITIONS_UPCOMING)
		{
			womPanel.displayCompetitionFetchError(event.getType(), event.getUsername());
		}
	}

	public void addInfoBox(CompetitionCardPanel p)
	{
		int competitionId = p.getCompetition().getId();
		if (hasInfoBox(competitionId))
		{
			return;
		}

		CompetitionInfoBox infoBox = new CompetitionInfoBox(p, this);
		competitionInfoBoxes.put(competitionId, infoBox);
		infoBoxManager.addInfoBox(infoBox);

		if (competitionsOnCanvas.stream().map(CanvasCompetition::getId).collect(Collectors.toSet()).contains(competitionId))
		{
			return;
		}

		competitionsOnCanvas.add(new CanvasCompetition(competitionId, p.getCompetition().isActive()));
		config.competitionsOnCanvas(gson.toJson(competitionsOnCanvas));
	}

	public void removeInfoBox(CompetitionCardPanel p)
	{
		int id = p.getCompetition().getId();
		infoBoxManager.removeIf(e -> e instanceof CompetitionInfoBox && ((CompetitionInfoBox) e).getCompetition().getId() == id);
		competitionInfoBoxes.remove(id);

		competitionsOnCanvas.removeIf(c -> c.getId() == id);
		config.competitionsOnCanvas(gson.toJson(competitionsOnCanvas));
	}

	public void clearOldCanvasCompetitions(Set<Integer> competitionIds, boolean ongoing)
	{
		competitionsOnCanvas.removeIf(onCanvas -> onCanvas.isOngoing() == ongoing && !competitionIds.contains(onCanvas.getId()));
		config.competitionsOnCanvas(gson.toJson(competitionsOnCanvas));
	}

	public boolean hasInfoBox(int id)
	{
		return competitionInfoBoxes.containsKey(id);
	}

	private void clearInfoBoxes()
	{
		infoBoxManager.removeIf(CompetitionInfoBox.class::isInstance);
		competitionInfoBoxes.clear();
	}

	private void updateScheduledNotifications()
	{
		cancelNotifications();

		List<DelayedAction> delayedActions = new ArrayList<>();

		for (ParticipantWithCompetition pwc : playerCompetitionsUpcoming)
		{
			Competition c = pwc.getCompetition();
			if (!c.hasStarted())
			{
				delayedActions.add(new DelayedAction(c.durationLeft().plusSeconds(1), () ->
					updateMostRecentPlayer(true)));
				if (!config.sendCompetitionNotification())
				{
					continue;
				}
				delayedActions.add(new DelayedAction(c.durationLeft().minusHours(1), () ->
					notifier.notify(c.getStatus())));
				delayedActions.add(new DelayedAction(c.durationLeft().minusMinutes(15), () ->
					notifier.notify(c.getStatus())));
				delayedActions.add(new DelayedAction(c.durationLeft().plusSeconds(1), () ->
					notifier.notify("Competition: " + c.getTitle() + " has started!")));
			}
		}

		for (ParticipantWithStanding pws : playerCompetitionsOngoing)
		{
			Competition c = pws.getCompetition();
			// Send an update when there are 15 minutes left so that there is at least one datapoint in the end
			delayedActions.add(new DelayedAction(c.durationLeft().minusMinutes(15), () ->
				updateMostRecentPlayer(true)));
			if (!config.sendCompetitionNotification())
			{
				continue;
			}
			delayedActions.add(new DelayedAction(c.durationLeft().minusHours(1), () ->
				notifier.notify(c.getStatus())));
			delayedActions.add(new DelayedAction(c.durationLeft().minusMinutes(15), () ->
				notifier.notify(c.getStatus())));
			delayedActions.add(new DelayedAction(c.durationLeft().minusMinutes(4), () ->
				notifier.notify("Competition: " + c.getTitle() + " is ending soon, logout now to record your final datapoint!")));
			delayedActions.add(new DelayedAction(c.durationLeft().plusSeconds(1), () ->
				notifier.notify("Competition: " + c.getTitle() + " is over, thanks for playing!")));
		}

		for (DelayedAction action : delayedActions)
		{
			if (!action.getDelay().isNegative())
			{
				scheduledFutures.add(scheduledExecutorService.schedule(action.getRunnable(),
					action.getDelay().getSeconds(), TimeUnit.SECONDS));
			}
		}
	}

	private void cancelNotifications()
	{
		for (ScheduledFuture<?> sf : scheduledFutures)
		{
			sf.cancel(false);
		}
		scheduledFutures.clear();
	}

	private String compareChanges(Map<String, GroupMembership> oldMembers, Map<String, GroupMembership> newMembers)
	{
		int membersAdded = 0;
		int ranksChanged = 0;
		for (String username : newMembers.keySet())
		{
			if (oldMembers.containsKey(username))
			{
				if (!newMembers.get(username).getRole().equals(oldMembers.get(username).getRole()))
				{
					ranksChanged += 1;
				}
			}
			else
			{
				membersAdded += 1;
			}
		}

		int membersRemoved = oldMembers.size() + membersAdded - newMembers.size();

		return String.format("Synced %d clan members. %d added, %d removed, %d ranks changed, %d ranks ignored.",
			newMembers.size(), membersAdded, membersRemoved, ranksChanged, ignoredRanks.size());
	}

	private void sendResponseToChat(String message, Color color)
	{
		ChatMessageBuilder cmb = new ChatMessageBuilder();
		cmb.append(color, MESSAGE_PREFIX + message);

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(cmb.build())
			.build());
	}

	private void sendHighlightedMessage(String chatMessage)
	{
		final String message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(MESSAGE_PREFIX + chatMessage)
			.build();

		chatMessageManager.queue(
			QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(message)
				.build());
	}

	private void createSyncButton(int w)
	{
		if (config.syncClanButton() && config.groupId() > 0 && !Strings.isNullOrEmpty(config.verificationCode()))
		{
			syncButton = new SyncButton(client, this, clientThread, womClient, chatboxPanelManager, w, groupMembers, ignoredRanks, alwaysIncludedOnSync);
		}
	}

	private Color getSuccessColor()
	{
		if (client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 0 || !client.isResized())
		{
			return new Color(client.getVarpValue(VarPlayerID.OPTION_CHAT_COLOUR_CLANBROADCAST_OPAQUE) - 1);

		}
		else
		{
			return new Color(client.getVarpValue(VarPlayerID.OPTION_CHAT_COLOUR_CLANBROADCAST_TRANSPARENT) - 1);
		}
	}

	@Provides
	WomUtilsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WomUtilsConfig.class);
	}

	@Override
	public void configure(Binder binder)
	{
		binder.bind(NameAutocompleter.class);
		binder.bind(WomClient.class);
		binder.bind(CodeWordOverlay.class);
	}
}
