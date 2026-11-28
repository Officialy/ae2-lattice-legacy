package dev.ae2lattice.client;

import dev.ae2lattice.net.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.Fluid;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import java.util.*;

public final class GraphRenderer {
    private static float zoom=1,panX=12,panY=60;
    private static boolean dragging;
    private static int lastX,lastY;
    private static final int W=202,H=88,DX=236,DY=104;
    private static final RenderItem ITEMS=new RenderItem();
    private static Snapshot.Node hovered;
    private static Snapshot current;
    private static int hoveredIndex=-1,selected=-1;
    private static boolean needFit=true,animations=true,searching,detailView;
    private static String query="",notice="";
    private static long noticeUntil;
    private static int clickX,clickY;
    private static int panelWidth;
    private static final FlowAnimation FLOW=new FlowAnimation();
    private static Snapshot layoutSource;
    private static int[][] cachedLayout;
    private GraphRenderer(){}
    public static void reset(){query="";searching=false;resetPage();}
    public static void resetPage(){
        zoom=1;panX=12;panY=64;dragging=false;hovered=null;hoveredIndex=-1;selected=-1;
        current=null;detailView=false;needFit=true;FLOW.reset();layoutSource=null;cachedLayout=null;
    }
    static void configure(boolean enabled){animations=enabled;}
    private static void toggleAnimation(){animations=!animations;ClientSettings.setAnimations(animations);}
    private static void message(String s){notice=s;noticeUntil=System.nanoTime()+3000000000L;}
    private static Snapshot.Node target(){
        return hovered!=null?hovered:current!=null && selected>=0 && selected<current.nodes.size()?current.nodes.get(selected):null;
    }
    private static void recipe(boolean usage){
        Snapshot.Node n=target();
        if(n==null){message("Select a crafting step first");return;}
        if(!NeiBridge.installed()){message("NEI is not installed");return;}
        if(!n.recipeSafe){message("Exact recipe key unavailable (oversize NBT or unsupported stack)");return;}
        if(!NeiBridge.open(n,usage))message("NEI found no matching recipe");
    }
    static boolean key(char c,int key,Snapshot snapshot){
        if(searching){
            if(key==Keyboard.KEY_ESCAPE || key==Keyboard.KEY_RETURN){searching=false;return true;}
            if(key==Keyboard.KEY_BACK && !query.isEmpty())query=query.substring(0,query.length()-1);
            else if(c>=32 && c!=127 && query.length()<48)query+=c;
            return true;
        }
        if(key==Keyboard.KEY_PRIOR){if(snapshot!=null)ClientProxy.page(snapshot.page-1);return true;}
        if(key==Keyboard.KEY_NEXT){if(snapshot!=null)ClientProxy.page(snapshot.page+1);return true;}
        if(key==Keyboard.KEY_HOME){needFit=true;return true;}
        if(key==Keyboard.KEY_P){if(target()!=null){if(hoveredIndex>=0)selected=hoveredIndex;detailView=!detailView;}return true;}
        if(detailView && key==Keyboard.KEY_ESCAPE){detailView=false;return true;}
        if(key==Keyboard.KEY_R){recipe(false);return true;}
        if(key==Keyboard.KEY_U){recipe(true);return true;}
        if(key==Keyboard.KEY_F){searching=true;return true;}
        if(key==Keyboard.KEY_A){toggleAnimation();return true;}
        if(key>=Keyboard.KEY_1 && key<=Keyboard.KEY_9){
            Snapshot.Node n=target();int i=key-Keyboard.KEY_1;
            if(n!=null && i<n.externalDependencies.length)ClientProxy.page(n.externalDependencies[i]/Snapshot.PAGE_SIZE);
            return true;
        }
        return false;
    }
    private static void button(FontRenderer f,int x,int y,int w,String text){
        Gui.drawRect(x,y,x+w,y+14,0xFF363B40);Gui.drawRect(x+1,y+1,x+w-1,y+13,0xFFB6BCC1);
        f.drawString(f.trimStringToWidth(text,w-6),x+3,y+3,0xFF24282B);
    }
    static void nativeButton(){button(Minecraft.getMinecraft().fontRenderer,4,4,44,"Tree");}
    private static void fit(int w,int h,int[][] pos){
        int maxX=W,maxY=H;for(int[] p:pos){maxX=Math.max(maxX,p[0]+W);maxY=Math.max(maxY,p[1]+H);}
        zoom=Math.max(.12f,Math.min(1.0f,Math.min((w-24f)/maxX,(h-92f)/maxY)));
        panX=12;panY=64;needFit=false;
    }
    public static void mouse(){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.currentScreen==null)return;
        int x=Mouse.getEventX()*mc.currentScreen.width/mc.displayWidth;
        int y=mc.currentScreen.height-Mouse.getEventY()*mc.currentScreen.height/mc.displayHeight-1;
        int wheel=Mouse.getEventDWheel();
        if(wheel!=0){
            float next=Math.max(.12f,Math.min(2.5f,zoom*(wheel>0?1.12f:1/1.12f)));
            panX=x-(x-panX)*next/zoom;panY=y-(y-panY)*next/zoom;zoom=next;
        }
        if(Mouse.getEventButton()==1 && Mouse.getEventButtonState() && hovered!=null && hovered.machine!=null)
            WorldHighlight.select(hovered.machine);
        if(detailView){if(Mouse.getEventButton()==0 && Mouse.getEventButtonState())detailView=false;return;}
        if(Mouse.getEventButton()==0){
            if(Mouse.getEventButtonState()){
                if(y>=4 && y<=18){
                    if(x>=4 && x<48){ClientProxy.toggle();return;}
                    if(x>=52 && x<88){needFit=true;return;}
                    if(x>=92 && x<142){searching=!searching;return;}
                    if(x>=146 && x<200){toggleAnimation();return;}
                }
                if(y>=23 && y<=37 && current!=null){
                    if(x>=4 && x<34){ClientProxy.page(current.page-1);return;}
                    if(x>=38 && x<68){ClientProxy.page(current.page+1);return;}
                }
                if(panelWidth>0 && x>=mc.currentScreen.width-panelWidth){
                    if(y>=88 && y<102){recipe(false);return;}
                    if(y>=106 && y<120){recipe(true);return;}
                    if(y>=124 && y<138){detailView=true;return;}
                    Snapshot.Node n=target();int link=(y-184)/12;
                    if(y>=184 && n!=null && link<n.externalDependencies.length){ClientProxy.page(n.externalDependencies[link]/Snapshot.PAGE_SIZE);return;}
                    return;
                }
                clickX=x;clickY=y;dragging=y>=56;lastX=x;lastY=y;
            }else{
                if(dragging && Math.abs(x-clickX)+Math.abs(y-clickY)<5 && hoveredIndex>=0){
                    selected=hoveredIndex;
                }
                dragging=false;
            }
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
        if(current!=null && data!=null && (current.page!=data.page || !Objects.equals(current.cpu,data.cpu) || data.elapsed<current.elapsed))selected=-1;
        current=data;hovered=null;hoveredIndex=-1;
        if(data==null || selected>=data.nodes.size())selected=-1;
        panelWidth=w>=440 && selected>=0?Math.min(180,w/3):0;
        int graphW=w-panelWidth;
        long now=System.nanoTime();FLOW.accept(data,now);
        if(data!=null){
            List<Snapshot.Node> nodes=data.nodes;
            if(layoutSource!=data){cachedLayout=layout(nodes);layoutSource=data;}
            int[][] pos=cachedLayout;
            if(needFit)fit(graphW,h,pos);
            int pulseBudget=32;
            GL11.glPushMatrix();
            GL11.glTranslatef(panX,panY,0);GL11.glScalef(zoom,zoom,1);
            for(int i=0;i<nodes.size();i++)for(int p:nodes.get(i).dependencies){
                if(p<0 || p>=nodes.size())continue;
                int ax=pos[p][0]+W,ay=pos[p][1]+H/2,bx=pos[i][0],by=pos[i][1]+H/2,mid=(ax+bx)/2;
                Gui.drawRect(Math.min(ax,mid),ay,Math.max(ax,mid)+1,ay+1,0xFF526C88);
                Gui.drawRect(mid,Math.min(ay,by),mid+1,Math.max(ay,by)+1,0xFF526C88);
                Gui.drawRect(Math.min(mid,bx),by,Math.max(mid,bx)+1,by+1,0xFF526C88);
                double phase=FLOW.phase(p,now);
                if(animations && phase>=0 && pulseBudget>0){
                    int[] pt=FlowAnimation.point(ax,ay,mid,bx,by,phase);
                    if(pt[0]*zoom+panX>=0 && pt[0]*zoom+panX<=graphW && pt[1]*zoom+panY>=42 && pt[1]*zoom+panY<h-16){
                        pulseBudget--;
                        Snapshot.Node source=nodes.get(p);Item item=source.itemId<0?null:Item.getItemById(source.itemId);
                        Gui.drawRect(pt[0]-4,pt[1]-4,pt[0]+5,pt[1]+5,0xCC40B5AA);
                        if(item!=null && zoom>=0.6f){
                            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                            ITEMS.renderItemAndEffectIntoGUI(f,mc.getTextureManager(),new ItemStack(item,1,source.damage),pt[0]-8,pt[1]-8);
                            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                        }
                    }
                }
            }
            for(int i=0;i<nodes.size();i++){
                Snapshot.Node n=nodes.get(i);int x=pos[i][0],y=pos[i][1];
                float gx=(mx-panX)/zoom,gy=(my-panY)/zoom;
                if(mx<graphW && my>56 && my<h-16 && gx>=x && gx<x+W && gy>=y && gy<y+H){hovered=n;hoveredIndex=i;}
                if(x*zoom+panX>graphW || (x+W)*zoom+panX<0 || y*zoom+panY>h || (y+H)*zoom+panY<42)continue;
                int edge=n.active>0?0xFF40B5AA:n.pending>0?0xFFD5A950:0xFF657587;
                boolean match=query.isEmpty()||n.label.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
                Gui.drawRect(x,y,x+W,y+H,match?0xFF273647:0xFF18222C);
                Gui.drawRect(x,y,x+3,y+H,edge);
                if(i==selected || (!query.isEmpty() && match)){
                    Gui.drawRect(x,y,x+W,y+2,0xFFB8E2FF);Gui.drawRect(x,y+H-2,x+W,y+H,0xFFB8E2FF);
                }
                if(n.externalDependencies.length>0)
                    text(f,"+"+n.externalDependencies.length+" off-page",x+W-75,y+76,0xA9D8FF,70);
                Item item=n.itemId<0?null:Item.getItemById(n.itemId);
                if(item!=null){
                    net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                    ITEMS.renderItemAndEffectIntoGUI(f,mc.getTextureManager(),new ItemStack(item,1,n.damage),x+8,y+6);
                    net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                }
                text(f,n.label,x+28,y+8,0xFFFFFF,W-34);
                text(f,"Stored "+n.stored+" | Active "+n.active,x+8,y+26,0xA9D8FF,W-16);
                text(f,"Pending "+n.pending+" | Runs "+n.runs,x+8,y+38,0xD4DCE4,W-16);
                text(f,n.reason.equals("UNDEFINED")||n.reason.isEmpty()?"CPU returned "+n.returned:n.reason,x+8,y+50,0xE7BC77,W-16);
                text(f,n.provider,x+8,y+62,0xC5D3DE,W-16);
                if(n.maxProgress>0){
                    Gui.drawRect(x+8,y+76,x+W-8,y+81,0xFF101820);
                    int bar=(int)((W-16)*Math.min(1.0,Math.max(0.0,(double)n.progress/n.maxProgress)));
                    Gui.drawRect(x+8,y+76,x+8+bar,y+81,0xFF48B6A5);
                }
            }
            GL11.glPopMatrix();
        }
        Gui.drawRect(0,0,w,56,0xFF1B2837);
        button(f,4,4,44,"Grid");button(f,52,4,36,"Fit");button(f,92,4,50,"Search");
        button(f,146,4,54,animations?"Flow: on":"Flow: off");
        if(w>208)text(f,"F8/G  |  R/U: NEI",208,8,0xE1EBF4,w-212);
        button(f,4,23,30,"<");button(f,38,23,30,">");
        String pageLabel=data==null?"Loading...":"Page "+(data.page+1)+"/"+data.pageCount()+"  |  "+data.totalNodes+" tracked";
        text(f,pageLabel,73,26,0xE1EBF4,w-77);
        String info=data==null?"Waiting for authorized server telemetry...":
            data.cpu+" | "+data.output+" | "+data.status+" | Remaining "+data.remaining+"/"+data.total+
            (data.truncated?" [partial]":"");
        if(searching || !query.isEmpty())info="Page search: "+query+(searching?"_":"");
        if(System.nanoTime()<noticeUntil)info=notice;
        text(f,info,5,43,0xA9D8FF,w-10);
        if(panelWidth>0 && data!=null && selected<data.nodes.size()){
            Snapshot.Node n=data.nodes.get(selected);int px=w-panelWidth;
            Gui.drawRect(px,56,w,h-16,0xFF1D2A38);
            text(f,"Step #"+(data.page*Snapshot.PAGE_SIZE+selected+1),px+5,62,0xA9D8FF,panelWidth-10);
            text(f,n.label,px+5,74,0xFFFFFF,panelWidth-10);
            button(f,px+5,88,panelWidth-10,"Recipes (R)");button(f,px+5,106,panelWidth-10,"Usages (U)");
            button(f,px+5,124,panelWidth-10,"Pattern / machine (P)");
            text(f,"Inputs: "+n.ingredients,px+5,142,0xE1EBF4,panelWidth-10);
            text(f,"Outputs: "+n.products,px+5,158,0xE1EBF4,panelWidth-10);
            for(int i=0;i<n.externalDependencies.length && 196+i*12<h-16;i++){
                int d=n.externalDependencies[i];
                text(f,(i+1)+": Step #"+(d+1)+" (page "+(d/Snapshot.PAGE_SIZE+1)+")",px+5,184+i*12,0xA9D8FF,panelWidth-10);
            }
        }
        if(data!=null && data.nodes.isEmpty() && ("Completed".equals(data.status)||"Canceled".equals(data.status))){
            text(f,data.status+": "+data.output,16,62,0xA9D8FF,w-32);
            text(f,"Elapsed: "+(data.elapsed/1000000000L)+"s | Work units: "+data.total,16,78,0xFFFFFF,w-32);
        }
        if(hovered!=null && hovered.machine!=null){
            Snapshot.Machine m=hovered.machine;
            List<String> lines=Arrays.asList(hovered.route.isEmpty()?"Candidate machine":hovered.route,
                "Dim "+m.dim+" / "+m.x+", "+m.y+", "+m.z,m.capacity<0?"Energy: not applicable":"Energy: "+m.energy+" / "+m.capacity+" EU",
                "Authorized sampled hatches: "+m.hatches,
                "Items: "+(m.inventory.isEmpty()?"Unavailable / empty":m.inventory),
                "Fluids: "+(m.fluids.isEmpty()?"Unavailable / empty":m.fluids),"Right-click: highlight for 10s");
            int tw=Math.min(w-16,340),tx=Math.max(4,Math.min(mx+10,w-tw-4)),ty=Math.max(44,Math.min(my+10,h-104));
            Gui.drawRect(tx,ty,tx+tw,ty+90,0xF01B2837);
            for(int i=0;i<lines.size();i++)text(f,lines.get(i),tx+5,ty+4+i*12,0xE1EBF4,tw-10);
        }
        if(detailView && data!=null && selected>=0 && selected<data.nodes.size())drawDetails(w,h,f,mc,data.nodes.get(selected),mx,my);
        Gui.drawRect(0,h-16,w,h,0xFF1B2837);
        text(f,"PgUp/PgDn: pages | P: pattern/machine | R/U: NEI | CPU-wide counts",5,h-12,0xA9BAC9,w-10);
    }
    private static void drawDetails(int w,int h,FontRenderer f,Minecraft mc,Snapshot.Node n,int mx,int my){
        int x=Math.max(5,(w-300)/2),right=Math.min(w-5,x+300),y=58;
        Gui.drawRect(x,y,right,h-18,0xFF202B38);
        text(f,n.label+" | click / P to close",x+6,y+5,0xFFFFFF,right-x-12);
        text(f,"Encoded pattern (sample, not live recipe)",x+6,y+19,0xA9D8FF,right-x-12);
        drawSlots(f,mc,n.details.inputs,x+6,y+31,right-6,mx,my);
        drawSlots(f,mc,n.details.outputs,x+6,y+65,right-6,mx,my);
        if(n.machine!=null && h>208){
            text(f,"Observed machine inventory / tanks (sample)",x+6,y+99,0xA9D8FF,right-x-12);
            drawSlots(f,mc,n.machine.slots,x+6,y+111,right-6,mx,my);
            if(h>240)drawSlots(f,mc,n.machine.tanks,x+6,y+145,right-6,mx,my);
        }
    }
    private static void drawSlots(FontRenderer f,Minecraft mc,List<Snapshot.Slot> entries,int x,int y,int right,int mx,int my){
        if(entries.isEmpty()){text(f,"No sampled entries",x,y+6,0xA9BAC9,right-x);return;}
        for(int i=0;i<entries.size() && x+28<=right;i++,x+=31){
            Snapshot.Slot s=entries.get(i);
            Gui.drawRect(x,y,x+28,y+28,0xFF141C26);Gui.drawRect(x+1,y+1,x+27,y+27,0xFF34485B);
            Item item=s.id<0?null:Item.getItemById(s.id);
            String name="";
            if(item!=null){
                ItemStack stack=new ItemStack(item,1,s.damage);
                net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                ITEMS.renderItemAndEffectIntoGUI(f,mc.getTextureManager(),stack,x+6,y+2);
                net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                name=stack.getDisplayName();
            }else if(!s.fluid.isEmpty() || s.capacity>0){
                Fluid fluid=FluidRegistry.getFluid(s.fluid);
                int color=fluid==null?0xFF4595CB:0xFF000000|(fluid.getColor()&0xFFFFFF);
                int bar=s.capacity>0?(int)(20*Math.min(1.0,(double)s.amount/s.capacity)):20;
                Gui.drawRect(x+5,y+23-bar,x+23,y+23,color);
                name=s.fluid.isEmpty()?"Empty tank":s.fluid;
            }
            text(f,Long.toString(s.amount),x+2,y+19,0xFFFFFF,24);
            if(mx>=x && mx<x+28 && my>=y && my<y+28 && !name.isEmpty()){
                int tx=Math.min(right-100,x);
                Gui.drawRect(tx,y-12,Math.min(right,tx+160),y,0xFF101820);
                text(f,name,tx+2,y-10,0xFFFFFF,Math.min(right-tx-4,156));
            }
        }
    }
}
