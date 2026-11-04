package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import java.util.*;
import static com.example.myvillage.worldplan.roads.RoadAStar.Point;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** 每座城独立、确定的公共接坡。所有官道复用同一通行面，只在外端连接。 */
record CityRoadAccess(WorldRoadPlanner.Exit exit,List<Point> centerline,List<Cell> cells,int expansions,int replans) {
    CityRoadAccess {centerline=List.copyOf(centerline);cells=List.copyOf(cells);}
    Point hub(){return centerline.getLast();}
    int feet2(){var p=hub();return cells.stream().filter(c->c.x()==p.x()&&c.z()==p.z()).findFirst().orElseThrow().feet2();}
    Set<Long> protectedBody(int width) {
        Set<Long> body=new HashSet<>();var hub=hub();
        for(var c:cells)for(int[] d:new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}){int x=c.x()+d[0],z=c.z()+d[1];
            if(Math.max(Math.abs(x-hub.x()),Math.abs(z-hub.z()))>width)body.add(Cell.key(x,z));
        }
        return Set.copyOf(body);
    }
    static CityRoadAccess plan(WorldPlan world,RoadRules rules,WorldRoadPlanner.Exit exit) {
        return plan(world,rules,exit,List.of());
    }
    static CityRoadAccess plan(WorldPlan world,RoadRules rules,WorldRoadPlanner.Exit exit,List<WorldRoadPlanner.Box> obstacles) {
        var start=exit.frame().outside(rules.width/2);
        var fixed=RoadNetworkGeometry.index(exit.existing());
        var budget=new RoadReplanner.Budget(rules.maxExpansions,rules.gradeReplans);
        List<String> failures=new ArrayList<>();
        for(int offset=0;offset<=rules.searchMargin;offset+=rules.gridStep) {
            var end=exit.frame().outside(exit.frame().escapeDistance()+offset);
            int endFeet=Math.max(world.rules().seaLevel+1,world.surfaceY(end.x(),end.z()))*2+rules.style.carriageLift2();
            var field=new RoadTerrainField(world,rules,rules.width,fixed,Map.of(Cell.key(start.x(),start.z()),exit.feet2(),Cell.key(end.x(),end.z()),endFeet)) {
                protected boolean permitsColumn(int x,int z,int ground){return WorldRoadPlanner.avoids(obstacles,rules.obstacleMargin,x,z)&&WorldRoadPlanner.columnAllowed(world,rules,exit.region(),exit.region(),x,z,ground);}
                protected boolean inSearchArea(int x,int z){return x>=Math.min(start.x(),end.x())-rules.searchMargin&&x<=Math.max(start.x(),end.x())+rules.searchMargin&&z>=Math.min(start.z(),end.z())-rules.searchMargin&&z<=Math.max(start.z(),end.z())+rules.searchMargin;}
            };
            if(!field.allowed(start.x(),start.z())||field.minimumFeet2(start.x(),start.z())>field.maximumFeet2(start.x(),start.z()))
                throw new IllegalStateException("省城出门处的完整路幅或固定地坪不可行："+exit.region()+" "+start);
            var anchors=new ArrayList<>(exit.existing());anchors.add(new Cell(end.x(),end.z(),endFeet,false));
            try {
                var geometry=RoadReplanner.findVia("公共接坡 "+exit.region(),List.of(start,end),field,rules.gridStep,budget,rules.cost,
                    path->RoadGeometry.build(path,rules.width,rules.smoothRadius,exit.feet2(),endFeet,field::height,field::column,rules.maxEarthwork,rules.maxColumns,anchors,rules.style.carriageLift2()));
                return new CityRoadAccess(exit,geometry.centerline(),geometry.cells(),rules.maxExpansions-budget.remainingExpansions(),budget.replans());
            }catch(java.util.concurrent.CancellationException e){throw e;}
            catch(IllegalArgumentException|IllegalStateException e){failures.add(end+"："+e.getMessage());if(budget.remainingExpansions()==0)break;}
        }
        throw new IllegalStateException("省城公共接坡不可行："+exit.region()+"；"+String.join("；",failures));
    }
}
