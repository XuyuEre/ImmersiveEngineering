package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.TargetingInfo;
import blusunrize.immersiveengineering.api.energy.wires.*;
import blusunrize.immersiveengineering.api.energy.wires.old.ImmersiveNetHandler;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.*;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.immersiveengineering.common.util.Utils;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

import static blusunrize.immersiveengineering.api.energy.wires.WireApi.INFOS;

public class TileEntityFeedthrough extends TileEntityImmersiveConnectable implements ITileDrop, IDirectionalTile,
		IHasDummyBlocks, IPropertyPassthrough, IBlockBounds, ICacheData
{
	public static TileEntityType<TileEntityFeedthrough> TYPE;

	public static final String WIRE = "wire";
	private static final String HAS_NEGATIVE = "hasNeg";
	private static final String FACING = "facing";
	private static final String POSITIVE = "positive";
	private static final String OFFSET = "offset";
	public static final String MIDDLE_STATE = "middle";

	@Nonnull
	public WireType reference = WireType.COPPER;
	@Nonnull
	public BlockState stateForMiddle = Blocks.DIRT.getDefaultState();
	@Nonnull
	Direction facing = Direction.NORTH;
	public int offset = 0;
	@Nullable
	public ConnectionPoint connPositive = null;
	public boolean hasNegative = false;
	private boolean formed = true;

	public TileEntityFeedthrough()
	{
		super(TYPE);
	}

	@Override
	public void writeCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		super.writeCustomNBT(nbt, descPacket);
		nbt.putString(WIRE, reference.getUniqueName());
		if(connPositive!=null)
			nbt.put(POSITIVE, connPositive.createTag());
		nbt.putBoolean(HAS_NEGATIVE, hasNegative);
		nbt.putInt(FACING, facing.getIndex());
		nbt.putInt(OFFSET, offset);
		CompoundNBT stateNbt = new CompoundNBT();
		Utils.stateToNBT(stateNbt, stateForMiddle);
		nbt.put(MIDDLE_STATE, stateNbt);
	}

	@Override
	public void readCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		super.readCustomNBT(nbt, descPacket);
		reference = WireType.getValue(nbt.getString(WIRE));
		if(nbt.contains(POSITIVE, NBT.TAG_COMPOUND))
			connPositive = new ConnectionPoint(nbt.getCompound(POSITIVE));
		hasNegative = nbt.getBoolean(HAS_NEGATIVE);
		facing = Direction.VALUES[nbt.getInt(FACING)];
		offset = nbt.getInt(OFFSET);
		stateForMiddle = Utils.stateFromNBT(nbt.getCompound(MIDDLE_STATE));
	}

	@Override
	public Vec3d getConnectionOffset(@Nonnull Connection con, ConnectionPoint here)
	{
		return getOffset(con.isEnd(connPositive));
	}

	private boolean isPositive(Vec3i offset)
	{
		return offset.getX()*facing.getXOffset()+
				offset.getY()*facing.getYOffset()+
				offset.getZ()*facing.getZOffset() > 0;
	}

	@Override
	public Vec3d getConnectionOffset(ImmersiveNetHandler.Connection con, TargetingInfo target, Vec3i offsetLink)
	{
		return getOffset(isPositive(offsetLink));
	}

	private Vec3d getOffset(boolean positive)
	{
		double l = INFOS.get(reference).connOffset;
		int factor = positive?1: -1;
		return new Vec3d(.5+(.5+l)*facing.getXOffset()*factor, .5+(.5+l)*facing.getYOffset()*factor,
				.5+(.5+l)*facing.getZOffset()*factor);
	}

	@Override
	public boolean canConnectCable(WireType cableType, ConnectionPoint target, Vec3i offset)
	{
		if(!WireApi.canMix(reference, cableType))
			return false;
		boolean positive = isPositive(offset);
		if(positive)
			return connPositive==null;
		else
			return !hasNegative;
	}

	@Override
	public void connectCable(WireType cableType, ConnectionPoint target, IImmersiveConnectable other, ConnectionPoint otherTarget)
	{
		if(target.getIndex() > 0)
			connPositive = otherTarget;
		else
			hasNegative = true;
	}

	@Override
	public void removeCable(Connection connection)
	{
		if(connection==null)
		{
			connPositive = null;
			hasNegative = false;
		}
		else
		{
			if(connection.isEnd(connPositive))
				connPositive = null;
			else
				hasNegative = false;
		}
	}

	@Override
	public Set<BlockPos> getIgnored(IImmersiveConnectable other)
	{
		return ImmutableSet.of(pos.offset(facing, 1), pos.offset(facing, -1));
	}

	@Override
	public BlockPos getConnectionMaster(WireType cableType, TargetingInfo target)
	{
		return pos.offset(facing, -offset);
	}

	@Override
	public NonNullList<ItemStack> getTileDrops(@Nullable PlayerEntity player, BlockState state)
	{
		WireApi.FeedthroughModelInfo info = INFOS.get(reference);
		if(info.canReplace())
		{
			if(offset==0)
				return Utils.getDrops(stateForMiddle);
			else
			{
				assert info.conn!=null;//If it's marked as replaceable it should have a state to replace with
				return NonNullList.from(ItemStack.EMPTY,
						new ItemStack(info.conn.getBlock(), 1));
			}
		}
		else
		{
			ItemStack stack = new ItemStack(state.getBlock(), 1);
			stack.setTagInfo(WIRE, new StringNBT(reference.getUniqueName()));
			CompoundNBT stateNbt = new CompoundNBT();
			Utils.stateToNBT(stateNbt, stateForMiddle);
			stack.setTagInfo(MIDDLE_STATE, stateNbt);
			return NonNullList.from(ItemStack.EMPTY, stack);
		}
	}

	@Override
	public ItemStack getPickBlock(@Nullable PlayerEntity player, BlockState state, RayTraceResult rayRes)
	{
		WireApi.FeedthroughModelInfo info = INFOS.get(reference);
		if(info.canReplace()&&offset==0)
		{
			//getPickBlock needs a proper World, not an IBlockAccess, which is hard to emulate quickly.
			// "world, pos" won't have anything remotely like the state this expects, I hope it won't notice.
			try
			{
				return stateForMiddle.getBlock().getPickBlock(stateForMiddle, rayRes, world, pos, player);
			} catch(Exception x)// We can't predict what is going to happen with weird inputs. The block is mostly inert, so it shouldn't be too bad.
			{
			}                   // No output as WAILA etc call this every tick (every frame?)
		}
		return getTileDrop(player, state);
	}

	@Override
	public void readOnPlacement(@Nullable LivingEntity placer, ItemStack stack)
	{
		reference = WireType.getValue(ItemNBTHelper.getString(stack, WIRE));
		stateForMiddle = Utils.stateFromNBT(ItemNBTHelper.getTagCompound(stack, MIDDLE_STATE));
	}

	@Override
	public Direction getFacing()
	{
		return facing;
	}

	@Override
	public void setFacing(Direction facing)
	{
		this.facing = facing;
	}

	@Override
	public int getFacingLimitation()
	{
		return 1;
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

	//Called after setFacing
	@Override
	public void placeDummies(BlockPos pos, BlockState state, Direction side, float hitX, float hitY, float hitZ)
	{
		for(int i = -1; i <= 1; i += 2)
		{
			BlockPos tmp = pos.offset(facing, i);
			world.setBlockState(tmp, state);
			TileEntity te = world.getTileEntity(tmp);
			if(te instanceof TileEntityFeedthrough)
			{
				((TileEntityFeedthrough)te).facing = facing;
				((TileEntityFeedthrough)te).offset = i;
				((TileEntityFeedthrough)te).reference = reference;
				((TileEntityFeedthrough)te).stateForMiddle = stateForMiddle;
				world.checkLight(tmp);
			}
		}
	}

	@Override
	public void breakDummies(BlockPos pos, BlockState state)
	{
		if(!formed)
			return;
		TileEntityFeedthrough master;
		BlockPos masterPos = pos.offset(facing, -offset);
		{
			TileEntity tmp = world.getTileEntity(masterPos);
			if(tmp instanceof TileEntityFeedthrough)
				master = (TileEntityFeedthrough)tmp;
			else
				master = null;
		}
		disassembleBlock(-1);
		disassembleBlock(1);
		Set<ImmersiveNetHandler.Connection> conns = ImmersiveNetHandler.INSTANCE.getConnections(world, masterPos);
		if(conns!=null)
		{
			if(master!=null)
				for(ImmersiveNetHandler.Connection c : conns)
				{
					BlockPos newPos = null;
					if(c.end.equals(master.connPositive))
					{
						if(offset!=1)
							newPos = masterPos.offset(facing);
					}
					else if(offset!=-1)
						newPos = masterPos.offset(facing, -1);
					if(newPos!=null)
					{
						/*TODO
						ImmersiveNetHandler.Connection reverse = ImmersiveNetHandler.INSTANCE.getReverseConnection(world.provider.getDimension(), c);
						ApiUtils.moveConnectionEnd(reverse, newPos, world);
						IImmersiveConnectable connector = ApiUtils.toIIC(newPos, world);
						IImmersiveConnectable otherEnd = ApiUtils.toIIC(reverse.start, world);
						if(connector!=null)
						{
							try
							{
								//TODO clean this up in 1.13
								connector.connectCable(reverse.cableType, null, otherEnd);
							} catch(Exception x)
							{
								IELogger.logger.info("Failed to fully move connection", x);
							}
						}*/
					}
				}
		}
		disassembleBlock(0);
	}

	private void disassembleBlock(int toBreak)
	{
		WireApi.FeedthroughModelInfo info = INFOS.get(reference);
		int offsetLocal = toBreak-offset;
		BlockPos replacePos = pos.offset(facing, offsetLocal);
		if(!info.canReplace())
			world.removeBlock(replacePos);
		else if(toBreak!=offset)
		{
			TileEntity te = world.getTileEntity(replacePos);
			if(te instanceof TileEntityFeedthrough)
				((TileEntityFeedthrough)te).formed = false;
			BlockState newState = Blocks.AIR.getDefaultState();
			switch(toBreak)
			{
				case -1:
					newState = info.conn.with(IEProperties.FACING_ALL, facing);
					break;
				case 0:
					newState = stateForMiddle;
					break;
				case 1:
					newState = info.conn.with(IEProperties.FACING_ALL, facing.getOpposite());
					break;
			}
			world.setBlockState(replacePos, newState);//TODO move wires properly

		}
	}

	@Override
	public boolean isDummy()
	{
		return false;//Every block has a model
	}

	private static float[] FULL_BLOCK = {0, 0, 0, 1, 1, 1};
	private float[] aabb;

	@Override
	public float[] getBlockBounds()
	{
		if(offset==0)
			return FULL_BLOCK;
		if(aabb==null)
		{
			float[] tmp = {
					5F/16, 0, 5F/16,
					11F/16, (float)INFOS.get(reference).connLength, 11F/16
			};
			aabb = Utils.rotateToFacing(tmp, offset > 0?facing: facing.getOpposite());
		}
		return aabb;
	}

	@Override
	public Object[] getCacheData()
	{
		return new Object[]{
				stateForMiddle, reference, facing
		};
	}

	@Override
	protected float getBaseDamage(Connection c)
	{
		return INFOS.get(reference).dmgPerEnergy;
	}

	@Override
	protected float getMaxDamage(Connection c)
	{
		return INFOS.get(reference).maxDmg;
	}

	@Override
	public boolean receiveClientEvent(int id, int arg)
	{
		if(id==253)
		{
			world.checkLight(pos);
			return true;
		}
		return super.receiveClientEvent(id, arg);
	}
}
