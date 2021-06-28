package pugz.better_concrete;

import net.minecraft.block.*;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.Random;
import java.util.stream.Stream;

public class LayerConcretePowderBlock extends FallingBlock implements Waterloggable {
    public static final IntProperty LAYERS = Properties.LAYERS;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    private static final VoxelShape[] LAYERS_TO_SHAPE = new VoxelShape[]{VoxelShapes.empty(), Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D), Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D), Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D), Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D), Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D), Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D), Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D), Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)};
    public final BlockState solidState;
    public final DyeColor color;

    public LayerConcretePowderBlock(Block solid, DyeColor color) {
        super(Settings.of(Material.AGGREGATE, color).strength(0.5F).sounds(BlockSoundGroup.SAND));
        this.setDefaultState(this.stateManager.getDefaultState().with(LAYERS, 8).with(WATERLOGGED, false));
        this.solidState = solid.getDefaultState();
        this.color = color;
    }

    @SuppressWarnings("deprecation")
    public PistonBehavior getPistonBehavior(BlockState state) {
        return state.get(LAYERS) == 1 ? PistonBehavior.DESTROY : PistonBehavior.NORMAL;
    }

    @SuppressWarnings("deprecation")
    public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type) {
        return switch (type) {
            case LAND -> state.get(LAYERS) < 5;
            case WATER, AIR -> false;
        };
    }

    @SuppressWarnings("deprecation")
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return LAYERS_TO_SHAPE[state.get(LAYERS)];
    }

    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return LAYERS_TO_SHAPE[state.get(LAYERS) - 1];
    }

    @SuppressWarnings("deprecation")
    public VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
        return LAYERS_TO_SHAPE[state.get(LAYERS)];
    }

    @SuppressWarnings("deprecation")
    public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return LAYERS_TO_SHAPE[state.get(LAYERS)];
    }

    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) && state.get(LAYERS) < 7 ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public boolean canFillWithFluid(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        return Waterloggable.super.canFillWithFluid(world, pos, state, fluid) && state.get(LAYERS) < 8;
    }

    @SuppressWarnings("deprecation")
    public boolean hasSidedTransparency(BlockState state) {
        return true;
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        return new ItemStack(Registry.BLOCK.get(new Identifier(this.color.getName() + "_concrete_powder")));
    }

    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LAYERS, WATERLOGGED);
    }

    @SuppressWarnings("deprecation")
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            ItemStack held = player.getStackInHand(hand);

            if (held.getItem() instanceof ShovelItem && hit.getSide() == Direction.UP) {
                held.damage(1, player, e -> e.sendToolBreakStatus(hand));

                if (state.get(LAYERS) > 1) world.setBlockState(pos, state.with(LAYERS, state.get(LAYERS) - 1), 3);
                else world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);

                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.FAIL;
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!world.isClient) {
            if (world.isAir(pos.down()) || canFallThrough(world.getBlockState(pos.down())) && pos.getY() >= 0) {
                FallingConcretePowderEntity entity = new FallingConcretePowderEntity(world, (double) pos.getX() + 0.5D, pos.getY(), (double) pos.getZ() + 0.5D, state.get(LAYERS), state);
                world.addEntities(Stream.of(entity));
            }
        }
    }

    public static boolean canFallThrough(BlockState state) {
        if (state.getBlock() instanceof LayerConcretePowderBlock || (state.getBlock() instanceof LayerConcreteBlock)) return false;

        Material material = state.getMaterial();
        return state.isAir() || state.isIn(BlockTags.FIRE) || material.isLiquid() || material.isReplaceable();
    }

    public void onLanding(World worldIn, BlockPos pos, BlockState fallingState, BlockState currentState, FallingConcretePowderEntity entity) {
        if (shouldSolidify(worldIn, pos, fallingState)) worldIn.setBlockState(pos, solidState.with(LAYERS, fallingState.get(LAYERS)).with(WATERLOGGED, fallingState.get(LAYERS) < 7), 3);
    }

    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockView world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        BlockState state = world.getBlockState(pos);
        return shouldSolidify(world, pos, state) ? this.solidState.with(LayerConcreteBlock.LAYERS, state.get(LAYERS)).with(LayerConcreteBlock.WATERLOGGED, state.get(WATERLOGGED)) : super.getPlacementState(ctx);
    }

    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return isTouchingLiquid(world, pos) ? this.solidState.with(LayerConcreteBlock.LAYERS, state.get(LAYERS)).with(LayerConcreteBlock.WATERLOGGED, state.get(WATERLOGGED)) : super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    private static boolean shouldSolidify(BlockView reader, BlockPos pos, BlockState state) {
        return causesSolidify(state) || isTouchingLiquid(reader, pos);
    }

    private static boolean isTouchingLiquid(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (state.getBlock() instanceof LayerConcretePowderBlock) {
            if (state.get(WATERLOGGED)) return true;
        }

        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN) {
                BlockState check = world.getBlockState(pos.offset(d));

                if (check.getBlock() instanceof LayerConcretePowderBlock || check.getBlock() instanceof LayerConcreteBlock) {
                    if (check.get(WATERLOGGED) && check.get(LAYERS) < state.get(LAYERS)) return true;
                }

                else if (causesSolidify(check) && !check.isSideSolidFullSquare(world, pos, d.getOpposite())) return true;
            }
        }

        return false;
    }

    private static boolean causesSolidify(BlockState state) {
        return state.getFluidState().isIn(FluidTags.WATER);
    }

    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(16) == 0) {
            BlockPos blockPos = pos.down();
            if (canFallThrough(world.getBlockState(blockPos))) {
                double d = (double)pos.getX() + random.nextDouble();
                double e = (double)pos.getY() - 0.05D;
                double f = (double)pos.getZ() + random.nextDouble();
                world.addParticle(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, state), d, e, f, 0.0D, 0.0D, 0.0D);
            }
        }
    }
}