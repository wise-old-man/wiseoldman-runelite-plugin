package net.wiseoldman.events;

import lombok.Value;
import net.wiseoldman.web.WomRequestType;

@Value
public class WomRequestFailed
{
	String username;
	WomRequestType type;
}
