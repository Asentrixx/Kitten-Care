package com.darre.kittencare;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Kitten Care",
	description = "Tracks kitten feed and pet timers with reminders",
	tags = {"kitten", "cat", "pet", "timer", "notification"}
)
public class KittenCarePlugin extends Plugin
{
	private static final Duration ACTION_CONFIRM_WINDOW = Duration.ofSeconds(8);
	private static final int OVERHEAD_ALERT_CYCLES = 100;
	private static final int WORLD_HOP_GRACE_TICKS = 10;
	private static final Duration GAME_TICK_DURATION = Duration.ofMillis(600);
	private static final Duration FEED_SATISFIED_DURATION = Duration.ofMinutes(24);
	private static final Duration FEED_WARNING_WINDOW = Duration.ofMinutes(3);
	private static final Duration FEED_CRITICAL_WINDOW = Duration.ofMinutes(3);
	private static final Duration ATTENTION_INITIAL_DURATION = Duration.ofMinutes(25);
	private static final Duration ATTENTION_SINGLE_STROKE_DURATION = Duration.ofMinutes(18);
	private static final Duration ATTENTION_MULTI_STROKE_DURATION = Duration.ofMinutes(25);
	private static final Duration ATTENTION_WOOL_DURATION = Duration.ofMinutes(51);
	private static final Duration ATTENTION_WARNING_WINDOW = Duration.ofMinutes(7);
	private static final Duration ATTENTION_CRITICAL_WINDOW = Duration.ofMinutes(7);
	private static final Pattern GUESS_AGE_PATTERN = Pattern.compile(
		"^after taking a good look at your (?<pet>kitten|cat) you guess that its age is: (?<age>[^.]+)\\. and approximate time until fully adult: (?<adult>[^,]+), assuming you look after it\\.?$"
	);
	private static final Pattern DURATION_PART_PATTERN = Pattern.compile(
		"^(?:(?<hours>\\d+) hours?)?(?:\\s*(?<minutes>\\d+) minutes?)?$"
	);
	private static final Set<String> FEED_MENU_OPTIONS = Set.of("feed");
	private static final Set<String> PET_MENU_OPTIONS = Set.of("stroke", "pet", "cuddle");
	private static final Set<String> FEED_CONFIRM_PREFIXES = Set.of(
		"you feed your kitten",
		"you feed your cat",
		"the kitten gobbles up the ",
		"the cat gobbles up the "
	);
	private static final Set<String> PET_CONFIRM_MESSAGES = Set.of(
		"you stroke your kitten",
		"you stroke your kitten.",
		"you softly stroke your kitten",
		"you softly stroke your kitten.",
		"you stroke your cat",
		"you stroke your cat.",
		"you softly stroke your cat",
		"you softly stroke your cat.",
		"you pet your kitten",
		"you pet your kitten.",
		"you pet your cat",
		"you pet your cat."
	);
	private static final Set<String> FEED_WARNING_PREFIXES = Set.of(
		"your kitten is hungry",
		"your kitten is hungry.",
		"your kitten is starving",
		"your kitten is starving.",
		"your cat is hungry",
		"your cat is hungry.",
		"your cat is starving",
		"your cat is starving."
	);
	private static final Set<String> PET_WARNING_PREFIXES = Set.of(
		"your kitten needs attention",
		"your kitten needs attention.",
		"your kitten wants attention",
		"your kitten wants attention.",
		"your kitten wants some attention",
		"your kitten wants some attention.",
		"your kitten looks lonely",
		"your kitten looks lonely.",
		"your cat needs attention",
		"your cat needs attention.",
		"your cat wants attention",
		"your cat wants attention.",
		"your cat wants some attention",
		"your cat wants some attention.",
		"your cat looks lonely",
		"your cat looks lonely."
	);
	private static final Set<String> WOOL_ITEM_NAMES = Set.of("ball of wool");

	enum CareStage
	{
		ADULT,
		UNKNOWN,
		CONTENT,
		WARNING,
		CRITICAL,
		RUNAWAY
	}

	private enum AttentionResetType
	{
		WARNING_SYNC,
		STROKE,
		WOOL
	}

	private enum PendingAttentionAction
	{
		NONE,
		STROKE,
		WOOL
	}

	static final class CareStatus
	{
		private final CareStage stage;
		private final Duration remaining;

