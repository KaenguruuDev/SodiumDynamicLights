/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of SodiumDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package toni.sodiumdynamiclights;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import toni.sodiumdynamiclights.accessor.WorldRendererAccessor;
import dev.lambdaurora.lambdynlights.api.DynamicLightHandlers;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSources;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

/**
 *
 * Represents the SodiumDynamicLights mod.
 *
 * @author LambdAurora
 * @version 2.3.2
 * @since 1.0.0
 */

@Mod("sodiumdynamiclights")
public class SodiumDynamicLights {
	public static final String NAMESPACE = "sodiumdynamiclights";
	private static final double MAX_RADIUS = 7.75;
	private static final double MAX_RADIUS_SQUARED = MAX_RADIUS * MAX_RADIUS;
	private record LightBucket(int x, int y, int z) {
	}

	private static SodiumDynamicLights INSTANCE;
	public final Logger logger = LoggerFactory.getLogger(NAMESPACE);
	public final DynamicLightsConfig config = new DynamicLightsConfig();
	private final Set<DynamicLightSource> dynamicLightSources = new HashSet<>();
	private final ReentrantReadWriteLock lightSourcesLock = new ReentrantReadWriteLock();
	private volatile Map<Level, Map<LightBucket, List<DynamicLightSource>>> lightBuckets = Map.of();
	private long lastUpdate = System.currentTimeMillis();
	private int lastUpdateCount = 0;

	public SodiumDynamicLights(IEventBus modEventBus, ModContainer modContainer) {
		modEventBus.addListener(this::clientSetup);
		NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
		modContainer.registerConfig(ModConfig.Type.CLIENT, DynamicLightsConfig.SPECS);
	}

	private void onInitializeClient() {
		INSTANCE = this;
		this.log("Initializing SodiumDynamicLights...");
		registerReloadListener(new SimplePreparableReloadListener<>() {
				@Override
				protected Object prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
					return null;
				}

				@Override
				protected void apply(Object object, ResourceManager resourceManager, ProfilerFiller profiler) {
					ItemLightSources.load(resourceManager);
				}
			});

