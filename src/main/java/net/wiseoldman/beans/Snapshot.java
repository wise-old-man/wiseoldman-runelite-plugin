package net.wiseoldman.beans;

import lombok.Value;

@Value
public class Snapshot
{
	int playerId;
	String createdAt;
	String importedAt;
	SnapshotData data;
}
