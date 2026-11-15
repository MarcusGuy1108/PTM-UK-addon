package com.ptmuk.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Someone who works in a shop: stands at their till (never wanders, can't be hurt), looks at
 * whoever's near, and wears their shop's uniform. Talk to them (click) to hand over what you're
 * buying, order, or pay; it's the till that knows the shop.
 */
public class ShopKeeperEntity extends PathfinderMob {
    /** Which face and uniform (see the renderer's skins): outfit * 3 + person. */
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(ShopKeeperEntity.class, EntityDataSerializers.INT);
    public static final int PEOPLE = 3, OUTFITS = 8;
    private BlockPos till = BlockPos.ZERO;

    public ShopKeeperEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKIN, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public void setup(BlockPos till, int skin, String name) {
        this.till = till;
        entityData.set(SKIN, skin);
        setCustomName(net.minecraft.network.chat.Component.literal(name));
    }

    public BlockPos till() {
        return till;
    }

    /** Long hair, so a name from the women's list (the skins alternate). */
    public static boolean longHair(int skin) {
        return (skin / PEOPLE + skin % PEOPLE) % 2 == 0;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide && level().getBlockEntity(till) instanceof ShopBlockEntity shop) {
            shop.talk(player, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 100 == 0 && !(level().getBlockEntity(till) instanceof ShopBlockEntity)) {
            discard();                                      // their till's gone
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("till", till.asLong());
        tag.putInt("skin", skin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        till = BlockPos.of(tag.getLong("till"));
        entityData.set(SKIN, tag.getInt("skin"));
    }
}
