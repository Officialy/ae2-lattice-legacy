package dev.ae2lattice.server;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.util.NamedDimensionalCoord;
import appeng.helpers.IInterfaceHost;
import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import java.util.*;

/** Read-only, bounded search from actual dispatch targets. Never crosses an unauthorized grid. */
final class MachineResolver {
    static final class Budget {
        int tiles=128,nodes=128,grids=8;
    }
    static final class Result {
        final GregTechProbe.Reading reading; final String route;
        Result(GregTechProbe.Reading reading,String route){this.reading=reading;this.route=route;}
    }
    private static boolean allowed(IGrid grid,EntityPlayerMP player){
        if(grid==null)return false;
        ISecurityGrid security=grid.getCache(ISecurityGrid.class);
        return security!=null && security.hasPermission(player,SecurityPermissions.CRAFT);
    }
    static TileEntity loaded(NamedDimensionalCoord pos,Budget budget){
        if(budget.tiles--<=0)return null;
        World w=DimensionManager.getWorld(pos.getDimension());
        return w!=null && w.blockExists(pos.x,pos.y,pos.z)?w.getTileEntity(pos.x,pos.y,pos.z):null;
    }
    static Result resolve(JobTrack.Dispatch dispatch,NamedDimensionalCoord provider,IGrid root,
                          EntityPlayerMP player,Budget budget){
        if(provider==null && (dispatch==null || dispatch.destinations.isEmpty()))return null;
        Set<TileEntity> candidates=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IGrid> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(root);
        boolean exact=false,subnet=false,partial=false;
        if(dispatch!=null && !dispatch.destinations.isEmpty()){
            exact=true;partial=dispatch.partial;
            for(NamedDimensionalCoord pos:dispatch.destinations){
                TileEntity tile=loaded(pos,budget);
                if(tile==null){partial=true;continue;}
                if(GregTechProbe.isMachine(tile)){candidates.add(tile);continue;}
                boolean found=false;
                for(IInterfaceHost host:interfaces(tile)){
                    IGrid grid=grid(host);
                    if(grid!=null && grid!=root && allowed(grid,player)){
                        subnet=true;found=true;
                        walk(grid,player,budget,visited,candidates,0);
                    }
                }
                if(!found)partial=true;
            }
        }else if(provider!=null){
            TileEntity tile=loaded(provider,budget);
            if(tile!=null && GregTechProbe.isMachine(tile))candidates.add(tile);
            else if(tile!=null)adjacent(tile,dispatch==null?0:dispatch.targetMask,budget,candidates);
        }
        if(candidates.size()!=1 || partial || budget.tiles<0 || budget.nodes<0 || budget.grids<0)
            return null;
        GregTechProbe.Reading reading=GregTechProbe.read(candidates.iterator().next(),player);
        if(reading==null)return null;
        return new Result(reading,subnet?"Authorized subnet candidate":exact?"Observed receiving tile":"Provider candidate");
    }
    private static List<IInterfaceHost> interfaces(TileEntity tile){
        List<IInterfaceHost> result=new ArrayList<>();
        if(tile instanceof IInterfaceHost)result.add((IInterfaceHost)tile);
        for(ForgeDirection d:ForgeDirection.VALID_DIRECTIONS){
            Object part=Platform.getPartFromTE(tile,d);
            if(part instanceof IInterfaceHost)result.add((IInterfaceHost)part);
        }
        return result;
    }
    private static IGrid grid(IInterfaceHost host){
        try{return host.getInterfaceDuality().getProxy().getGrid();}
        catch(Exception ignored){return null;}
    }
    private static void adjacent(TileEntity tile,int mask,Budget budget,Set<TileEntity> candidates){
        World w=tile.getWorldObj();
        for(ForgeDirection d:ForgeDirection.VALID_DIRECTIONS){
            if(mask!=0 && (mask&(1<<d.ordinal()))==0)continue;
            if(budget.tiles--<=0)return;
            int x=tile.xCoord+d.offsetX,y=tile.yCoord+d.offsetY,z=tile.zCoord+d.offsetZ;
            if(!w.blockExists(x,y,z))continue;
            TileEntity target=w.getTileEntity(x,y,z);
            if(GregTechProbe.isMachine(target))candidates.add(target);
        }
    }
    private static void walk(IGrid grid,EntityPlayerMP player,Budget budget,Set<IGrid> visited,
                             Set<TileEntity> candidates,int depth){
        if(depth>=2 || !allowed(grid,player) || !visited.add(grid))return;
        if(budget.grids--<=0)return;
        for(IGridNode node:grid.getNodes()){
            if(budget.nodes--<=0)return;
            Object host=node.getMachine();
            // Direct AE-capable GT hatches expose an actual grid location.
            if(host instanceof IGridProxyable){
                try{
                    appeng.api.util.DimensionalCoord c=((IGridProxyable)host).getLocation();
                    if(c!=null){
                        TileEntity tile=loaded(new NamedDimensionalCoord(c,""),budget);
                        if(GregTechProbe.isMachine(tile))candidates.add(tile);
                    }
                }catch(RuntimeException ignored){}
            }
            if(!(host instanceof IInterfaceHost))continue;
            IInterfaceHost iface=(IInterfaceHost)host;
            TileEntity tile=iface.getTileEntity();
            if(tile==null || tile.getWorldObj()==null)continue;
            World w=tile.getWorldObj();
            if(!w.blockExists(tile.xCoord,tile.yCoord,tile.zCoord))continue;
            for(ForgeDirection d:iface.getTargets()){
                if(d==ForgeDirection.UNKNOWN)continue;
                if(budget.tiles--<=0)return;
                int x=tile.xCoord+d.offsetX,y=tile.yCoord+d.offsetY,z=tile.zCoord+d.offsetZ;
                if(!w.blockExists(x,y,z))continue;
                TileEntity target=w.getTileEntity(x,y,z);
                if(target==null)continue;
                if(GregTechProbe.isMachine(target))candidates.add(target);
                else for(IInterfaceHost next:interfaces(target)){
                    IGrid other=grid(next);
                    if(other!=grid)walk(other,player,budget,visited,candidates,depth+1);
                }
            }
        }
    }
}