		DynamicLightHandlers.registerDefaultHandlers();
	}

	private void clientSetup(FMLClientSetupEvent event) {
		onInitializeClient();
	}

	private static final List<PreparableReloadListener> RELOAD_LISTENERS = new ArrayList<>();

	public static void registerReloadListener(PreparableReloadListener listener) {
		RELOAD_LISTENERS.add(listener);
	}

	private void addReloadListeners(AddReloadListenerEvent event) {
		RELOAD_LISTENERS.forEach(event::addListener);
	}

	/**
	 * Updates all light sources.
	 *
	 * @param renderer the renderer
	 */
	public void updateAll(@NotNull LevelRenderer renderer) {
		if (!this.config.getDynamicLightsMode().isEnabled())
			return;

		long now = System.currentTimeMillis();
		if (now >= this.lastUpdate + 50) {
			this.lastUpdate = now;
			this.lastUpdateCount = 0;

			this.lightSourcesLock.readLock().lock();
			try {
				for (var lightSource : this.dynamicLightSources) {
					if (lightSource.sodiumdynamiclights$updateDynamicLight(renderer)) this.lastUpdateCount++;
				}
				this.rebuildLightBucketsLocked();
			} finally {
				this.lightSourcesLock.readLock().unlock();
			}
		}
	}

	/**
	 * Returns the last number of dynamic light source updates.
	 *
	 * @return the last number of dynamic light source updates
	 */
	public int getLastUpdateCount() {
		return this.lastUpdateCount;
	}

	/**
	 * Returns the lightmap with combined light levels.
	 *
	 * @param pos the position
	 * @param lightmap the vanilla lightmap coordinates
	 * @return the modified lightmap coordinates
	 */
	public int getLightmapWithDynamicLight(@NotNull BlockPos pos, int lightmap) {
		return this.getLightmapWithDynamicLight(this.getDynamicLightLevel(pos), lightmap);
	}

	/**
	 * Returns the lightmap with combined light levels.
	 *
	 * @param entity the entity
	 * @param lightmap the vanilla lightmap coordinates
	 * @return the modified lightmap coordinates
	 */
	public int getLightmapWithDynamicLight(@NotNull Entity entity, int lightmap) {
		int posLightLevel = (int) this.getDynamicLightLevel(entity.getOnPos());
		int entityLuminance = ((DynamicLightSource) entity).sdl$getLuminance();

		return this.getLightmapWithDynamicLight(Math.max(posLightLevel, entityLuminance), lightmap);
	}

	/**
	 * Returns the lightmap with combined light levels.
	 *
	 * @param dynamicLightLevel the dynamic light level
	 * @param lightmap the vanilla lightmap coordinates
	 * @return the modified lightmap coordinates
	 */
	public int getLightmapWithDynamicLight(double dynamicLightLevel, int lightmap) {
		if (dynamicLightLevel > 0) {
			// lightmap is (skyLevel << 20 | blockLevel << 4)

			// Get vanilla block light level.
			int blockLevel = LightTexture.block(lightmap);
			if (dynamicLightLevel > blockLevel) {
				// Equivalent to a << 4 bitshift with a little quirk: this one ensure more precision (more decimals are saved).
				int luminance = (int) (dynamicLightLevel * 16.0);
				lightmap &= 0xfff00000;
				lightmap |= luminance & 0x000fffff;
			}
		}

		return lightmap;
	}

	/**
	 * Returns the dynamic light level at the specified position.
	 *
	 * @param pos the position
	 * @return the dynamic light level at the specified position
	 */
	public double getDynamicLightLevel(@NotNull BlockPos pos) {
		if (!this.config.getDynamicLightsMode().isEnabled())
			return 0;

		Level level = Minecraft.getInstance().level;
		if (level == null)
			return 0;

		Map<LightBucket, List<DynamicLightSource>> buckets = this.lightBuckets.get(level);
		if (buckets == null)
			return 0;

		double result = 0;
		LightBucket center = getLightBucket(pos);
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					var sources = buckets.get(new LightBucket(center.x() + x, center.y() + y, center.z() + z));
					if (sources == null)
						continue;

					for (var lightSource : sources)
						result = maxDynamicLightLevel(pos, lightSource, result);
				}
			}
		}

		return Mth.clamp(result, 0, 15);
	}

	/**
	 * Returns the dynamic light level generated by the light source at the specified position.
	 *
	 * @param pos the position
	 * @param lightSource the light source
	 * @param currentLightLevel the current surrounding dynamic light level
	 * @return the dynamic light level at the specified position
	 */
    public static double maxDynamicLightLevel(
        @NotNull BlockPos pos,
        @NotNull DynamicLightSource lightSource,
        double currentLightLevel
    ) {
        double dx = pos.getX() - lightSource.sdl$getDynamicLightX() + 0.5;
        double dy = pos.getY() - lightSource.sdl$getDynamicLightY() + 0.5;
        double dz = pos.getZ() - lightSource.sdl$getDynamicLightZ() + 0.5;

        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared > MAX_RADIUS_SQUARED)
            return currentLightLevel;

        int luminance = lightSource.sdl$getLuminance();
        if (luminance <= 0)
            return currentLightLevel;

        double multiplier = 1.0 - Math.sqrt(distanceSquared) / MAX_RADIUS;
        double lightLevel = multiplier * luminance;

        return Math.max(lightLevel, currentLightLevel);
    }

	private static LightBucket getLightBucket(@NotNull BlockPos pos) {
		return new LightBucket(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
	}

	private static LightBucket getLightBucket(@NotNull DynamicLightSource lightSource) {
		return new LightBucket(
				Mth.floor(lightSource.sdl$getDynamicLightX()) >> 4,
				Mth.floor(lightSource.sdl$getDynamicLightY()) >> 4,
				Mth.floor(lightSource.sdl$getDynamicLightZ()) >> 4
		);
	}

	private void rebuildLightBucketsLocked() {
		Map<Level, Map<LightBucket, List<DynamicLightSource>>> rebuiltBuckets = new HashMap<>();
		for (var lightSource : this.dynamicLightSources) {
			Level level = lightSource.sdl$getDynamicLightLevel();
			if (level == null)
				continue;

			rebuiltBuckets
					.computeIfAbsent(level, ignored -> new HashMap<>())
					.computeIfAbsent(getLightBucket(lightSource), ignored -> new ArrayList<>())
					.add(lightSource);
		}

		Map<Level, Map<LightBucket, List<DynamicLightSource>>> immutableBuckets = new HashMap<>();
		for (var levelEntry : rebuiltBuckets.entrySet()) {
			Map<LightBucket, List<DynamicLightSource>> immutableLevelBuckets = new HashMap<>();
			for (var bucketEntry : levelEntry.getValue().entrySet())
				immutableLevelBuckets.put(bucketEntry.getKey(), List.copyOf(bucketEntry.getValue()));
			immutableBuckets.put(levelEntry.getKey(), Map.copyOf(immutableLevelBuckets));
		}

		this.lightBuckets = Map.copyOf(immutableBuckets);
	}

	/**
	 * Adds the light source to the tracked light sources.
	 *
	 * @param lightSource the light source to add
	 */
	public void addLightSource(@NotNull DynamicLightSource lightSource) {
		if (!lightSource.sdl$getDynamicLightLevel().isClientSide())
			return;
		if (!this.config.getDynamicLightsMode().isEnabled())
			return;
		if (this.containsLightSource(lightSource))
			return;
		this.lightSourcesLock.writeLock().lock();
		try {
			this.dynamicLightSources.add(lightSource);
			this.rebuildLightBucketsLocked();
		} finally {
			this.lightSourcesLock.writeLock().unlock();
		}
	}

	/**
	 * Returns whether the light source is tracked or not.
	 *
	 * @param lightSource the light source to check
	 * @return {@code true} if the light source is tracked, else {@code false}
	 */
	public boolean containsLightSource(@NotNull DynamicLightSource lightSource) {
		if (!lightSource.sdl$getDynamicLightLevel().isClientSide())
			return false;

		boolean result;
		this.lightSourcesLock.readLock().lock();
		result = this.dynamicLightSources.contains(lightSource);
		this.lightSourcesLock.readLock().unlock();
		return result;
	}

	/**
	 * Returns the number of dynamic light sources that currently emit lights.
	 *
	 * @return the number of dynamic light sources emitting light
	 */
	public int getLightSourcesCount() {
		int result;

		this.lightSourcesLock.readLock().lock();
		result = this.dynamicLightSources.size();
		this.lightSourcesLock.readLock().unlock();

		return result;
	}

	/**
	 * Removes the light source from the tracked light sources.
	 *
	 * @param lightSource the light source to remove
	 */
	public void removeLightSource(@NotNull DynamicLightSource lightSource) {
		this.lightSourcesLock.writeLock().lock();

		var dynamicLightSources = this.dynamicLightSources.iterator();
		DynamicLightSource it;
		while (dynamicLightSources.hasNext()) {
			it = dynamicLightSources.next();
			if (it.equals(lightSource)) {
				dynamicLightSources.remove();
				lightSource.sodiumdynamiclights$scheduleTrackedChunksRebuild(Minecraft.getInstance().levelRenderer);
				this.rebuildLightBucketsLocked();
				break;
			}
		}

		this.lightSourcesLock.writeLock().unlock();
	}

	/**
	 * Clears light sources.
	 */
	public void clearLightSources() {
		this.lightSourcesLock.writeLock().lock();

		var dynamicLightSources = this.dynamicLightSources.iterator();
		DynamicLightSource it;
		while (dynamicLightSources.hasNext()) {
			it = dynamicLightSources.next();
			dynamicLightSources.remove();
			if (it.sdl$getLuminance() > 0)
				it.sdl$resetDynamicLight();
			it.sodiumdynamiclights$scheduleTrackedChunksRebuild(Minecraft.getInstance().levelRenderer);
		}
		this.lightBuckets = Map.of();

		this.lightSourcesLock.writeLock().unlock();
	}

	/**
	 * Removes light sources if the filter matches.
	 *
	 * @param filter the removal filter
	 */
	public void removeLightSources(@NotNull Predicate<DynamicLightSource> filter) {
		this.lightSourcesLock.writeLock().lock();

		var dynamicLightSources = this.dynamicLightSources.iterator();
		DynamicLightSource it;
		while (dynamicLightSources.hasNext()) {
			it = dynamicLightSources.next();
			if (filter.test(it)) {
				dynamicLightSources.remove();
				if (it.sdl$getLuminance() > 0)
					it.sdl$resetDynamicLight();
				it.sodiumdynamiclights$scheduleTrackedChunksRebuild(Minecraft.getInstance().levelRenderer);
				this.rebuildLightBucketsLocked();
				break;
			}
		}

		this.lightSourcesLock.writeLock().unlock();
	}

	/**
	 * Removes entities light source from tracked light sources.
	 */
	public void removeEntitiesLightSource() {
		this.removeLightSources(lightSource -> (lightSource instanceof Entity && !(lightSource instanceof Player)));
	}

	/**
	 * Removes Creeper light sources from tracked light sources.
	 */
	public void removeCreeperLightSources() {
		this.removeLightSources(entity -> entity instanceof Creeper);
	}

	/**
	 * Removes TNT light sources from tracked light sources.
	 */
	public void removeTntLightSources() {
		this.removeLightSources(entity -> entity instanceof PrimedTnt);
	}

	/**
	 * Removes block entities light source from tracked light sources.
	 */
	public void removeBlockEntitiesLightSource() {
		this.removeLightSources(lightSource -> lightSource instanceof BlockEntity);
	}

	/**
	 * Prints a message to the terminal.
	 *
	 * @param info the message to print
	 */
	public void log(String info) {
		this.logger.info("[LambDynLights] " + info);
	}

	/**
	 * Prints a warning message to the terminal.
	 *
	 * @param info the message to print
	 */
	public void warn(String info) {
		this.logger.warn("[LambDynLights] " + info);
	}

	/**
	 * Schedules a chunk rebuild at the specified chunk position.
	 *
	 * @param renderer the renderer
	 * @param chunkPos the chunk position
	 */
	public static void scheduleChunkRebuild(@NotNull LevelRenderer renderer, @NotNull BlockPos chunkPos) {
		scheduleChunkRebuild(renderer, chunkPos.getX(), chunkPos.getY(), chunkPos.getZ());
	}

	/**
	 * Schedules a chunk rebuild at the specified chunk position.
	 *
	 * @param renderer the renderer
	 * @param chunkPos the packed chunk position
	 */
	public static void scheduleChunkRebuild(@NotNull LevelRenderer renderer, long chunkPos) {
		scheduleChunkRebuild(renderer, BlockPos.getX(chunkPos), BlockPos.getY(chunkPos), BlockPos.getZ(chunkPos));
	}

	public static void scheduleChunkRebuild(@NotNull LevelRenderer renderer, int x, int y, int z) {
		if (Minecraft.getInstance().level != null)
			((WorldRendererAccessor) renderer).sodiumdynamiclights$scheduleChunkRebuild(x, y, z, false);
	}

	/**
	 * Updates the tracked chunk sets.
	 *
	 * @param chunkPos the packed chunk position
	 * @param old the set of old chunk coordinates to remove this chunk from it
	 * @param newPos the set of new chunk coordinates to add this chunk to it
	 */
	public static void updateTrackedChunks(@NotNull BlockPos chunkPos, @Nullable LongOpenHashSet old, @Nullable LongOpenHashSet newPos) {
		if (old != null || newPos != null) {
			long pos = chunkPos.asLong();
			if (old != null)
				old.remove(pos);
			if (newPos != null)
				newPos.add(pos);
		}
	}

	/**
	 * Updates the dynamic lights tracking.
	 *
	 * @param lightSource the light source
	 */
	public static void updateTracking(@NotNull DynamicLightSource lightSource) {
		boolean enabled = lightSource.sdl$isDynamicLightEnabled();
		int luminance = lightSource.sdl$getLuminance();

		if (!enabled && luminance > 0) {
			lightSource.sdl$setDynamicLightEnabled(true);
		} else if (enabled && luminance < 1) {
			lightSource.sdl$setDynamicLightEnabled(false);
		}
	}

	private static boolean isEyeSubmergedInFluid(LivingEntity entity) {
		if (!SodiumDynamicLights.get().config.getWaterSensitiveCheck().get()) {
			return false;
		}

		var eyePos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
		return !entity.level().getFluidState(eyePos).isEmpty();
	}

	public static int getLivingEntityLuminanceFromItems(LivingEntity entity) {
		boolean submergedInFluid = isEyeSubmergedInFluid(entity);
		int luminance = 0;

		for (var equipped : entity.getAllSlots()) {
			if (!equipped.isEmpty())
				luminance = Math.max(luminance, SodiumDynamicLights.getLuminanceFromItemStack(equipped, submergedInFluid));
		}



		return luminance;
	}

	/**
	 * Returns the luminance from an item stack.
	 *
	 * @param stack the item stack
	 * @param submergedInWater {@code true} if the stack is submerged in water, else {@code false}
	 * @return the luminance of the item
	 */
	public static int getLuminanceFromItemStack(@NotNull ItemStack stack, boolean submergedInWater) {
		return ItemLightSources.getLuminance(stack, submergedInWater);
	}

	/**
	 * Returns the SodiumDynamicLights mod instance.
	 *
	 * @return the mod instance
	 */
	public static SodiumDynamicLights get() {
		return INSTANCE;
	}
}
