package dev.ae2lattice.client;
import appeng.api.AEApi;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.GuiRecipe;
import dev.ae2lattice.net.Snapshot;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import cpw.mods.fml.common.Loader;

/** Optional client integration. No NEI classes are touched unless NEI is installed. */
final class NeiBridge {
    static boolean installed(){return Loader.isModLoaded("NotEnoughItems");}
    static boolean open(Snapshot.Node node,boolean usage){
        if(!installed() || node==null || !node.recipeSafe)return false;
        try{return Impl.open(node,usage);}catch(RuntimeException|LinkageError ex){return false;}
    }
    static boolean isRecipeFor(GuiScreen screen,GuiScreen parent){
        if(!installed() || parent==null)return false;
        try{return Impl.isRecipeFor(screen,parent);}catch(LinkageError ex){return false;}
    }
    private static final class Impl {
        static boolean isRecipeFor(GuiScreen screen,GuiScreen parent){
            return screen instanceof GuiRecipe && ((GuiRecipe<?>)screen).getFirstScreenGeneral()==parent;
        }
        static boolean open(Snapshot.Node node,boolean usage){
            ItemStack stack;
            if(!node.fluidName.isEmpty()){
                FluidStack fluid=FluidRegistry.getFluidStack(node.fluidName,1000);
                if(fluid==null)return false;
                stack=AEApi.instance().storage().createFluidStack(fluid).getItemStackForNEI();
            }else{
                Item item=Item.getItemById(node.itemId);
                if(item==null)return false;
                stack=new ItemStack(item,1,node.damage);
            }
            return stack!=null && (usage?GuiUsageRecipe.openRecipeGui("item",stack):GuiCraftingRecipe.openRecipeGui("item",stack));
        }
    }
}
