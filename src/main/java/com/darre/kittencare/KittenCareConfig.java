package com.darre.kittencare;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

@ConfigGroup("kittencare")
public interface KittenCareConfig extends Config
{
	@Range(min = 0, max = 600)
	@ConfigItem(
		position = 1,
		keyName = "feedReminderSeconds",
		name = "Feed reminder lead (seconds)",
		description = "How early to remind before the hunger warning window starts"
	)
	default int feedReminderSeconds()
	{
		return 90;
	}

	@Range(min = 0, max = 600)
	@ConfigItem(
		position = 2,
		keyName = "petReminderSeconds",
		name = "Pet reminder lead (seconds)",
		description = "How early to remind before the attention warning window starts"
	)
	default int petReminderSeconds()
	{
		return 90;
	}

	@Range(min = 5, max = 600)
	@ConfigItem(
		position = 3,
		keyName = "repeatReminderSeconds",
		name = "Repeat reminder every (seconds)",
		description = "How often reminders repeat while your kitten is in a warning or critical state"
	)
	default int repeatReminderSeconds()
	{
		return 30;
	}

	@ConfigItem(
		position = 4,
		keyName = "notifyInChat",
		name = "Show game chat reminders",
		description = "Post reminder messages in the game chat"
	)
	default boolean notifyInChat()
	{
		return true;
	}

	@ConfigItem(
		position = 5,
		keyName = "desktopNotification",
		name = "Desktop notification",
		description = "Desktop alert behavior for reminders"
	)
	default Notification desktopNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(
		position = 6,
		keyName = "showOverlay",
		name = "Show countdown overlay",
		description = "Show the hunger, attention, and growth overlay"
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		position = 7,
		keyName = "syncFromWarningMessages",
		name = "Sync from warning messages",
		description = "When RuneScape says your kitten is hungry, starving, needy, or lonely, sync to that exact stage"
	)
	default boolean syncFromWarningMessages()
	{
		return true;
	}

	@ConfigItem(
		position = 8,
		keyName = "showFollowerOverlay",
		name = "Show follower overlay",
		description = "Draw a scene overlay over your kitten/cat while it is out"
	)
	default boolean showFollowerOverlay()
	{
		return true;
	}

	@ConfigItem(
		position = 9,
		keyName = "debugMode",
		name = "Debug mode",
		description = "Show extra state updates in chat/log for testing"
	)
	default boolean debugMode()
	{
		return false;
	}
}
