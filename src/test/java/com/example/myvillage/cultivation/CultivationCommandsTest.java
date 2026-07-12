package com.example.myvillage.cultivation;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CultivationCommandsTest {
    private static final Map<String, String> PINYIN_ALIASES = Map.ofEntries(
            Map.entry("info", "chakan"),
            Map.entry("reset", "chongzhi"),
            Map.entry("setrealm", "shezhijingjie"),
            Map.entry("setprogress", "shezhixiuwei"),
            Map.entry("setstability", "shezhiwendingdu"),
            Map.entry("setpower", "shezhilingli"),
            Map.entry("setroot", "shezhilinggen"),
            Map.entry("clearroot", "qingchulinggen"),
            Map.entry("learn", "xuexi"),
            Map.entry("forget", "yiwang"),
            Map.entry("setmastery", "shezhishuliandu"));

    @Test
    void englishAndPinyinRootsExposeEquivalentAliasTrees() {
        CommandNode<CommandSourceStack> englishRoot = CultivationCommands.command().build();
        CommandNode<CommandSourceStack> pinyinRoot = CultivationCommands.pinyinCommand().build();

        assertEquals("cultivation", englishRoot.getName());
        assertEquals("xiulian", pinyinRoot.getName());
        assertEquals(expectedLiterals(), childNames(englishRoot));
        assertEquals(expectedLiterals(), childNames(pinyinRoot));
        assertEquals(shape(englishRoot), shape(pinyinRoot));
    }

    @Test
    void everyPinyinSubcommandMatchesItsEnglishCommandShape() {
        for (CommandNode<CommandSourceStack> root : List.of(
                CultivationCommands.command().build(),
                CultivationCommands.pinyinCommand().build())) {
            PINYIN_ALIASES.forEach((english, pinyin) ->
                    assertEquals(shape(root.getChild(english)), shape(root.getChild(pinyin)),
                            () -> english + " and " + pinyin + " must parse identical arguments"));
        }
    }

    private static Set<String> expectedLiterals() {
        return PINYIN_ALIASES.entrySet().stream()
                .flatMap(entry -> java.util.stream.Stream.of(entry.getKey(), entry.getValue()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> childNames(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String shape(CommandNode<CommandSourceStack> node) {
        String children = node.getChildren().stream()
                .map(child -> child.getName() + ":" + shape(child))
                .sorted()
                .collect(Collectors.joining(","));
        return node.getClass().getSimpleName()
                + "(exec=" + (node.getCommand() != null)
                + ",redirect=" + (node.getRedirect() != null)
                + ",children=[" + children + "])";
    }
}