		private CareStatus(CareStage stage, Duration remaining)
		{
			this.stage = stage;
			this.remaining = remaining;
		}

		CareStage getStage()
		{
			return stage;
		}

		Duration getRemaining()
		{
			return remaining;
		}
	}

	@Inject
	private Client client;

	@Inject
	private KittenCareConfig config;

	@Inject
	private Notifier notifier;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private KittenCareOverlay overlay;

	@Inject
	private KittenCareNpcOverlay npcOverlay;

	private Instant lastFedAt;
	private Instant lastPettedAt;

	private Instant nextFeedReminderAt;
	private Instant nextPetReminderAt;

	private Instant pendingFeedInteractionAt;
	private Instant pendingPetInteractionAt;
	private PendingAttentionAction pendingAttentionAction = PendingAttentionAction.NONE;
	private Duration petContentDuration;
	private AttentionResetType lastAttentionResetType;
	private Duration guessedAge;
	private Duration guessedAdultEta;
	private int trackedGrowthTicks;
	private int worldHopGraceTicks;

	private boolean hasKitten;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(npcOverlay);
		updateHasKitten();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(npcOverlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();
		if (gameState == GameState.HOPPING)
		{
			worldHopGraceTicks = WORLD_HOP_GRACE_TICKS;
			hasKitten = false;
			clearPendingInteractionState();
			return;
		}

		if (gameState == GameState.LOGIN_SCREEN)
		{
			clearTrackedState();
			hasKitten = false;
			worldHopGraceTicks = 0;
			return;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			updateHasKitten();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String target = sanitize(event.getMenuTarget());
		if (!isTrackedCatText(target))
		{
			return;
		}

		if (!isKittenFollower())
		{
			return;
		}

		String option = sanitize(event.getMenuOption());
		Instant now = Instant.now();
		if (isPetMenuOption(option))
		{
			pendingPetInteractionAt = now;
			pendingAttentionAction = PendingAttentionAction.STROKE;
			return;
		}

		if (isFeedMenuOption(option))
		{
			confirmFeed("feed-menu");
			return;
		}

		if (event.getMenuAction() == MenuAction.WIDGET_TARGET_ON_NPC
			|| "use".equals(option))
		{
			if (isWoolAction(event))
			{
				confirmAttention(PendingAttentionAction.WOOL, "wool-menu");
				return;
			}

			pendingFeedInteractionAt = now;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM
			&& event.getType() != ChatMessageType.MESBOX)
		{
			return;
		}

		String message = sanitize(event.getMessage());
		if (message.isEmpty())
		{
			return;
		}

		if (handleGuessAgeMessage(message))
		{
			return;
		}

		if (config.debugMode() && isTrackedCatText(message))
		{
			log.debug("Tracked pet message observed: {}", message);
		}

		if (looksLikeFeedConfirmation(message))
		{
			confirmFeed("chat");
		}

		if (looksLikePetConfirmation(message))
		{
			confirmAttention(pendingAttentionAction == PendingAttentionAction.NONE ? PendingAttentionAction.STROKE : pendingAttentionAction, "chat");
		}

		if (looksLikeFeedWarning(message))
		{
			if (config.syncFromWarningMessages())
			{
				syncFeedWarning(message);
			}
		}

		if (looksLikePetWarning(message))
		{
			if (config.syncFromWarningMessages())
			{
				syncAttentionWarning(message);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (worldHopGraceTicks > 0)
		{
			worldHopGraceTicks--;
		}

		updateHasKitten();
		if (!hasKitten)
		{
			if (worldHopGraceTicks == 0)
			{
				clearRuntimeReminderState();
			}
			return;
		}

		worldHopGraceTicks = 0;

		if (guessedAge != null)
		{
			trackedGrowthTicks++;
		}

		if (pendingFeedInteractionAt != null && !withinWindow(pendingFeedInteractionAt, Instant.now()))
		{
			pendingFeedInteractionAt = null;
		}

		if (pendingPetInteractionAt != null && !withinWindow(pendingPetInteractionAt, Instant.now()))
		{
			pendingPetInteractionAt = null;
			pendingAttentionAction = PendingAttentionAction.NONE;
		}

		updateReminderState();
	}

	boolean hasTrackedKitten()
	{
		return hasKitten;
	}

	NPC getTrackedFollower()
	{
		NPC follower = client.getFollower();
		return isTrackedFollower(follower) ? follower : null;
	}

	Duration getFeedRemaining()
	{
		return getFeedStatus().getRemaining();
	}

	Duration getPetRemaining()
	{
		return getPetStatus().getRemaining();
	}

	CareStage getFeedStage()
	{
		return getFeedStatus().getStage();
	}

	CareStage getPetStage()
	{
		return getPetStatus().getStage();
	}

	String getFeedStageText()
	{
		switch (getFeedStage())
		{
			case ADULT:
				return "No care needed";
			case WARNING:
				return "Hungry";
			case CRITICAL:
				return "Very hungry";
			case RUNAWAY:
				return "Run away";
			case CONTENT:
				return "Content";
			case UNKNOWN:
			default:
				return "Waiting";
		}
	}

	String getPetStageText()
	{
		switch (getPetStage())
		{
			case ADULT:
				return "No care needed";
			case WARNING:
				return "Needs attention";
			case CRITICAL:
				return "Lonely";
			case RUNAWAY:
				return "Run away";
			case CONTENT:
				return "Happy";
			case UNKNOWN:
			default:
				return "Waiting";
		}
	}

	Duration getFeedReminderLead()
	{
		return Duration.ofSeconds(Math.max(0, config.feedReminderSeconds()));
	}

	Duration getPetReminderLead()
	{
		return Duration.ofSeconds(Math.max(0, config.petReminderSeconds()));
	}

	Duration getGuessedAge()
	{
		return advanceDuration(guessedAge);
	}

	Duration getGuessedAdultEta()
	{
		if (isAdultTrackedPet())
		{
			return Duration.ZERO;
		}

		return countdownDuration(guessedAdultEta);
	}

	boolean isAdultTrackedPet()
	{
		NPC follower = getTrackedFollower();
		if (follower == null || follower.getName() == null)
		{
			return false;
		}

		String followerName = sanitize(follower.getName());
		return isTrackedCatText(followerName) && !followerName.contains("kitten");
	}

	String getTrackedPetDisplayName()
	{
		NPC follower = getTrackedFollower();
		if (follower == null || follower.getName() == null)
		{
			return "Cat";
		}

		return Text.removeTags(follower.getName()).trim();
	}

	String formatDuration(Duration duration)
	{
		if (duration == null)
		{
			return "--:--";
		}

		boolean overdue = duration.isNegative();
		Duration abs = duration.abs();
		long totalSeconds = abs.getSeconds();
		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;
		String value = String.format(Locale.ENGLISH, "%d:%02d", minutes, seconds);
		return overdue ? "-" + value : value;
	}

	String formatAge(Duration duration)
	{
		if (duration == null)
		{
			return "?";
		}

		long totalMinutes = Math.max(0, duration.toMinutes());
		long hours = totalMinutes / 60;
		long minutes = totalMinutes % 60;
		if (hours > 0)
		{
			return String.format(Locale.ENGLISH, "%dh %02dm", hours, minutes);
		}

		return String.format(Locale.ENGLISH, "%dm", minutes);
	}

	private void updateReminderState()
	{
		CareStatus feedStatus = getFeedStatus();
		CareStatus petStatus = getPetStatus();

		nextFeedReminderAt = maybeRemind(
			buildFeedReminderMessage(feedStatus),
			buildFeedOverheadAlert(feedStatus),
			feedStatus,
			getFeedReminderLead(),
			nextFeedReminderAt
		);

		nextPetReminderAt = maybeRemind(
			buildPetReminderMessage(petStatus),
			buildPetOverheadAlert(petStatus),
			petStatus,
			getPetReminderLead(),
			nextPetReminderAt
		);
	}

	private Instant maybeRemind(String reminderMessage, String overheadMessage, CareStatus status, Duration reminderLead, Instant nextAt)
	{
		Duration remaining = status.getRemaining();
		if (remaining == null || status.getStage() == CareStage.ADULT)
		{
			return null;
		}

		if (status.getStage() == CareStage.CONTENT && remaining.compareTo(reminderLead) > 0)
		{
			return null;
		}

		Instant now = Instant.now();
		if (nextAt != null && now.isBefore(nextAt))
		{
			return nextAt;
		}

		sendReminder(reminderMessage, overheadMessage);
		return now.plusSeconds(Math.max(5, config.repeatReminderSeconds()));
	}

	private void sendReminder(String message, String overheadMessage)
	{
		if (config.notifyInChat())
		{
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				"<col=ff9ac1>[Kitten Care]</col> " + message,
				null
			);
		}

		notifier.notify(config.desktopNotification(), message);
		showOverheadAlert(overheadMessage);
	}

	private void updateHasKitten()
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			hasKitten = false;
			return;
		}

		boolean oldHasKitten = hasKitten;
		hasKitten = isKittenFollower();
		if (hasKitten)
		{
			return;
		}

		if (worldHopGraceTicks > 0)
		{
			return;
		}

		if (oldHasKitten && !hasKitten)
		{
			clearTrackedState();
		}
	}

