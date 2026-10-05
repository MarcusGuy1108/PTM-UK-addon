package com.ptmuk.fare;

import com.ptmuk.block.FurnitureBlock;
import com.ptmuk.block.FurnitureType;
import com.ptmuk.registry.ModItems;
import com.rinventor.ptm2.core.init.ModSounds;
import com.rinventor.ptm2.dimension.virtual.util.PaymentUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ticket and top-up machine. With a Cube Card in hand it adds ten fares of credit (one fare when
 * sneaking), paid from the player's PTM2 bank account; with an empty hand it issues a new card.
 */
public class TopUpMachineBlock extends FurnitureBlock {
    public static final int FARES_PER_TOP_UP = 10;

    public TopUpMachineBlock(FurnitureType type) {
        super(type);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        boolean card = stack.is(ModItems.CUBE_CARD.get());
        if (!card && !(stack.isEmpty() && hand == InteractionHand.MAIN_HAND)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        if (!card) {
            player.setItemInHand(hand, new ItemStack(ModItems.CUBE_CARD.get()));
            Fares.beep(level, at, ModSounds.TICKET_MACHINE_SUCCESS.get());
            player.displayClientMessage(Component.translatable("message.ptmuk.top_up.new_card")
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        double fare = Fares.fare(level);
        if (fare <= 0) {
            player.displayClientMessage(Component.translatable("message.ptmuk.top_up.free")
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        double amount = Fares.round(fare * (player.isShiftKeyDown() ? 1 : FARES_PER_TOP_UP));
        if (!PaymentUtils.hasIndefiniteMoney(player) && PaymentUtils.getBalance(player) + 1e-6 < amount) {
            Fares.beep(level, at, ModSounds.TICKET_MACHINE_FAIL.get());
            player.displayClientMessage(Component.translatable("message.ptmuk.top_up.no_money",
                    Fares.money(level, amount), Fares.money(level, PaymentUtils.getBalance(player)))
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        PaymentUtils.pay(player, amount, Component.translatable("message.ptmuk.top_up.description").getString());
        CubeCardItem.setBalance(stack, CubeCardItem.balance(stack) + amount);
        Fares.beep(level, at, ModSounds.TICKET_MACHINE_SUCCESS.get());
        player.displayClientMessage(Component.translatable("message.ptmuk.top_up.done", Fares.money(level, amount),
                Fares.money(level, CubeCardItem.balance(stack))).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }
}
