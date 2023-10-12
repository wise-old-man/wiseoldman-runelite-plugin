package net.wiseoldman.events;

import net.wiseoldman.beans.CompetitionInfo;
import lombok.Value;

@Value
public class WomCompetitionInfoFetched
{
	CompetitionInfo comp;
}