	private void confirmFeed(String source)
	{
		Instant now = Instant.now();
		if (!withinWindow(pendingFeedInteractionAt, now) && !looksLikeDirectFeedSignal(source))
		{
			return;
		}

		lastFedAt = now;
		nextFeedReminderAt = null;
		pendingFeedInteractionAt = null;
		if (config.debugMode())
		{
			log.debug("Feed confirmed from {}", source);
		}
	}

	private void confirmAttention(PendingAttentionAction action, String source)
	{
		Instant now = Instant.now();
		boolean directSignal = action == PendingAttentionAction.WOOL
			? "wool-menu".equals(source)
			: looksLikeDirectPetSignal(source);
		if (!withinWindow(pendingPetInteractionAt, now) && !directSignal)
		{
			return;
		}

		if (action == PendingAttentionAction.WOOL)
		{
			petContentDuration = ATTENTION_WOOL_DURATION;
			lastAttentionResetType = AttentionResetType.WOOL;
		}
		else
		{
			boolean isStackedStroke = lastAttentionResetType == AttentionResetType.STROKE
				&& lastPettedAt != null
				&& getPetStage() == CareStage.CONTENT;
			petContentDuration = isStackedStroke ? ATTENTION_MULTI_STROKE_DURATION : ATTENTION_SINGLE_STROKE_DURATION;
			lastAttentionResetType = AttentionResetType.STROKE;
		}

		lastPettedAt = now;
		nextPetReminderAt = null;
		pendingPetInteractionAt = null;
		pendingAttentionAction = PendingAttentionAction.NONE;
		if (config.debugMode())
		{
			log.debug("Attention confirmed from {} using {}", source, action);
		}
	}

