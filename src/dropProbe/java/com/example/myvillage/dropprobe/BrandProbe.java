package com.example.myvillage.dropprobe;

import com.example.myvillage.ModBrand;
import com.example.myvillage.drops.DropRuntime;
import com.example.myvillage.resources.BuiltinResources;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 独立服务器中的命名、真实命令执行与权限检查，不进入交付模组。 */
final class BrandProbe {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static JsonArray checks,executions;
    private record Result(int result,List<String> lines,String error) {}
    private interface Checked { void run()throws Exception; }
    static void register(){NeoForge.EVENT_BUS.addListener(BrandProbe::commands);}
    private static void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("brand_probe").requires(s->s.hasPermission(4)).executes(c->run(c.getSource())));}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void check(String id,Checked action){
        var row=new JsonObject();row.addProperty("id",id);
        try{action.run();row.addProperty("passed",true);}catch(Throwable error){row.addProperty("passed",false);row.addProperty("error",error.toString());}
        checks.add(row);
    }
    private static CommandSourceStack stack(MinecraftServer server,int permission,List<String> lines){
        CommandSource capture=new CommandSource(){
            public void sendSystemMessage(Component message){lines.add(message.getString());}
            public boolean acceptsSuccess(){return true;}
            public boolean acceptsFailure(){return true;}
            public boolean shouldInformAdmins(){return false;}
        };
        return FakePlayerFactory.getMinecraft(server.overworld()).createCommandSourceStack().withPermission(permission).withSource(capture);
    }
    private static Result execute(MinecraftServer server,String command,int permission){
        var lines=new ArrayList<String>();int result=-1;String error="";
        try{result=server.getCommands().getDispatcher().execute(command,stack(server,permission,lines));}catch(Exception failure){error=failure.toString();}
        var value=new Result(result,List.copyOf(lines),error);var row=new JsonObject();row.addProperty("command",command);row.addProperty("permission",permission);row.add("result",JSON.toJsonTree(value));executions.add(row);return value;
    }
    private static int run(CommandSourceStack source){
        var server=source.getServer();var dispatcher=server.getCommands().getDispatcher();checks=new JsonArray();executions=new JsonArray();
        check("metadata",()->require(ModList.get().getModContainerById("yuanxian").orElseThrow().getModInfo().getDisplayName().equals("源仙"),"模组显示名错误"));
        check("namespace",()->{require(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse("yuanxian:demon_wolf")),"新实体ID缺失");require(!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse("myvillage:demon_wolf")),"旧实体ID仍被注册");});
        for(String locale:List.of("zh_cn","en_us"))check("language_"+locale,()->{
            try(var input=BuiltinResources.open("assets/yuanxian/lang/"+locale+".json")){
                var data=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                require(data.get("itemGroup.yuanxian.main").getAsString().equals("源仙"),"主创造栏名称错误");require(data.get("key.categories.yuanxian").getAsString().equals("源仙"),"按键分类名称错误");
            }
        });
        check("shared_tree",()->require(dispatcher.getRoot().getChild(ModBrand.COMMAND_SHORT).getRedirect()==dispatcher.getRoot().getChild(ModBrand.COMMAND_ROOT),"快捷入口未引用同一棵树"));
        check("old_root_absent",()->require(dispatcher.getRoot().getChild("myvillage")==null,"旧根入口仍被注册"));
        check("help_default_and_localized_alias",()->{
            var full=execute(server,"yuanxian",0);var shortName=execute(server,"yx",0);var chinese=execute(server,"yx 帮助",0);
            require(full.result()==1&&shortName.result()==1&&chinese.result()==1,"零参数或中文帮助无法执行");require(full.lines().equals(shortName.lines())&&full.lines().equals(chinese.lines()),"帮助输出不一致");require(String.join("",full.lines()).contains("源仙指令"),"帮助名称错误");
        });
        check("help_permissions",()->{var value=execute(server,"yx",0);String text=String.join("",value.lines());require(text.contains("aptitude")&&!text.contains("drops")&&!text.contains("worldplan"),"帮助未按权限过滤");});
        for(int permission:new int[]{0,1})check("admin_denied_level_"+permission,()->{
            var before=DropRuntime.snapshot(server);
            for(String name:List.of("yuanxian","yx"))require(execute(server,name+" drops reload",permission).result()!=1,"非管理员重载成功");
            require(before==DropRuntime.snapshot(server),"被拒绝的命令改变了快照");
        });
        check("admin_execution_on_both_entries",()->{
            for(String name:List.of("yuanxian","yx")){
                require(execute(server,name+" drops status",2).result()==1,"管理员状态查询失败");long before=DropRuntime.snapshot(server).revision();
                require(execute(server,name+" drops reload",2).result()==1,"管理员重载失败");require(DropRuntime.snapshot(server).revision()==before+1,"重载没有执行一次");
            }
        });
        check("admin_branches",()->{
            var root=dispatcher.getRoot().getChild("yuanxian");var low=stack(server,0,new ArrayList<>());var admin=stack(server,2,new ArrayList<>());
            for(String name:List.of("place","town","manor","worldplan","roads","wilderness","qianji","quest","habitat","world","drops")){
                var child=root.getChild(name);require(child!=null&&!child.canUse(low)&&child.canUse(admin),"管理员分支权限错误："+name);
            }
        });
        check("public_command_execution",()->{for(String name:List.of("yuanxian","yx"))require(execute(server,name+" aptitude",0).result()==1,"普通玩家资质查询失败");});
        check("parameter_roundtrip",()->{
            var full=execute(server,"yuanxian drops show minecraft:zombie normal donghuang yuanxian:qi_refining 8",2);var shortName=execute(server,"yx drops show minecraft:zombie normal donghuang yuanxian:qi_refining 8",2);
            require(full.result()==1&&shortName.result()==1&&full.lines().equals(shortName.lines()),"快捷入口参数或查询结果不同");
        });
        check("completion_parity",()->{
            for(int level:new int[]{0,2})for(String suffix:List.of(" "," drops "," drops show ")){
                var s=stack(server,level,new ArrayList<>());
                var full=dispatcher.getCompletionSuggestions(dispatcher.parse("yuanxian"+suffix,s)).get().getList().stream().map(v->v.getText()).toList();
                var shortName=dispatcher.getCompletionSuggestions(dispatcher.parse("yx"+suffix,s)).get().getList().stream().map(v->v.getText()).toList();require(full.equals(shortName),"补全候选不同");
            }
        });
        var report=new JsonObject();report.addProperty("display_name",ModBrand.DISPLAY_NAME);report.addProperty("root",ModBrand.COMMAND_ROOT);report.addProperty("shortcut",ModBrand.COMMAND_SHORT);report.add("checks",checks);report.add("executions",executions);
        boolean passed=checks.asList().stream().allMatch(v->v.getAsJsonObject().get("passed").getAsBoolean());report.addProperty("passed",passed);
        try{Files.writeString(FMLPaths.GAMEDIR.get().resolve("brand-probe.json"),JSON.toJson(report)+"\n");}catch(Exception error){throw new IllegalStateException(error);}
        var summary=new JsonObject();summary.addProperty("passed",passed);summary.add("checks",checks);source.sendSuccess(()->Component.literal("BRAND_PROBE "+summary),false);return passed?1:0;
    }
}
