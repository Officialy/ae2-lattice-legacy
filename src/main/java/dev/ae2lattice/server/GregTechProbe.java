package dev.ae2lattice.server;
import dev.ae2lattice.net.Snapshot;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import dev.ae2lattice.mixin.HatchWatcherAccessor;
import appeng.me.helpers.IGridProxyable;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTankInfo;

/** Only machine owners receive optional machine telemetry. No mutation or chunk loading. */
final class GregTechProbe {
    static boolean isMachine(TileEntity tile){return tile instanceof BaseMetaTileEntity;}
    static MachineReading read(TileEntity tile,EntityPlayerMP player) {
        if(!(tile instanceof BaseMetaTileEntity)) return null;
        BaseMetaTileEntity gt=(BaseMetaTileEntity)tile;
        if(!authorized(gt,player))return null;
        // Smart/ME input hatches can point to a registered multiblock controller.
        // Never guess a controller by physical proximity or select among multiple watchers.
        BaseMetaTileEntity controller=controller(gt,player);
        if(controller!=null)gt=controller;
        StringBuilder items=new StringBuilder(), fluids=new StringBuilder();
        Set<BaseMetaTileEntity> hatches=Collections.newSetFromMap(new IdentityHashMap<>());
        IMetaTileEntity meta=gt.getMetaTileEntity();
        if(meta instanceof MTEMultiBlockBase){
            MTEMultiBlockBase multi=(MTEMultiBlockBase)meta;
            addHatches(hatches,multi.mInputBusses,player);
            addHatches(hatches,multi.mOutputBusses,player);
            addHatches(hatches,multi.mInputHatches,player);
            addHatches(hatches,multi.mOutputHatches,player);
            addHatches(hatches,multi.mDualInputHatches,player);
            addHatches(hatches,multi.mEnergyHatches,player);
            addHatches(hatches,multi.getExoticEnergyHatches(),player);
        }
        collect(gt,items,fluids);
        for(BaseMetaTileEntity hatch:hatches)collect(hatch,items,fluids);
        tile=gt;
        Snapshot.Machine machine=new Snapshot.Machine(tile.getWorldObj().provider.dimensionId,tile.xCoord,tile.yCoord,tile.zCoord,
            Math.max(0,gt.getStoredEU()),Math.max(0,gt.getEUCapacity()),items.toString(),fluids.toString(),hatches.size());
        return new MachineReading(!gt.isAllowedToWork()?"Disabled":gt.isActive()?"Running":"Idle",
            Math.max(0,gt.getProgress()),Math.max(0,gt.getMaxProgress()),machine);
    }
    private static boolean authorized(BaseMetaTileEntity tile,EntityPlayerMP player){
        return tile.getWorldObj()!=null && tile.getWorldObj().blockExists(tile.xCoord,tile.yCoord,tile.zCoord)
            && tile.getWorldObj().getTileEntity(tile.xCoord,tile.yCoord,tile.zCoord)==tile
            && player.getUniqueID().equals(tile.getOwnerUuid()) && tile.canAccessData();
    }
    private static BaseMetaTileEntity controller(BaseMetaTileEntity tile,EntityPlayerMP player){
        Object meta=tile.getMetaTileEntity();
        if(!(meta instanceof MTEHatch) || !(meta instanceof HatchWatcherAccessor))return null;
        List<?> watchers=((HatchWatcherAccessor)meta).lattice$watchers();
        if(watchers.size()!=1 || !(watchers.get(0) instanceof MTEMultiBlockBase))return null;
        MTEMultiBlockBase multi=(MTEMultiBlockBase)watchers.get(0);
        Object base=multi.getBaseMetaTileEntity();
        if(!(base instanceof BaseMetaTileEntity))return null;
        BaseMetaTileEntity result=(BaseMetaTileEntity)base;
        return authorized(result,player)?result:null;
    }
    private static void addHatches(Set<BaseMetaTileEntity> result,Iterable<?> list,EntityPlayerMP player){
        int seen=0;
        for(Object value:list){
            if(result.size()>=12 || seen++>=24)break;
            if(!(value instanceof IMetaTileEntity))continue;
            Object base=((IMetaTileEntity)value).getBaseMetaTileEntity();
            if(base instanceof BaseMetaTileEntity && authorized((BaseMetaTileEntity)base,player))
                result.add((BaseMetaTileEntity)base);
        }
    }
    private static void collect(BaseMetaTileEntity gt,StringBuilder items,StringBuilder fluids){
        // Avoid ME-backed virtual inventories: those can query a second network.
        if(gt.getMetaTileEntity() instanceof IGridProxyable)return;
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
        // Summaries remain bounded by the snapshot codec.
    }
}
