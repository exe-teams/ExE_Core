package io.github.sponeru.execore;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class AstralProcessingConfigReader
{
    private AstralProcessingConfigReader()
    {
    }

    static Config.AstralOreProcessing load(Path configPath, Config.AstralOreProcessing fallback) throws IOException
    {
        if (Files.notExists(configPath))
        {
            return fallback;
        }

        try (CommentedFileConfig config = CommentedFileConfig.builder(configPath).sync().build())
        {
            config.load();
            UnmodifiableConfig values = config.get("astralOreProcessing");

            if (values == null)
            {
                return fallback;
            }

            return new Config.AstralOreProcessing(
                    readBoolean(values, "enabled", fallback.enabled()),
                    readPositiveInt(values, "reconstructionOutput", fallback.reconstructionOutput()),
                    readPositiveInt(values, "nucleosynthesisOutput", fallback.nucleosynthesisOutput()),
                    readPositiveInt(values, "compressionOutput", fallback.compressionOutput()),
                    readPositiveInt(values, "dissolutionSlurryOutput", fallback.dissolutionOutput()),
                    readPositiveInt(values, "washingSlurryOutput", fallback.washingOutput()),
                    readPositiveInt(values, "crystallizingOutput", fallback.crystallizingOutput()),
                    readPositiveInt(values, "injectingOutput", fallback.injectingOutput()),
                    readPositiveInt(values, "purifyingOutput", fallback.purifyingOutput()),
                    readPositiveInt(values, "crushingOutput", fallback.crushingOutput()),
                    readPositiveInt(values, "enrichingOutput", fallback.enrichingOutput()));
        }
        catch (RuntimeException exception)
        {
            throw new IOException("Failed to read Astral processing settings from " + configPath, exception);
        }
    }

    private static int readPositiveInt(UnmodifiableConfig config, String key, int fallback)
    {
        Object value = config.get(key);

        if (value instanceof Number number)
        {
            return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, number.longValue()));
        }

        return fallback;
    }

    private static boolean readBoolean(UnmodifiableConfig config, String key, boolean fallback)
    {
        Object value = config.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }
}
