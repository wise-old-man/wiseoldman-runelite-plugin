package net.wiseoldman.util;

import java.time.Duration;
import lombok.Value;

@Value
public class DelayedAction
{
	Duration delay;
	Runnable runnable;
}
