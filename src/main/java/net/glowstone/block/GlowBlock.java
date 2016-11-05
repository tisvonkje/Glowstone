package net.glowstone.block;

import net.glowstone.GlowChunk;
import net.glowstone.GlowWorld;
import net.glowstone.block.blocktype.BlockRedstone;
import net.glowstone.block.blocktype.BlockRedstoneTorch;
import net.glowstone.block.blocktype.BlockType;
import net.glowstone.block.entity.TileEntity;
import net.glowstone.net.message.play.game.BlockChangeMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Button;
import org.bukkit.material.Diode;
import org.bukkit.material.Lever;
import org.bukkit.metadata.MetadataStore;
import org.bukkit.metadata.MetadataStoreBase;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a single block in a world.
 */
public final class GlowBlock implements Block {

    /**
     * The BlockFaces of a single-layer 3x3 area.
     */
    private static final BlockFace[] LAYER = new BlockFace[]{
            BlockFace.NORTH_WEST, BlockFace.NORTH, BlockFace.NORTH_EAST,
            BlockFace.EAST, BlockFace.SELF, BlockFace.WEST,
            BlockFace.SOUTH_WEST, BlockFace.SOUTH, BlockFace.SOUTH_EAST};

    /**
     * The BlockFaces of all directly adjacent.
     */
    private static final BlockFace[] ADJACENT = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    /**
     * The metadata store for blocks.
     */
    private static final MetadataStore<Block> metadata = new BlockMetadataStore();
    private static final Map<GlowBlock, List<Long>> counterMap = new HashMap<>();
    private final int x;
    private final int y;
    private final int z;
    private GlowChunk chunk;
    private int type = -1;

    public GlowBlock(GlowChunk chunk, int x, int y, int z) {
        this.chunk = chunk;
        this.x = x;
        this.y = Math.min(256, Math.max(y, 0));
        this.z = z;
    }

    // --- Basics --- \\
    @Override
    public GlowWorld getWorld() {
        return chunk.getWorld();
    }

