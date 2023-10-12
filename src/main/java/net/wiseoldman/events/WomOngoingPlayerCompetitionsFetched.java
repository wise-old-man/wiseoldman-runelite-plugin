package net.wiseoldman.events;

import net.wiseoldman.beans.ParticipantWithStanding;
import lombok.Value;

@Value
public class WomOngoingPlayerCompetitionsFetched
{
	String username;
	ParticipantWithStanding[] competitions;
}
