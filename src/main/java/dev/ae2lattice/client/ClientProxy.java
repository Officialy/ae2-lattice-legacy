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
    private static int nonce, ticks, lastReceived, page;
    private static boolean preferTree;
    private static Snapshot snapshot;
    @Override public void init(){
        ClientSettings.load();preferTree=ClientSettings.tree();GraphRenderer.configure(ClientSettings.animations());
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
    public static void toggle(){
        if(!available || !(Minecraft.getMinecraft().currentScreen instanceof GuiCraftingCPU))return;
        screen=(GuiCraftingCPU)Minecraft.getMinecraft().currentScreen;
        tree=!tree;preferTree=tree;ClientSettings.setTree(tree);page=0;nonce++;snapshot=null;GraphRenderer.reset();
    }
    public static void page(int target){
        if(!active() || snapshot==null)return;
        int next=Math.max(0,Math.min(snapshot.pageCount()-1,target));
        if(next==page)return;
        page=next;nonce++;snapshot=null;GraphRenderer.resetPage();
    }
    public static boolean nativeMouse(){
        Minecraft mc=Minecraft.getMinecraft();
        if(!available || tree || !(mc.currentScreen instanceof GuiCraftingCPU))return false;
        if(org.lwjgl.input.Mouse.getEventButton()!=0 || !org.lwjgl.input.Mouse.getEventButtonState())return false;
        int x=org.lwjgl.input.Mouse.getEventX()*mc.currentScreen.width/mc.displayWidth;
        int y=mc.currentScreen.height-org.lwjgl.input.Mouse.getEventY()*mc.currentScreen.height/mc.displayHeight-1;
        if(x>=4 && x<=48 && y>=4 && y<=18){toggle();return true;}
        return false;
    }
    public static boolean key(char c,int key){
        if(!(Minecraft.getMinecraft().currentScreen instanceof GuiCraftingCPU))return false;
        if(key==org.lwjgl.input.Keyboard.KEY_F8 && available){
            toggle();return true;
        }
        if(active()){
            if(GraphRenderer.key(c,key,snapshot))return true;
            if(key==org.lwjgl.input.Keyboard.KEY_ESCAPE || key==org.lwjgl.input.Keyboard.KEY_G){toggle();return true;}
            return true;
        }
        return false;
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        ticks++;
        Minecraft mc=Minecraft.getMinecraft();
        // NEI is a child of the native crafting GUI; retain page and view across recipe lookups.
        if(available && tree && NeiBridge.isRecipeFor(mc.currentScreen,screen)){INBOX.set(null);return;}
        GuiCraftingCPU current=mc.currentScreen instanceof GuiCraftingCPU?(GuiCraftingCPU)mc.currentScreen:null;
        if(screen!=current || (!available && tree)){
            screen=current;tree=current!=null && available && preferTree;page=0;snapshot=null;nonce++;GraphRenderer.reset();
        }
        Snapshot next=INBOX.getAndSet(null);
        if(active() && next!=null && next.nonce==nonce && next.window==screen.inventorySlots.windowId){
            snapshot=next;page=next.page;lastReceived=ticks;
        }
        if(active() && ticks%10==0 && mc.thePlayer!=null)
            LatticeNetwork.channel.sendToServer(new LatticeNetwork.Request(screen.inventorySlots.windowId,nonce,page));
        if(snapshot!=null && ticks-lastReceived>60)snapshot=null;
    }
    @SubscribeEvent public void drawPre(GuiScreenEvent.DrawScreenEvent.Pre e){
        if(e.gui==screen && active()){
            e.setCanceled(true);GraphRenderer.draw(e.gui.width,e.gui.height,e.mouseX,e.mouseY,snapshot);
        }
    }
    @SubscribeEvent public void drawPost(GuiScreenEvent.DrawScreenEvent.Post e){
        if(e.gui==screen && available && !active())
            GraphRenderer.nativeButton();
    }
}
