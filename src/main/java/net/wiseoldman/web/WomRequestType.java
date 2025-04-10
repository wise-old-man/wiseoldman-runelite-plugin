package net.wiseoldman.web;

import lombok.Getter;

@Getter
public enum WomRequestType
{
	COMPETITIONS_ONGOING("ongoing"),
	COMPETITIONS_UPCOMING("upcoming");

	final String name;

	WomRequestType(String name)
	{
		this.name = name;
	}
}
