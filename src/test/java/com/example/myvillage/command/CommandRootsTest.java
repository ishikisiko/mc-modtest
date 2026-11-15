package com.example.myvillage.command;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

class CommandRootsTest {
    private static LiteralArgumentBuilder<Integer> literal(String name) { return LiteralArgumentBuilder.literal(name); }
    private static CommandDispatcher<Integer> tree(boolean adminFirst) {
        var d=new CommandDispatcher<Integer>();
        var admin=CommandRoots.<Integer>guardedRoot("yuanxian",p->p>=2).then(literal("admin").executes(c->20)
                .then(literal("strict").requires(p->p>=4).executes(c->40)));
        var player=CommandRoots.<Integer>root("yuanxian").executes(c->1).then(literal("player").executes(c->10));
        if(adminFirst){d.register(admin);d.register(player);}else{d.register(player);d.register(admin);}
        d.register(CommandRoots.shortcut("yx",d.getRoot().getChild("yuanxian")));return d;
    }
    @Test void sharedRootPreservesPlayerAccessAndAdminGuardInEitherOrder()throws Exception {
        for(boolean first:new boolean[]{true,false})for(String prefix:new String[]{"yuanxian","yx"}){
            var d=tree(first);assertEquals(10,d.execute(prefix+" player",0));assertEquals(20,d.execute(prefix+" admin",2));
            assertThrows(CommandSyntaxException.class,()->d.execute(prefix+" admin",0));
            assertThrows(CommandSyntaxException.class,()->d.execute(prefix+" admin strict",2));assertEquals(40,d.execute(prefix+" admin strict",4));
        }
    }
    @Test void shortcutExecutesRootAndRedirectsToTheSameMutableTree()throws Exception {
        var d=tree(true);assertEquals(1,d.execute("yuanxian",0));assertEquals(1,d.execute("yx",0));
        assertSame(d.getRoot().getChild("yuanxian"),d.getRoot().getChild("yx").getRedirect());
        d.register(CommandRoots.<Integer>root("yuanxian").then(literal("late").executes(c->99)));
        assertEquals(99,d.execute("yx late",0));assertNull(d.getRoot().getChild("myvillage"));
    }
    @Test void guardedBuiltNodesRetainArgumentsCommandsAndOwnRequirements()throws Exception {
        var child=literal("value").requires(p->p>=3)
                .then(RequiredArgumentBuilder.<Integer,Integer>argument("n",IntegerArgumentType.integer()).executes(c->IntegerArgumentType.getInteger(c,"n"))).build();
        var d=new CommandDispatcher<Integer>();d.register(CommandRoots.<Integer>guardedRoot("yuanxian",p->p>=2).then(child));
        assertThrows(CommandSyntaxException.class,()->d.execute("yuanxian value 7",2));assertEquals(7,d.execute("yuanxian value 7",3));
    }
    @Test void shortcutCompletionMatchesCanonicalForEachPermissionLevel()throws Exception {
        var d=tree(false);
        // Brigadier's server completion API does not filter requirements; Minecraft filters its client tree separately.
        for(int level:new int[]{0,2,4})for(String suffix:new String[]{" "," a"," admin "}){
            var canonical=d.getCompletionSuggestions(d.parse("yuanxian"+suffix,level)).get().getList().stream().map(s->s.getText()).toList();
            var shortcut=d.getCompletionSuggestions(d.parse("yx"+suffix,level)).get().getList().stream().map(s->s.getText()).toList();
            assertEquals(canonical,shortcut);
            if(suffix.equals(" "))assertTrue(shortcut.contains("player"));
        }
    }
    @Test void shortcutRetainsExplicitTargetRootRestriction()throws Exception {
        var d=new CommandDispatcher<Integer>();var node=d.register(literal("restricted").requires(p->p>=3).executes(c->3));
        d.register(CommandRoots.shortcut("short",node));assertThrows(CommandSyntaxException.class,()->d.execute("short",0));assertEquals(3,d.execute("short",3));
    }
}
