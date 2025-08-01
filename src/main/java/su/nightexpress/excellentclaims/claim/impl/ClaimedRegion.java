package su.nightexpress.excellentclaims.claim.impl;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentclaims.ClaimPlugin;
import su.nightexpress.excellentclaims.Placeholders;
import su.nightexpress.excellentclaims.api.claim.ClaimType;
import su.nightexpress.excellentclaims.api.claim.RegionClaim;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.geodata.Cuboid;
import su.nightexpress.nightcore.util.geodata.pos.BlockPos;
import su.nightexpress.nightcore.util.geodata.pos.ChunkPos;

import java.io.File;
import java.util.Set;
import java.util.function.UnaryOperator;

public class ClaimedRegion extends AbstractClaim implements RegionClaim {

    private Cuboid cuboid;

    public ClaimedRegion(@NotNull ClaimPlugin plugin, @NotNull File file) {
        super(plugin, ClaimType.REGION, file);
    }

    @Override
    @NotNull
    public UnaryOperator<String> replacePlaceholders() {
        return Placeholders.CLAIM.replacer(this);
    }

    @Override
    protected boolean loadAdditional(@NotNull FileConfig config) {
        BlockPos minPos = BlockPos.read(config, "Data.MinPos");
        BlockPos maxPos = BlockPos.read(config, "Data.MaxPos");
        this.setCuboid(minPos, maxPos);

        return true;
    }

    @Override
    protected void saveAdditional(@NotNull FileConfig config) {
        this.cuboid.getMin().write(config, "Data.MinPos");
        this.cuboid.getMax().write(config, "Data.MaxPos");
    }

    @Override
    protected void writeSettings(@NotNull FileConfig config) {
        super.writeSettings(config);
    }

    @Override
    @NotNull
    public Set<ChunkPos> getEffectiveChunkPositions() {
        return this.cuboid.getIntersectingChunkPositions();
    }

    @Override
    public boolean isIntersecting(@NotNull Cuboid cuboid) {
        return this.cuboid.isIntersectingWith(cuboid);
    }

    @Override
    public boolean isEmpty() {
        return this.cuboid.isEmpty();
    }

    @Override
    protected boolean contains(@NotNull BlockPos blockPos) {
        return !this.isEmpty() && this.cuboid.contains(blockPos);
    }

    @NotNull
    @Override
    public Cuboid getCuboid() {
        return cuboid;
    }

    @Override
    public void setCuboid(@NotNull BlockPos min, @NotNull BlockPos max) {
        this.setCuboid(new Cuboid(min, max));
    }

    @Override
    public void setCuboid(@NotNull Cuboid cuboid) {
        this.cuboid = cuboid;
    }
}