	private boolean withinWindow(Instant actionAt, Instant now)
	{
		return actionAt != null && Duration.between(actionAt, now).abs().compareTo(ACTION_CONFIRM_WINDOW) <= 0;
	}

	private String sanitize(String message)
	{
		return Text.removeTags(message == null ? "" : message).toLowerCase(Locale.ENGLISH).trim();
	}

	private boolean isTrackedCatText(String text)
	{
		return text.contains("kitten") || text.contains(" cat") || text.startsWith("cat") || text.contains("hellcat");
	}

	private boolean looksLikeFeedConfirmation(String message)
	{
		return startsWithAny(message, FEED_CONFIRM_PREFIXES);
	}

	private boolean looksLikePetConfirmation(String message)
	{
		return PET_CONFIRM_MESSAGES.contains(message);
	}

	private boolean looksLikeFeedWarning(String message)
	{
		return startsWithAny(message, FEED_WARNING_PREFIXES);
	}

	private boolean looksLikePetWarning(String message)
	{
		return startsWithAny(message, PET_WARNING_PREFIXES);
	}

	private boolean looksLikeDirectFeedSignal(String source)
	{
		return "chat".equals(source) || "feed-menu".equals(source);
	}

	private boolean looksLikeDirectPetSignal(String source)
	{
		return "chat".equals(source) || "menu".equals(source);
	}

	private boolean isKittenFollower()
	{
		return isTrackedFollower(client.getFollower());
	}

	private boolean isTrackedFollower(NPC follower)
	{
		if (follower == null || follower.getName() == null)
		{
			return false;
		}

		return isTrackedCatText(sanitize(follower.getName()));
	}

	private boolean isFeedMenuOption(String option)
	{
		return FEED_MENU_OPTIONS.contains(option);
	}

	private boolean isPetMenuOption(String option)
	{
		return PET_MENU_OPTIONS.contains(option);
	}

	private boolean startsWithAny(String text, Set<String> prefixes)
	{
		for (String prefix : prefixes)
		{
			if (text.startsWith(prefix))
			{
				return true;
			}
		}

		return false;
	}

