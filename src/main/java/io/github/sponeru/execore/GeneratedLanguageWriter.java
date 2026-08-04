package io.github.sponeru.execore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class GeneratedLanguageWriter
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GeneratedLanguageWriter()
    {
    }

    static void write(Path root, String language, Map<String, String> entries) throws IOException
    {
        if (entries.isEmpty())
        {
            return;
        }

        Path langRoot = root.resolve("assets").resolve(ExampleMod.MODID).resolve("lang");
        Path languageFile = langRoot.resolve(language + ".json");
        Files.createDirectories(langRoot);
        JsonObject json = new JsonObject();

        if (Files.isRegularFile(languageFile))
        {
            try (var reader = Files.newBufferedReader(languageFile, StandardCharsets.UTF_8))
            {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }

        entries.forEach(json::addProperty);
        Files.writeString(languageFile, GSON.toJson(json) + '\n', StandardCharsets.UTF_8);
    }
}
