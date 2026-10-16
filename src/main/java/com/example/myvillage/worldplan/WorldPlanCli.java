package com.example.myvillage.worldplan;

import java.nio.file.Files;
import java.nio.file.Path;

/** Plain JDK/Gson entry point; exports without Gradle, Minecraft, a server or chunk generation. */
public final class WorldPlanCli {
    private WorldPlanCli() {}
    public static void main(String[] args)throws Exception {
        if(args.length<3||args.length>5)throw new IllegalArgumentException("seed frozen_rules.json output_directory [radius] [resolution]");
        long seed=Long.parseLong(args[0]);WorldPlanRules rules=new WorldPlanRules(Files.readString(Path.of(args[1])));
        WorldPlan plan=new WorldPlan(seed,rules);
        int radius=args.length>=4?Integer.parseInt(args[3]):rules.coreRadius+2*rules.coreBlend;
        int resolution=args.length>=5?Integer.parseInt(args[4]):256;
        WorldPlanExporter.export(plan,Path.of(args[2]),resolution,radius);
        System.out.println("seed="+seed+" plan="+plan.fingerprint()+" city="+plan.city()+" spawn="+plan.spawn());
    }
}
