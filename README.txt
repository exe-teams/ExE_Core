
Astral Mekanism ore processing
==============================

ExE Core 1.0 requires Astral Mekanism 1.8.2 or newer. For every material in
config/execore-materials.toml, ExE Core independently registers the intermediate
items and slurries and generates this processing chain:

The development runtime resolves Astral Mekanism from CurseMaven using
curse.maven:astral-mekanism-1415150:8299719. It is declared as a Gradle
implementation dependency, so it is available on both compile and runtime
classpaths without bundling its source code into ExE Core.

reconstruction -> nucleosynthesizing -> compressing -> dissolution -> washing
-> crystallizing -> injecting -> purifying -> crushing -> enriching

Per-material controls:
- generate.astral_processing: enable or disable the chain for that material.
- astral_multiplier: multiply every stage output for that material.
- astral_output: optional final item id produced by enriching. It defaults to drop.

Normal ores can enter the same intermediate item stages as Astral Mekanism's
native processing. Dense ores use execore:dense_ores/<material> so they do not
overlap any unscaled normal-ore input. Every recipe that directly processes a
dense ore applies dense_factor, including reconstruction, nucleosynthesizing,
compressing, dissolution, injecting and purifying.

Example: astral_output = "minecraft:emerald"

Global stage outputs are in config/execore-common.toml under
[astralOreProcessing]. Item and slurry outputs have no gameplay cap after applying
the per-material multiplier. Dense reconstruction uses the exact reconstruction
output x astral_multiplier x dense_factor value. Values beyond Java's integer
range are clamped only to prevent numeric overflow.

The same stage output settings also override Astral Mekanism's native
unique-processing recipes. ExE Core preserves each native recipe's inputs,
chemicals, duration and output item, and changes only its output count or amount.

The integration targets Astral Mekanism's public recipe and registry contracts.
No Astral Mekanism source code is copied into ExE Core.

Source installation information for modders
-------------------------------------------
This code follows the Minecraft Forge installation methodology. It will apply
some small patches to the vanilla MCP source code, giving you and it access 
to some of the data and functions you need to build a successful mod.

Note also that the patches are built against "un-renamed" MCP source code (aka
SRG Names) - this means that you will not be able to read them directly against
normal code.

Setup Process:
==============================

Step 1: Open your command-line and browse to the folder where you extracted the zip file.

Step 2: You're left with a choice.
If you prefer to use Eclipse:
1. Run the following command: `./gradlew genEclipseRuns`
2. Open Eclipse, Import > Existing Gradle Project > Select Folder 
   or run `gradlew eclipse` to generate the project.

If you prefer to use IntelliJ:
1. Open IDEA, and import project.
2. Select your build.gradle file and have it import.
3. Run the following command: `./gradlew genIntellijRuns`
4. Refresh the Gradle Project in IDEA if required.

If at any point you are missing libraries in your IDE, or you've run into problems you can 
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
(this does not affect your code) and then start the process again.

Mapping Names:
=============================
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license, if you do not agree with it you can change your mapping names to other crowdsourced names in your 
build.gradle. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md

Additional Resources: 
=========================
Community Documentation: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
LexManos' Install Video: https://youtu.be/8VEdtQLuLO0
Forge Forums: https://forums.minecraftforge.net/
Forge Discord: https://discord.minecraftforge.net/
