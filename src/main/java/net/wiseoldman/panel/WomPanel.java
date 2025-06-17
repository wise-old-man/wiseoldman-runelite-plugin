package net.wiseoldman.panel;

import com.google.common.base.Strings;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.WorldType;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.wiseoldman.WomUtilsConfig;
import net.wiseoldman.WomUtilsPlugin;
import net.wiseoldman.beans.Competition;
import net.wiseoldman.beans.GroupInfo;
import net.wiseoldman.beans.ParticipantWithCompetition;
import net.wiseoldman.beans.ParticipantWithStanding;
import net.wiseoldman.beans.PlayerInfo;
import net.wiseoldman.web.WomRequestType;
import net.wiseoldman.web.WomClient;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.LinkBrowser;
import okhttp3.HttpUrl;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

@Slf4j
public class WomPanel extends PluginPanel
{
	@Inject
	private Client client;

	@Inject
	private WomUtilsPlugin plugin;

	/* The maximum allowed username length in RuneScape accounts */
	private static final int MAX_USERNAME_LENGTH = 12;
	private static final String DEFAULT_GROUP_FILTER = "All";
	private static final String FETCHING_ERROR_TEMPLATE =
		"<html><body  style='color:#FF0000; text-align:center;'>Failed to load %s competitions</body></html>";

	private final SkillingPanel skillingPanel;
	private final BossingPanel bossingPanel;
	private final ActivitiesPanel activitiesPanel;
	private final PluginErrorPanel competitionsErrorPanel;
	public final PluginErrorPanel noCompetitionsErrorPanel;
	private final JPanel ongoingCompetitionsPanel;
	private final JPanel upComingCompetitionsPanel;
	private final JPanel groupFilterPanel = new JPanel();
	private final JComboBox<String> groupFilter = new JComboBox<>(new String[]{DEFAULT_GROUP_FILTER});

	private final MaterialTabGroup topTabGroup;

	private final NameAutocompleter nameAutocompleter;
	private final MaterialTab lookupTab;
	private final WomClient womClient;
	private final WomUtilsConfig config;

	private IconTextField searchBar;

	private final java.util.List<MiscInfoLabel> miscInfoLabels = new ArrayList<>();
	private final java.util.List<JButton> buttons = new ArrayList<>();
	private final List<CompetitionCardPanel> competitionCardPanels = new ArrayList<>();

	public boolean active;
	boolean nonGroupCompetitions = false;

