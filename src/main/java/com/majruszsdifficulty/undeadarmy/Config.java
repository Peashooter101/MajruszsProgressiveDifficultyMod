package com.majruszsdifficulty.undeadarmy;

import com.majruszsdifficulty.Registries;
import com.majruszsdifficulty.gamestage.GameStage;
import com.majruszsdifficulty.undeadarmy.data.ExtraLootInfo;
import com.majruszsdifficulty.undeadarmy.data.UndeadArmyInfo;
import com.majruszsdifficulty.undeadarmy.data.WaveDef;
import com.majruszsdifficulty.undeadarmy.data.WavesDef;
import com.mlib.modhelper.AutoInstance;
import com.mlib.config.BooleanConfig;
import com.mlib.config.ConfigGroup;
import com.mlib.config.DoubleConfig;
import com.mlib.config.IntegerConfig;
import com.mlib.data.JsonListener;
import com.mlib.data.SerializableHelper;
import com.mlib.contexts.base.Condition;
import com.mlib.contexts.base.ModConfigs;
import com.mlib.contexts.OnDeath;
import com.mlib.contexts.OnLoot;
import com.mlib.contexts.OnServerTick;
import com.mlib.contexts.OnCheckSpawn;
import com.mlib.levels.LevelHelper;
import com.mlib.loot.LootHelper;
import com.mlib.math.Range;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import java.util.function.Supplier;

@AutoInstance
public class Config {
	static final ResourceLocation EXTRA_LOOT_ID = Registries.getLocation( "undead_army/extra_mob_loot" );
	private final BooleanConfig availability = new BooleanConfig( true );
	private final BooleanConfig naturalSpawnOnly = new BooleanConfig( false );
	private final BooleanConfig resetParticipantsKillRequirement = new BooleanConfig( false );
	private final DoubleConfig waveDuration = new DoubleConfig( 1200.0, new Range<>( 300.0, 3600.0 ) );
	private final DoubleConfig preparationDuration = new DoubleConfig( 10.0, new Range<>( 1.0, 30.0 ) );
	private final DoubleConfig highlightDelay = new DoubleConfig( 300.0, new Range<>( 30.0, 3600.0 ) );
	private final DoubleConfig extraSizePerPlayer = new DoubleConfig( 0.5, new Range<>( 0.0, 1.0 ) );
	private final IntegerConfig armyRadius = new IntegerConfig( 70, new Range<>( 35, 140 ) );
	private final IntegerConfig killRequirement = new IntegerConfig( 75, new Range<>( 0, 1000 ) );
	private final IntegerConfig killRequirementFirst = new IntegerConfig( 25, new Range<>( 0, 1000 ) );
	private final IntegerConfig killRequirementWarning = new IntegerConfig( 3, new Range<>( -1, 1000 ) );
	private final Supplier< WavesDef > wavesDef;

	public Config() {
		ConfigGroup group = ModConfigs.registerSubgroup( Registries.Groups.UNDEAD_ARMY )
			.addConfig( this.availability.name( "is_enabled" ).comment( "Determines whether the Undead Army can spawn in any way." ) )
			.addConfig( this.naturalSpawnOnly.name( "natural_spawns_only" )
				.comment( "Determines if the undead has to spawn naturally to contribute to the kill_requirement." ) )
			.addConfig( this.waveDuration.name( "wave_duration" ).comment( "Duration that players have to defeat a single wave (in seconds)." ) )
			.addConfig( this.preparationDuration.name( "preparation_duration" ).comment( "Duration before the next wave arrives (in seconds)." ) )
			.addConfig( this.highlightDelay.name( "highlight_delay" ).comment( "Duration before all mobs will be highlighted (in seconds)." ) )
			.addConfig( this.extraSizePerPlayer.name( "extra_size_per_player" )
				.comment( "Extra size ratio per each additional player on multiplayer (0.25 means ~25% bigger army per player)." ) )
			.addConfig( this.armyRadius.name( "army_radius" ).comment( "Radius, which determines how big is the raid circle (in blocks)." ) )
			.addConfig( this.killRequirement.name( "kill_requirement" )
				.comment( "Required amount of killed undead to start the Undead Army. (set to 0 if you want to disable this)" ) )
			.addConfig( this.killRequirementFirst.name( "kill_requirement_first" )
				.comment( "Required amount of killed undead to start the first Undead Army." ) )
			.addConfig( this.resetParticipantsKillRequirement.name( "reset_participants_kill_requirement" )
				.comment( "If all participants of an undead army should have their kill count reset (false resets only the person who caused it)." ) )
			.addConfig( this.killRequirementWarning.name( "kill_requirement_warning" )
				.comment( "How many left to kill until the undead army warning shows up (set to -1 to disable this)." ));

		this.wavesDef = JsonListener.add( "custom", Registries.getLocation( "undead_army_waves" ), WavesDef.class, WavesDef::new );

		OnServerTick.listen( data->Registries.getUndeadArmyManager().tick() )
			.addCondition( Condition.isEndPhase() )
			.insertTo( group );

		OnDeath.listen( this::updateKilledUndead )
			.addCondition( Condition.predicate( data->this.getRequiredKills() > 0 ) )
			.addCondition( Condition.predicate( data->data.target.getMobType() == MobType.UNDEAD ) )
			.addCondition( Condition.predicate( data->!this.isNaturalSpawnOnly() || data.target.getPersistentData().contains( "majruszsdifficulty.undeadarmy.natural" ) ) )
			.addCondition( Condition.predicate( data->!Registries.getUndeadArmyManager().isPartOfUndeadArmy( data.target ) ) )
			.addCondition( Condition.predicate( data->data.attacker instanceof ServerPlayer ) )
			.addCondition( Condition.predicate( data->LevelHelper.isEntityIn( data.attacker, Level.OVERWORLD ) ) )
			.insertTo( group );

		OnLoot.listen( this::giveExtraLoot )
			.addCondition( Condition.isServer() )
			.addCondition( OnLoot.hasDamageSource() )
			.addCondition( Condition.predicate( data->!data.context.getQueriedLootTableId().equals( EXTRA_LOOT_ID ) ) )
			.addCondition( Condition.predicate( data->data.entity instanceof Mob mob && mob.getMobType() == MobType.UNDEAD ) )
			.addCondition( Condition.predicate( data->ExtraLootInfo.hasExtraLootTag( data.entity ) ) )
			.insertTo( group );

		OnCheckSpawn.listen( this::markNaturalSpawn )
			.addCondition( Condition.predicate( data->data.event.getSpawnType() == MobSpawnType.NATURAL ) )
			.addCondition( Condition.predicate( data->data.mob.getMobType() == MobType.UNDEAD ) );
	}

