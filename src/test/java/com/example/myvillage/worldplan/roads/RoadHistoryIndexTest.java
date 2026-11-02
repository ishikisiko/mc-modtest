package com.example.myvillage.worldplan.roads;

import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadHistoryIndexTest {
    @Test void spatialSkipsPreserveTheExactLinearCollisionPredicate()throws Exception {
        Class<?> node=Arrays.stream(RoadAStar.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Node")).findFirst().orElseThrow();
        Constructor<?> constructor=node.getDeclaredConstructor(RoadAStar.Point.class,node,double.class,double.class,long.class,int.class,int.class);constructor.setAccessible(true);
        Method indexed=RoadAStar.class.getDeclaredMethod("simple",node,RoadAStar.Point.class,int.class);indexed.setAccessible(true);
        var random=new Random(20261102);
        for(int sample=0;sample<600;sample++){
            int n=1+random.nextInt(130),width=List.of(1,3,5).get(sample%3);var path=new ArrayList<RoadAStar.Point>();Object tail=null;int x=-50,z=-50;
            for(int i=0;i<n;i++){x+=random.nextInt(9)-4;z+=random.nextInt(9)-4;var p=new RoadAStar.Point(x,z);path.add(p);tail=constructor.newInstance(p,tail,0.,0.,(long)i,0,999);}
            var to=sample%4==0?path.get(random.nextInt(path.size())):new RoadAStar.Point(x+random.nextInt(17)-8,z+random.nextInt(17)-8);
            assertEquals(linear(path,to,width),(boolean)indexed.invoke(null,tail,to,width),"sample="+sample+" width="+width+" path="+path+" to="+to);
        }
    }
    private static boolean linear(List<RoadAStar.Point> path,RoadAStar.Point to,int width){
        if(path.size()<2)return true;var from=path.getLast();int behind=distance(from,path.get(path.size()-2));
        for(int k=path.size()-2;k>=0;k--){var a=path.get(k);int along=behind;if(k>0)behind+=distance(a,path.get(k-1));if(a.equals(to))return false;if(width<=1||k==0)continue;var b=path.get(k-1);int first=distance(from,to),second=distance(a,b);
            for(int i=0;i<=first;i++){double t=first==0?0:i/(double)first;int x=(int)Math.round(from.x()+(to.x()-from.x())*t),z=(int)Math.round(from.z()+(to.z()-from.z())*t);
                for(int j=0;j<=second;j++){if(along+i+j<=width*2)continue;double u=second==0?0:j/(double)second;int ox=(int)Math.round(a.x()+(b.x()-a.x())*u),oz=(int)Math.round(a.z()+(b.z()-a.z())*u);if(Math.max(Math.abs(x-ox),Math.abs(z-oz))<=width)return false;}
            }
        }return true;
    }
    private static int distance(RoadAStar.Point a,RoadAStar.Point b){return Math.max(Math.abs(a.x()-b.x()),Math.abs(a.z()-b.z()));}
}
