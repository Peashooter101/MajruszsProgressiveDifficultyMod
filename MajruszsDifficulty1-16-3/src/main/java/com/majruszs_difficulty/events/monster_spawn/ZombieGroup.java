package com.majruszs_difficulty.events.monster_spawn;

import com.majruszs_difficulty.MajruszsDifficulty;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.server.ServerWorld;

public class ZombieGroup extends EnemyGroup {
	protected static final double stoneSwordChance = 0.25D;
	protected static final double woodenAxeChance = 0.25D;

	public ZombieGroup( MonsterEntity leader, ServerWorld world ) {
		super( leader, world, 1, 3 );

		giveArmorToLeader( world, Armors.leather );
	}

	@Override
	protected CreatureEntity spawnChild( ServerWorld world ) {
		ZombieEntity zombie = EntityType.ZOMBIE.create( world );

		giveWeaponTo( zombie, world );
		setupGoals( zombie, 9, 9 );

		return zombie;
	}

	protected ItemStack generateWeapon() {
		double itemChance = MajruszsDifficulty.RANDOM.nextDouble();

		if( itemChance <= woodenAxeChance )
			return new ItemStack( Items.WOODEN_AXE );
		else if( itemChance <= woodenAxeChance + stoneSwordChance )
			return new ItemStack( Items.STONE_SWORD );
		else
			return null;
	}
}