	private void clearPendingInteractionState()
	{
		pendingFeedInteractionAt = null;
		pendingPetInteractionAt = null;
		pendingAttentionAction = PendingAttentionAction.NONE;
	}

	private void clearRuntimeReminderState()
	{
		nextFeedReminderAt = null;
		nextPetReminderAt = null;
		clearPendingInteractionState();
	}

	private void clearTrackedState()
	{
		lastFedAt = null;
		lastPettedAt = null;
		petContentDuration = null;
		lastAttentionResetType = null;
		guessedAge = null;
		guessedAdultEta = null;
		trackedGrowthTicks = 0;
		clearRuntimeReminderState();
	}

	private boolean handleGuessAgeMessage(String message)
	{
		Matcher matcher = GUESS_AGE_PATTERN.matcher(message);
		if (!matcher.matches())
		{
			return false;
		}

		Duration age = parseHumanDuration(matcher.group("age"));
		Duration adultEta = parseHumanDuration(matcher.group("adult"));
		if (age == null && adultEta == null)
		{
			return false;
		}

		guessedAge = age;
		guessedAdultEta = adultEta;
		trackedGrowthTicks = 0;
		if (config.debugMode())
		{
			log.debug("Tracked age updated: age={}, adultEta={}", guessedAge, guessedAdultEta);
		}
		return true;
	}

	private Duration parseHumanDuration(String text)
	{
		if (text == null)
		{
			return null;
		}

		Matcher matcher = DURATION_PART_PATTERN.matcher(text.trim());
		if (!matcher.matches())
		{
			return null;
		}

		String hoursText = matcher.group("hours");
		String minutesText = matcher.group("minutes");
		if (hoursText == null && minutesText == null)
		{
			return null;
		}

		long hours = hoursText == null ? 0 : Long.parseLong(hoursText);
		long minutes = minutesText == null ? 0 : Long.parseLong(minutesText);
		return Duration.ofHours(hours).plusMinutes(minutes);
	}

	CareStatus getFeedStatus()
	{
		if (isAdultTrackedPet())
		{
			return new CareStatus(CareStage.ADULT, null);
		}

		if (lastFedAt == null)
		{
			return new CareStatus(CareStage.UNKNOWN, null);
		}

		return buildStatus(Duration.between(lastFedAt, Instant.now()), FEED_SATISFIED_DURATION, FEED_WARNING_WINDOW, FEED_CRITICAL_WINDOW);
	}

	CareStatus getPetStatus()
	{
		if (isAdultTrackedPet())
		{
			return new CareStatus(CareStage.ADULT, null);
		}

		if (lastPettedAt == null || petContentDuration == null)
		{
			return new CareStatus(CareStage.UNKNOWN, null);
		}

		return buildStatus(Duration.between(lastPettedAt, Instant.now()), petContentDuration, ATTENTION_WARNING_WINDOW, ATTENTION_CRITICAL_WINDOW);
	}

	private CareStatus buildStatus(Duration elapsed, Duration contentDuration, Duration warningWindow, Duration criticalWindow)
	{
		if (elapsed.isNegative())
		{
			return new CareStatus(CareStage.CONTENT, contentDuration);
		}

		Duration warningAt = contentDuration;
		Duration criticalAt = warningAt.plus(warningWindow);
		Duration runawayAt = criticalAt.plus(criticalWindow);

		if (elapsed.compareTo(warningAt) < 0)
		{
			return new CareStatus(CareStage.CONTENT, warningAt.minus(elapsed));
		}

		if (elapsed.compareTo(criticalAt) < 0)
		{
			return new CareStatus(CareStage.WARNING, criticalAt.minus(elapsed));
		}

		if (elapsed.compareTo(runawayAt) < 0)
		{
			return new CareStatus(CareStage.CRITICAL, runawayAt.minus(elapsed));
		}

		return new CareStatus(CareStage.RUNAWAY, Duration.ZERO);
	}

	private Duration advanceDuration(Duration baseDuration)
	{
		if (baseDuration == null)
		{
			return baseDuration;
		}

		return baseDuration.plus(GAME_TICK_DURATION.multipliedBy(trackedGrowthTicks));
	}

	private Duration countdownDuration(Duration baseDuration)
	{
		if (baseDuration == null)
		{
			return baseDuration;
		}

		Duration remaining = baseDuration.minus(GAME_TICK_DURATION.multipliedBy(trackedGrowthTicks));
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}

