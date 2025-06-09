package net.wiseoldman.ui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;
import net.wiseoldman.WomUtilsPlugin;

public class UnsyncedOverlay extends Overlay
{
	private final Client client;
	private final BufferedImage icon;

	@Getter
	@Setter
	private boolean isVisible;

	@Inject
	private UnsyncedOverlay(Client client)
	{
		this.client = client;
		this.icon = ImageUtil.loadImageResource(WomUtilsPlugin.class, "wom-icon.png");
		this.isVisible = false;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!this.isVisible)
		{
			return null;
		}

		Point iconPos = getIconPosition();
		graphics.drawImage(icon, iconPos.getX() + 2, iconPos.getY() + 2, null);

		return null;
	}

	private Point getIconPosition()
	{
		Widget clanTab;

		Widget logoutButton = client.getWidget(InterfaceID.ToplevelPreEoc.STONE10);
		if (logoutButton != null && !logoutButton.isHidden() && logoutButton.getParent() != null)
		{
			clanTab = client.getWidget(InterfaceID.ToplevelPreEoc.STONE7);
		}
		else if (client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 0 || !client.isResized())
		{
			clanTab = client.getWidget(InterfaceID.Toplevel.STONE7);
		}
		else
		{
			clanTab = client.getWidget(InterfaceID.ToplevelOsrsStretch.STONE7);
		}

		if (clanTab != null)
		{
			return clanTab.getCanvasLocation();
		}

		return new Point(0, 0);
	}
}