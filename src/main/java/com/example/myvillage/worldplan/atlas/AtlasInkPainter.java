package com.example.myvillage.worldplan.atlas;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/** Headless Java2D realization of the approved SVG study. Called off the render thread, once per plan. */
public final class AtlasInkPainter {
    public static final int WIDTH=1440, HEIGHT=1024, MAP_X=83, MAP_Y=93, MAP_SIZE=880;
    private static final Color PAPER=new Color(0xeeeadd), INK=new Color(0x343e37), MUTED=new Color(0x7c8274);
    private static final Color RED=new Color(0x9f4e38);
    private final Font brush, book;
    public AtlasInkPainter() {
        brush=load("brush.ttf");book=load("book.ttf");
    }
    private static Font load(String name) {
        try (InputStream in=AtlasInkPainter.class.getResourceAsStream("/assets/myvillage/atlas/"+name)) {
            if (in==null) throw new IllegalStateException("Missing atlas typeface "+name);
            return Font.createFont(Font.TRUETYPE_FONT,in);
        } catch (Exception e) { throw new IllegalStateException("Atlas typeface",e); }
    }
    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);return g;
    }
    private void text(Graphics2D g,String value,float x,float y,float size,boolean calligraphy,Color color) {
        Font f=(calligraphy?brush:book).deriveFont(size);
        if (f.canDisplayUpTo(value)>=0) f=new Font(Font.SERIF,Font.PLAIN,Math.round(size));
        g.setFont(f);g.setColor(color);g.drawString(value,x-g.getFontMetrics().stringWidth(value)/2f,y);
    }
    public BufferedImage paper(boolean chinese) {
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);Graphics2D g=graphics(image);
        Path2D sheet=new Path2D.Double();sheet.moveTo(30,28);sheet.quadTo(161,20,302,25);sheet.lineTo(648,24);
        sheet.quadTo(982,18,1251,26);sheet.lineTo(1403,24);sheet.quadTo(1413,185,1407,398);sheet.lineTo(1412,745);
        sheet.quadTo(1416,879,1406,997);sheet.lineTo(1129,999);sheet.quadTo(875,1004,589,997);sheet.lineTo(28,1001);
        sheet.quadTo(21,801,26,592);sheet.lineTo(24,284);sheet.closePath();g.setClip(sheet);
        g.setPaint(new GradientPaint(0,0,new Color(0xf7f3e8),WIDTH,HEIGHT,new Color(0xe6e2d2)));g.fillRect(0,0,WIDTH,HEIGHT);
        Random random=new Random(17);for(int y=20;y<HEIGHT;y+=3)for(int x=20;x<WIDTH;x+=3){
            g.setColor(new Color(92,82,58,random.nextInt(6)));g.fillRect(x,y,1+random.nextInt(3),1);
        }
        g.setColor(new Color(0x96977e));g.setStroke(new BasicStroke(1));
        for(int[] p:new int[][]{{58,123,58,57,344,57},{59,900,59,969,311,969},{1326,924,1326,969,1150,969},{1072,57,1327,57,1327,126}}){
            Path2D path=new Path2D.Double();path.moveTo(p[0],p[1]);path.lineTo(p[2],p[3]);path.lineTo(p[4],p[5]);g.draw(path);
        }
        if(chinese){String title="山海舆图";for(int i=0;i<title.length();i++)text(g,title.substring(i,i+1),1160,201+i*100,91,true,INK);
            String sub="洲域海陆总览";for(int i=0;i<sub.length();i++)text(g,sub.substring(i,i+1),1254,196+i*29,19,false,MUTED);
        }else{text(g,"Cultivation",1160,190,42,false,INK);text(g,"Atlas",1160,250,64,false,INK);}
        g.setColor(RED);g.fill(new Rectangle2D.Double(1136,548,47,48));
        text(g,"山海",1159,571,18,false,PAPER);text(g,"舆图",1159,591,18,false,PAPER);
        text(g,chinese?"此身所在":"Your location",1160,658,17,false,MUTED);
        text(g,chinese?"归此身":"Locate self",1160,789,24,false,INK);
        text(g,chinese?"览全图":"Overview",1160,843,24,false,INK);
        text(g,chinese?"收卷":"Close",1270,947,20,false,MUTED);
        g.setColor(MUTED);g.setStroke(new BasicStroke(1.7f));g.drawLine(1113,904,1131,904);g.drawLine(1192,904,1210,904);g.drawLine(1201,895,1201,913);
        g.setStroke(new BasicStroke(.9f));g.drawLine(1110,728,1210,728);g.drawLine(111,957,301,957);g.drawLine(111,952,111,961);g.drawLine(301,952,301,961);
        text(g,chinese?"北":"N",119,75,19,false,MUTED);g.fill(new Polygon(new int[]{119,114,119,124},new int[]{87,103,99,103},4));
        text(g,chinese?"洲界":"Regions",480,965,17,false,MUTED);g.setStroke(new BasicStroke(1,0,0,10,new float[]{5,4,1,4},0));g.drawLine(423,958,454,958);
        text(g,chinese?"海域":"Sea",640,965,17,false,MUTED);g.setStroke(new BasicStroke(1));
        g.draw(new CubicCurve2D.Double(568,958,578,948,588,968,598,958));
        text(g,chinese?"拖动移图，滚轮缩放":"Drag to pan · Scroll to zoom",845,984,15,false,MUTED);
        // Only marginal cloud flourishes: no fictional terrain in the geographical field.
        g.setColor(new Color(0xabb09e));g.setStroke(new BasicStroke(1.2f));
        for(int[] at:new int[][]{{102,129},{963,869}}){Path2D c=new Path2D.Double();c.moveTo(at[0],at[1]+27);c.curveTo(at[0]+28,at[1]+12,at[0]+53,at[1]+15,at[0]+66,at[1]+2);c.curveTo(at[0]+76,at[1]-10,at[0]+59,at[1]-20,at[0]+49,at[1]-9);c.curveTo(at[0]+39,at[1]+3,at[0]+57,at[1]+11,at[0]+74,at[1]+5);c.curveTo(at[0]+95,at[1]-3,at[0]+102,at[1]+4,at[0]+114,at[1]+12);c.curveTo(at[0]+130,at[1]+22,at[0]+144,at[1]+18,at[0]+166,at[1]+8);g.draw(c);}
        g.dispose();return image;
    }
    public BufferedImage label(String name) {
        BufferedImage image=new BufferedImage(220,60,BufferedImage.TYPE_INT_ARGB);Graphics2D g=graphics(image);
        text(g,name,110,43,34,true,INK);g.dispose();return image;
    }
    public BufferedImage scaleLabel(int radius,boolean chinese) {
        BufferedImage image=new BufferedImage(220,60,BufferedImage.TYPE_INT_ARGB);Graphics2D g=graphics(image);
        long blocks=2L*radius*190/MAP_SIZE;
        text(g,blocks+(chinese?"格":" blocks"),110,40,17,false,MUTED);g.dispose();return image;
    }
    public BufferedImage map(WorldAtlas atlas) {
        int n=atlas.resolution();byte[] cells=atlas.cells();BufferedImage image=new BufferedImage(MAP_SIZE,MAP_SIZE,BufferedImage.TYPE_INT_ARGB);Graphics2D g=graphics(image);
        g.setPaint(new GradientPaint(0,0,new Color(0xd2ddd1),MAP_SIZE,MAP_SIZE,new Color(0xafc5bb)));g.fillRect(0,0,MAP_SIZE,MAP_SIZE);
        g.setColor(new Color(0x91b2a5));g.setStroke(new BasicStroke(.65f));
        for(int y=20;y<MAP_SIZE;y+=64)for(int x=0;x<MAP_SIZE;x+=95)g.draw(new CubicCurve2D.Double(x+9,y+18,x+24,y+10,x+39,y+26,x+69,y+18));
        boolean[] land=new boolean[n*n];for(int i=0;i<cells.length;i++)land[i]=(cells[i]&128)==0;
        Path2D coast=contours(land,n);AffineTransform transform=AffineTransform.getScaleInstance(MAP_SIZE/(double)n,MAP_SIZE/(double)n);
        Shape shape=transform.createTransformedShape(coast);g.setColor(new Color(0xe8e7d8));g.fill(shape);
        g.setClip(shape);g.setPaint(new RadialGradientPaint(430,250,700,new float[]{0,1},new Color[]{new Color(108,130,111,22),new Color(210,211,188,0)}));
        g.fillRect(0,0,MAP_SIZE,MAP_SIZE);g.setClip(null);
        g.setColor(new Color(0x708579));g.setStroke(new BasicStroke(.9f));g.draw(shape);
        double unit=MAP_SIZE/(double)n;g.setColor(new Color(113,130,115,155));
        g.setStroke(new BasicStroke(.9f,0,0,10,new float[]{4,3,1,3},0));
        // Deliberately broken thin ink lines, without a categorical province fill.
        Path2D borders=new Path2D.Double();
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){
            int at=z*n+x,a=cells[at]&127;if(a==0||(x+z)%7>3)continue;
            if(x<n-1){int b=cells[at+1]&127;if(b!=0&&a!=b){borders.moveTo((x+1)*unit,z*unit);borders.lineTo((x+1)*unit,(z+1)*unit);}}
            if(z<n-1){int b=cells[at+n]&127;if(b!=0&&a!=b){borders.moveTo(x*unit,(z+1)*unit);borders.lineTo((x+1)*unit,(z+1)*unit);}}
        }g.draw(borders);
        g.setColor(new Color(124,130,116,82));g.setStroke(new BasicStroke(1,0,0,10,new float[]{2,10},0));
        double r=atlas.coreRadius()/(2.0*atlas.radius())*MAP_SIZE;g.draw(new Ellipse2D.Double(MAP_SIZE/2.0-r,MAP_SIZE/2.0-r,2*r,2*r));
        for(var region:atlas.regions()) {
            float x=(float)((region.x()+atlas.radius())/(2*atlas.radius())*MAP_SIZE),y=(float)((region.z()+atlas.radius())/(2*atlas.radius())*MAP_SIZE);
            if(region.id().equals("donghai"))x+=66;
            Font f=brush.deriveFont(50f);if(f.canDisplayUpTo(region.name())>=0)f=new Font(Font.SERIF,Font.PLAIN,45);
            var outline=f.createGlyphVector(g.getFontRenderContext(),region.name()).getOutline();double w=outline.getBounds2D().getWidth();
            Shape letters=AffineTransform.getTranslateInstance(x-w/2,y).createTransformedShape(outline);
            g.setColor(new Color(0xe8e7d8));g.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(letters);g.setColor(INK);g.fill(letters);
        }
        g.setComposite(AlphaComposite.DstIn);
        float center=MAP_SIZE/2f;g.setPaint(new RadialGradientPaint(center,center,center*1.1f,new float[]{0,.73f,.9f,1},new Color[]{new Color(255,255,255,255),new Color(255,255,255,255),new Color(255,255,255,180),new Color(255,255,255,0)}));g.fillRect(0,0,MAP_SIZE,MAP_SIZE);
        g.dispose();return image;
    }
    /** Closed edge loops with quadratic smoothing below one sampled cell. */
    private static Path2D contours(boolean[] mask,int n) {
        Map<Integer,ArrayDeque<Integer>> edges=new LinkedHashMap<>();
        for(int y=0;y<n;y++)for(int x=0;x<n;x++)if(mask[y*n+x]){
            int a=y*(n+1)+x,b=a+1,c=b+n+1,d=a+n+1;
            if(y==0||!mask[(y-1)*n+x])edge(edges,a,b);
            if(x==n-1||!mask[y*n+x+1])edge(edges,b,c);
            if(y==n-1||!mask[(y+1)*n+x])edge(edges,c,d);
            if(x==0||!mask[y*n+x-1])edge(edges,d,a);
        }
        Path2D out=new Path2D.Double(Path2D.WIND_EVEN_ODD);
        while(!edges.isEmpty()){
            int start=edges.keySet().iterator().next(),at=start;List<Point2D.Double> p=new ArrayList<>();
            do{p.add(new Point2D.Double(at%(n+1),at/(n+1)));var choices=edges.get(at);int next=choices.removeLast();if(choices.isEmpty())edges.remove(at);at=next;}while(at!=start&&edges.containsKey(at));
            double area=0;for(int i=0;i<p.size();i++){var a=p.get(i);var b=p.get((i+1)%p.size());area+=a.x*b.y-b.x*a.y;}if(Math.abs(area)<24)continue;
            var first=p.getFirst();var last=p.getLast();out.moveTo((last.x+first.x)/2,(last.y+first.y)/2);
            for(int i=0;i<p.size();i++){var a=p.get(i);var b=p.get((i+1)%p.size());out.quadTo(a.x,a.y,(a.x+b.x)/2,(a.y+b.y)/2);}out.closePath();
        }return out;
    }
    private static void edge(Map<Integer,ArrayDeque<Integer>> edges,int from,int to){edges.computeIfAbsent(from,k->new ArrayDeque<>()).add(to);}
}
