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
 * In 1.16.5, model offsets are computed in {@code AbstractBlock.AbstractBlockState#getModelOffset}.
 *
 * This mixin overrides offsets for the foliage blocks that had special random offsets in 1.7.3,
 * to match the 1.7.3 formulas exactly:
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
    private static final long NIBBLE_MASK = 0xFL;

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

    /**
     * Equivalent to the internal scramble in {@code net.minecraft.util.math.MathHelper#getSeed} (1.7.x)
     * and {@code net.minecraft.util.Mth#getSeed} (1.16.x), but without the final right-shift.
     */
    @Unique
    private static long scrambleSeed(long seed) {
        return seed * seed * 42317861L + seed * 11L;
    }

    @Unique
    private static long seedXYZ(BlockPos pos) {
        // Keep the exact overflow/precedence semantics: the X term is an int multiply, then widened.
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
    }

    @Unique
    private static long seedXZ(BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        return (long) (x * 3129871) ^ (long) z * 116129781L;
    }

    @Unique
    private static double nibbleFrac(long seed, int shift) {
        // Important: keep the float division (1.7.3 did this in float space).
        return (double) ((float) ((seed >> shift) & NIBBLE_MASK) / 15.0F);
    }

    @Unique
    private static Vec3d offsetXZFromSeed(long seed, double scale) {
        double dx = (nibbleFrac(seed, 16) - 0.5D) * scale;
        double dz = (nibbleFrac(seed, 24) - 0.5D) * scale;
        return new Vec3d(dx, 0.0D, dz);
    }

    @Unique
    private static Vec3d offset173TallGrassLike(BlockPos pos) {
        // 1.7.3 tesselateCross: Blocks.TALLGRASS
        long seed = scrambleSeed(seedXYZ(pos));

        double dx = (nibbleFrac(seed, 16) - 0.5D) * 0.5D;
        double dy = (nibbleFrac(seed, 20) - 1.0D) * 0.2D;
        double dz = (nibbleFrac(seed, 24) - 0.5D) * 0.5D;
        return new Vec3d(dx, dy, dz);
    }

    @Unique
    private static Vec3d offset173SmallFlower(BlockPos pos) {
        // 1.7.3 tesselateCross: Blocks.RED_FLOWER / Blocks.YELLOW_FLOWER
        return offsetXZFromSeed(scrambleSeed(seedXYZ(pos)), 0.3D);
    }

    @Unique
    private static Vec3d offset173DoublePlant(BlockPos pos) {
        // 1.7.3 tesselateDoublePlant: seed is XZ-only so the upper and lower halves match.
        return offsetXZFromSeed(scrambleSeed(seedXZ(pos)), 0.3D);
    }

    // Use the full descriptor so the mixin remapper/refmap has an unambiguous target.
    @Inject(
            method = "getModelOffset(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/Vec3d;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void grassalign$getModelOffset(BlockView world, BlockPos pos, CallbackInfoReturnable<Vec3d> cir) {
        if (this.is173TallGrassLike()) {
            cir.setReturnValue(offset173TallGrassLike(pos));
            return;
        }

        if (this.is173SmallFlower()) {
            cir.setReturnValue(offset173SmallFlower(pos));
            return;
        }

        if (this.is173DoublePlant()) {
            cir.setReturnValue(offset173DoublePlant(pos));
        }
    }
}
