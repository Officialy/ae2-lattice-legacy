package dev.ae2lattice.client;

import dev.ae2lattice.net.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.util.*;

public final class GraphRenderer {
    private static float zoom=1,panX=12,panY=60;
    private static boolean dragging;
    private static int lastX,lastY;
    private static final int W=202,H=88,DX=236,DY=104;
    private static final RenderItem ITEMS=new RenderItem();
    private GraphRenderer(){}
    public static void reset(){zoom=1;panX=12;panY=60;dragging=false;}
    public static void mouse(){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.currentScreen==null)return;
        int x=Mouse.getEventX()*mc.currentScreen.width/mc.displayWidth;
        int y=mc.currentScreen.height-Mouse.getEventY()*mc.currentScreen.height/mc.displayHeight-1;
        int wheel=Mouse.getEventDWheel();
        if(wheel!=0){
            float next=Math.max(.30f,Math.min(2.5f,zoom*(wheel>0?1.12f:1/1.12f)));
            panX=x-(x-panX)*next/zoom;panY=y-(y-panY)*next/zoom;zoom=next;
        }
        if(Mouse.getEventButton()==0){
            dragging=Mouse.getEventButtonState();lastX=x;lastY=y;
        } else if(dragging && Mouse.isButtonDown(0)){
            panX+=x-lastX;panY+=y-lastY;lastX=x;lastY=y;
        } else dragging=false;
    }
    private static int[][] layout(List<Snapshot.Node> nodes){
        int n=nodes.size();
        int[] depth=new int[n],degree=new int[n];
        List<List<Integer>> consumers=new ArrayList<>();
        for(int i=0;i<n;i++)consumers.add(new ArrayList<>());
        for(int i=0;i<n;i++)for(int p:nodes.get(i).dependencies){
            if(p>=0 && p<n && p!=i){degree[i]++;consumers.get(p).add(i);}
        }
        ArrayDeque<Integer> queue=new ArrayDeque<>();
        for(int i=0;i<n;i++)if(degree[i]==0)queue.add(i);
        while(!queue.isEmpty()){
            int i=queue.remove();
            for(int c:consumers.get(i)){
                depth[c]=Math.max(depth[c],depth[i]+1);
                if(--degree[c]==0)queue.add(c);
            }
        }
        // Cycles are possible in inferred pattern dependencies. Keep every node visible.
        int max=0;for(int d:depth)max=Math.max(max,d);
        for(int i=0;i<n;i++)if(degree[i]>0)depth[i]=max+1;
        Map<Integer,Integer> rows=new HashMap<>();
        int[][] pos=new int[n][2];
        for(int i=0;i<n;i++){
            int row=rows.getOrDefault(depth[i],0);rows.put(depth[i],row+1);
            pos[i][0]=depth[i]*DX;pos[i][1]=row*DY;
        }
        return pos;
    }
    private static void text(FontRenderer f,String s,int x,int y,int color,int width){
        f.drawString(f.trimStringToWidth(s,width),x,y,color);
    }
    public static void draw(int w,int h,int mx,int my,Snapshot data){
        Minecraft mc=Minecraft.getMinecraft();FontRenderer f=mc.fontRenderer;
        Gui.drawRect(0,0,w,h,0xFF111923);
        if(data!=null){
            List<Snapshot.Node> nodes=data.nodes;
            int[][] pos=layout(nodes);
            GL11.glPushMatrix();
            GL11.glTranslatef(panX,panY,0);GL11.glScalef(zoom,zoom,1);
            for(int i=0;i<nodes.size();i++)for(int p:nodes.get(i).dependencies){
                if(p<0 || p>=nodes.size())continue;
                int ax=pos[p][0]+W,ay=pos[p][1]+H/2,bx=pos[i][0],by=pos[i][1]+H/2,mid=(ax+bx)/2;
                Gui.drawRect(Math.min(ax,mid),ay,Math.max(ax,mid)+1,ay+1,0xFF526C88);
                Gui.drawRect(mid,Math.min(ay,by),mid+1,Math.max(ay,by)+1,0xFF526C88);
                Gui.drawRect(Math.min(mid,bx),by,Math.max(mid,bx)+1,by+1,0xFF526C88);
            }
            for(int i=0;i<nodes.size();i++){
                Snapshot.Node n=nodes.get(i);int x=pos[i][0],y=pos[i][1];
                int edge=n.active>0?0xFF40B5AA:n.pending>0?0xFFD5A950:0xFF657587;
                Gui.drawRect(x,y,x+W,y+H,0xFF273647);
                Gui.drawRect(x,y,x+3,y+H,edge);
                Item item=n.itemId<0?null:Item.getItemById(n.itemId);
                if(item!=null){
                    net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                    ITEMS.renderItemAndEffectIntoGUI(f,mc.getTextureManager(),new ItemStack(item,1,n.damage),x+8,y+6);
                    net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                }
                text(f,n.label,x+28,y+8,0xFFFFFF,W-34);
                text(f,"Stored "+n.stored+" | Active "+n.active,x+8,y+26,0xA9D8FF,W-16);
                text(f,"Pending "+n.pending+" | Runs "+n.runs,x+8,y+38,0xD4DCE4,W-16);
                text(f,n.reason.equals("UNDEFINED")?"":n.reason,x+8,y+50,0xE7BC77,W-16);
                text(f,n.provider,x+8,y+62,0xC5D3DE,W-16);
                if(n.maxProgress>0){
                    Gui.drawRect(x+8,y+76,x+W-8,y+81,0xFF101820);
                    int bar=(int)((W-16)*Math.min(1.0,Math.max(0.0,(double)n.progress/n.maxProgress)));
                    Gui.drawRect(x+8,y+76,x+8+bar,y+81,0xFF48B6A5);
                }
            }
            GL11.glPopMatrix();
        }
        Gui.drawRect(0,0,w,42,0xFF1B2837);
        text(f,"Lattice  |  F8 / Esc: Grid  |  Drag: pan  |  Wheel: zoom",8,7,0xE1EBF4,w-16);
        String info=data==null?"Waiting for authorized server telemetry...":
            data.cpu+"  |  "+data.output+"  |  "+data.status+"  |  Remaining "+data.remaining+"/"+data.total+
            (data.truncated?"  [partial graph]":"");
        text(f,info,8,22,0xA9D8FF,w-16);
        Gui.drawRect(0,h-16,w,h,0xFF1B2837);
        text(f,"Amounts are CPU-wide per output. Machine progress is candidate telemetry, not dispatch attribution.",5,h-12,0xA9BAC9,w-10);
    }
}
