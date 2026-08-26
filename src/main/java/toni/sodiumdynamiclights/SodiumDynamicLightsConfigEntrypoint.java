package toni.sodiumdynamiclights;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Registers the Dynamic Lights controls in Sodium 0.8's Graphics Settings UI. */
public final class SodiumDynamicLightsConfigEntrypoint implements ConfigEntryPoint {
    private static final String NAMESPACE = SodiumDynamicLights.NAMESPACE;

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        var options = builder.registerOwnModOptions();

        options.addPage(builder.createOptionPage()
                .setName(Component.translatable("sodium.dynamiclights.options.page"))
                .addOptionGroup(builder.createOptionGroup()
                        .addOption(builder.createEnumOption(id("mode"), DynamicLightsMode.class)
                                .setName(Component.translatable("sodium.dynamiclights.options.mode"))
                                .setTooltip(Component.translatable("sodium.dynamiclights.options.mode.desc"))
                                .setStorageHandler(DynamicLightsConfig.SPECS::save)
                                .setBinding(DynamicLightsConfig.DYNAMIC_LIGHTS_MODE::set, DynamicLightsConfig.DYNAMIC_LIGHTS_MODE::get)
                                .setDefaultValue(DynamicLightsMode.REALTIME)
                                .setElementNameProvider(DynamicLightsMode::getTranslatedText)))
                .addOptionGroup(builder.createOptionGroup()
                        .addOption(booleanOption(builder, "self", "sodium.dynamiclights.options.self", "sodium.dynamiclights.options.self.desc", DynamicLightsConfig.SELF_LIGHT_SOURCE, true))
                        .addOption(booleanOption(builder, "entities", "sodium.dynamiclights.options.entities", "sodium.dynamiclights.options.entities.desc", DynamicLightsConfig.ENTITIES_LIGHT_SOURCE, false))
                        .addOption(booleanOption(builder, "blockentities", "sodium.dynamiclights.options.blockentities", "sodium.dynamiclights.options.blockentities.desc", DynamicLightsConfig.BLOCK_ENTITIES_LIGHT_SOURCE, true))
                        .addOption(booleanOption(builder, "underwater", "sodium.dynamiclights.options.underwater", "sodium.dynamiclights.options.underwater.desc", DynamicLightsConfig.WATER_SENSITIVE_CHECK, true)))
                .addOptionGroup(builder.createOptionGroup()
                        .addOption(explosiveOption(builder, "tnt", "sodium.dynamiclights.options.tnt", "sodium.dynamiclights.options.tnt.desc", DynamicLightsConfig.TNT_LIGHTING_MODE, ExplosiveLightingMode.SIMPLE))
                        .addOption(explosiveOption(builder, "creeper", "sodium.dynamiclights.options.creeper", "sodium.dynamiclights.options.creeper.desc", DynamicLightsConfig.CREEPER_LIGHTING_MODE, ExplosiveLightingMode.OFF))));
    }

    private static net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder booleanOption(
            ConfigBuilder builder, String path, String name, String tooltip,
            net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value, boolean defaultValue) {
        return builder.createBooleanOption(id(path))
                .setName(Component.translatable(name))
                .setTooltip(Component.translatable(tooltip))
                .setStorageHandler(DynamicLightsConfig.SPECS::save)
                .setBinding(value::set, value::get)
                .setDefaultValue(defaultValue);
    }

    private static net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder<ExplosiveLightingMode> explosiveOption(
            ConfigBuilder builder, String path, String name, String tooltip,
            net.neoforged.neoforge.common.ModConfigSpec.EnumValue<ExplosiveLightingMode> value, ExplosiveLightingMode defaultValue) {
        return builder.createEnumOption(id(path), ExplosiveLightingMode.class)
                .setName(Component.translatable(name))
                .setTooltip(Component.translatable(tooltip))
                .setStorageHandler(DynamicLightsConfig.SPECS::save)
                .setBinding(value::set, value::get)
                .setDefaultValue(defaultValue)
                .setElementNameProvider(ExplosiveLightingMode::getTranslatedText);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }
}