    @Override
    public GlowChunk getChunk() {
        return chunk;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Override
    public Location getLocation() {
        return new Location(getWorld(), x, y, z);
    }

    @Override
    public Location getLocation(Location loc) {
        if (loc == null) {
            return null;
        }
        loc.setWorld(getWorld());
        loc.setX(x);
        loc.setY(y);
        loc.setZ(z);
        return loc;
    }

    public TileEntity getTileEntity() {
        return getChunk().getEntity(x & 0xf, y, z & 0xf);
    }

    @Override
    public GlowBlockState getState() {
        TileEntity entity = getTileEntity();
        if (entity != null) {
            GlowBlockState state = entity.getState();
            if (state != null) {
                return state;
            }
        }
        return new GlowBlockState(this);
    }

    @Override
    public Biome getBiome() {
        return getWorld().getBiome(x, z);
    }

    @Override
    public void setBiome(Biome bio) {
        getWorld().setBiome(x, z, bio);
    }

    @Override
    public double getTemperature() {
        return getWorld().getTemperature(x, z);
    }

    @Override
    public double getHumidity() {
        return getWorld().getHumidity(x, z);
    }

    // --- Relative blocks --- \\
    @Override
    public BlockFace getFace(Block block) {
        if (block == null || block.getWorld() != getWorld()) {
            return null;
        }
        for (BlockFace face : BlockFace.values()) {
            if (x + face.getModX() == block.getX()
                    && y + face.getModY() == block.getY()
                    && z + face.getModZ() == block.getZ()) {
                return face;
            }
        }
        return null;
    }

    @Override
    public GlowBlock getRelative(int modX, int modY, int modZ) {
        if (modX == 0 && modY == 0 && modZ == 0) {
            return this;
        }
        return getWorld().getBlockAt(x + modX, y + modY, z + modZ);
    }

    @Override
    public GlowBlock getRelative(BlockFace face) {
        if (face == BlockFace.SELF) {
            return this;
        }
        return getRelative(face.getModX(), face.getModY(), face.getModZ());
    }

    @Override
    public GlowBlock getRelative(BlockFace face, int distance) {
        if (distance == 0) {
            return this;
        } else if (distance == 1) {
            return getRelative(face);
        }
        return getRelative(face.getModX() * distance, face.getModY() * distance, face.getModZ() * distance);
    }

    // --- Type (Material and ID) and data  --- \\
    // Type (Material)
    @Override
    public Material getType() {
        return Material.getMaterial(getTypeId());
    }

    @Override
    public void setType(Material type) {
        setTypeId(type.getId());
    }

    @Override
    public void setType(Material type, boolean applyPhysics) {
        setTypeId(type.getId(), applyPhysics);
    }

    /**
     * Set the Material type of a block with data and optionally apply
     * physics.
     *
     * @param type The type to set the block to.
     * @param data The raw data to set the block to.
     * @param applyPhysics notify this block and surrounding blocks to update physics
     */
    public void setType(Material type, byte data, boolean applyPhysics) {
        setTypeIdAndData(type.getId(), data, applyPhysics);
    }

    // Type ID
    @Override
    public int getTypeId() {
        return getTypeId(false);
    }

    private int getTypeId(boolean noCache) {
        if (noCache) {
            // we need to read from storage, not from cache
            // & 0xf ensures we get the location relative to the chunk [0 to 15]
            return getChunk().getType(x & 0xf, z & 0xf, y);
        }
        if (type > -1) {
            // cache has been initialized, just return the cached value
            return type;
        }
        // set type to enable cache from stored value
        return type = getTypeId(true);
    }

    @Override
    public boolean setTypeId(int type) {
        return setTypeId(type, true);
    }

    @Override
    public boolean setTypeId(int type, boolean applyPhysics) {
        return setTypeIdAndData(type, getData(), applyPhysics);
    }

    // Metadata
    @Override
    public byte getData() {
        return (byte) getChunk().getMetaData(x & 0xf, z & 0xf, y);
    }

    @Override
    public void setData(byte data) {
        setData(data, true);
    }

    @Override
    public void setData(byte data, boolean applyPhysics) {
        setTypeIdAndData(getTypeId(), data, applyPhysics);
    }

    // Type and data
    @Override
    public boolean setTypeIdAndData(int type, byte data, boolean applyPhysics) {
        Material oldType = getType();
        Material newType = Material.getMaterial(type);
        byte oldData = getData();
        boolean changed = false;

        if (oldType != newType) {
            getChunk().setType(x & 0xf, z & 0xf, y, type);
            this.type = type;
            changed = true;
        }
        if (oldData != data) {
            getChunk().setMetaData(x & 0xf, z & 0xf, y, data);
            changed = true;
        }

        if (changed) {
            // send block change message first because it isn't so blocking
            BlockChangeMessage bcmsg = new BlockChangeMessage(x, y, z, type, data);
            getWorld().getRawPlayers().parallelStream().forEach(p -> p.sendBlockChange(bcmsg));
            if (applyPhysics) {
                applyPhysics(oldType, Material.getMaterial(type), oldData, data);
            }
        }

        return changed;
    }

    // Lighting data
    @Override
    public byte getLightLevel() {
        return (byte) Math.max(getLightFromSky(), getLightFromBlocks());
    }

    @Override
    public byte getLightFromSky() {
        return getChunk().getSkyLight(x & 0xf, z & 0xf, y);
    }

    @Override
    public byte getLightFromBlocks() {
        return getChunk().getBlockLight(x & 0xf, z & 0xf, y);
    }

    @Override
    public boolean isEmpty() {
        return getType() == Material.AIR;
    }

    @Override
    public boolean isLiquid() {
        Material mat = getType();
        return mat == Material.WATER || mat == Material.STATIONARY_WATER || mat == Material.LAVA || mat == Material.STATIONARY_LAVA;
    }

    /**
     * Get block material's flammable ability. (ability to have fire spread to it)
     *
     * @return if this block is flammable
     */
    public boolean isFlammable() {
        return getMaterialValues().getFlammability() >= 0;
    }

    /**
     * Get block material's burn ability. (ability to have fire consume it)
     *
     * @return if this block is burnable
     */
    public boolean isBurnable() {
        return getMaterialValues().getFireResistance() >= 0;
    }

    public MaterialValueManager.GlowMaterial getMaterialValues() {
        return MaterialValueManager.getValues(getType());
    }

    ////////////////////////////////////////////////////////////////////////////
    // Redstone
    // TODO: move some of the blocktype specific stuff to new blocktype system
    @Override
    public boolean isBlockPowered() {
        // Strong powered?

        if (getType() == Material.REDSTONE_BLOCK) {
            return true;
        }

        if (getType() == Material.LEVER && ((Lever) getState().getData()).isPowered()) {
            return true;
        }

        if ((getType() == Material.WOOD_BUTTON || getType() == Material.STONE_BUTTON)
                && ((Button) getState().getData()).isPowered()) {
            return true;
        }

        // Now checking for power attached, only solid blocks transmit this..
        if (!getType().isSolid()) {
            return false;
        }

        for (BlockFace face : ADJACENT) {
            GlowBlock target = getRelative(face);
            switch (target.getType()) {
                case LEVER:
                    Lever lever = (Lever) target.getState().getData();
                    if (lever.isPowered() && lever.getAttachedFace() == target.getFace(this)) {
                        return true;
                    }
                    break;
                case STONE_BUTTON:
                case WOOD_BUTTON:
                    Button button = (Button) target.getState().getData();
                    if (button.isPowered() && button.getAttachedFace() == target.getFace(this)) {
                        return true;
                    }
                    break;
                case DIODE_BLOCK_ON:
                    if (((Diode) target.getState().getData()).getFacing() == target.getFace(this)) {
                        return true;
                    }
                    break;
                case REDSTONE_TORCH_ON:
                    if (face == BlockFace.DOWN) {
                        return true;
                    }
                    break;
                case REDSTONE_WIRE:
                    if (target.getData() > 0 && BlockRedstone.calculateConnections(target).contains(target.getFace(this))) {
                        return true;
                    }
                    break;
            }
        }

        return false;
    }

    @Override
    public boolean isBlockIndirectlyPowered() {
        // Is a nearby block directly powered?
        for (BlockFace face : ADJACENT) {
            GlowBlock block = getRelative(face);
            if (block.isBlockPowered()) {
                return true;
            }

            switch (block.getType()) {
                case REDSTONE_TORCH_ON:
                    if (face != BlockRedstoneTorch.getAttachedBlockFace(block).getOppositeFace()) {
                        return true;
                    }
                    break;
                case REDSTONE_WIRE:
                    if (block.getData() > 0 && BlockRedstone.calculateConnections(block).contains(block.getFace(this))) {
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    @Override
    public boolean isBlockFacePowered(BlockFace face) {
        // Strong powered?

        if (getType() == Material.REDSTONE_BLOCK) {
            return true;
        }

        if (getType() == Material.LEVER && ((Lever) getState().getData()).isPowered()) {
            return true;
        }

        if ((getType() == Material.WOOD_BUTTON || getType() == Material.STONE_BUTTON)
                && ((Button) getState().getData()).isPowered()) {
            return true;
        }

        // Now checking for power attached, only solid blocks transmit this..
        if (!getType().isSolid()) {
            return false;
        }

        GlowBlock target = getRelative(face);
        switch (target.getType()) {
            case LEVER:
                Lever lever = (Lever) target.getState().getData();
                if (lever.isPowered() && lever.getAttachedFace() == target.getFace(this)) {
                    return true;
                }
                break;
            case STONE_BUTTON:
            case WOOD_BUTTON:
                Button button = (Button) target.getState().getData();
                if (button.isPowered() && button.getAttachedFace() == target.getFace(this)) {
                    return true;
                }
                break;
            case DIODE_BLOCK_ON:
                if (((Diode) target.getState().getData()).getFacing() == target.getFace(this)) {
                    return true;
                }
                break;
            case REDSTONE_TORCH_ON:
                if (face == BlockFace.DOWN) {
                    return true;
                }
                break;
            case REDSTONE_WIRE:
                if (target.getData() > 0 && BlockRedstone.calculateConnections(target).contains(target.getFace(this))) {
                    return true;
                }
                break;
        }

        return false;
    }

    @Override
    public boolean isBlockFaceIndirectlyPowered(BlockFace face) {
        GlowBlock block = getRelative(face);
        if (block.isBlockPowered()) {
            return true;
        }
        switch (block.getType()) {
            case REDSTONE_TORCH_ON:
                if (face != BlockRedstoneTorch.getAttachedBlockFace(block).getOppositeFace()) {
                    return true;
                }
                break;
            case REDSTONE_WIRE:
                if (block.getData() > 0 && BlockRedstone.calculateConnections(block).contains(block.getFace(this))) {
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public int getBlockPower(BlockFace face) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getBlockPower() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public PistonMoveReaction getPistonMoveReaction() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String toString() {
        return "GlowBlock{chunk=" + getChunk() + ",x=" + x + ",y=" + y + ",z=" + z + ",type=" + getType() + ",data=" + getData() + "}";
    }

    ////////////////////////////////////////////////////////////////////////////
    // Drops and breaking

    /**
     * Break the block naturally, randomly dropping only some of the drops.
     *
     * @param yield The approximate portion of the drops to actually drop.
     * @return true if the block was destroyed
     */
    public boolean breakNaturally(float yield) {
        if (isEmpty()) {
            return false;
        }

        Location location = getLocation();
        // TODO: move to new blocktype system
        Collection<ItemStack> toDrop = ItemTable.instance().getBlock(getType()).getMinedDrops(this);
        toDrop.parallelStream().filter(stack -> ThreadLocalRandom.current().nextFloat() < yield).parallel().forEach(stack -> getWorld().dropItemNaturally(location, stack));

        setType(Material.AIR);
        return true;
    }

    @Override
    public boolean breakNaturally() {
        return breakNaturally(1.0f);
    }

    @Override
    public boolean breakNaturally(ItemStack tool) {
        if (!getDrops(tool).isEmpty()) {
            return breakNaturally();
        }
        return setTypeId(Material.AIR.getId());
    }

    @Override
    public Collection<ItemStack> getDrops() {
        return ItemTable.instance().getBlock(getType()).getMinedDrops(this);
    }

    @Override
    public Collection<ItemStack> getDrops(ItemStack tool) {
        return ItemTable.instance().getBlock(getType()).getDrops(this, tool);
    }

    /**
     * Tell this block that the chunk was loaded.
     */
    public void chunkUnloaded() {
        chunk = null;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Metadata
    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        metadata.setMetadata(this, metadataKey, newMetadataValue);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return metadata.getMetadata(this, metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return metadata.hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        metadata.removeMetadata(this, metadataKey, owningPlugin);
    }

    // --- Physics --- \\

    /**
     * Notify this block and its surrounding blocks that this block has changed
     * type and data.
     *
     * @param oldType the old block type
     * @param newType the new block type
     * @param oldData the old data
     * @param newData the new data
     */
    public void applyPhysics(Material oldType, Material newType, byte oldData, byte newData) {
        // notify the surrounding blocks that this block has changed
        ItemTable itemTable = ItemTable.instance();

        for (int y = -1; y <= 1; y++) {
            for (BlockFace face : LAYER) {
                if (y == 0 && face == BlockFace.SELF) {
                    continue;
                }

                GlowBlock notify = getRelative(face.getModX(), face.getModY() + y, face.getModZ());

                BlockFace blockFace;
                if (y == 0) {
                    blockFace = face.getOppositeFace();
                } else if (y == -1 && face == BlockFace.SELF) {
                    blockFace = BlockFace.UP;
                } else if (y == 1 && face == BlockFace.SELF) {
                    blockFace = BlockFace.DOWN;
                } else {
                    blockFace = null;
                }

                BlockType notifyType = itemTable.getBlock(notify.getTypeId());
                if (notifyType != null) {
                    notifyType.onNearBlockChanged(notify, blockFace, this, oldType, oldData, newType, newData);
                }
            }
        }

        BlockType type = itemTable.getBlock(oldType);
        if (type != null) {
            type.onBlockChanged(this, oldType, oldData, newType, newData);
        }
    }

    public void count(int timeout) {
        GlowBlock target = this;
        List<Long> gameTicks = new ArrayList<>();
        for (GlowBlock block : counterMap.keySet()) {
            if (block.getLocation().equals(getLocation())) {
                gameTicks = counterMap.get(block);
                target = block;
                break;
            }
        }

        long time = getWorld().getFullTime();
        gameTicks.add(time + timeout);

        counterMap.put(target, gameTicks);
    }

    public int getCounter() {
        GlowBlock target = this;
        List<Long> gameTicks = new ArrayList<>();
        for (GlowBlock block : counterMap.keySet()) {
            if (block.getLocation().equals(getLocation())) {
                gameTicks = counterMap.get(block);
                target = block;
                break;
            }
        }

        long time = getWorld().getFullTime();

        for (Iterator<Long> it = gameTicks.iterator(); it.hasNext(); ) {
            long rate = it.next();
            if (rate < time) {
                it.remove();
            }
        }

        counterMap.put(target, gameTicks);
        return gameTicks.size();
    }

    @Override
    public int hashCode() {
        return y << 24 ^ x ^ z ^ getWorld().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        GlowBlock other = (GlowBlock) obj;
        return x == other.x && y == other.y && z == other.z && getWorld().equals(other.getWorld());
    }

    /**
     * The metadata store class for blocks.
     */
    private static final class BlockMetadataStore extends MetadataStoreBase<Block> implements MetadataStore<Block> {

        @Override
        protected String disambiguate(Block subject, String metadataKey) {
            return subject.getWorld() + "," + subject.getX() + "," + subject.getY() + "," + subject.getZ() + ":" + metadataKey;
        }
    }
}

