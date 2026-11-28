package dev.ae2lattice.client;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;

final class ClientSettings {
    private static Configuration config;
    private static boolean tree,animations=true;
    private ClientSettings(){}
    static void load(){
        try{
            config=new Configuration(new File(Minecraft.getMinecraft().mcDataDir,"config/ae2lattice-client.cfg"));
            config.load();
            tree=config.get("view","tree",false).getBoolean(false);
            animations=config.get("view","animations",true).getBoolean(true);
            if(config.hasChanged())config.save();
        }catch(RuntimeException ex){LogManager.getLogger("AE2 Lattice").debug("Unable to load client settings",ex);}
    }
    static boolean tree(){return tree;}
    static boolean animations(){return animations;}
    static void setTree(boolean value){tree=value;save();}
    static void setAnimations(boolean value){animations=value;save();}
    private static void save(){
        if(config==null)return;
        try{
            config.get("view","tree",false).set(tree);
            config.get("view","animations",true).set(animations);
            if(config.hasChanged())config.save();
        }catch(RuntimeException ex){LogManager.getLogger("AE2 Lattice").debug("Unable to save client settings",ex);}
    }
}
