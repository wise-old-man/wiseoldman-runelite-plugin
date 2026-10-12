package net.wiseoldman.beans;

import java.util.Date;
import lombok.Data;

@Data
public class CompetitionMetric
{
	int weight;
	Metric metric;
	Date createdAt;
}
