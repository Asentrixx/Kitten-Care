package com.darre.kittencare;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class KittenCareOverlay extends Overlay
{
	private static final Color SAFE_COLOR = new Color(97, 200, 172);
	private static final Color WARN_COLOR = new Color(255, 92, 92);
	private static final Color DUE_COLOR = new Color(196, 30, 58);

	private final KittenCarePlugin plugin;
	private final KittenCareConfig config;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	KittenCareOverlay(KittenCarePlugin plugin, KittenCareConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || !plugin.hasTrackedKitten())
		{
			return null;
		}

		Duration feedRemaining = plugin.getFeedRemaining();
		Duration petRemaining = plugin.getPetRemaining();
		Duration feedLead = plugin.getFeedReminderLead();
		Duration petLead = plugin.getPetReminderLead();
		Duration guessedAge = plugin.getGuessedAge();
		Duration guessedAdultEta = plugin.getGuessedAdultEta();
		KittenCarePlugin.CareStage feedStage = plugin.getFeedStage();
		KittenCarePlugin.CareStage petStage = plugin.getPetStage();
		boolean adultPet = plugin.isAdultTrackedPet();
		Duration nextCare = minDuration(feedRemaining, petRemaining);
		Color moodColor = moodColor(feedStage, petStage, nextCare, feedLead, petLead);
		Color feedColor = timerColor(feedStage, feedRemaining, feedLead);
		Color petColor = timerColor(petStage, petRemaining, petLead);

		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(TitleComponent.builder().text("Kitten Care (=^.^=)").color(moodColor).build());
		panelComponent.getChildren().add(
			LineComponent.builder()
				.left("Follower")
				.right(plugin.getTrackedPetDisplayName())
				.rightColor(moodColor)
				.build()
		);
		panelComponent.getChildren().add(
			LineComponent.builder()
				.left("Food")
				.leftColor(feedColor)
				.right(adultPet ? "Done" : plugin.formatDuration(feedRemaining))
				.rightColor(feedColor)
				.build()
		);
		panelComponent.getChildren().add(
			LineComponent.builder()
				.left("Food state")
				.leftColor(feedColor)
				.right(plugin.getFeedStageText())
				.rightColor(feedColor)
				.build()
		);
		panelComponent.getChildren().add(
			LineComponent.builder()
				.left("Play")
				.leftColor(petColor)
				.right(adultPet ? "Done" : plugin.formatDuration(petRemaining))
				.rightColor(petColor)
				.build()
		);
		panelComponent.getChildren().add(
			LineComponent.builder()
				.left("Play state")
				.leftColor(petColor)
				.right(plugin.getPetStageText())
				.rightColor(petColor)
				.build()
		);
		panelComponent.getChildren().add(
			LineComponent.builder()
				.left("Mood")
				.leftColor(moodColor)
				.right(moodText(feedStage, petStage, nextCare, feedLead, petLead))
				.rightColor(moodColor)
				.build()
		);
		if (guessedAge != null)
		{
			panelComponent.getChildren().add(
				LineComponent.builder()
					.left("Age")
					.right(plugin.formatAge(guessedAge))
					.build()
			);
		}
		if (guessedAdultEta != null)
		{
			panelComponent.getChildren().add(
				LineComponent.builder()
					.left("Adult")
					.right(plugin.formatAge(guessedAdultEta))
					.build()
			);
		}

		return panelComponent.render(graphics);
	}

	private Duration minDuration(Duration a, Duration b)
	{
		if (a == null)
		{
			return b;
		}

		if (b == null)
		{
			return a;
		}

		return a.compareTo(b) <= 0 ? a : b;
	}

	private String moodText(KittenCarePlugin.CareStage feedStage, KittenCarePlugin.CareStage petStage, Duration nextCare, Duration feedLead, Duration petLead)
	{
		if (feedStage == KittenCarePlugin.CareStage.ADULT && petStage == KittenCarePlugin.CareStage.ADULT)
		{
			return "Grown up";
		}

		if (feedStage == KittenCarePlugin.CareStage.CRITICAL || petStage == KittenCarePlugin.CareStage.CRITICAL
			|| feedStage == KittenCarePlugin.CareStage.RUNAWAY || petStage == KittenCarePlugin.CareStage.RUNAWAY)
		{
			return "Panic";
		}

		if (feedStage == KittenCarePlugin.CareStage.WARNING || petStage == KittenCarePlugin.CareStage.WARNING)
		{
			return "Demanding";
		}

		if (nextCare == null)
		{
			return "Waiting";
		}

		Duration moodLead = feedLead.compareTo(petLead) <= 0 ? feedLead : petLead;
		if (nextCare.compareTo(moodLead) <= 0)
		{
			return "Restless";
		}

		return "Purring";
	}

	private Color moodColor(KittenCarePlugin.CareStage feedStage, KittenCarePlugin.CareStage petStage, Duration nextCare, Duration feedLead, Duration petLead)
	{
		if (feedStage == KittenCarePlugin.CareStage.ADULT && petStage == KittenCarePlugin.CareStage.ADULT)
		{
			return SAFE_COLOR;
		}

		if (feedStage == KittenCarePlugin.CareStage.CRITICAL || petStage == KittenCarePlugin.CareStage.CRITICAL
			|| feedStage == KittenCarePlugin.CareStage.RUNAWAY || petStage == KittenCarePlugin.CareStage.RUNAWAY)
		{
			return DUE_COLOR;
		}

		if (feedStage == KittenCarePlugin.CareStage.WARNING || petStage == KittenCarePlugin.CareStage.WARNING)
		{
			return WARN_COLOR;
		}

		if (nextCare == null)
		{
			return Color.WHITE;
		}

		Duration moodLead = feedLead.compareTo(petLead) <= 0 ? feedLead : petLead;
		if (nextCare.compareTo(moodLead) <= 0)
		{
			return WARN_COLOR;
		}

		return SAFE_COLOR;
	}

	private Color timerColor(KittenCarePlugin.CareStage stage, Duration remaining, Duration reminderLead)
	{
		if (stage == KittenCarePlugin.CareStage.ADULT)
		{
			return SAFE_COLOR;
		}

		if (remaining == null)
		{
			return Color.WHITE;
		}

		if (stage == KittenCarePlugin.CareStage.CRITICAL || stage == KittenCarePlugin.CareStage.RUNAWAY)
		{
			return DUE_COLOR;
		}

		if (stage == KittenCarePlugin.CareStage.WARNING || remaining.compareTo(reminderLead) <= 0)
		{
			return WARN_COLOR;
		}

		return SAFE_COLOR;
	}
}