	@Inject
	public WomPanel(Client client, WomUtilsPlugin plugin, NameAutocompleter nameAutocompleter, WomClient womClient, WomUtilsConfig config,
					SkillingPanel skillingPanel, BossingPanel bossingPanel, ActivitiesPanel activitiesPanel)
	{
		this.client = client;
		this.plugin = plugin;
		this.nameAutocompleter = nameAutocompleter;
		this.womClient = womClient;
		this.config = config;
		this.skillingPanel = skillingPanel;
		this.bossingPanel = bossingPanel;
		this.activitiesPanel = activitiesPanel;


		// The layout seems to be ignoring the top margin and only gives it
		// a 2-3 pixel margin, so I set the value to 18 to compensate
		// TODO: Figure out why this layout is ignoring most of the top margin
		setBorder(new EmptyBorder(18, 10, 0, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new GridBagLayout());

		// Expand sub items to fit width of panel, align to top of panel
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 0;
		c.insets = new Insets(0, 0, 10, 0);

		JPanel competitionsPanel = new JPanel();
		competitionsPanel.setLayout(new BoxLayout(competitionsPanel, BoxLayout.Y_AXIS));

		competitionsErrorPanel = new PluginErrorPanel();
		competitionsErrorPanel.setContent("No competitions found", "Please log in to load your ongoing and upcoming competitions.");

		noCompetitionsErrorPanel = new PluginErrorPanel();
		noCompetitionsErrorPanel.setContent("No competitions found", "You are not participating in any competitions.");
		noCompetitionsErrorPanel.setVisible(false);

		ongoingCompetitionsPanel = new JPanel();
		ongoingCompetitionsPanel.setLayout(new BoxLayout(ongoingCompetitionsPanel, BoxLayout.Y_AXIS));

		upComingCompetitionsPanel = new JPanel();
		upComingCompetitionsPanel.setLayout(new BoxLayout(upComingCompetitionsPanel, BoxLayout.Y_AXIS));

		JLabel groupFilterLabel = new JLabel("<html><body><p text-align:left;'>Filter by group</p</body></html>");
		groupFilterLabel.setFont(FontManager.getRunescapeSmallFont());
		groupFilterLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		groupFilterLabel.setBorder(new EmptyBorder(0, -1, 5, 0));

		groupFilter.setFont(FontManager.getRunescapeSmallFont());
		groupFilter.setAlignmentX(Component.CENTER_ALIGNMENT);
		groupFilter.setSelectedItem(DEFAULT_GROUP_FILTER);
		groupFilter.addActionListener(e -> {
			String selectedFilter = (String) groupFilter.getSelectedItem();
			filterCompetitions(selectedFilter);
		});

		groupFilterPanel.setLayout(new BoxLayout(groupFilterPanel, BoxLayout.Y_AXIS));
		groupFilterPanel.add(groupFilterLabel);
		groupFilterPanel.add(groupFilter);
		groupFilterPanel.setVisible(false);

		competitionsPanel.add(groupFilterPanel);
		competitionsPanel.add(competitionsErrorPanel);
		competitionsPanel.add(noCompetitionsErrorPanel);
		competitionsPanel.add(ongoingCompetitionsPanel);
		competitionsPanel.add(upComingCompetitionsPanel);

		// Holds currently visible tab
		JPanel topDisplay = new JPanel();
		topTabGroup = new MaterialTabGroup(topDisplay);
		lookupTab = new MaterialTab("Lookup", topTabGroup, createLookupPanel());
		MaterialTab competitionsTab = new MaterialTab("Competitions", topTabGroup, competitionsPanel);

		topTabGroup.setBorder(new EmptyBorder(0, 0, 0, 0));
		topTabGroup.addTab(competitionsTab);
		topTabGroup.addTab(lookupTab);
		topTabGroup.select(competitionsTab);

		add(topTabGroup, c);
		c.gridy++;
		add(topDisplay, c);

		addInputKeyListener(nameAutocompleter);
	}

	public void shutdown()
	{
		removeInputKeyListener(nameAutocompleter);
	}

	@Override
	public void onActivate()
	{
		super.onActivate();
		searchBar.requestFocusInWindow();
		active = true;
	}

	@Override
	public void onDeactivate()
	{
		super.onDeactivate();
		active = false;
	}

	private void toggleButtons(boolean enabled)
	{
		for (JButton button : buttons)
		{
			button.setEnabled(enabled);
		}
	}

	public void lookup(String username)
	{
		searchBar.setText(username);
		topTabGroup.select(lookupTab);
		lookup();
	}

	private void lookup()
	{
		final String lookup = sanitize(searchBar.getText());
		toggleButtons(false);

		if (Strings.isNullOrEmpty(lookup))
		{
			return;
		}

		/* RuneScape usernames can't be longer than 12 characters long */
		if (lookup.length() > MAX_USERNAME_LENGTH)
		{
			searchBar.setIcon(IconTextField.Icon.ERROR);
			return;
		}

		searchBar.setEditable(false);
		searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);

		resetOverview();
		skillingPanel.reset();
		bossingPanel.reset();
		activitiesPanel.reset();

		womClient.lookupAsync(lookup).whenCompleteAsync((result, ex) -> updateAfterSearch(lookup, result, ex));
	}

	private void updateAfterSearch(String lookup, PlayerInfo result, Throwable ex)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (!sanitize(searchBar.getText()).equals(lookup))
			{
				// search has changed in the meantime
				return;
			}

			toggleButtons(true);

			if (result == null || ex != null)
			{
				if (ex != null)
				{
					log.warn("Error fetching Wise Old Man data " + ex.getMessage());
				}

				searchBar.setIcon(IconTextField.Icon.ERROR);
				searchBar.setEditable(true);

				// Track option
				return;
			}

