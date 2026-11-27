package dev.ae2lattice.server;
import dev.ae2lattice.net.Snapshot;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTankInfo;

/** Only machine owners receive optional machine telemetry. No mutation or chunk loading. */
final class GregTechProbe {
    static final class Reading {
        final String state; final int progress,max;
        final Snapshot.Machine machine;
        Reading(String state,int progress,int max,Snapshot.Machine machine){
            this.state=state;this.progress=progress;this.max=max;this.machine=machine;
        }
    }
    static boolean isMachine(TileEntity tile){return tile instanceof BaseMetaTileEntity;}
    static Reading read(TileEntity tile,EntityPlayerMP player) {
        if(!(tile instanceof BaseMetaTileEntity)) return null;
        BaseMetaTileEntity gt=(BaseMetaTileEntity)tile;
        if(!player.getUniqueID().equals(gt.getOwnerUuid()) || !gt.canAccessData()) return null;
        StringBuilder items=new StringBuilder(), fluids=new StringBuilder();
        int size=Math.max(0,gt.getSizeInventory()), slots=Math.min(size,16);
        try {
            for(int i=0;i<slots && items.length()<80;i++){
                ItemStack item=gt.getStackInSlot(i);
                if(item==null || item.stackSize<=0)continue;
                if(items.length()>0)items.append("; ");
                items.append(item.stackSize).append("x ").append(item.getDisplayName());
            }
            if(size>slots)items.append(" [partial]");
        } catch(RuntimeException ignored) {items.setLength(0);}
        try {
            FluidTankInfo[] tanks=gt.getTankInfo(ForgeDirection.UNKNOWN);
            if(tanks!=null)for(int i=0;i<Math.min(tanks.length,8) && fluids.length()<80;i++){
                FluidTankInfo tank=tanks[i];
                if(tank==null || tank.fluid==null)continue;
                if(fluids.length()>0)fluids.append("; ");
                fluids.append(tank.fluid.amount).append("mB ").append(tank.fluid.getFluid().getName());
            }
        } catch(RuntimeException ignored) {fluids.setLength(0);}
        Snapshot.Machine machine=new Snapshot.Machine(tile.getWorldObj().provider.dimensionId,tile.xCoord,tile.yCoord,tile.zCoord,
            Math.max(0,gt.getStoredEU()),Math.max(0,gt.getEUCapacity()),items.toString(),fluids.toString());
        return new Reading(!gt.isAllowedToWork()?"Disabled":gt.isActive()?"Running":"Idle",
            Math.max(0,gt.getProgress()),Math.max(0,gt.getMaxProgress()),machine);
    }
}
