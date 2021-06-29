package pugz.better_concrete;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ConcretePowderBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import static pugz.better_concrete.LayerConcretePowderBlock.LAYERS;

public class BetterConcrete implements ModInitializer {
	public static final String MOD_ID = "better_concrete";

	public static List<Block> CONCRETES = new ArrayList<Block>();
	public static List<Block> CONCRETE_POWDERS = new ArrayList<Block>();

	public static final EntityType<FallingConcretePowderEntity> FALLING_CONCRETE = Registry.register(Registry.ENTITY_TYPE, new Identifier(MOD_ID, "falling_concrete"), EntityType.Builder.<FallingConcretePowderEntity>create(FallingConcretePowderEntity::new, SpawnGroup.CREATURE).setDimensions(0.98F, 0.98F).maxTrackingRange(10).trackingTickInterval(20).build("falling_concrete"));

	@Override
	public void onInitialize() {
		for (DyeColor color : DyeColor.values()) {
			final Block CONCRETE = createBlock(color.name().toLowerCase() + "_concrete", new LayerConcreteBlock(color), null);
			final Block CONCRETE_POWDER = createBlock(color.name().toLowerCase() + "_concrete_powder", new LayerConcretePowderBlock(CONCRETE, color), null);

			CONCRETES.add(CONCRETE);
			CONCRETE_POWDERS.add(CONCRETE_POWDER);
		}

		EntityRendererRegistry.INSTANCE.register(FALLING_CONCRETE, FallingConcretePowderRenderer::new);
		UseBlockCallback.EVENT.register(this::onRightClickBlock);
	}

	private ActionResult onRightClickBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof ConcretePowderBlock) {
			ItemStack held = player.getStackInHand(hand);
			if (held.getItem() instanceof ShovelItem && hit.getSide() == Direction.UP) {
				for (Block block : CONCRETE_POWDERS) {
					if (Registry.BLOCK.getId(block).getPath().equals(Registry.BLOCK.getId(state.getBlock()).getPath())) {
						if (!player.isCreative() && !world.isClient) held.damage(1, player, e -> e.sendToolBreakStatus(hand));
						world.setBlockState(pos, block.getDefaultState().with(LAYERS, 7).with(LayerConcretePowderBlock.WATERLOGGED, world.getFluidState(pos).isIn(FluidTags.WATER)), 3);
						return ActionResult.success(world.isClient);
					}
				}
			}
		}

		return ActionResult.PASS;
	}

	public static <B extends Block> Block createBlock(String name, B block, ItemGroup group) {
		Registry.register(Registry.BLOCK, new Identifier(MOD_ID, name), block);
		if (group != null) Registry.register(Registry.ITEM, new Identifier(MOD_ID, name), new BlockItem(block, new Item.Settings().group(group)));
		return block;
	}
}
