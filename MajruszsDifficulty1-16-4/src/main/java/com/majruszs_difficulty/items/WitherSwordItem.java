package com.majruszs_difficulty.items;

import com.majruszs_difficulty.ConfigHandler.Config;
import com.majruszs_difficulty.RegistryHandler;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber
public class WitherSwordItem extends SwordItem {
	public WitherSwordItem() {
		super( CustomItemTier.WITHER, 2, -2.4f, ( new Item.Properties() ).group( RegistryHandler.ITEM_GROUP ) );
	}

	@SubscribeEvent
	public static void onHit( LivingAttackEvent event ) {
		DamageSource source = event.getSource();
		if( !( source.getTrueSource() instanceof LivingEntity ) )
			return;

		ItemStack attackerItemStack = ( ( LivingEntity )source.getTrueSource() ).getHeldItemMainhand();
		if( attackerItemStack.getItem() instanceof WitherSwordItem ) {
			LivingEntity target = event.getEntityLiving();
			int effectDuration = Config.getDurationInSeconds( Config.Durations.WITHER_SWORD_EFFECT );
			target.addPotionEffect( new EffectInstance( Effects.WITHER, effectDuration, 1 ) );
		}
	}

	@Override
	@OnlyIn( Dist.CLIENT )
	public void addInformation( ItemStack stack, @Nullable World world, List< ITextComponent > toolTip, ITooltipFlag flag ) {
		toolTip.add( new TranslationTextComponent( "item.majruszs_difficulty.wither_sword.tooltip" ).mergeStyle( TextFormatting.GRAY ) );
	}
}