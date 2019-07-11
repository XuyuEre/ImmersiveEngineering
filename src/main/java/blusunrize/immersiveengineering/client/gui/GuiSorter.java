/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.client.gui;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.client.ClientUtils;
import blusunrize.immersiveengineering.common.blocks.wooden.TileEntitySorter;
import blusunrize.immersiveengineering.common.gui.ContainerSorter;
import blusunrize.immersiveengineering.common.network.MessageTileSync;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.client.renderer.GlStateManager.DestFactor.ZERO;
import static net.minecraft.client.renderer.GlStateManager.SourceFactor.ONE;
import static net.minecraft.client.renderer.GlStateManager.SourceFactor.SRC_ALPHA;

public class GuiSorter extends GuiIEContainerBase
{
	TileEntitySorter tile;

	public GuiSorter(PlayerInventory inventoryPlayer, TileEntitySorter tile)
	{
		super(new ContainerSorter(inventoryPlayer, tile));
		this.tile = tile;
		this.ySize = 244;
	}

	@Override
	public void render(int mx, int my, float partial)
	{
		super.render(mx, my, partial);
		for(Button button : this.buttons)
		{
			if(button instanceof ButtonSorter)
				if(mx > button.x&&mx < button.x+18&&my > button.y&&my < button.y+18)
				{
					List<ITextComponent> tooltip = new ArrayList<>();
					int type = ((ButtonSorter)button).type;
					String[] split = I18n.format(Lib.DESC_INFO+"filter."+(type==0?"oreDict": type==1?"nbt": "fuzzy")).split("<br>");
					for(int i = 0; i < split.length; i++)
						tooltip.add(new StringTextComponent(split[i]).setStyle(new Style().setColor(i==0?TextFormatting.WHITE: TextFormatting.GRAY)));
					ClientUtils.drawHoveringText(tooltip, mx, my, fontRenderer, guiLeft+xSize, -1);
					RenderHelper.enableGUIStandardItemLighting();
				}
		}
	}


	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int mx, int my)
	{
		GlStateManager.color3f(1.0F, 1.0F, 1.0F);
		ClientUtils.bindTexture("immersiveengineering:textures/gui/sorter.png");
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
		for(int side = 0; side < 6; side++)
		{
			int x = guiLeft+30+(side/2)*58;
			int y = guiTop+44+(side%2)*76;
			String s = I18n.format("desc.immersiveengineering.info.blockSide."+Direction.byIndex(side).toString()).substring(0, 1);
			GlStateManager.enableBlend();
			ClientUtils.font().drawStringWithShadow(s, x-(ClientUtils.font().getStringWidth(s)/2), y, 0xaacccccc);
		}
		ClientUtils.bindTexture("immersiveengineering:textures/gui/sorter.png");
	}

	@Override
	public void initGui()
	{
		super.initGui();
		this.buttons.clear();
		for(int side = 0; side < 6; side++)
			for(int i = 0; i < 3; i++)
			{
				int x = guiLeft+3+(side/2)*58+i*18;
				int y = guiTop+3+(side%2)*76;
				ButtonSorter b = new ButtonSorter(side*3+i, x, y, i)
				{
					@Override
					public void onClick(double mX, double mY)
					{
						int side = id/3;
						int bit = id%3;
						int mask = (1<<bit);
						tile.sideFilter[side] = tile.sideFilter[side]^mask;

						CompoundNBT tag = new CompoundNBT();
						tag.putIntArray("sideConfig", tile.sideFilter);
						ImmersiveEngineering.packetHandler.sendToServer(new MessageTileSync(tile, tag));
						initGui();
					}
				};
				b.active = i==0?this.tile.doOredict(side): i==1?this.tile.doNBT(side): this.tile.doFuzzy(side);
				this.buttons.add(b);
			}
	}

	public static class ButtonSorter extends Button
	{
		int type;
		boolean active = false;

		public ButtonSorter(int id, int x, int y, int type)
		{
			super(id, x, y, 18, 18, "");
			this.type = type;
		}

		@Override
		public void render(int mx, int my, float partialTicks)
		{
			if(this.visible)
			{
				GlStateManager.color3f(1.0F, 1.0F, 1.0F);
				this.hovered = mx >= this.x&&my >= this.y&&mx < this.x+this.width&&my < this.y+this.height;
				GlStateManager.enableBlend();
				GlStateManager.blendFuncSeparate(SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, ONE, ZERO);
				this.drawTexturedModalRect(this.x, this.y, 176+type*18, (active?3: 21), this.width, this.height);
			}
		}
	}
}
