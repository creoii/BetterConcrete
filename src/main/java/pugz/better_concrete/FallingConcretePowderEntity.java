package pugz.better_concrete;

import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.AutomaticItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class FallingConcretePowderEntity extends Entity {
    public int timeFalling;
    private int layers;
    public boolean shouldDropItem = true;
    protected static final TrackedData<BlockPos> BLOCK_POS = DataTracker.registerData(FallingConcretePowderEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Integer> LAYERS = DataTracker.registerData(FallingConcretePowderEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private BlockState fallState;
    //private final EntitySize size;

    public FallingConcretePowderEntity(EntityType<? extends FallingConcretePowderEntity> entity, World world) {
        super(entity, world);
        this.layers = 1;
    }

    public FallingConcretePowderEntity(World world, double x, double y, double z, int layers, BlockState state) {
        super(BetterConcrete.FALLING_CONCRETE, world);
        this.setPosition(x, y, z);
        this.setVelocity(Vec3d.ZERO);
        this.inanimate = true;
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.layers = layers;
        this.setData(getBlockPos(), layers);
        fallState = state;
        //size = new EntitySize(0.98f, 0.1225f * layers, true);
    }

    public World getWorldClient() {
        return this.world;
    }

    public void tick() {
        if (this.fallState.isAir() || !(fallState.getBlock() instanceof LayerConcretePowderBlock)) {
            this.discard();
        } else {
            Block block = this.fallState.getBlock();
            if (this.timeFalling++ == 0) {
                BlockPos blockpos = this.getBlockPos();
                if (this.world.getBlockState(blockpos).isOf(block)) {
                    this.world.removeBlock(blockpos, false);
                } else if (!this.world.isClient) {
                    return;
                }
            }

            if (!this.hasNoGravity()) {
                this.setVelocity(this.getVelocity().add(0.0D, -0.04D, 0.0D));
            }

            this.move(MovementType.SELF, this.getVelocity());
            if (!this.world.isClient) {
                BlockPos blockpos1 = this.getBlockPos();
                boolean flag = this.fallState.getBlock() instanceof ConcretePowderBlock;
                boolean flag1 = flag && this.world.getFluidState(blockpos1).isIn(FluidTags.WATER);
                double d0 = this.getVelocity().lengthSquared();
                if (flag && d0 > 1.0D) {
                    BlockHitResult hit = this.world.raycast(new RaycastContext(new Vec3d(this.prevX, this.prevY, this.prevZ), this.getPos(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.SOURCE_ONLY, this));
                    if (hit.getType() != HitResult.Type.MISS && this.world.getFluidState(hit.getBlockPos()).isIn(FluidTags.WATER)) {
                        blockpos1 = hit.getBlockPos();
                        flag1 = true;
                    }
                }

                if (!this.onGround && !flag1) {
                    if (!this.world.isClient && (this.timeFalling > 100 && (blockpos1.getY() < 1 || blockpos1.getY() > 256) || this.timeFalling > 600)) {
                        if (this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
                            this.dropItem(block);
                        }

                        this.discard();
                    }
                } else {
                    BlockState hitState = this.world.getBlockState(blockpos1);
                    this.setVelocity(this.getVelocity().multiply(0.7D, -0.5D, 0.7D));
                    if (!hitState.isOf(Blocks.MOVING_PISTON)) {
                        this.discard();
                        boolean flag2 = hitState.canReplace(new AutomaticItemPlacementContext(this.world, blockpos1, Direction.DOWN, ItemStack.EMPTY, Direction.UP));
                        boolean flag3 = FallingBlock.canFallThrough(this.world.getBlockState(blockpos1.down()));
                        boolean flag4 = this.fallState.canPlaceAt(this.world, blockpos1) && !flag3;
                        if ((flag2 || (hitState.getBlock() instanceof LayerConcretePowderBlock || hitState.getBlock() instanceof LayerConcreteBlock)) && flag4) {
                            if (this.fallState.contains(Properties.WATERLOGGED) && this.world.getFluidState(blockpos1).getFluid() == Fluids.WATER) {
                                this.fallState = this.fallState.with(Properties.WATERLOGGED, true);
                            }

                            if (hitState.getBlock() instanceof LayerConcretePowderBlock) {
                                if (((LayerConcretePowderBlock) block).color == ((LayerConcretePowderBlock) hitState.getBlock()).color) {
                                    this.shouldDropItem = false;
                                    if (hitState.get(LayerConcretePowderBlock.LAYERS) == 8)
                                        world.setBlockState(blockpos1.up(), this.fallState, 3);

                                    else {
                                        int totalLayers = hitState.get(LayerConcretePowderBlock.LAYERS) + this.fallState.get(LayerConcretePowderBlock.LAYERS);

                                        if (totalLayers <= 8)
                                            world.setBlockState(blockpos1, hitState.with(LayerConcretePowderBlock.LAYERS, totalLayers), 3);
                                        else {
                                            world.setBlockState(blockpos1, this.fallState.with(LayerConcretePowderBlock.LAYERS, 8), 3);
                                            world.setBlockState(blockpos1.up(), this.fallState.with(LayerConcretePowderBlock.LAYERS, totalLayers - 8), 3);
                                        }
                                    }
                                } else this.shouldDropItem = true;
                            } else if (hitState.getBlock() instanceof LayerConcreteBlock) {
                                if (((LayerConcretePowderBlock) block).color == ((LayerConcretePowderBlock) hitState.getBlock()).color) {
                                    this.shouldDropItem = false;
                                    if (hitState.get(LayerConcreteBlock.WATERLOGGED) && hitState.get(LayerConcreteBlock.LAYERS) < 7) {
                                        int totalLayers = hitState.get(LayerConcreteBlock.LAYERS) + this.fallState.get(LayerConcretePowderBlock.LAYERS);

                                        if (totalLayers <= 8)
                                            world.setBlockState(blockpos1, hitState.with(LayerConcreteBlock.LAYERS, totalLayers).with(LayerConcreteBlock.WATERLOGGED, totalLayers < 8), 3);
                                        else {
                                            world.setBlockState(blockpos1, hitState.with(LayerConcreteBlock.LAYERS, 8).with(LayerConcreteBlock.WATERLOGGED, false), 3);
                                            world.setBlockState(blockpos1.up(), this.fallState.with(LayerConcretePowderBlock.LAYERS, totalLayers - 8).with(LayerConcretePowderBlock.WATERLOGGED, false), 3);
                                        }
                                    }
                                } else this.shouldDropItem = true;
                            } else if (!(hitState.getBlock() instanceof LayerConcretePowderBlock)) {
                                if (this.world.setBlockState(blockpos1, this.fallState, 3)) {
                                    ((LayerConcretePowderBlock) block).onLanding(this.world, blockpos1, this.fallState, hitState, this);
                                }
                            } else if (this.shouldDropItem && this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS) && this.layers == 8) {
                                this.dropItem(block);
                            }
                        } else if (this.shouldDropItem && this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS) && this.layers == 8) {
                            this.dropItem(block);
                        }
                    }
                }
            }
            this.setVelocity(this.getVelocity().multiply(0.98D));
        }
    }

    public void setData(BlockPos pos, int layers) {
        this.dataTracker.set(BLOCK_POS, pos);
        this.dataTracker.set(LAYERS, layers);
    }

    public BlockPos getFallingBlockPos() {
        return this.dataTracker.get(BLOCK_POS);
    }

    public int getLayers() {
        return this.dataTracker.get(LAYERS);
    }

    public boolean collides() {
        return !this.isRemoved();
    }


    @Override
    public boolean doesRenderOnFire() {
        return false;
    }

    protected MoveEffect getMoveEffect() {
        return MoveEffect.NONE;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(BLOCK_POS, BlockPos.ORIGIN);
        this.dataTracker.startTracking(LAYERS, 1);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (NbtHelper.toBlockState(nbt.getCompound("BlockState")).getBlock() instanceof LayerConcretePowderBlock) this.fallState = NbtHelper.toBlockState(nbt.getCompound("BlockState"));
        else this.fallState = BetterConcrete.CONCRETE_POWDERS.get(0).getDefaultState();
        this.timeFalling = nbt.getInt("Time");
        if (nbt.contains("Layers", NbtElement.INT_TYPE)) {
            this.layers = nbt.getInt("Layers");
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.put("BlockState", NbtHelper.fromBlockState(this.fallState));
        nbt.putInt("Time", this.timeFalling);
        nbt.putInt("Layers", this.layers);
    }

    public BlockState getBlockState() {
        return fallState;
    }

    public Packet<?> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this, Block.getRawIdFromState(this.getBlockState()));
    }

    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        this.fallState = Block.getStateFromRawId(packet.getEntityData());
        this.inanimate = true;
        double d = packet.getX();
        double e = packet.getY();
        double f = packet.getZ();
        this.setPosition(d, e + (double)((1.0F - this.getHeight()) / 2.0F), f);
    }
}