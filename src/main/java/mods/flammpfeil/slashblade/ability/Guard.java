package mods.flammpfeil.slashblade.ability;

import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Guard {
    private static final class SingletonHolder {
        private static final Guard instance = new Guard();
    }

    public static Guard getInstance() {
        return Guard.SingletonHolder.instance;
    }

    private Guard() {
    }

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event){
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();

        //begin executable check -----------------
        //item check
        ItemStack stack = victim.getMainHandItem();
        LazyOptional<ISlashBladeState> slashBlade = stack.getCapability(CapabilitySlashBlade.BLADESTATE);
        if(!slashBlade.isPresent()) return;
        if(slashBlade.filter(b->b.isBroken()).isPresent()) return;

        //user check
        if(!victim.isOnGround()) return;
        LazyOptional<IInputState> input = victim.getCapability(CapabilityInputState.INPUT_STATE);
        if(!input.isPresent()) return;

        //commanc check
        InputCommand targetCommand = InputCommand.SNEAK;
        if(!input.filter(i->i.getCommands().contains(targetCommand)).isPresent()) return;

        //range check
        if(!isInsideGuardableRange(source, victim)) return;


        //performance branch -----------------
        //just check
        long timeStartPress = input.map(i->{
            Long l = i.getLastPressTimes().get(targetCommand);
            return l == null ? 0 : l;
        }).get();
        long timeCurrent = victim.level.getGameTime();

        int soulSpeedLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.SOUL_SPEED,victim);
        int justAcceptancePeriod = 3 + soulSpeedLevel;

        boolean isJust = false;
        if(timeCurrent - timeStartPress < justAcceptancePeriod)
            isJust = true;

        //rank check
        boolean isHighRank = false;
        LazyOptional<IConcentrationRank> rank = victim.getCapability(CapabilityConcentrationRank.RANK_POINT);
        if(rank.filter(r-> IConcentrationRank.ConcentrationRanks.S.level <= r.getRank(timeCurrent).level).isPresent())
            isHighRank = true;

        //damage sauce check
        boolean isProjectile = source.isProjectile();


        //after executable check -----------------
        if(!isJust){
            if(!isProjectile) return;
            if(!isHighRank && source.isBypassArmor()) return;

            boolean inMotion = slashBlade.filter(s->{
                ComboState current = s.resolvCurrentComboState(victim);
                if(current != ComboState.NONE && current == current.getNext(victim))
                    return true;
                else
                    return false;
            }).isPresent();
            if(inMotion) return;
        }else{
            if(!isProjectile && !(source.getDirectEntity() instanceof LivingEntity))
                return;
        }

        //execute performance------------------
        //damage cancel
        event.setCanceled(true);

        //Motion
        if(isJust){
            slashBlade.ifPresent(s->s.updateComboSeq(victim, Extra.EX_COMBO_A1));
        }else{
            slashBlade.ifPresent(s->s.updateComboSeq(victim, Extra.EX_COMBO_A1_END2));
        }

        //DirectAttack knockback
        if(!isProjectile){
            Entity entity = source.getDirectEntity();
            if (entity instanceof LivingEntity) {
                ((LivingEntity)entity).knockback(0.5D, entity.getX() - victim.getX(), entity.getZ() - victim.getZ());
            }
        }

        //untouchable time
        if(isJust)
            Untouchable.setUntouchable(victim, 10);

        //rankup
        if(isJust)
            rank.ifPresent(r->r.addRankPoint(DamageSource.thorns(victim)));

        //play sound
        if(victim instanceof Player){
            victim.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 1.0F + victim.level.random.nextFloat() * 0.4F);
        }

        //cost-------------------------
        if(!isJust && !isHighRank){
            slashBlade.ifPresent(s->{
                s.damageBlade(stack, 1, victim, ItemSlashBlade.getOnBroken(stack));
            });
        }

    }


    public boolean isInsideGuardableRange(DamageSource source, LivingEntity victim){
        Vec3 sPos = source.getSourcePosition();
        if (sPos != null) {
            Vec3 viewVec = victim.getViewVector(1.0F);
            Vec3 attackVec = sPos.vectorTo(victim.position()).normalize();
            attackVec = new Vec3(attackVec.x, 0.0D, attackVec.z);
            if (attackVec.dot(viewVec) < 0.0D) {
                return true;
            }
        }
        return false;
    }
}
