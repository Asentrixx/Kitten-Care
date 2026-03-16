package com.darre.kittencare;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

class KittenCareNpcOverlay extends Overlay
{
	private static final Color SAFE_COLOR = new Color(97, 200, 172);
	private static final Color WARN_COLOR = new Color(255, 92, 92);
	private static final Color DUE_COLOR = new Color(196, 30, 58);

	private final KittenCarePlugin plugin;
	private final KittenCareConfig config;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	KittenCareNpcOverlay(KittenCarePlugin plugin, KittenCareConfig config, ModelOutlineRenderer modelOutlineRenderer)
	{
		this.plugin = plugin;
		this.config = config;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showFollowerOverlay() || !plugin.hasTrackedKitten())
		{
			return null;
		}

		NPC follower = plugin.getTrackedFollower();
		if (follower == null)
		{
			return null;
		}

		Duration feedRemaining = plugin.getFeedRemaining();
		Duration petRemaining = plugin.getPetRemaining();
		KittenCarePlugin.CareStage feedStage = plugin.getFeedStage();
		KittenCarePlugin.CareStage petStage = plugin.getPetStage();
		boolean adultPet = plugin.isAdultTrackedPet();
		Duration nextCare = minDuration(feedRemaining, petRemaining);
		KittenCarePlugin.CareStage nextStage = nextStage(feedStage, petStage, feedRemaining, petRemaining);
		Color overlayColor = timerColor(nextStage, nextCare, plugin.getFeedReminderLead(), plugin.getPetReminderLead());
		String label = adultPet ? plugin.getTrackedPetDisplayName() : nextCare == null ? plugin.getTrackedPetDisplayName() : "Care " + plugin.formatDuration(nextCare);

		modelOutlineRenderer.drawOutline(follower, outlineWidth(nextStage, nextCare, plugin.getFeedReminderLead(), plugin.getPetReminderLead()), overlayColor, 0);
		OverlayUtil.renderActorOverlay(graphics, follower, label, overlayColor);
		return null;
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

	private KittenCarePlugin.CareStage nextStage(KittenCarePlugin.CareStage feedStage, KittenCarePlugin.CareStage petStage, Duration feedRemaining, Duration petRemaining)
	{
		if (feedStage == KittenCarePlugin.CareStage.ADULT && petStage == KittenCarePlugin.CareStage.ADULT)
		{
			return KittenCarePlugin.CareStage.ADULT;
		}

		if (feedRemaining == null)
		{
			return petStage;
		}

		if (petRemaining == null)
		{
			return feedStage;
		}

		return feedRemaining.compareTo(petRemaining) <= 0 ? feedStage : petStage;
	}

	private int outlineWidth(KittenCarePlugin.CareStage stage, Duration remaining, Duration feedLead, Duration petLead)
	{
		if (stage == KittenCarePlugin.CareStage.ADULT)
		{
			return 3;
		}

		if (remaining == null)
		{
			return 2;
		}

		if (stage == KittenCarePlugin.CareStage.CRITICAL || stage == KittenCarePlugin.CareStage.RUNAWAY)
		{
			return 6;
		}

		if (stage == KittenCarePlugin.CareStage.WARNING)
		{
			return 5;
		}

		Duration reminderLead = feedLead.compareTo(petLead) <= 0 ? feedLead : petLead;
		if (remaining.compareTo(reminderLead) <= 0)
		{
			return 4;
		}

		return 2;
	}

	private Color timerColor(KittenCarePlugin.CareStage stage, Duration remaining, Duration feedLead, Duration petLead)
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

		if (stage == KittenCarePlugin.CareStage.WARNING)
		{
			return WARN_COLOR;
		}

		Duration reminderLead = feedLead.compareTo(petLead) <= 0 ? feedLead : petLead;
		if (remaining.compareTo(reminderLead) <= 0)
		{
			return WARN_COLOR;
		}

		return SAFE_COLOR;
	}
}