package com.majruszsdifficulty.gamemodifiers.configs;

import com.majruszsdifficulty.goals.FollowGroupLeaderGoal;
import com.majruszsdifficulty.goals.TargetAsLeaderGoal;
import com.mlib.Random;
import com.mlib.config.ConfigGroup;
import com.mlib.config.IntegerConfig;
import com.mlib.entities.EntityHelper;
import com.mlib.items.ItemHelper;
import com.mlib.levels.LevelHelper;
import com.mlib.loot.LootHelper;
import com.mlib.math.AnyPos;
import com.mlib.math.Range;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class MobGroupConfig extends ConfigGroup {
	public static final String SIDEKICK_TAG = "MajruszsDifficultySidekick";
	public static final String LEADER_TAG = "MajruszsDifficultyLeader";
	static final Range< Integer > RANGE = new Range<>( 1, 9 );
	final Supplier< EntityType< ? extends PathfinderMob > > mob;
	final IntegerConfig min;
	final IntegerConfig max;
	final ResourceLocation leaderSet;
	final ResourceLocation sidekickSet;

	public MobGroupConfig( Supplier< EntityType< ? extends PathfinderMob > > mob, Range< Integer > range, ResourceLocation leaderSet,
		ResourceLocation sidekickSet
	) {
		this.mob = mob;
		this.min = new IntegerConfig( range.from, RANGE );
		this.max = new IntegerConfig( range.to, RANGE );
		this.leaderSet = leaderSet;
		this.sidekickSet = sidekickSet;

		this.addConfig( this.min.name( "min_count" ).comment( "Minimum amount of extra mobs to spawn." ) );
		this.addConfig( this.max.name( "max_count" ).comment( "Maximum amount of extra mobs to spawn." ) );
	}

	public List< PathfinderMob > spawn( PathfinderMob leader ) {
		int sidekickAmount = Random.nextInt( this.getMinCount(), this.getMaxCount() + 1 );
		List< PathfinderMob > sidekicks = new ArrayList<>();
		for( int idx = 0; idx < sidekickAmount; idx++ ) {
			PathfinderMob sidekick = EntityHelper.createSpawner( this.getMob(), leader.level() )
				.position( this.getRandomizedPosition( leader.level(), leader.position() ) )
				.spawn();

			if( sidekick != null ) {
				this.addSidekickGoals( sidekick, leader );
				this.markAsSidekick( sidekick );
				this.applyArmorSet( sidekick, this.sidekickSet );
				sidekicks.add( sidekick );
			}
		}
		this.markAsLeader( leader );
		this.applyArmorSet( leader, this.leaderSet );

		return sidekicks;
	}

	public EntityType< ? extends PathfinderMob > getMob() {
		return this.mob.get();
	}

	public int getMinCount() {
		return Math.min( this.min.get(), this.max.get() );
	}

	public int getMaxCount() {
		return Math.max( this.min.get(), this.max.get() );
	}

	private void addSidekickGoals( PathfinderMob sidekick, PathfinderMob leader ) {
		sidekick.goalSelector.addGoal( 9, new FollowGroupLeaderGoal( sidekick, leader, 1.0, 6.0f, 5.0f ) );
		sidekick.targetSelector.addGoal( 9, new TargetAsLeaderGoal( sidekick, leader ) );
	}

	private void markAsSidekick( PathfinderMob sidekick ) {
		sidekick.getPersistentData().putBoolean( SIDEKICK_TAG, true );
	}

	private void markAsLeader( PathfinderMob leader ) {
		leader.getPersistentData().putBoolean( LEADER_TAG, true );
	}

	private void applyArmorSet( PathfinderMob mob, ResourceLocation location ) {
		if( location == null ) {
			return;
		}

		LootHelper.getLootTable( location )
			.getRandomItems( LootHelper.toGiftParams( mob ) )
			.forEach( itemStack->ItemHelper.equip( mob, itemStack ) );

		Arrays.stream( EquipmentSlot.values() )
			.forEach( slot->mob.setDropChance( slot, 0.05f ) );
	}

	private Vec3 getRandomizedPosition( Level level, Vec3 position ) {
		for( int idx = 0; idx < 3; ++idx ) {
			Vec3 newPosition = AnyPos.from( position ).add( Random.nextInt( -3, 4 ), 0.0, Random.nextInt( -3, 4 ) ).vec3();
			Optional< BlockPos > spawnPoint = LevelHelper.findBlockPosOnGround( level, newPosition.x, new Range<>( newPosition.y - 3, newPosition.y + 3 ), newPosition.z );
			if( spawnPoint.isPresent() ) {
				return AnyPos.from( spawnPoint.get() ).add( 0.5, 0.0, 0.5 ).vec3();
			}
		}

		return position;
	}
}
