package net.wiseoldman.web;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WomCommand
{
	EHP("!ehp", "Efficient hours played: "),
	EHB("!ehb", "Efficient hours bossed: "),
	TTM("!ttm","Time to max: "),
	TT200M("!tt200m","Time to 200m: "),
	;

	private final String command;
	private final String message;


	public static WomCommand fromCommand(String command)
	{
		for (WomCommand c : values())
		{
			if (c.getCommand().equals(command.toLowerCase()))
			{
				return c;
			}
		}
		return null;
	}
}
