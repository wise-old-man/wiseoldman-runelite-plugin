package net.wiseoldman.events;

import net.wiseoldman.beans.ParticipantWithCompetition;
import lombok.Value;

@Value
public class WomUpcomingPlayerCompetitionsFetched
{
	String username;
	ParticipantWithCompetition[] competitions;
}
