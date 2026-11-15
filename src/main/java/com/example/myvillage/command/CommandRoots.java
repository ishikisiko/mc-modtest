package com.example.myvillage.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import java.util.function.Predicate;

/** 共享根节点合并时，将权限留在各模块的一级子命令上。 */
public final class CommandRoots {
    private CommandRoots() {}
    public static <S> LiteralArgumentBuilder<S> root(String name) { return LiteralArgumentBuilder.literal(name); }
    public static <S> LiteralArgumentBuilder<S> guardedRoot(String name,Predicate<S> guard) { return new GuardedRoot<>(name,guard); }
    public static <S> LiteralArgumentBuilder<S> shortcut(String name,CommandNode<S> target) {
        var builder=LiteralArgumentBuilder.<S>literal(name).requires(target.getRequirement()).redirect(target);
        if(target.getCommand()!=null)builder.executes(target.getCommand());
        return builder;
    }
    private static final class GuardedRoot<S> extends LiteralArgumentBuilder<S> {
        private final Predicate<S> guard;
        private GuardedRoot(String name,Predicate<S> guard) { super(name);this.guard=guard; }
        @Override public LiteralArgumentBuilder<S> then(ArgumentBuilder<S,?> child) {
            var own=child.getRequirement();child.requires(source->guard.test(source)&&own.test(source));
            return super.then(child);
        }
        @Override public LiteralArgumentBuilder<S> then(CommandNode<S> child) {
            var copy=child.createBuilder();for(var nested:child.getChildren())copy.then(nested);
            return then(copy);
        }
    }
}
