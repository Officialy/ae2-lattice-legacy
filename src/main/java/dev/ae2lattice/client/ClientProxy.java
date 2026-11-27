package dev.ae2lattice.client;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import dev.ae2lattice.CommonProxy;
import dev.ae2lattice.net.LatticeNetwork;
import dev.ae2lattice.net.Snapshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import appeng.client.gui.implementations.GuiCraftingCPU;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientProxy extends CommonProxy {
    private static final AtomicReference<Snapshot> INBOX=new AtomicReference<>();
    private static volatile boolean available;
    private static GuiCraftingCPU screen;
    private static boolean tree;
    private static int nonce, ticks, lastReceived;
    private static Snapshot snapshot;
    @Override public void init(){
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new WorldHighlight());
    }
    @Override public void receive(Snapshot value){ INBOX.set(value); }
    @SubscribeEvent public void registration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> e){
        if(e.side==Side.CLIENT && e.registrations.contains(LatticeNetwork.CHANNEL))
            available="REGISTER".equals(e.operation);
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent e){
        available=false; INBOX.set(null);
    }
    public static boolean active(){return tree && available && Minecraft.getMinecraft().currentScreen==screen;}
    public static boolean key(int key){
        if(!(Minecraft.getMinecraft().currentScreen instanceof GuiCraftingCPU))return false;
        if(key==org.lwjgl.input.Keyboard.KEY_F8 && available){
            screen=(GuiCraftingCPU)Minecraft.getMinecraft().currentScreen;
            tree=!tree;nonce++;snapshot=null;GraphRenderer.reset();return true;
        }
        if(active() && key==org.lwjgl.input.Keyboard.KEY_ESCAPE){
            tree=false;nonce++;snapshot=null;return true;
        }
        return active();
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        ticks++;
        Minecraft mc=Minecraft.getMinecraft();
        GuiCraftingCPU current=mc.currentScreen instanceof GuiCraftingCPU?(GuiCraftingCPU)mc.currentScreen:null;
        if(screen!=current || (!available && tree)){
            screen=current;tree=false;snapshot=null;nonce++;GraphRenderer.reset();
        }
        Snapshot next=INBOX.getAndSet(null);
        if(active() && next!=null && next.nonce==nonce && next.window==screen.inventorySlots.windowId){
            snapshot=next;lastReceived=ticks;
        }
        if(active() && ticks%10==0 && mc.thePlayer!=null)
            LatticeNetwork.channel.sendToServer(new LatticeNetwork.Request(screen.inventorySlots.windowId,nonce));
        if(snapshot!=null && ticks-lastReceived>60)snapshot=null;
    }
    @SubscribeEvent public void drawPre(GuiScreenEvent.DrawScreenEvent.Pre e){
        if(e.gui==screen && active()){
            e.setCanceled(true);GraphRenderer.draw(e.gui.width,e.gui.height,e.mouseX,e.mouseY,snapshot);
        }
    }
    @SubscribeEvent public void drawPost(GuiScreenEvent.DrawScreenEvent.Post e){
        if(e.gui==screen && available && !active())
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow("F8: Lattice Tree",4,4,0xB8E2FF);
    }
}
