package net.wiseoldman.beans;

import java.util.Date;
import lombok.Data;

@Data
public class ParticipantWithStanding
{
	int playerId;
	int competitionId;
	String teamName;
	Date createdAt;
	Date updatedAt;
	CompetitionProgress progress;
	int rank;
	Competition competition;
}