			if (result.getLatestSnapshot() == null)
			{
				log.warn("Player on WOM without snapshot {}.", lookup);
				searchBar.setIcon(IconTextField.Icon.ERROR);
				searchBar.setEditable(true);

				// Update option
				return;
			}

			//successful player search
			searchBar.setIcon(IconTextField.Icon.SEARCH);
			searchBar.setEditable(true);

			applyResult(result);
		});
	}

	private void applyOverviewResult(PlayerInfo result)
	{
		for (MiscInfoLabel infoLabel : miscInfoLabels)
		{
			infoLabel.format(result, config.relativeTime());
		}
	}

	private void resetOverview()
	{
		for (MiscInfoLabel infoLabel : miscInfoLabels)
		{
			infoLabel.reset();
		}
	}

	private void applyResult(PlayerInfo result)
	{
		assert SwingUtilities.isEventDispatchThread();

		nameAutocompleter.addToSearchHistory(result.getUsername());

		applyOverviewResult(result);
		skillingPanel.update(result);
		bossingPanel.update(result);
		activitiesPanel.update(result);
	}

	void addInputKeyListener(KeyListener l)
	{
		this.searchBar.addKeyListener(l);
	}

	void removeInputKeyListener(KeyListener l)
	{
		this.searchBar.removeKeyListener(l);
	}

	private static String sanitize(String lookup)
	{
		return lookup.replace('\u00A0', ' ');
	}

	private void openPlayerProfile(String username)
	{
		String url = new HttpUrl.Builder()
			.scheme("https")
			.host(client.getWorldType().contains(WorldType.SEASONAL) ? "league.wiseoldman.net" : "wiseoldman.net")
			.addPathSegment("players")
			.addPathSegment(username)
			.build()
			.toString();

		SwingUtilities.invokeLater(() -> LinkBrowser.browse(url));
	}

	private JPanel createLookupPanel()
	{
		JPanel lookupPanel = new JPanel();
		lookupPanel.setLayout(new GridBagLayout());
		lookupPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 0;
		c.insets = new Insets(0, 0, 10, 0);

		searchBar = new IconTextField();
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setMinimumSize(new Dimension(0, 30));
		searchBar.addActionListener(e -> lookup());
		searchBar.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() != 2)
				{
					return;
				}
				if (client == null)
				{
					return;
				}

				Player localPlayer = client.getLocalPlayer();

				if (localPlayer != null)
				{
					lookup(localPlayer.getName());
				}
			}
		});
		searchBar.addClearListener(() ->
		{
			searchBar.setIcon(IconTextField.Icon.SEARCH);
			searchBar.setEditable(true);
			toggleButtons(false);
		});

		lookupPanel.add(searchBar, c);
		c.gridy++;

		lookupPanel.add(createButtonsPanel(), c);
		c.gridy++;

		JLabel overviewTitle = new JLabel("Overview");
		overviewTitle.setFont(FontManager.getRunescapeBoldFont());
		lookupPanel.add(overviewTitle, c);
		c.gridy++;

		lookupPanel.add(createOverViewPanel(), c);
		c.gridy++;

		MiscInfoLabel lastUpdated = new MiscInfoLabel(MiscInfo.LAST_UPDATED);
		miscInfoLabels.add(lastUpdated);
		lookupPanel.add(lastUpdated, c);
		c.gridy++;

		// Holds currently visible tab
		JPanel display = new JPanel();
		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		MaterialTab skillingTab = new MaterialTab("Skills", tabGroup, skillingPanel);
		MaterialTab bossingTab = new MaterialTab("Bosses", tabGroup, bossingPanel);
		MaterialTab activitiesTab = new MaterialTab("Activities", tabGroup, activitiesPanel);

		tabGroup.setBorder(new EmptyBorder(10, 0, 0, 0));
		tabGroup.addTab(skillingTab);
		tabGroup.addTab(bossingTab);
		tabGroup.addTab(activitiesTab);
		tabGroup.select(skillingTab);

		lookupPanel.add(tabGroup, c);
		c.gridy++;
		lookupPanel.add(display, c);
		c.gridy++;

		return lookupPanel;
	}

	private JPanel createButtonsPanel()
	{
		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new GridBagLayout());
		buttonsPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.ipady = 10;

		JButton updateButton = new JButton();
		updateButton.setFont(FontManager.getRunescapeSmallFont());
		updateButton.setEnabled(false);
		updateButton.addActionListener(e ->
			womClient.updateAsync(sanitize(searchBar.getText())).whenCompleteAsync((result, ex) ->
			{
				updateAfterSearch(sanitize(searchBar.getText()), result, ex);
			}));
		updateButton.setText("Update");

		JButton profileButton = new JButton();
		profileButton.setFont(FontManager.getRunescapeSmallFont());
		profileButton.setEnabled(false);
		profileButton.addActionListener(e ->
			openPlayerProfile(sanitize(searchBar.getText())));
		profileButton.setText("Open Profile");

		buttons.add(updateButton);
		buttons.add(profileButton);

		buttonsPanel.add(updateButton, gbc);
		gbc.gridx++;
		gbc.insets.left = 7;
		buttonsPanel.add(profileButton, gbc);

		return buttonsPanel;
	}

	private JPanel createOverViewPanel()
	{
		JPanel miscInfoPanel = new JPanel();
		miscInfoPanel.setLayout(new GridLayout(3, 2, 5, 5));

		for (MiscInfo info : MiscInfo.values())
		{
			if (info != MiscInfo.LAST_UPDATED)
			{
				MiscInfoLabel miscInfoLabel = new MiscInfoLabel(info);
				miscInfoLabels.add(miscInfoLabel);
				miscInfoPanel.add(miscInfoLabel);
			}
		}
		return miscInfoPanel;
	}

	public void addOngoingCompetitions(List<ParticipantWithStanding> competitions)
	{
		ongoingCompetitionsPanel.removeAll();

		JPanel ongoingCompetitions = new JPanel();
		ongoingCompetitions.setLayout(new BoxLayout(ongoingCompetitions, BoxLayout.Y_AXIS));

		// Remove any competition from canvas list that are no longer in the newly fetched competitions list
		Set<Integer> currentOngoing = competitions.stream().map(ParticipantWithStanding::getCompetitionId).collect(Collectors.toSet());
		plugin.clearOldCanvasCompetitions(currentOngoing, true);

		for (ParticipantWithStanding c : competitions)
		{
			CompetitionCardPanel competitionPanel = new CompetitionCardPanel(client, plugin, c);
			competitionCardPanels.add(competitionPanel);
			ongoingCompetitions.add(competitionPanel);

			if (plugin.competitionsOnCanvas.stream().anyMatch(cc -> cc.getId() == c.getCompetitionId()) ||
				config.addCompetitionsToCanvas().equals(WomUtilsConfig.CompetitionsToAddToCanvas.ONGOING) ||
				config.addCompetitionsToCanvas().equals(WomUtilsConfig.CompetitionsToAddToCanvas.BOTH))
			{
				plugin.addInfoBox(competitionPanel);
			}
		}

		if (competitionsErrorPanel.isVisible() && !competitionCardPanels.isEmpty())
		{
			competitionsErrorPanel.setVisible(false);
		}

		ongoingCompetitionsPanel.add(ongoingCompetitions);
	}

	public void addUpcomingCompetitions(List<ParticipantWithCompetition> competitions)
	{
		upComingCompetitionsPanel.removeAll();

		JPanel upcomingCompetitions = new JPanel();
		upcomingCompetitions.setLayout(new BoxLayout(upcomingCompetitions, BoxLayout.Y_AXIS));

		// Remove any competition from canvas list that are no longer in the newly fetched competitions list
		Set<Integer> currentUpcoming = competitions.stream().map(ParticipantWithCompetition::getCompetitionId).collect(Collectors.toSet());
		plugin.clearOldCanvasCompetitions(currentUpcoming, false);

		for (ParticipantWithCompetition c : competitions)
		{
			CompetitionCardPanel competitionPanel = new CompetitionCardPanel(client, plugin, c);
			competitionCardPanels.add(competitionPanel);
			upcomingCompetitions.add(competitionPanel);

			if (plugin.competitionsOnCanvas.stream().anyMatch(cc -> cc.getId() == c.getCompetitionId()) ||
				config.addCompetitionsToCanvas().equals(WomUtilsConfig.CompetitionsToAddToCanvas.UPCOMING) ||
				config.addCompetitionsToCanvas().equals(WomUtilsConfig.CompetitionsToAddToCanvas.BOTH))
			{
				plugin.addInfoBox(competitionPanel);
			}
		}


		if (competitionsErrorPanel.isVisible() && !competitionCardPanels.isEmpty())
		{
			competitionsErrorPanel.setVisible(false);
		}

		upComingCompetitionsPanel.add(upcomingCompetitions);
	}

	public void displayCompetitionFetchError(WomRequestType type, String username)
	{
		JPanel retryPanel = new JPanel();
		retryPanel.setLayout(new BorderLayout());
		retryPanel.setBorder(new EmptyBorder(20, 0, 20, 0));
		JLabel errorLabel = new JLabel(String.format(FETCHING_ERROR_TEMPLATE, type.getName()), SwingConstants.CENTER);
		errorLabel.setFont(FontManager.getRunescapeSmallFont());

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton retryButton = new JButton();
		retryButton.setFont(FontManager.getRunescapeSmallFont());
		retryButton.setText("Retry");
		buttonPanel.add(retryButton);

		retryPanel.add(errorLabel, BorderLayout.CENTER);
		retryPanel.add(buttonPanel, BorderLayout.SOUTH);
		competitionsErrorPanel.setVisible(false);
		if (type == WomRequestType.COMPETITIONS_UPCOMING)
		{
			upComingCompetitionsPanel.removeAll();
			retryButton.addActionListener(e ->
				womClient.fetchUpcomingPlayerCompetitions(username)
			);
			upComingCompetitionsPanel.add(retryPanel);
		}
		else if (type == WomRequestType.COMPETITIONS_ONGOING)
		{
			ongoingCompetitionsPanel.removeAll();
			retryButton.addActionListener(e ->
				womClient.fetchOngoingPlayerCompetitions(username)
			);
			ongoingCompetitionsPanel.add(retryPanel);
		}
	}

	private void filterCompetitions(String filter)
	{
		for (CompetitionCardPanel p : competitionCardPanels)
		{
			GroupInfo group = p.getCompetition().getGroup();
			p.setVisible(filter.equals(DEFAULT_GROUP_FILTER) || (group != null && group.getName().equals(filter)));
		}
	}

	public void addGroupFilters(Competition[] competitions)
	{
		for (Competition c : competitions)
		{
			GroupInfo group = c.getGroup();
			if (group != null && !filterContainsItem(group.getName()))
			{
				groupFilter.addItem(group.getName());
			}

			nonGroupCompetitions = nonGroupCompetitions || group == null;

		}

		// > 2 because of the default "All" option always being included.
		int itemCount = groupFilter.getItemCount();
		if (itemCount > 2 || (itemCount == 2 && nonGroupCompetitions))
		{
			groupFilterPanel.setVisible(true);
		}
	}

	public void resetGroupFilter()
	{
		groupFilter.removeAllItems();
		groupFilter.addItem(DEFAULT_GROUP_FILTER);
		groupFilter.setSelectedItem(DEFAULT_GROUP_FILTER);
		groupFilter.revalidate();
		groupFilterPanel.setVisible(false);
	}

	public void resetCompetitionsPanel()
	{
		competitionCardPanels.clear();
		ongoingCompetitionsPanel.removeAll();
		upComingCompetitionsPanel.removeAll();
		competitionsErrorPanel.setVisible(true);
		noCompetitionsErrorPanel.setVisible(false);
	}

	public void updateCompetitionCountdown()
	{
		for (CompetitionCardPanel p : competitionCardPanels)
		{
			SwingUtilities.invokeLater(p::updateCountDown);
		}
	}

	private boolean filterContainsItem(String item)
	{
		for (int i = 0; i < groupFilter.getItemCount(); i++)
		{
			if (groupFilter.getItemAt(i).equals(item))
			{
				return true;
			}
		}

		return false;
	}

	public void showNoCompetitionsError()
	{
		competitionsErrorPanel.setVisible(false);
		noCompetitionsErrorPanel.setVisible(true);
	}
}
