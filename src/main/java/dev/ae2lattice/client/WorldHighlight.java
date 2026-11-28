package dev.ae2lattice.client;
import dev.ae2lattice.net.Snapshot;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

public final class WorldHighlight {
    private static Snapshot.Machine target;
    private static World world;
    private static long until;
    public static void select(Snapshot.Machine machine){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld==null || mc.theWorld.provider.dimensionId!=machine.dim)return;
        target=machine;world=mc.theWorld;until=System.nanoTime()+10000000000L;
    }
    public static void clear(){target=null;world=null;}
    @SubscribeEvent public void render(RenderWorldLastEvent event){
        Minecraft mc=Minecraft.getMinecraft();
        if(target==null)return;
        if(world!=mc.theWorld || System.nanoTime()>until){clear();return;}
        if(!world.blockExists(target.x,target.y,target.z))return;
        Entity view=mc.renderViewEntity;
        if(view==null)return;
        double px=view.lastTickPosX+(view.posX-view.lastTickPosX)*event.partialTicks;
        double py=view.lastTickPosY+(view.posY-view.lastTickPosY)*event.partialTicks;
        double pz=view.lastTickPosZ+(view.posZ-view.lastTickPosZ)*event.partialTicks;
        double x=target.x-px-.005,y=target.y-py-.005,z=target.z-pz-.005,s=1.01;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);GL11.glLineWidth(2f);
            Tessellator t=Tessellator.instance;
            t.startDrawing(GL11.GL_LINES);t.setColorRGBA(72,220,200,210);
            for(int i=0;i<8;i++)for(int bit=1;bit<=4;bit<<=1)if((i&bit)==0){
                vertex(t,i,x,y,z,s);vertex(t,i|bit,x,y,z,s);
            }
            t.draw();
        }finally{GL11.glPopAttrib();}
    }
    private static void vertex(Tessellator t,int i,double x,double y,double z,double s){
        t.addVertex(x+((i&1)==0?0:s),y+((i&2)==0?0:s),z+((i&4)==0?0:s));
    }
}