	private String buildFeedReminderMessage(CareStatus status)
	{
		Duration remaining = status.getRemaining();
		if (remaining == null)
		{
			return "Meow! Time for kitten snacks";
		}

		switch (status.getStage())
		{
			case WARNING:
				return "Your kitten is hungry. Feed it within " + formatDuration(remaining) + ".";
			case CRITICAL:
			case RUNAWAY:
				return "Your kitten is very hungry. Feed it within " + formatDuration(remaining) + " before it runs away.";
			case CONTENT:
			default:
				return "Meow! Time for kitten snacks (due in " + formatDuration(remaining) + ")";
		}
	}

	private String buildPetReminderMessage(CareStatus status)
	{
		Duration remaining = status.getRemaining();
		if (remaining == null)
		{
			return "Meow! Time for kitten snuggles";
		}

		switch (status.getStage())
		{
			case WARNING:
				return "Your kitten needs attention. Help it within " + formatDuration(remaining) + ".";
			case CRITICAL:
			case RUNAWAY:
				return "Your kitten feels lonely. Help it within " + formatDuration(remaining) + " before it runs away.";
			case CONTENT:
			default:
				return "Meow! Time for kitten snuggles (due in " + formatDuration(remaining) + ")";
		}
	}

	private String buildFeedOverheadAlert(CareStatus status)
	{
		return buildOverheadAlert(true, status);
	}

	private String buildPetOverheadAlert(CareStatus status)
	{
		return buildOverheadAlert(false, status);
	}

	private String buildOverheadAlert(boolean feedAlert, CareStatus status)
	{
		Duration remaining = status.getRemaining();
		if (remaining == null)
		{
			return null;
		}

		String petLabel = getTrackedPetLabel();
		if (feedAlert)
		{
			switch (status.getStage())
			{
				case WARNING:
					return "My " + petLabel + " is hungry!";
				case CRITICAL:
				case RUNAWAY:
					return "OMG feed my " + petLabel + " now!";
				case CONTENT:
				default:
					return "OMG I need to feed my " + petLabel + " before it runs away!";
			}
		}

		switch (status.getStage())
		{
			case WARNING:
				return "My " + petLabel + " needs attention!";
			case CRITICAL:
			case RUNAWAY:
				return "OMG play with my " + petLabel + " now!";
			case CONTENT:
			default:
				return "OMG I need to play with my " + petLabel + " before it gets lonely!";
		}
	}

	private String getTrackedPetLabel()
	{
		NPC follower = getTrackedFollower();
		if (follower == null)
		{
			return "kitten";
		}

		String name = sanitize(follower.getName());
		if (name.contains("hellcat"))
		{
			return "hellcat";
		}

		if (name.contains("kitten"))
		{
			return "kitten";
		}

		return "cat";
	}

	private void showOverheadAlert(String message)
	{
		if (message == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		localPlayer.setOverheadText(message);
		localPlayer.setOverheadCycle(OVERHEAD_ALERT_CYCLES);
	}

	private void syncFeedWarning(String message)
	{
		Instant now = Instant.now();
		Duration elapsed = message.contains("starving")
			? FEED_SATISFIED_DURATION.plus(FEED_WARNING_WINDOW)
			: FEED_SATISFIED_DURATION;
		lastFedAt = now.minus(elapsed);
		nextFeedReminderAt = null;
	}

	private void syncAttentionWarning(String message)
	{
		Instant now = Instant.now();
		Duration baseDuration = petContentDuration != null ? petContentDuration : ATTENTION_INITIAL_DURATION;
		Duration elapsed = message.contains("lonely")
			? baseDuration.plus(ATTENTION_WARNING_WINDOW)
			: baseDuration;
		petContentDuration = baseDuration;
		lastAttentionResetType = AttentionResetType.WARNING_SYNC;
		lastPettedAt = now.minus(elapsed);
		nextPetReminderAt = null;
	}

	private boolean isWoolAction(MenuOptionClicked event)
	{
		int itemId = event.getItemId();
		if (itemId <= 0)
		{
			return false;
		}

		String itemName = sanitize(itemManager.getItemComposition(itemId).getName());
		return WOOL_ITEM_NAMES.contains(itemName);
	}

	@Provides
	KittenCareConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KittenCareConfig.class);
	}
}
