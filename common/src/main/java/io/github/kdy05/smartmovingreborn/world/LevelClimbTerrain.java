package io.github.kdy05.smartmovingreborn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@link ClimbTerrain} over a level, as seen by one entity (its collision context). Shapes are tested with a
 * margin of a sixteenth: at the half block border, so that carpets and beds (nine sixteenths high) count as
 * below it, and at the block's sides, so that chests and cacti (a sixteenth narrower) count as full.
 */
public final class LevelClimbTerrain implements ClimbTerrain {
    private static final double MARGIN = 1.0 / 16;

    /** How the original saw a block: the kinds it named, and every other block by its material. */
    private enum Kind { LADDER, VINE, TRAP_DOOR, DOOR, FENCE_GATE, FENCE, WALL, PANE, OTHER }

    private final BlockGetter level;
    private final CollisionContext context;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public LevelClimbTerrain(BlockGetter level, Entity entity) {
        this.level = level;
        this.context = CollisionContext.of(entity);
    }

    @Override
    public BlockState state(int x, int y, int z) {
        return level.getBlockState(pos.set(x, y, z));
    }

    private static Kind kind(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LadderBlock) {
            return Kind.LADDER;
        }
        if (block instanceof VineBlock) {
            return Kind.VINE;
        }
        if (block instanceof TrapDoorBlock) {
            return Kind.TRAP_DOOR;
        }
        if (block instanceof DoorBlock) {
            return Kind.DOOR;
        }
        if (block instanceof FenceGateBlock) {
            return Kind.FENCE_GATE;
        }
        if (block instanceof FenceBlock) {
            return Kind.FENCE;
        }
        if (block instanceof WallBlock) {
            return Kind.WALL;
        }
        if (block instanceof IronBarsBlock) {
            return Kind.PANE;
        }
        return Kind.OTHER;
    }

    private Kind kind(int x, int y, int z) {
        return kind(state(x, y, z));
    }

    /** The ladders' and vines' materials were not solid. */
    private static boolean emptyKind(Kind kind) {
        return kind == Kind.LADDER || kind == Kind.VINE;
    }

    private static Direction direction(ClimbOrientation o) {
        return o.diagonal || o == ClimbOrientation.ZZ ? null : Direction.fromDelta(o.x, 0, o.z);
    }

    @Override
    public boolean isLadder(int x, int y, int z) {
        return kind(x, y, z) == Kind.LADDER;
    }

    @Override
    public boolean isVine(int x, int y, int z) {
        return kind(x, y, z) == Kind.VINE;
    }

    @Override
    public boolean isClimbable(int x, int y, int z) {
        return state(x, y, z).is(BlockTags.CLIMBABLE);
    }

    /** A ladder faces away from the block it is fixed to. */
    @Override
    public boolean hasLadderOrientation(int x, int y, int z, ClimbOrientation o) {
        BlockState state = state(x, y, z);
        Direction direction = direction(o);
        return direction != null && state.getBlock() instanceof LadderBlock
                && state.getValue(LadderBlock.FACING) == direction.getOpposite();
    }

    @Override
    public boolean hasVineOrientation(int x, int y, int z, ClimbOrientation o) {
        BlockState state = state(x, y, z);
        Direction direction = direction(o);
        return direction != null && state.getBlock() instanceof VineBlock
                && state.getValue(VineBlock.getPropertyForFace(direction));
    }

    @Override
    public boolean isIronBars(int x, int y, int z) {
        return state(x, y, z).is(Blocks.IRON_BARS);
    }

    @Override
    public boolean isPane(int x, int y, int z) {
        return kind(x, y, z) == Kind.PANE;
    }

    @Override
    public boolean isFenceBase(int x, int y, int z) {
        Kind kind = kind(x, y, z);
        return kind == Kind.FENCE || kind == Kind.WALL;
    }

    @Override
    public boolean isWall(int x, int y, int z) {
        return kind(x, y, z) == Kind.WALL;
    }

    @Override
    public boolean isFenceGate(int x, int y, int z) {
        return kind(x, y, z) == Kind.FENCE_GATE;
    }

    @Override
    public boolean isOpenFenceGate(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return kind(state) == Kind.FENCE_GATE && state.getValue(FenceGateBlock.OPEN);
    }

    /**
     * Panes, fences and walls by their connections towards an axis direction; the original also asked about
     * diagonal neighbours, which 1.20.1 blocks do not record, so those count as not connected. A closed fence
     * gate counts in every direction when its gate spans {@code heading}'s axis, like the original.
     */
    @Override
    public boolean wallConnects(int x, int y, int z, ClimbOrientation side, ClimbOrientation heading) {
        BlockState state = state(x, y, z);
        Kind kind = kind(state);
        if (kind == Kind.FENCE_GATE) {
            Direction across = direction(heading);
            return !state.getValue(FenceGateBlock.OPEN) && across != null
                    && state.getValue(FenceGateBlock.FACING).getAxis() != across.getAxis();
        }
        Direction direction = direction(side);
        if (direction == null) {
            return false;
        }
        return switch (kind) {
            case FENCE, PANE -> state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction));
            case WALL -> state.getValue(wallSide(direction)) != WallSide.NONE;
            default -> false;
        };
    }

    private static EnumProperty<WallSide> wallSide(Direction direction) {
        return switch (direction) {
            case NORTH -> WallBlock.NORTH_WALL;
            case EAST -> WallBlock.EAST_WALL;
            case SOUTH -> WallBlock.SOUTH_WALL;
            default -> WallBlock.WEST_WALL;
        };
    }

    @Override
    public boolean isTrapDoor(int x, int y, int z) {
        return kind(x, y, z) == Kind.TRAP_DOOR;
    }

    @Override
    public boolean isClosedTrapDoor(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return kind(state) == Kind.TRAP_DOOR && !state.getValue(TrapDoorBlock.OPEN);
    }

    /**
     * The hinge side, where an open trap door stands, lies opposite its facing. A diagonal direction counts
     * when either of its axis steps leads there.
     */
    @Override
    public boolean isTrapDoorFront(int x, int y, int z, ClimbOrientation o) {
        BlockState state = state(x, y, z);
        if (kind(state) != Kind.TRAP_DOOR) {
            return false;
        }
        Direction hinge = state.getValue(TrapDoorBlock.FACING).getOpposite();
        return hinge.getStepX() != 0 && hinge.getStepX() == o.x || hinge.getStepZ() != 0 && hinge.getStepZ() == o.z;
    }

    @Override
    public boolean isDoor(int x, int y, int z) {
        return kind(x, y, z) == Kind.DOOR;
    }

    @Override
    public boolean isDoorTop(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return kind(state) == Kind.DOOR && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    /** By the side of the block its panel takes, open or closed. */
    @Override
    public boolean isDoorFrontBlocked(int x, int y, int z, ClimbOrientation o) {
        BlockState state = state(x, y, z);
        if (kind(state) != Kind.DOOR) {
            return true;
        }
        AABB panel = state.getShape(level, pos.set(x, y, z)).bounds();
        if (panel.maxX <= 0.5) {
            return o.x < 0;
        }
        if (panel.minX >= 0.5) {
            return o.x > 0;
        }
        if (panel.maxZ <= 0.5) {
            return o.z < 0;
        }
        return panel.minZ >= 0.5 ? o.z > 0 : true;
    }

    @Override
    public boolean isStair(int x, int y, int z) {
        return state(x, y, z).getBlock() instanceof StairBlock;
    }

    @Override
    public boolean isTopStair(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.TOP;
    }

    // Shapes

    private VoxelShape shape(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return state.getCollisionShape(level, pos.set(x, y, z), context);
    }

    /** The near part of a block towards {@code o}, from {@code minY} to {@code maxY}. */
    private static VoxelShape near(ClimbOrientation o, double minY, double maxY) {
        return Shapes.box(o.x < 0 ? 0.5 : MARGIN, minY, o.z < 0 ? 0.5 : MARGIN,
                o.x > 0 ? 0.5 : 1 - MARGIN, maxY, o.z > 0 ? 0.5 : 1 - MARGIN);
    }

    private static boolean touches(VoxelShape shape, VoxelShape region) {
        return Shapes.joinIsNotEmpty(shape, region, BooleanOp.AND);
    }

    private static boolean covers(VoxelShape shape, VoxelShape region) {
        return !Shapes.joinIsNotEmpty(region, shape, BooleanOp.ONLY_FIRST);
    }

    private boolean lowerEmpty(int x, int y, int z, ClimbOrientation o) {
        return !touches(shape(x, y, z), near(o, MARGIN, 0.5));
    }

    private boolean upperEmpty(int x, int y, int z, ClimbOrientation o) {
        return !touches(shape(x, y, z), near(o, 0.5 + MARGIN, 1));
    }

    @Override
    public boolean isFullEmpty(int x, int y, int z) {
        Kind kind = kind(x, y, z);
        if (kind != Kind.OTHER) {
            return emptyKind(kind);
        }
        return !touches(shape(x, y, z), Shapes.box(0, MARGIN, 0, 1, 1, 1));
    }

    /** The original also counted any trap door's upper half as empty. */
    @Override
    public boolean isUpperHalfEmpty(int x, int y, int z, ClimbOrientation o) {
        Kind kind = kind(x, y, z);
        if (kind != Kind.OTHER) {
            return emptyKind(kind) || kind == Kind.TRAP_DOOR;
        }
        return upperEmpty(x, y, z, o);
    }

    @Override
    public boolean isLowerHalfEmpty(int x, int y, int z, ClimbOrientation o) {
        Kind kind = kind(x, y, z);
        if (kind != Kind.OTHER) {
            return emptyKind(kind);
        }
        return lowerEmpty(x, y, z, o);
    }

    /** The original did not count trap doors and open fence gates as solid. */
    @Override
    public boolean isUpperHalfSolid(int x, int y, int z, ClimbOrientation o) {
        BlockState state = state(x, y, z);
        Kind kind = kind(state);
        if (kind != Kind.OTHER) {
            return !emptyKind(kind) && kind != Kind.TRAP_DOOR
                    && !(kind == Kind.FENCE_GATE && state.getValue(FenceGateBlock.OPEN));
        }
        return !upperEmpty(x, y, z, o);
    }

    @Override
    public boolean isBottomHalf(int x, int y, int z, ClimbOrientation o) {
        return kind(x, y, z) == Kind.OTHER && !lowerEmpty(x, y, z, o) && upperEmpty(x, y, z, o);
    }

    @Override
    public boolean isTopHalf(int x, int y, int z, ClimbOrientation o) {
        return kind(x, y, z) == Kind.OTHER && lowerEmpty(x, y, z, o) && !upperEmpty(x, y, z, o);
    }

    @Override
    public boolean hasHalfLedge(int x, int y, int z, ClimbOrientation o) {
        return kind(x, y, z) == Kind.OTHER && !lowerEmpty(x, y, z, o)
                && !covers(shape(x, y, z), near(o, 0.5 + MARGIN, 0.5 + 2 * MARGIN));
    }

    @Override
    public boolean isLowerHalfFull(int x, int y, int z, ClimbOrientation o) {
        return kind(x, y, z) != Kind.OTHER || covers(shape(x, y, z), near(o, 0.5 - 2 * MARGIN, 0.5 - MARGIN));
    }
}
