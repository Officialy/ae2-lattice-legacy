package dev.ae2lattice.server;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

/** Only machine owners receive optional machine telemetry. No mutation or chunk loading. */
final class GregTechProbe {
    static final class Reading {
        final String state; final int progress,max;
        Reading(String state,int progress,int max){this.state=state;this.progress=progress;this.max=max;}
    }
    static Reading read(TileEntity tile,EntityPlayerMP player) {
        if(!(tile instanceof BaseMetaTileEntity)) return null;
        BaseMetaTileEntity gt=(BaseMetaTileEntity)tile;
        if(!player.getUniqueID().equals(gt.getOwnerUuid())) return null;
        return new Reading(!gt.isAllowedToWork()?"Disabled":gt.isActive()?"Running":"Idle",
            Math.max(0,gt.getProgress()),Math.max(0,gt.getMaxProgress()));
    }
}
