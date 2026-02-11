package net.earthcomputer.grassalign.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In modern versions, model offsets are computed in {@code AbstractBlock.AbstractBlockState#getModelOffset}.
 *
 * This mixin overrides offsets for foliage blocks that had special random offsets in old versions,
 * using the b1.6-tb3 jitter seed formula (instead of the usual pre-1.8 XOR seed).
 *
 * <ul>
 *   <li>Short grass / fern / dead bush: XYZ jitter (includes vertical component)</li>
 *   <li>Small flowers: XZ jitter</li>
 *   <li>Tall plants (double-height plants): XZ jitter with an XZ-only seed (keeps both halves aligned)</li>
 * </ul>
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockMixin {
    @Shadow
    public abstract boolean isOf(Block block);

    @Unique
    private boolean is173TallGrassLike() {
        // In 1.7.3, this was all one block (Blocks.TALLGRASS) with variants:
        // deadbush, tallgrass, fern.
        return this.isOf(Blocks.GRASS) || this.isOf(Blocks.FERN) || this.isOf(Blocks.DEAD_BUSH);
    }

    @Unique
    private boolean is173SmallFlower() {
        // In 1.7.3, all small flowers were either RED_FLOWER or YELLOW_FLOWER.
        return this.isOf(Blocks.DANDELION)
                || this.isOf(Blocks.POPPY)
                || this.isOf(Blocks.BLUE_ORCHID)
                || this.isOf(Blocks.ALLIUM)
                || this.isOf(Blocks.AZURE_BLUET)
                || this.isOf(Blocks.RED_TULIP)
                || this.isOf(Blocks.ORANGE_TULIP)
                || this.isOf(Blocks.WHITE_TULIP)
                || this.isOf(Blocks.PINK_TULIP)
                || this.isOf(Blocks.OXEYE_DAISY)
                || this.isOf(Blocks.CORNFLOWER)
                || this.isOf(Blocks.LILY_OF_THE_VALLEY)
                || this.isOf(Blocks.WITHER_ROSE);
    }

    @Unique
    private boolean is173DoublePlant() {
        // 1.7.3's DOUBLE_PLANT offsets (tall flowers + tall grass + large fern).
        return this.isOf(Blocks.SUNFLOWER)
                || this.isOf(Blocks.LILAC)
                || this.isOf(Blocks.ROSE_BUSH)
                || this.isOf(Blocks.PEONY)
                || this.isOf(Blocks.TALL_GRASS)
                || this.isOf(Blocks.LARGE_FERN);
    }

    @Unique
    private static long scrambleSeed(long seed) {
        return seed * seed * 42317861L + seed * 11L;
    }

    @Unique
    private static long seedXYZ(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        // b1.6-tb3 (RenderBlocks#renderBlockReed) combines X/Z/Y using 32-bit int arithmetic
        // (including overflow), then widens to long.
        long seed = (long) (x * 3129871 + z * 6129781 + y);
        return scrambleSeed(seed);
    }

    @Unique
    private static long seedXZ(BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        // b1.6-tb3: X/Z-only variant (keeps both halves aligned), using the same int-overflow
        // behavior as the XYZ seed.
        long seed = (long) (x * 3129871 + z * 6129781);
        return scrambleSeed(seed);
    }

    @Unique
    private static double jitter(long seed, int shift, double subtract, double multiply) {
        // Keep the float division (then widen to double) to match 1.7.3 exactly.
        return (((double) ((float) ((seed >> shift) & 15L) / 15.0F)) - subtract) * multiply;
    }

    @Unique
    private static Vec3d offset173TallGrassLike(BlockPos pos) {
        // 1.7.3 tesselateCross: Blocks.TALLGRASS
        long seed = seedXYZ(pos);
        return new Vec3d(
                jitter(seed, 16, 0.5D, 0.5D),
                jitter(seed, 20, 1.0D, 0.2D),
                jitter(seed, 24, 0.5D, 0.5D)
        );
    }

    @Unique
    private static Vec3d offset173SmallFlower(BlockPos pos) {
        // 1.7.3 tesselateCross: Blocks.RED_FLOWER / Blocks.YELLOW_FLOWER
        long seed = seedXYZ(pos);
        return new Vec3d(
                jitter(seed, 16, 0.5D, 0.3D),
                0.0D,
                jitter(seed, 24, 0.5D, 0.3D)
        );
    }

    @Unique
    private static Vec3d offset173DoublePlant(BlockPos pos) {
        // 1.7.3 tesselateDoublePlant: seed is XZ-only so the upper and lower halves match.
        long seed = seedXZ(pos);
        return new Vec3d(
                jitter(seed, 16, 0.5D, 0.3D),
                0.0D,
                jitter(seed, 24, 0.5D, 0.3D)
        );
    }

    // Use the full descriptor so the mixin remapper/refmap has an unambiguous target.
    @Inject(
            method = "getModelOffset(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/Vec3d;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void grassaligntb3$getModelOffset(BlockView world, BlockPos pos, CallbackInfoReturnable<Vec3d> cir) {
        if (this.is173TallGrassLike()) {
            cir.setReturnValue(offset173TallGrassLike(pos));
        } else if (this.is173SmallFlower()) {
            cir.setReturnValue(offset173SmallFlower(pos));
        } else if (this.is173DoublePlant()) {
            cir.setReturnValue(offset173DoublePlant(pos));
        }
    }
}
