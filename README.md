# Sodium Dynamic Lights

This is a fork of Sodium Dynamic Lights maintained by KaenguruuDev.

## What this solves

The original mod had multiple severe performance issues in its hot paths that became very noticeable when combined with
large Create: Aeronautics entities. This fork reduces the number of calculations by aborting earlier for out of range
light sources.

Additionally, the mod configuration system was updated to be compatible with Sodium's new options api restoring the
ability to configure the mod settings through the in-game graphics menu.

> [!NOTE]
> This fork was developed specifically for [Create Attack 7](https://create-n-beyond.de/create-attack-7). I will accept
> contributions, but I do not intend to actively develop this fork.

## Compatibility

This NeoForge 1.21.1 build targets Sodium 0.8.13-beta.2 or newer. Its Dynamic Lights settings are registered through
Sodium's Config API.

## Attribution

The original Sodium Dynamic Lights mod was created by toni and is based on LambDynamicLights by LambdAurora. Their
original attribution and licensing are retained in this fork.