	public boolean isEnabled() {
		return this.availability.isEnabled();
	}

	public boolean isNaturalSpawnOnly() { return this.naturalSpawnOnly.isEnabled(); }

	public boolean isResetParticipantsKillRequirement() { return this.resetParticipantsKillRequirement.isEnabled(); }

	public int getWaveDuration() {
		return this.waveDuration.asTicks();
	}

	public int getPreparationDuration() {
		return this.preparationDuration.asTicks();
	}

	public float getSizeMultiplier( int playerCount ) {
		return 1.0f + Math.max( playerCount - 1, 0 ) * this.extraSizePerPlayer.asFloat();
	}

	public int getWavesNum() {
		return this.getWaves().size();
	}

	public int getArmyRadius() {
		return this.armyRadius.get();
	}

	public int getHighlightDelay() {
		return this.highlightDelay.asTicks();
	}

	public int getRequiredKills() {
		return this.killRequirement.get();
	}

	public int getInitialKillsCount() {
		return this.killRequirement.get() - this.killRequirementFirst.get();
	}

	public int getSpawnRadius() {
		return this.getArmyRadius() - 15; // maybe one day add a config
	}

	public int getKillRequirementWarning() { return this.killRequirementWarning.get(); }

	public WaveDef getWave( int waveIdx ) {
		List< WaveDef > waves = this.getWaves();

		return waves.get( Mth.clamp( waveIdx - 1, 0, waves.size() - 1 ) );
	}

	public List< WaveDef > getWaves() {
		return this.wavesDef.get()
			.stream()
			.filter( waveDef->GameStage.atLeast( waveDef.gameStage ) )
			.toList();
	}

	public UndeadArmyInfo readUndeadArmyInfo( CompoundTag tag ) {
		return SerializableHelper.read( ()->new UndeadArmyInfo( this.getInitialKillsCount() ), tag );
	}

	private void updateKilledUndead( OnDeath.Data data ) {
		ServerPlayer player = ( ServerPlayer )data.attacker;

		SerializableHelper.modify( ()->new UndeadArmyInfo( this.getInitialKillsCount() ), player.getPersistentData(), info->{
			++info.killedUndead;
			if( info.killedUndead >= this.getRequiredKills() && Registries.getUndeadArmyManager().tryToSpawn( player ) ) {
				info.killedUndead = 0;
			} else if( info.killedUndead == this.getRequiredKills() - this.getKillRequirementWarning() ) {
				player.sendSystemMessage( Component.translatable( "majruszsdifficulty.undead_army.warning" ).withStyle( ChatFormatting.DARK_PURPLE ) );
			}
		} );
	}

	private void giveExtraLoot( OnLoot.Data data ) {
		List< ItemStack > extraLoot = LootHelper.getLootTable( EXTRA_LOOT_ID )
			.getRandomItems( this.toExtraLootParams( data ) );

		data.generatedLoot.addAll( extraLoot );
	}

	private LootParams toExtraLootParams( OnLoot.Data data ) {
		return new LootParams.Builder( data.getServerLevel() )
			.withParameter( LootContextParams.ORIGIN, data.entity.position() )
			.withParameter( LootContextParams.THIS_ENTITY, data.entity )
			.withParameter( LootContextParams.DAMAGE_SOURCE, data.damageSource )
			.withOptionalParameter( LootContextParams.KILLER_ENTITY, data.killer )
			.create( LootContextParamSets.ENTITY );
	}

	private void markNaturalSpawn( OnCheckSpawn.Data data ) {
		data.mob.getPersistentData().putBoolean( "majruszsdifficulty.undeadarmy.natural", true );
	}
}
