package net.wiseoldman.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.wiseoldman.WomUtilsPlugin;
import net.wiseoldman.beans.Competition;
import net.wiseoldman.beans.GroupInfo;
import net.wiseoldman.beans.Metric;
import net.wiseoldman.beans.ParticipantWithCompetition;
import net.wiseoldman.beans.ParticipantWithStanding;
import net.wiseoldman.util.Format;
import net.wiseoldman.util.Utils;


@Slf4j
public class CompetitionCardPanel extends JPanel
{
	private static final String INFO_LABEL_TEMPLATE =
		"<html><body style='color:%s'>%s<br>%s<span style='color:%s'>%s</span></body></html>";
	private static final String HOSTED_BY_TEMPLATE =
		"<html><body style='color:%s'>%s<span style='color:%s'>%s</span></body></html>";

	private static final String LIGHT_GRAY = ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR);
	private static final String WHITE = ColorUtil.toHexColor(Color.WHITE);
	private static final String GREEN = ColorUtil.toHexColor(ColorScheme.PROGRESS_COMPLETE_COLOR);

	private static final String ELLIPSIS = "...";

	/* The competition's info box wrapping container */
	private final JPanel container = new JPanel();
	private final JPanel headerPanel = new JPanel();
	private final JPanel titlePanel = new JPanel(new BorderLayout());
	private final JPanel infoPanel = new JPanel();

	// Holds all the competition information
	private final JLabel timerLabel = new JLabel();
	//	private final JLabel statusLabel = new JLabel();
	private final JLabel groupLabel = new JLabel();
	private final JLabel logoutReminderLabel =
		new JLabel("<html><body style='color:#FF0000; text-align:center;'>The competition has started. " +
			"Please relog to start tracking your gains.</body></html>", SwingConstants.CENTER);
	private final JLabel statusDotLabel = new JLabel("●");

	Competition competition;
	private String groupName;
	private boolean truncated;
	String fetchedStatus;

	private final FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());


	CompetitionCardPanel(ParticipantWithStanding p)
	{
		competition = p.getCompetition();

		Metric metric = p.getCompetition().getMetric();

		setupPanel(competition.getTitle(), metric, competition.getGroup());

		double gained = p.getProgress().getGained();
		JLabel gainedLabel = new JLabel(String.format(INFO_LABEL_TEMPLATE, LIGHT_GRAY, "Gained", "", gained > 0 ? GREEN : WHITE,
			(gained > 0 ? "+" : "") + Format.formatNumber(gained)));
		gainedLabel.setFont(FontManager.getRunescapeSmallFont());
		gainedLabel.setToolTipText(String.format("%,.1f", p.getProgress().getGained()));
		gainedLabel.setPreferredSize(new Dimension(53, gainedLabel.getPreferredSize().height));

		JLabel rankLabel = new JLabel(String.format(INFO_LABEL_TEMPLATE, LIGHT_GRAY, "Rank", "", WHITE, Utils.ordinalOf(p.getRank())));
		rankLabel.setFont(FontManager.getRunescapeSmallFont());

		infoPanel.add(gainedLabel);
		infoPanel.add(rankLabel);

		container.add(headerPanel, BorderLayout.NORTH);
		container.add(infoPanel, BorderLayout.CENTER);

		add(container);
	}

	CompetitionCardPanel(ParticipantWithCompetition p)
	{
		competition = p.getCompetition();

		Metric metric = p.getCompetition().getMetric();
		Competition competition = p.getCompetition();

		setupPanel(competition.getTitle(), metric, competition.getGroup());

		container.add(headerPanel, BorderLayout.NORTH);
		container.add(infoPanel, BorderLayout.CENTER);

		add(container);
	}

	private void setupPanel(String title, Metric metric, GroupInfo group)
	{
		groupName = group != null ? group.getName() : null;
		fetchedStatus = competition.hasStarted() ? "ongoing" : "upcoming";

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(5, 0, 0, 0));

		container.setLayout(new BorderLayout());
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		createHeaderPanel(title, metric, group);

		infoPanel.setLayout(new DynamicGridLayout(1, 4));
		infoPanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
		infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		infoPanel.setBorder(new EmptyBorder(7, 7, 5, 7));

		String[] keyValue = getCountdown();
		timerLabel.setText(String.format(INFO_LABEL_TEMPLATE, LIGHT_GRAY, keyValue[0], "", WHITE, keyValue[1]));
		timerLabel.setFont(FontManager.getRunescapeSmallFont());
		timerLabel.setPreferredSize(new Dimension(60, timerLabel.getPreferredSize().height));

		infoPanel.add(timerLabel);

		logoutReminderLabel.setFont(FontManager.getRunescapeBoldFont());
		logoutReminderLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		logoutReminderLabel.setVisible(false);
		container.add(logoutReminderLabel, BorderLayout.SOUTH);
	}

	private String[] getCountdown()
	{
		String timeStatus = competition.getTimeStatus();
		if (timeStatus.equals("Ended"))
		{
			return new String[]{"Ended", "\u00A0", ""};
		}

		String[] splitStatus = timeStatus.split(" in ");
		String prefix = splitStatus[0] + " in";
		String time = splitStatus[1];

		// when the seconds are 0, the seconds part is removed, so we have to check that seconds are there.
		if (time.contains("d") && time.contains("s"))
		{
			return new String[]{prefix, time.split("m\\s[0-9]{1,2}s")[0] + "m", time};
		}
		else
		{
			return new String[]{prefix, time, time};
		}
	}

	void updateCountDown()
	{
		String[] keyValue = getCountdown();
		if (competition.isActive() && fetchedStatus.equals("upcoming"))
		{
			logoutReminderLabel.setVisible(true);
			fetchedStatus = "ongoing";
		}
		statusDotLabel.setForeground(getStatusColor());
		timerLabel.setText(String.format(INFO_LABEL_TEMPLATE, LIGHT_GRAY, keyValue[0], "", WHITE, keyValue[1]));
	}

	private void createHeaderPanel(String title, Metric metric, GroupInfo group)
	{
		headerPanel.setLayout(new GridBagLayout());
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.setBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR));

		GridBagConstraints titleConstraints = new GridBagConstraints();
		titleConstraints.weightx = 1.0;
		titleConstraints.fill = GridBagConstraints.HORIZONTAL;

		GridBagConstraints iconConstraints = new GridBagConstraints();

		headerPanel.add(iconPanel(metric), iconConstraints);
		headerPanel.add(createTitlePanel(title, group), titleConstraints);
	}

	private JPanel iconPanel(Metric metric)
	{
		JPanel fixedIconPanel = new JPanel(new BorderLayout());
		fixedIconPanel.setPreferredSize(new Dimension(41, 40));
		fixedIconPanel.setMinimumSize(new Dimension(41, 40));
		fixedIconPanel.setMaximumSize(new Dimension(41, 40));
		fixedIconPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		fixedIconPanel.setBorder(new EmptyBorder(0, 8, 0, 4));

		ImageIcon iconBg = new ImageIcon(ImageUtil.loadImageResource(WomUtilsPlugin.class, "icon_bg.png"));
		ImageIcon metricIcon = new ImageIcon(metric.loadIcon(metric.getType()));

		BufferedImage combinedImage = new BufferedImage(
			iconBg.getIconWidth(),
			iconBg.getIconHeight(),
			BufferedImage.TYPE_INT_ARGB
		);

		Graphics2D g2d = combinedImage.createGraphics();
		g2d.drawImage(iconBg.getImage(), 0, 0, null);
		int x = (iconBg.getIconWidth() - metricIcon.getIconWidth()) / 2;
		int y = (iconBg.getIconHeight() - metricIcon.getIconHeight()) / 2;
		g2d.drawImage(metricIcon.getImage(), x, y, null);
		g2d.dispose();

		ImageIcon combinedIcon = new ImageIcon(combinedImage);

		JLabel metricIconLabel = new JLabel(combinedIcon);
		metricIconLabel.setBounds(0, 0, 41, 40);

		statusDotLabel.setFont(new Font("Arial", Font.BOLD, 9));
		statusDotLabel.setForeground(getStatusColor());
		statusDotLabel.setBounds(27, 24, 9, 9);

		fixedIconPanel.add(metricIconLabel);
		fixedIconPanel.add(statusDotLabel);

		metricIconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		metricIconLabel.setVerticalAlignment(SwingConstants.CENTER);
		metricIconLabel.setToolTipText(metric.getName());

		fixedIconPanel.add(metricIconLabel, BorderLayout.CENTER);

		return fixedIconPanel;
	}

	private Color getStatusColor()
	{
		if (competition.isActive())
		{
			return ColorScheme.PROGRESS_COMPLETE_COLOR;
		}
		else if (!competition.hasStarted())
		{
			return ColorScheme.PROGRESS_INPROGRESS_COLOR;
		}
		else
		{
			return Color.RED;
		}
	}

	private JPanel createTitlePanel(String title, GroupInfo group)
	{
		titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(FontManager.getRunescapeFont());
		titleLabel.setForeground(Color.white);
		titleLabel.setToolTipText(title);
		titlePanel.add(titleLabel, BorderLayout.NORTH);
		titlePanel.setBorder(new EmptyBorder(8, 0, 8, 0));


		if (group != null)
		{
			groupLabel.setText(String.format(HOSTED_BY_TEMPLATE, LIGHT_GRAY, "Hosted by ",
				ColorUtil.toHexColor(ColorScheme.GRAND_EXCHANGE_LIMIT), group.getName()));
			groupLabel.setFont(FontManager.getRunescapeSmallFont());
			titlePanel.add(groupLabel, BorderLayout.SOUTH);
		}


		return titlePanel;
	}

	private void truncateGroupName()
	{
		if (!isShowing() || groupName == null)
		{
			return;
		}

		int groupNameWidth = fm.stringWidth(groupName);
		int availableWidth = titlePanel.getWidth() - fm.stringWidth("Hosted by ");

		if (availableWidth >= groupNameWidth)
		{
			return;
		}

		double charactersPerPixel = (double) groupName.length() / groupNameWidth;
		int charactersToDisplay = (int) (Math.floor(availableWidth * charactersPerPixel) - ELLIPSIS.length() - 1);

		String truncatedGroupName = groupName.substring(0, charactersToDisplay) + ELLIPSIS;
		groupLabel.setText(String.format(HOSTED_BY_TEMPLATE, LIGHT_GRAY, "Hosted by ",
			ColorUtil.toHexColor(ColorScheme.GRAND_EXCHANGE_LIMIT), truncatedGroupName));
		groupLabel.setToolTipText(groupName);

		truncated = true;
	}

	@Override
	public void doLayout()
	{
		super.doLayout();
		if (!truncated)
		{
			SwingUtilities.invokeLater(this::truncateGroupName);
		}
	}
}
