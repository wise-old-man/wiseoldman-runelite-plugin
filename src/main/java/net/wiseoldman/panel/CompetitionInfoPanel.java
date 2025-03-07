package net.wiseoldman.panel;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.wiseoldman.beans.ParticipantWithStanding;


public class CompetitionInfoPanel extends JPanel
{
	/* The competition's info box wrapping container */
	private final JPanel container = new JPanel();

	CompetitionInfoPanel(ParticipantWithStanding p)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(5, 0, 0, 0));

		container.setLayout(new BorderLayout());
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JLabel title = new JLabel();
		title.setText(p.getCompetition().getTitle());
		container.add(title);

		add(container, BorderLayout.NORTH);
	}
}
