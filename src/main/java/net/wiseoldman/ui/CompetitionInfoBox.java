package net.wiseoldman.ui;

import lombok.Getter;
import net.wiseoldman.WomUtilsPlugin;
import net.wiseoldman.beans.Competition;
import net.wiseoldman.beans.CompetitionProgress;
import net.wiseoldman.beans.Metric;
import net.wiseoldman.panel.CompetitionCardPanel;
import net.wiseoldman.util.Utils;
import java.awt.Color;
import java.text.DecimalFormat;
import java.time.Duration;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.util.ColorUtil;
import org.apache.commons.lang3.time.DurationFormatUtils;

public class CompetitionInfoBox extends InfoBox
{
	@Getter
	final Competition competition;
	final WomUtilsPlugin plugin;
	final int rank;
	final CompetitionProgress progress;

	private static final Color ACTIVE_COLOR = new Color(0x51f542);

	public CompetitionInfoBox(CompetitionCardPanel p, WomUtilsPlugin plugin)
	{
		super(p.getCompetition().getMetric().loadIcon(p.getCompetition().getMetric().getType()), plugin);
		this.competition = p.getCompetition();
		this.rank = p.getRank();
		this.progress = p.getProgress();
		this.plugin = plugin;
	}

	@Override
	public String getTooltip()
	{
		Metric metric = competition.getMetric();
		StringBuilder sb = new StringBuilder();
		sb.append(competition.getTitle()).append("</br>")
			.append("Metric: ").append(metric.getName()).append("</br>")
			.append(competition.getTimeStatus());
		if (progress != null)
		{
			sb.append("</br>");
			double gained = progress.getGained();
			if (gained > 0)
			{
				String coloredRank = ColorUtil.wrapWithColorTag(Utils.ordinalOf(rank), Color.GREEN);
				sb.append("Ranked: ").append(coloredRank);

				final DecimalFormat df;
				if (metric == Metric.EHB || metric == Metric.EHP)
				{
					// These are the only ones actually in decimal
					df = new DecimalFormat("####.##");
				}
				else
				{
					df = new DecimalFormat("###,###,###");
				}

				String formattedProgress = df.format(gained);
				String coloredProgress = ColorUtil.wrapWithColorTag(formattedProgress, Color.GREEN);
				sb.append(" (Gained ").append(coloredProgress);

				switch (metric)
				{
					case EHB:
					case EHP:
						sb.append(" hours");
						break;
					default:
						sb.append(getUnitForType(metric.getType()));
				}
				sb.append(")");
			}
		}
		return sb.toString();
	}

	private String getUnitForType(HiscoreSkillType type)
	{
		if (type == null)
		{
			return "";
		}
		switch (type)
		{
			case SKILL:
				return " xp";
			case BOSS:
				return " kills";
			case ACTIVITY:
				return " points";
			default:
				return "";
		}
	}

	@Override
	public Color getTextColor()
	{
		return competition.isActive() ? ACTIVE_COLOR : Color.YELLOW;
	}

	@Override
	public String getText()
	{
		Duration timeLeft = competition.durationLeft();

		if (timeLeft.toDays() > 9)
		{
			return DurationFormatUtils.formatDuration(timeLeft.toMillis(), "d'd'");
		}
		else if (timeLeft.toDays() > 0)
		{
			return DurationFormatUtils.formatDuration(timeLeft.toMillis(), "d'd'H'h'");
		}
		else if (timeLeft.toHours() > 0)
		{
			return DurationFormatUtils.formatDuration(timeLeft.toMillis(), "H'h'm'm'");
		}
		else
		{
			return DurationFormatUtils.formatDuration(timeLeft.toMillis(), "mm:ss");
		}
	}

	@Override
	public boolean cull()
	{
		return competition.hasEnded();
	}
}
