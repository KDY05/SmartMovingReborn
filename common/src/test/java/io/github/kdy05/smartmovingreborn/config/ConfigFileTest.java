package io.github.kdy05.smartmovingreborn.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFileTest {
    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve(SmartMovingClientConfig.FILE_NAME);
    }

    private String read() throws IOException {
        return Files.readString(file(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private void write(String content) throws IOException {
        Files.writeString(file(), content, StandardCharsets.UTF_8);
    }

    @Test
    void missingFileIsCreatedWithDefaultsAndComments() throws IOException {
        SmartMovingClientConfig config = new SmartMovingClientConfig();
        ConfigFile.LoadResult result = config.load(file());

        assertTrue(result.needsSave());
        assertTrue(result.warnings().isEmpty(), result.warnings()::toString);
        String text = read();
        assertTrue(text.startsWith("# Smart Moving Reborn client configuration\n"));
        assertTrue(text.contains("\n# Crawling\n"));
        assertTrue(text.contains("\n# To switch on/off crawling\nmove.crawl=true\n"));
        assertTrue(text.contains("\n# Speed factor while crawling (>= 0, <= 1, relative to default"));
        assertTrue(text.contains(" speed)\nmove.crawl.factor=0.15\n"));
        assertTrue(text.lines().allMatch(line -> line.length() <= 72 || !line.startsWith("#")),
                "comment lines wrap at 72 columns");
        for (Property<?> property : config.properties()) {
            assertTrue(text.contains("\n" + property.key() + "="), property.key());
        }
    }

    @Test
    void reloadKeepsEditedValuesAndLeavesFileUntouched() throws IOException {
        new SmartMovingClientConfig().load(file());
        String edited = read().replace("move.crawl.factor=0.15", "move.crawl.factor=0.25")
                .replace("move.slide=true", "move.slide=FALSE");
        write(edited);

        SmartMovingClientConfig config = new SmartMovingClientConfig();
        ConfigFile.LoadResult result = config.load(file());

        assertFalse(result.needsSave());
        assertTrue(result.warnings().isEmpty(), result.warnings()::toString);
        assertEquals(0.25f, config.crawlFactor.get());
        assertFalse(config.slide.get());
        assertEquals(edited, read());
    }

    @Test
    void unknownKeysAndMalformedLinesAreIgnoredWithWarnings() throws IOException {
        new SmartMovingClientConfig().load(file());
        String edited = read() + "move.speed.user=true\nthis line has no separator\n";
        write(edited);

        ConfigFile.LoadResult result = new SmartMovingClientConfig().load(file());

        assertFalse(result.needsSave());
        assertEquals(2, result.warnings().size(), result.warnings()::toString);
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("unknown key 'move.speed.user'")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("no '=' found")));
        assertEquals(edited, read());
    }

    @Test
    void invalidValueFallsBackToDefaultAndIsRewritten() throws IOException {
        write("move.crawl=maybe\nmove.crawl.factor=fast\nmove.climb.base=Smart\n");

        SmartMovingClientConfig config = new SmartMovingClientConfig();
        ConfigFile.LoadResult result = config.load(file());

        assertTrue(result.needsSave());
        assertEquals(2, result.warnings().size(), result.warnings()::toString);
        assertTrue(config.crawl.get());
        assertEquals(0.15f, config.crawlFactor.get());
        assertEquals(SmartMovingConfig.CLIMB_SMART, config.climbBase.get());
        String text = read();
        assertTrue(text.contains("\nmove.crawl=true\n"));
        assertTrue(text.contains("\nmove.crawl.factor=0.15\n"));
        assertTrue(text.contains("\nmove.climb.base=smart\n"));
    }

    @Test
    void nonFiniteNumbersAreInvalid() throws IOException {
        write("move.crawl.factor=NaN\nmove.sneak.factor=Infinity\n");

        SmartMovingClientConfig config = new SmartMovingClientConfig();
        ConfigFile.LoadResult result = config.load(file());

        assertEquals(2, result.warnings().size(), result.warnings()::toString);
        assertEquals(0.15f, config.crawlFactor.get());
        assertEquals(0.3f, config.sneakFactor.get());
    }

    @Test
    void listCommasInsideBracketsDoNotSplit() {
        StringListProperty property = new StringListProperty("test");
        assertEquals(List.of("minecraft:iron_bars", "#minecraft:trapdoors[half=bottom,open=false]", "a"),
                property.parse(" minecraft:iron_bars ,#minecraft:trapdoors[half=bottom,open=false],, a"));
    }

    @Test
    void defaultListSurvivesRoundTrip() throws IOException {
        SmartMovingClientConfig first = new SmartMovingClientConfig();
        first.load(file());
        SmartMovingClientConfig second = new SmartMovingClientConfig();
        second.load(file());
        assertEquals(first.ceilingClimbBlocks.get(), second.ceilingClimbBlocks.get());
        assertEquals(2, second.ceilingClimbBlocks.get().size());
    }
}
