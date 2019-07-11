/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.energy.wires.Connection;
import blusunrize.immersiveengineering.api.energy.wires.ConnectionPoint;
import blusunrize.immersiveengineering.api.energy.wires.TileEntityImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.WireType;
import blusunrize.immersiveengineering.api.energy.wires.localhandlers.EnergyTransferHandler.EnergyConnector;
import blusunrize.immersiveengineering.client.ClientUtils;
import blusunrize.immersiveengineering.client.models.IOBJModelCallback;
import blusunrize.immersiveengineering.common.Config.IEConfig;
import blusunrize.immersiveengineering.common.EventHandler;
import blusunrize.immersiveengineering.common.IEContent;
import blusunrize.immersiveengineering.common.blocks.BlockFakeLight.TileEntityFakeLight;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.*;
import blusunrize.immersiveengineering.common.util.ChatUtils;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.chickenbones.Matrix4;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.LightType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.model.TRSRTransformation;

import javax.annotation.Nonnull;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import java.util.*;

public class TileEntityFloodlight extends TileEntityImmersiveConnectable implements ITickable, IAdvancedDirectionalTile,
		IHammerInteraction, ISpawnInterdiction, IBlockBounds, IActiveState, ILightValue, IOBJModelCallback<BlockState>,
		EnergyConnector
{
	public static TileEntityType<TileEntityFloodlight> TYPE;

	public int energyStorage = 0;
	private int energyDraw = IEConfig.Machines.floodlight_energyDraw;
	private int maximumStorage = IEConfig.Machines.floodlight_maximumStorage;
	public boolean active = false;
	public boolean redstoneControlInverted = false;
	public Direction facing = Direction.NORTH;
	public Direction side = Direction.UP;
	public float rotY = 0;
	public float rotX = 0;
	public List<BlockPos> fakeLights = new ArrayList<>();
	public List<BlockPos> lightsToBePlaced = new ArrayList<>();
	public List<BlockPos> lightsToBeRemoved = new ArrayList<>();
	final int timeBetweenSwitches = 20;
	int switchCooldown = 0;
	private boolean shouldUpdate = true;
	public boolean computerOn = true;
	public int controllingComputers = 0;
	public int turnCooldown = 0;

	public TileEntityFloodlight()
	{
		super(TYPE);
	}

	@Override
	public void tick()
	{
		if(world.isRemote)
			return;
		if(turnCooldown > 0)
			turnCooldown--;
		// Needed for CC floodlight compat112, specifically the waiting function
		// Commented out since there is no CC compat112 right now
//		if(turnCooldown == 0)
//			notifyAll();
		boolean b = active;
		boolean enabled;
		if(shouldUpdate)
		{
			lightsToBePlaced.clear();
			updateFakeLights(true, active);
			markDirty();
			this.markContainingBlockForUpdate(null);
			shouldUpdate = false;
		}

		enabled = (controllingComputers > 0&&computerOn)||(world.getRedstonePowerFromNeighbors(getPos()) > 0^redstoneControlInverted);
		if(energyStorage >= (!active?energyDraw*10: energyDraw)&&enabled&&switchCooldown <= 0)
		{
			energyStorage -= energyDraw;
			if(!active)
				active = true;
		}
		else if(active)
		{
			active = false;
			switchCooldown = timeBetweenSwitches;
		}

		switchCooldown--;
		if(active!=b||world.getGameTime()%512==((getPos().getX()^getPos().getZ())&511))
		{
			this.markContainingBlockForUpdate(null);
			updateFakeLights(true, active);
			world.checkLightFor(LightType.BLOCK, getPos());
		}
		if(!active)
		{
			if(!lightsToBePlaced.isEmpty())
				lightsToBePlaced.clear();
		}
		if((!lightsToBePlaced.isEmpty()||!lightsToBeRemoved.isEmpty())&&world.getGameTime()%8==((getPos().getX()^getPos().getZ())&7))
		{
			Iterator<BlockPos> it = lightsToBePlaced.iterator();
			int timeout = 0;
			while(it.hasNext()&&timeout++ < Math.max(16, 32-lightsToBeRemoved.size()))
			{
				BlockPos cc = it.next();
				//				world.setBlockState(cc, Blocks.glass.getDefaultState(), 2);
				world.setBlockState(cc, IEContent.blockFakeLight.getDefaultState(), 2);
				TileEntity te = world.getTileEntity(cc);
				if(te instanceof TileEntityFakeLight)
					((TileEntityFakeLight)te).floodlightCoords = new int[]{getPos().getX(), getPos().getY(), getPos().getZ()};
				fakeLights.add(cc);
				it.remove();
			}
			it = lightsToBeRemoved.iterator();
			while(it.hasNext()&&timeout++ < 32)
			{
				BlockPos cc = it.next();
				if(Utils.getExistingTileEntity(world, cc) instanceof TileEntityFakeLight)
					world.removeBlock(cc);
				it.remove();
			}
		}
	}


	public void updateFakeLights(boolean deleteOld, boolean genNew)
	{
		Iterator<BlockPos> it = this.fakeLights.iterator();
		ArrayList<BlockPos> tempRemove = new ArrayList<BlockPos>();
		while(it.hasNext())
		{
			BlockPos cc = it.next();
			TileEntity te = world.getTileEntity(cc);
			if(te instanceof TileEntityFakeLight)
			{
				if(deleteOld)
					tempRemove.add(cc);
			}
			else
				it.remove();
		}
		if(genNew)
		{
			float angle = (float)(facing==Direction.NORTH?180: facing==Direction.EAST?90: facing==Direction.WEST?-90: 0);
			float yRotation = rotY;
			double angleX = Math.toRadians(rotX);

			Vec3d[] rays = {
					/*Straight*/new Vec3d(0, 0, 1),
					/*U,D,L,R*/new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1),
					/*Intermediate*/new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1),
					/*Diagonal*/new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1), new Vec3d(0, 0, 1)};
			Matrix4 mat = new Matrix4();
			if(side==Direction.DOWN)
				mat.scale(1, -1, 1);
			else if(side!=Direction.UP)
			{
				angle = facing==Direction.DOWN?180: facing==Direction.NORTH?-90: facing==Direction.SOUTH?90: angle;
				if(side.getAxis()==Axis.X)
				{
					mat.rotate(Math.PI/2, -1, 0, 0);
					mat.rotate(Math.PI/2, 0, 0, -side.getAxisDirection().getOffset());
				}
				else
				{
					mat.rotate(Math.PI/2, -1, 0, 0);
					if(side==Direction.SOUTH)//I dunno why south is giving me so much trouble, but this works, so who cares
					{
						mat.rotate(Math.PI, 0, 0, 1);
						if(facing.getAxis()==Axis.X)
							angle = -angle;
					}
				}
			}

			double angleY = Math.toRadians(angle+yRotation);
			mat.rotate(angleY, 0, 1, 0);
			mat.rotate(-angleX, 1, 0, 0);
			rays[0] = mat.apply(rays[0]);
			mat.rotate(Math.PI/8, 0, 1, 0);
			rays[1] = mat.apply(rays[1]);
			mat.rotate(-Math.PI/16, 0, 1, 0);
			rays[5] = mat.apply(rays[5]);
			mat.rotate(-Math.PI/8, 0, 1, 0);
			rays[6] = mat.apply(rays[6]);
			mat.rotate(-Math.PI/16, 0, 1, 0);
			rays[2] = mat.apply(rays[2]);
			mat.rotate(Math.PI/8, 0, 1, 0);
			mat.rotate(Math.PI/8, 1, 0, 0);
			rays[3] = mat.apply(rays[3]);
			mat.rotate(-Math.PI/16, 1, 0, 0);
			rays[7] = mat.apply(rays[7]);
			mat.rotate(-Math.PI/8, 1, 0, 0);
			rays[8] = mat.apply(rays[8]);
			mat.rotate(-Math.PI/16, 1, 0, 0);
			rays[4] = mat.apply(rays[4]);
			mat.rotate(Math.PI/8, 1, 0, 0);
			mat.rotate(Math.PI/16, 1, 0, 0);
			mat.rotate(Math.PI/16, 0, 1, 0);
			rays[9] = mat.apply(rays[9]);
			mat.rotate(-Math.PI/8, 0, 1, 0);
			rays[10] = mat.apply(rays[10]);
			mat.rotate(-Math.PI/8, 1, 0, 0);
			rays[11] = mat.apply(rays[11]);
			mat.rotate(Math.PI/8, 0, 1, 0);
			rays[12] = mat.apply(rays[12]);
			for(int ray = 0; ray < rays.length; ray++)
			{
				int offset = ray==0?0: ray < 4?3: 1;
				placeLightAlongVector(rays[ray], offset, tempRemove);
			}
		}
		this.lightsToBeRemoved.addAll(tempRemove);
	}

	public void placeLightAlongVector(Vec3d vec, int offset, ArrayList<BlockPos> checklist)
	{
		Vec3d light = new Vec3d(getPos()).add(.5, .75, .5);
		int range = 32;
		HashSet<BlockPos> ignore = new HashSet<BlockPos>();
		ignore.add(getPos());
		BlockPos hit = Utils.rayTraceForFirst(Utils.addVectors(vec, light), light.add(vec.x*range, vec.y*range, vec.z*range), world, ignore);
		double maxDistance = hit!=null?new Vec3d(hit).add(.5, .75, .5).squareDistanceTo(light): range*range;
		for(int i = 1+offset; i <= range; i++)
		{
			BlockPos target = getPos().add(Math.round(vec.x*i), Math.round(vec.y*i), Math.round(vec.z*i));
			double dist = (vec.x*i*vec.x*i)+(vec.y*i*vec.y*i)+(vec.z*i*vec.z*i);
			if(dist > maxDistance)
				break;
			if(target.getY() > 255||target.getY() < 0)
				continue;
			//&&world.getBlockLightValue(xx,yy,zz)<12 using this makes it not work in daylight .-.

			if(!target.equals(getPos())&&world.isAirBlock(target))
			{
				if(!checklist.remove(target))
					lightsToBePlaced.add(target);
				i += 2;
			}
		}
	}


	@Override
	public double getInterdictionRangeSquared()
	{
		return active?1024: 0;
	}

	@Override
	public void remove()
	{
		synchronized(EventHandler.interdictionTiles)
		{
			EventHandler.interdictionTiles.remove(this);
		}
		super.remove();
	}

	@Override
	public void onChunkUnloaded()
	{
		synchronized(EventHandler.interdictionTiles)
		{
			EventHandler.interdictionTiles.remove(this);
		}
		super.onChunkUnloaded();
	}

	@Override
	public void readCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		super.readCustomNBT(nbt, descPacket);
		boolean oldActive = active;
		active = nbt.getBoolean("active");
		energyStorage = nbt.getInt("energy");
		redstoneControlInverted = nbt.getBoolean("redstoneControlInverted");
		facing = Direction.byIndex(nbt.getInt("facing"));
		side = Direction.byIndex(nbt.getInt("side"));
		rotY = nbt.getFloat("rotY");
		rotX = nbt.getFloat("rotX");
		int lightAmount = nbt.getInt("lightAmount");
		fakeLights.clear();
		for(int i = 0; i < lightAmount; i++)
		{
			int[] icc = nbt.getIntArray("fakeLight_"+i);
			fakeLights.add(new BlockPos(icc[0], icc[1], icc[2]));
		}
		if(world!=null&&world.isRemote)
			this.markContainingBlockForUpdate(null);
		if(descPacket)
		{
			controllingComputers = nbt.getBoolean("computerControlled")?1: 0;
			computerOn = nbt.getBoolean("computerOn");
		}
		if(world!=null&&oldActive!=active)
		{
			world.checkLightFor(LightType.BLOCK, pos);
		}
	}

	@Override
	public void writeCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		super.writeCustomNBT(nbt, descPacket);
		nbt.putBoolean("active", active);
		nbt.putInt("energyStorage", energyStorage);
		nbt.putBoolean("redstoneControlInverted", redstoneControlInverted);
		nbt.putInt("facing", facing.ordinal());
		nbt.putInt("side", side.ordinal());
		nbt.putFloat("rotY", rotY);
		nbt.putFloat("rotX", rotX);
		nbt.putInt("lightAmount", fakeLights.size());
		for(int i = 0; i < fakeLights.size(); i++)
		{
			BlockPos cc = fakeLights.get(i);
			nbt.putIntArray("fakeLight_"+i, new int[]{cc.getX(), cc.getY(), cc.getZ()});
		}
		if(descPacket)
		{
			nbt.putBoolean("computerControlled", controllingComputers > 0);
			nbt.putBoolean("computerOn", computerOn);
		}
	}

	@Override
	public boolean receiveClientEvent(int id, int arg)
	{
		if(id==1)
		{
			this.markContainingBlockForUpdate(null);
			world.checkLightFor(LightType.BLOCK, getPos());
			return true;
		}
		return super.receiveClientEvent(id, arg);
	}

	@Override
	public boolean canConnectCable(WireType cableType, ConnectionPoint target, Vec3i offset)
	{
		return WireType.LV_CATEGORY.equals(cableType.getCategory());
	}

	@Override
	public Vec3d getConnectionOffset(@Nonnull Connection con, ConnectionPoint here)
	{
		BlockPos other = con==null?pos: con.getOtherEnd(here).getPosition();
		int xDif = other.getX()-pos.getX();
		int yDif = other.getY()-pos.getY();
		int zDif = other.getZ()-pos.getZ();
		double x, y, z;
		switch(side)
		{
			case DOWN:
			case UP:
				x = (Math.abs(xDif) >= Math.abs(zDif))?(xDif >= 0)?.9375: .0625: .5;
				y = (side==Direction.DOWN)?.9375: .0625;
				z = (Math.abs(zDif) > Math.abs(xDif))?(zDif >= 0)?.9375: .0625: .5;
				break;
			case NORTH:
			case SOUTH:
				x = (Math.abs(xDif) >= Math.abs(yDif))?(xDif >= 0)?.9375: .0625: .5;
				y = (Math.abs(yDif) > Math.abs(xDif))?(yDif >= 0)?.9375: .0625: .5;
				z = (side==Direction.NORTH)?.9375: .0625;
				break;
			case WEST:
			case EAST:
			default:
				x = (side==Direction.WEST)?.9375: .0625;
				y = (Math.abs(yDif) >= Math.abs(zDif))?(yDif >= 0)?.9375: .0625: .5;
				z = (Math.abs(zDif) > Math.abs(yDif))?(zDif >= 0)?.9375: .0625: .5;
				break;
		}
		return new Vec3d(x, y, z);
	}

	@Override
	public float[] getBlockBounds()
	{
		return new float[]{
				side.getAxis()==Axis.X?0: .0625f,
				side.getAxis()==Axis.Y?0: .0625f,
				side.getAxis()==Axis.Z?0: .0625f,
				side.getAxis()==Axis.X?1: .9375f,
				side.getAxis()==Axis.Y?1: .9375f,
				side.getAxis()==Axis.Z?1: .9375f
		};
	}

	@Override
	public boolean getIsActive()
	{
		return active;
	}

	@Override
	public int getLightValue()
	{
		return active?15: 0;
	}

	@Override
	public boolean hammerUseSide(Direction side, PlayerEntity player, float hitX, float hitY, float hitZ)
	{
		if(player.isSneaking()&&side!=this.side)
		{
			boolean base = this.side==Direction.DOWN?hitY >= .8125: this.side==Direction.UP?hitY <= .1875: this.side==Direction.NORTH?hitZ >= .8125: this.side==Direction.UP?hitZ <= .1875: this.side==Direction.WEST?hitX >= .8125: hitX <= .1875;
			if(base)
			{
				redstoneControlInverted = !redstoneControlInverted;
				ChatUtils.sendServerNoSpamMessages(player, new TranslationTextComponent(Lib.CHAT_INFO+"rsControl."+(redstoneControlInverted?"invertedOn": "invertedOff")));
				markDirty();
				this.markContainingBlockForUpdate(null);
				return true;
			}
		}
		if(side.getAxis()==this.side.getAxis())
			turnY(player.isSneaking(), false);
		else
			turnX(player.isSneaking(), false);
		return true;
	}

	@Override
	public Direction getFacing()
	{
		return side;
	}

	@Override
	public void setFacing(Direction facing)
	{
		this.side = facing;
	}

	@Override
	public int getFacingLimitation()
	{
		return 0;
	}

	@Override
	public boolean mirrorFacingOnPlacement(LivingEntity placer)
	{
		return false;
	}

	@Override
	public boolean canHammerRotate(Direction side, float hitX, float hitY, float hitZ, LivingEntity entity)
	{
		return false;
	}

	@Override
	public boolean canRotate(Direction axis)
	{
		return false;
	}

	@Override
	public void onDirectionalPlacement(Direction side, float hitX, float hitY, float hitZ, LivingEntity placer)
	{
		Direction f = Direction.fromAngle(placer.rotationYaw);
		if(f==side.getOpposite())
			f = placer.rotationPitch > 0?Direction.DOWN: Direction.UP;
		facing = f;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public boolean shouldRenderGroup(BlockState object, String group)
	{
		if("glass".equals(group))
			return MinecraftForgeClient.getRenderLayer()==BlockRenderLayer.TRANSLUCENT;
		else
			return MinecraftForgeClient.getRenderLayer()==BlockRenderLayer.SOLID;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public Optional<TRSRTransformation> applyTransformations(BlockState object, String group, Optional<TRSRTransformation> transform)
	{
		if(!transform.isPresent())
			transform = Optional.of(new TRSRTransformation((Matrix4f)null));
		Matrix4f mat = transform.get().getMatrixVec();//TODO is this correct?
		Vector3f transl = new Vector3f(.5f, .5f, .5f);

		double yaw = 0;
		double pitch = 0;
		double roll = 0;

		//		pitch, yaw, roll
		if(side.getAxis()==Axis.Y)
		{
			yaw = facing==Direction.SOUTH?180: facing==Direction.WEST?90: facing==Direction.EAST?-90: 0;
			if(side==Direction.DOWN)
				roll = 180;
		}
		else //It's a mess, but it works!
		{
			if(side==Direction.NORTH)
			{
				pitch = 90;
				yaw = 180;
			}
			if(side==Direction.SOUTH)
				pitch = 90;
			if(side==Direction.WEST)
			{
				pitch = 90;
				yaw = -90;
			}
			if(side==Direction.EAST)
			{
				pitch = 90;
				yaw = 90;
			}

			if(facing==Direction.DOWN)
				roll += 180;
			else if(side.getAxis()==Axis.X&&facing.getAxis()==Axis.Z)
				roll += 90*facing.getAxisDirection().getOffset()*side.getAxisDirection().getOffset();
			else if(side.getAxis()==Axis.Z&&facing.getAxis()==Axis.X)
				roll += -90*facing.getAxisDirection().getOffset()*side.getAxisDirection().getOffset();
		}

		transl.add(new Vector3f(side.getXOffset()*.125f, side.getYOffset()*.125f, side.getZOffset()*.125f));
		if("axis".equals(group)||"light".equals(group)||"off".equals(group)||"glass".equals(group))
		{
			if(side.getAxis()==Axis.Y)
				yaw += rotY;
			else
				roll += rotY;
			if("light".equals(group)||"off".equals(group)||"glass".equals(group))
				pitch += rotX;
		}
		mat.setRotation(ClientUtils.degreeToQuaterion(pitch, yaw, roll));
		mat.setTranslation(transl);
		return Optional.of(new TRSRTransformation(mat));
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public String getCacheKey(BlockState object)
	{
		return side+":"+facing+":"+rotX+":"+rotY+":"+active;
	}

	//computer stuff
	public boolean canComputerTurn()
	{
		return turnCooldown <= 0||!active;
	}

	public void turnX(boolean dir, boolean throwException)
	{
		if(!canComputerTurn())
		{
			if(throwException)
				throw new IllegalArgumentException("The floodlight can't turn again yet.");
			else
				return;
		}
		this.rotX = Math.min(191.25f, Math.max(-11.25f, rotX+(dir?-11.25f: 11.25f)));
		world.addBlockEvent(getPos(), getBlockState().getBlock(), 255, 0);
		turnCooldown = 20;
		shouldUpdate = true;
	}

	public void turnY(boolean dir, boolean throwException)
	{
		if(!canComputerTurn())
		{
			if(throwException)
				throw new IllegalArgumentException("The floodlight can't turn again yet.");
			else
				return;
		}
		this.rotY += dir?-11.25: 11.25;
		this.rotY %= 360;
		world.addBlockEvent(getPos(), getBlockState().getBlock(), 255, 0);
		turnCooldown = 20;
		shouldUpdate = true;
	}

	@Override
	public boolean isSource(ConnectionPoint cp)
	{
		return false;
	}

	@Override
	public boolean isSink(ConnectionPoint cp)
	{
		return true;
	}

	@Override
	public int getRequestedEnergy()
	{
		if(energyStorage < maximumStorage)
			return maximumStorage-energyStorage;
		return 0;
	}

	@Override
	public void insertEnergy(int amount)
	{
		energyStorage += amount;
	}
}