package su.nightexpress.excellentclaims.claim.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentclaims.ClaimPlugin;
import su.nightexpress.excellentclaims.Placeholders;
import su.nightexpress.excellentclaims.api.claim.Claim;
import su.nightexpress.excellentclaims.claim.ClaimManager;
import su.nightexpress.excellentclaims.config.Lang;
import su.nightexpress.excellentclaims.flag.impl.ClaimFlag;
import su.nightexpress.excellentclaims.flag.registry.EntityFlags;
import su.nightexpress.excellentclaims.flag.registry.NaturalFlags;
import su.nightexpress.excellentclaims.flag.registry.PlayerFlags;
import su.nightexpress.excellentclaims.util.Relation;
import su.nightexpress.excellentclaims.util.RelationType;
import su.nightexpress.nightcore.manager.AbstractListener;
import su.nightexpress.nightcore.util.CommandUtil;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.LangUtil;

import java.util.List;

public class FlagListener extends AbstractListener<ClaimPlugin> {

    private final ClaimManager manager;

    public FlagListener(@NotNull ClaimPlugin plugin, @NotNull ClaimManager manager) {
        super(plugin);
        this.manager = manager;
    }

    private boolean isAdminMode(@NotNull Player player) {
        return this.plugin.getMemberManager().isAdminMode(player);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!this.manager.canBreak(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_BREAK.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(block.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!this.manager.canBuild(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_PLACE.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(block.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFerilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (player != null && this.isAdminMode(player)) return;

        //plugin.debug("BlockFertilize = " + event.getBlock().getType().name());

        Block block = event.getBlock();
        Location originLocation = block.getLocation();
        Claim origin = this.manager.getPrioritizedClaim(originLocation);
        ClaimFlag<Boolean> flag = PlayerFlags.BLOCK_FERTILIZE;

        if (origin != null && origin.hasFlag(flag) && !origin.getFlag(flag) && (player != null && !origin.isOwnerOrMember(player))) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_FERTILIZE.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(block.getType())));
            return;
        }

        event.getBlocks().removeIf(currentBlock -> {
            Relation relation = this.manager.getRelation(origin, currentBlock.getLocation());
            return relation.getType(player) == RelationType.INVADE || (player != null && !relation.isTargetMember(player) && !relation.checkTargetFlag(flag));
        });

        if (event.getBlocks().isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        Block block = event.getHarvestedBlock();
        if (!this.manager.canHarvest(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_HARVEST.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(block.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTreeGrow(StructureGrowEvent event) {
        Player player = event.getPlayer();
        if (player != null && this.isAdminMode(player)) return;

        Location sourceLocation = event.getLocation();
        TreeType type = event.getSpecies();
        boolean isMushroom = type == TreeType.RED_MUSHROOM || type == TreeType.BROWN_MUSHROOM;
        boolean isBoneMeal = event.isFromBonemeal();
        ClaimFlag<Boolean> flag = isMushroom ? NaturalFlags.MUSHROOM_GROW : NaturalFlags.TREE_GROW;

        //plugin.debug("StructureGrow: " + player + " / isBoneMeal: " + isBoneMeal + " / " + flag.getId());

        Claim origin = this.manager.getPrioritizedClaim(sourceLocation);
        if (origin != null && origin.hasFlag(flag) && !origin.getFlag(flag)) {
            event.setCancelled(true);
            return;
        }

        event.getBlocks().removeIf(state -> {
            Relation relation = this.manager.getRelation(origin, state.getLocation());
            return relation.getType(player) == RelationType.INVADE || !relation.checkTargetFlag(flag);
        });

        if (event.getBlocks().isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        BlockState newState = event.getNewState();
        BlockData blockData = newState.getBlockData();
        Material fromType = block.getType();
        Material toType = newState.getType();
        ClaimFlag<Boolean> flag;

        //plugin.debug("BlockGrow = " + fromType.name() + " -> " + toType.name());

        Location source = block.getLocation();
        Location target = newState.getLocation();

        if (fromType == Material.TURTLE_EGG) {
            flag = NaturalFlags.TURTLE_EGG_HATCH;
        }
        else if (fromType == Material.AIR && toType == Material.CACTUS) {
            flag = NaturalFlags.CACTUS_GROW;
            source = block.getRelative(BlockFace.DOWN).getLocation(); // Because it grows at new 'air' position.
        }
        else if (fromType == Material.AIR && toType == Material.SUGAR_CANE) {
            flag = NaturalFlags.SUGAR_CANE_GROW;
            source = block.getRelative(BlockFace.DOWN).getLocation(); // Because it grows at new 'air' position.
        }
        else if (fromType == Material.SMALL_AMETHYST_BUD || fromType == Material.MEDIUM_AMETHYST_BUD || fromType == Material.LARGE_AMETHYST_BUD || fromType == Material.AMETHYST_CLUSTER) {
            flag = NaturalFlags.AMETHYST_GROW;
        }
        else if (blockData instanceof Ageable) {
            flag = NaturalFlags.CROP_GROW;
        }
        else return;


        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        BlockState newState = event.getNewState();
        Material oldMaterial = block.getType();
        Material material = newState.getType();
        ClaimFlag<Boolean> flag;

        if (material == Material.ICE) {
            flag = NaturalFlags.ICE_FORM;
        }
        else if (material == Material.SNOW) {
            flag = NaturalFlags.SNOW_FORM;
        }
        else if (material == Material.CAVE_VINES || material == Material.VINE) {
            flag = NaturalFlags.VINE_GROW;
        }
        else return;

        Location source = block.getLocation();
        Location target = newState.getLocation();
        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Block sourceBlock = event.getSource();
        Block targetBlock = event.getBlock();
        BlockState targetState = event.getNewState();
        Material sourceType = sourceBlock.getType();
        Material targetType = targetState.getType();

        ClaimFlag<Boolean> flag;

        //plugin.debug("BlockSpread = " + sourceType.name() + " -> " + targetType.name());

        if (targetType == Material.GRASS_BLOCK) {
            flag = NaturalFlags.GRASS_GROW;
        }
        else if (targetType == Material.BAMBOO) {
            flag = NaturalFlags.BAMBOO_GROW;
        }
        else if (targetType == Material.MYCELIUM) {
            flag = NaturalFlags.MYCELIUM_SPREAD;
        }
        else if (targetType == Material.FIRE) {
            flag = NaturalFlags.FIRE_SPREAD;
        }
        else if (targetType == Material.CAVE_VINES || targetType == Material.VINE || targetType == Material.KELP) {
            flag = NaturalFlags.VINE_GROW;
        }
        else if (targetType == Material.SMALL_AMETHYST_BUD || targetType == Material.MEDIUM_AMETHYST_BUD || targetType == Material.LARGE_AMETHYST_BUD) {
            flag = NaturalFlags.AMETHYST_FORM;
        }
        else {

            return;
        }

        Location source = sourceBlock.getLocation();
        Location target = targetBlock.getLocation();

        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        Block block = event.getBlock();
        BlockState newState = event.getNewState();
        Material fromMaterial = block.getType();
        Material toMaterial = newState.getType();
        ClaimFlag<Boolean> flag;

        //plugin.debug("BlockFade = " + fromMaterial.name() + " -> " + toMaterial.name());

        if (fromMaterial == Material.ICE && toMaterial == Material.WATER) {
            flag = NaturalFlags.ICE_MELT;
        }
        else if (fromMaterial == Material.SNOW && toMaterial == Material.AIR) {
            flag = NaturalFlags.SNOW_MELT;
        }
        else if (fromMaterial == Material.FIRE && toMaterial == Material.AIR) {
            flag = NaturalFlags.FIRE_BURN_OUT;
        }
        else if (fromMaterial == Material.FARMLAND && toMaterial == Material.DIRT) {
            flag = NaturalFlags.FARMLAND_DRY;
        }
        else if (Tag.CORALS.isTagged(fromMaterial) || Tag.CORAL_BLOCKS.isTagged(fromMaterial)) {
            flag = NaturalFlags.CORAL_DIE;
        }
        else return;

        Location source = block.getLocation();
        Location target = newState.getLocation();
        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        Block block = event.getBlock();
        Claim claim = this.manager.getPrioritizedClaim(block);
        if (claim == null || !claim.hasFlag(NaturalFlags.LEAF_DECAY)) return;

        if (!claim.getFlag(NaturalFlags.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        Block from = event.getIgnitingBlock();

        Location source = from == null ? null : from.getLocation();
        Location target = block.getLocation();
        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(NaturalFlags.FIRE_DAMAGE_BLOCKS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> blockList = event.blockList();

        //plugin.debug("BlockExplode = " + event.getExplodedBlockState().getType().name());

        ClaimFlag<Boolean> flag = NaturalFlags.EXPLOSION_BLOCK_DAMAGE;

        blockList.removeIf(block -> {
            Claim claim = this.manager.getPrioritizedClaim(block);
            return claim != null && claim.hasFlag(flag) && !claim.getFlag(flag);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        Block block = event.getBlock();
        Location source = block.getLocation();

        if (!this.checkPistonBlocks(event.getBlocks(), source, event.getDirection(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        Block block = event.getBlock();
        Location source = block.getLocation();

        if (!this.checkPistonBlocks(event.getBlocks(), source, event.getDirection(), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        // TODO Config option high frequency flags
        Block sourceBlock = event.getBlock();
        Block targetBlock = event.getToBlock();
        Material fromType = sourceBlock.getType();

        ClaimFlag<Boolean> flag;
        if (fromType == Material.WATER) {
            flag = NaturalFlags.WATER_FLOW;
        }
        else if (fromType == Material.LAVA) {
            flag = NaturalFlags.LAVA_FLOW;
        }
        else return;

        Relation relation = this.manager.getRelation(sourceBlock.getLocation(), targetBlock.getLocation());
        if (relation.getType() == RelationType.INVADE && !relation.checkTargetFlag(flag)) {
            event.setCancelled(true);
        }
    }

    private boolean checkPistonBlocks(@NotNull List<Block> blocks, @NotNull Location source, @NotNull BlockFace direction, boolean isExtend) {
        if (blocks.isEmpty()) {
            Location target = (isExtend ? source.getBlock().getRelative(direction) : source.getBlock()).getLocation();
            Relation relation = this.manager.getRelation(source, target);
            return relation.getType() != RelationType.INVADE && relation.checkBothFlag(NaturalFlags.PISTON_USE);
        }

        return blocks.stream().allMatch(block -> {
            Location target = (isExtend ? block.getRelative(direction) : block).getLocation();
            Relation relation = this.manager.getRelation(source, target);
            return relation.getType() != RelationType.INVADE && relation.checkBothFlag(NaturalFlags.PISTON_USE);
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerItemBlockUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack itemStack = event.getItem();
        Action action = event.getAction();

        if (itemStack != null && !itemStack.getType().isAir() && event.useItemInHand() != Event.Result.DENY) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (!this.manager.canUseItem(player, player.getLocation(), itemStack)) {
                    event.setCancelled(true);
                    event.setUseItemInHand(Event.Result.DENY);
                    Lang.PROTECTION_ITEM_USE.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, ItemUtil.getNameSerialized(itemStack)));
                }
            }
        }

        if (block != null && !block.isEmpty() && event.useInteractedBlock() != Event.Result.DENY) {
            //plugin.debug("PlayerInteractBlock = " + player.getName() + " -> " + block + " (" + event.getAction().name() + ")");

            if (!this.manager.canUseBlock(player, block, action)) {
                event.setUseInteractedBlock(Event.Result.DENY);
                Lang.PROTECTION_BLOCK_INTERACT.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(block.getType())));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof Projectile projectile)) return;

        if (!this.manager.canThrowProjectile(player, projectile)) {
            event.setCancelled(true);
            Lang.PROTECTION_PROJECTILE_SHOOT.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(projectile.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerLaunchProjectile(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) return;

        if (!this.manager.canThrowProjectile(player, projectile)) {
            event.setCancelled(true);
            Lang.PROTECTION_PROJECTILE_THROW.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(projectile.getType())));
        }
    }

    // TODO Enderpearl land check flag to cancel teleport, Egg disable hatch when thrown outside

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!this.manager.canBreak(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_BREAK.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(block.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!this.manager.canBuild(player, event.getBlock())) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_PLACE.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(event.getBucket())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getEntity();
        if (!this.manager.canBreak(player, entity.getLocation())) {
            event.setCancelled(true);
            Lang.PROTECTION_ENTITY_INTERACT.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(entity.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerEntityInteract(PlayerInteractEntityEvent event) {
        this.handleEntityInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerArmorStandUse(PlayerArmorStandManipulateEvent event) {
        this.handleEntityInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleEntityInteract(@NotNull Player player, @NotNull Entity entity, @NotNull Cancellable event) {
        if (!this.manager.canUseEntity(player, entity.getLocation(), entity)) {
            event.setCancelled(true);
            Lang.PROTECTION_ENTITY_INTERACT.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(entity.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerFishing(PlayerFishEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getCaught();

        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && entity != null) {
            if (!this.manager.canUseEntity(player, entity.getLocation(), entity)) {
                event.setCancelled(true);
                event.getHook().remove();
                Lang.PROTECTION_ENTITY_INTERACT.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(entity.getType())));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerPortalUse(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (this.isAdminMode(player)) return;

        Claim claim = this.manager.getPrioritizedClaim(player.getLocation());
        if (claim == null || claim.isOwnerOrMember(player)) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        ClaimFlag<Boolean> flag = switch (cause) {
            case NETHER_PORTAL -> PlayerFlags.USE_NETHER_PORTAL;
            case END_PORTAL -> PlayerFlags.USE_END_PORTAL;
            default -> null;
        };
        if (flag == null || !claim.hasFlag(flag)) return;

        if (!claim.getFlag(flag)) {
            event.setCancelled(true);
            Lang.PROTECTION_PORTAL_USE.getMessage().send(player);
        }
    }


    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;

        if (!this.manager.canMobSpawn(entity, entity.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player != null && !this.manager.canBuild(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_PLACE.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(event.getEntity().getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        EntityType entityType = entity.getType();
        List<Block> blockList = event.blockList();

        //plugin.debug("EntityExplode = " + entity);

        ClaimFlag<Boolean> flag;

        if (entityType == EntityType.TNT || entityType == EntityType.TNT_MINECART) {
            flag = NaturalFlags.TNT_BLOCK_DAMAGE;
        }
        else if (entityType == EntityType.CREEPER) {
            flag = EntityFlags.CREEPER_BLOCK_DAMAGE;
        }
        else if (entityType == EntityType.WITHER_SKULL || entityType == EntityType.WITHER) {
            flag = EntityFlags.WITHER_BLOCK_DAMAGE;
        }
        else if (entityType == EntityType.FIREBALL || entityType == EntityType.SMALL_FIREBALL || entityType == EntityType.DRAGON_FIREBALL) {
            flag = EntityFlags.FIREBALL_BLOCK_DAMAGE;
        }
        else if (entityType == EntityType.END_CRYSTAL) {
            flag = EntityFlags.END_CRYSTAL_BLOCK_DAMAGE;
        }
        else {
            flag = NaturalFlags.EXPLOSION_BLOCK_DAMAGE;
        }

        blockList.removeIf(block -> {
            Claim claim = this.manager.getPrioritizedClaim(block);
            return claim != null && claim.hasFlag(flag) && !claim.getFlag(flag);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        Entity entity = event.getEntity();
        Block block = event.getBlock();

        //plugin.debug("EntityInteract = " + entity + " -> " + block);

        if (!this.manager.canUseBlock(entity, block, null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityBlockChange(EntityChangeBlockEvent event) {
        Block sourceBlock = event.getBlock();
        Claim claim = this.manager.getPrioritizedClaim(sourceBlock);
        if (claim == null) return;

        Entity entity = event.getEntity();
        Player player = entity instanceof Player p ? p : null;
        if (player != null && (this.isAdminMode(player) || claim.isOwnerOrMember(player))) return;

        //BlockData toData = event.getBlockData();
        Material fromType = sourceBlock.getType();
        Material toType = event.getTo();
        EntityType entityType = entity.getType();

        //plugin.debug("EntityBlockChange = " + entity.getType().name() + " changed " + fromType.name() + " -> " + toType.name());

        ClaimFlag<Boolean> flag;
        if (entityType == EntityType.SILVERFISH) {
            flag = EntityFlags.SILVERFISH_INFEST;
        }
        else if (entityType == EntityType.PLAYER && fromType == Material.FARMLAND) {
            flag = PlayerFlags.BLOCK_TRAMPLING;
        }
        else if (entityType == EntityType.VILLAGER) {
            flag = EntityFlags.VILLAGER_FARM;
        }
        else if (entity instanceof Animals) {
            flag = EntityFlags.ANIMAL_GRIEF;
        }
        else if (entityType == EntityType.ENDERMAN) {
            flag = EntityFlags.ENDERMAN_GRIEF;
        }
        else if (entityType == EntityType.RAVAGER) {
            flag = EntityFlags.RAVAGER_GRIEF;
        }
        else if (entityType == EntityType.ENDER_DRAGON) {
            flag = EntityFlags.ENDER_DRAGON_GRIEF;
        }
        else return;

        if (!claim.hasFlag(flag)) return;
        if (!claim.getFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        Entity entity = event.getEntity();
        Block sourceBlock = event.getBlock();
        BlockState toState = event.getNewState();
        Material fromType = sourceBlock.getType();
        Material toType = toState.getType();
        EntityType entityType = entity.getType();

        //plugin.debug("EntityBlockForm = " + entity.getType().name() + " formed " + sourceBlock.getType().name() + " -> " + toType.name());

        ClaimFlag<Boolean> flag;
        if (entityType == EntityType.SNOW_GOLEM && toType == Material.SNOW) {
            flag = EntityFlags.SNOWMAN_TRAIL;
        }
        else return;

        Relation relation = this.manager.getRelation(sourceBlock.getLocation(), toState.getLocation());
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        DamageSource source = event.getDamageSource();
        Entity target = event.getEntity();
        Entity damager = source.getCausingEntity();

        if (!this.manager.canDamage(damager, target, source.getDamageType())) {
            event.setCancelled(true);
            if (damager != null) Lang.PROTECTION_DAMAGE_ENTITY.getMessage().send(damager, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(target.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        Hanging hanging = event.getEntity();
        Location location = hanging.getLocation();

        if (!(remover instanceof Player player) || (!this.manager.canBreak(player, location))) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_BREAK.getMessage().send(remover, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(hanging.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player != null && !this.manager.canBuild(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_PLACE.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(event.getEntity().getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Entity remover = event.getAttacker();
        Vehicle vehicle = event.getVehicle();
        Location location = vehicle.getLocation();

        if (!(remover instanceof Player player) || (!this.manager.canBreak(player, location))) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_BREAK.getMessage().send(remover, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, LangUtil.getSerializedName(vehicle.getType())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (this.isAdminMode(player)) return;

        Claim claim = this.manager.getPrioritizedClaim(player.getLocation());
        if (claim == null || claim.isOwnerOrMember(player) || !claim.hasFlag(PlayerFlags.PLAYER_ITEM_DROP)) return;

        if (!claim.getFlag(PlayerFlags.PLAYER_ITEM_DROP)) {
            event.setCancelled(true);
            Lang.PROTECTION_ITEM_DROP.getMessage().send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getMemberManager().isAdminMode(player)) return;

        Claim claim = this.manager.getPrioritizedClaim(player.getLocation());
        if (claim == null || claim.isOwnerOrMember(player) || !claim.hasFlag(PlayerFlags.PLAYER_ITEM_PICKUP)) return;

        if (!claim.getFlag(PlayerFlags.PLAYER_ITEM_PICKUP)) {
            event.setCancelled(true);
            Lang.PROTECTION_ITEM_PICKUP.getMessage().send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerCommandUse(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        String commandName = CommandUtil.getCommandName(message);
        Command command = CommandUtil.getCommand(commandName).orElse(null);
        if (command == null) return;

        if (!this.manager.canUseCommand(player, command)) {
            event.setCancelled(true);
            Lang.PROTECTION_COMMAND_USAGE.getMessage().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, command.getName()));
        }
    }
}
