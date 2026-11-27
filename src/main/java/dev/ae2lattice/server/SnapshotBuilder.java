package dev.ae2lattice.server;

import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.NamedDimensionalCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.Loader;
import dev.ae2lattice.mixin.CpuAccessor;
import dev.ae2lattice.mixin.TaskAccessor;
import dev.ae2lattice.net.Snapshot;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

final class SnapshotBuilder {
    private static final int PROBE_LIMIT=24;
    static Snapshot build(CraftingCPUCluster cpu,EntityPlayerMP player,int window,int nonce){
        if(!cpu.isBusy()) {
            JobTrack.clear(cpu);
            return new Snapshot(window,nonce,cpu.getName(),"","Idle",0,0,0,false,Collections.emptyList());
        }
        List<ICraftingPatternDetails> patterns=JobTrack.patterns(cpu);
        Map<ICraftingPatternDetails,CraftingCPUCluster.TaskProgress> tasks=((CpuAccessor)(Object)cpu).lattice$getTasks();
        boolean truncated=patterns.size()>Snapshot.MAX_NODES || tasks.size()>patterns.size();
        int count=Math.min(patterns.size(),Snapshot.MAX_NODES);
        Map<IAEStack<?>,List<Integer>> producers=new HashMap<>();
        for(int i=0;i<count;i++) {
            for(IAEStack<?> out:patterns.get(i).getCondensedAEOutputs()) if(out!=null)
                producers.computeIfAbsent(key(out),k->new ArrayList<>()).add(i);
        }
        List<Snapshot.Node> nodes=new ArrayList<>();
        int probes=0;
        for(int i=0;i<count;i++){
            ICraftingPatternDetails pattern=patterns.get(i);
            Set<Integer> deps=new LinkedHashSet<>();
            for(IAEStack<?> input:pattern.getCondensedAEInputs()) if(input!=null){
                List<Integer> candidates=producers.get(key(input));
                if(candidates!=null) for(int idx:candidates) if(idx!=i) deps.add(idx);
            }
            if(deps.size()>Snapshot.MAX_EDGES) truncated=true;
            int[] edges=deps.stream().limit(Snapshot.MAX_EDGES).mapToInt(Integer::intValue).toArray();
            IAEStack<?> out=first(pattern.getCondensedAEOutputs());
            CraftingCPUCluster.TaskProgress task=tasks.get(pattern);
            long runs=task==null?0:((TaskAccessor)(Object)task).lattice$getRemaining();
            String label=out==null?"Input-only pattern":out.getDisplayName();
            long stored=0,active=0,pending=0;
            String reason="",provider="";
            int progress=-1,max=-1,id=-1,damage=0;
            if(out!=null){
                stored=cpu.getStackAmount(out,CraftingItemList.STORAGE);
                active=cpu.getStackAmount(out,CraftingItemList.ACTIVE);
                pending=cpu.getStackAmount(out,CraftingItemList.PENDING);
                if(pending>0) reason=cpu.getScheduledReason(out).name();
                ItemStack icon=out.getItemStackForNEI();
                if(icon!=null){id=Item.getIdFromItem(icon.getItem());damage=icon.getItemDamage();}
                if((active>0 || pending>0) && probes<PROBE_LIMIT){
                    probes++;
                    List<NamedDimensionalCoord> candidates=cpu.getProviders(out);
                    if(candidates!=null && !candidates.isEmpty()){
                        NamedDimensionalCoord coord=candidates.get(0);
                        provider=coord.getCustomName()+(candidates.size()>1?" (+"+(candidates.size()-1)+")":"");
                        // Candidates are not proof of which CPU a machine is serving.
                        if(candidates.size()==1 && Loader.isModLoaded("gregtech")){
                            GregTechProbe.Reading reading=probe(coord,player);
                            if(reading!=null){provider+=" / "+reading.state; progress=reading.progress; max=reading.max;}
                        }
                    }
                }
            }
            nodes.add(new Snapshot.Node(label,reason,provider,runs,stored,active,pending,progress,max,id,damage,edges));
        }
        IAEStack<?> output=cpu.getFinalMultiOutput();
        return new Snapshot(window,nonce,cpu.getName(),output==null?"":output.getDisplayName(),
            cpu.isSuspended()?"Suspended":"Crafting",cpu.getElapsedTime(),cpu.getStartItemCount(),
            cpu.getRemainingItemCount(),truncated,nodes);
    }
    private static IAEStack<?> key(IAEStack<?> stack){
        IAEStack<?> copy=stack.copy();copy.setStackSize(1);return copy;
    }
    private static IAEStack<?> first(IAEStack<?>[] stacks){
        for(IAEStack<?> s:stacks) if(s!=null)return s;return null;
    }
    private static GregTechProbe.Reading probe(NamedDimensionalCoord c,EntityPlayerMP player){
        World world=DimensionManager.getWorld(c.getDimension());
        if(world==null || !world.blockExists(c.x,c.y,c.z)) return null;
        GregTechProbe.Reading found=null;
        // Only adjacent loaded GT tiles; refuse ambiguous multi-machine surroundings.
        for(ForgeDirection d:ForgeDirection.VALID_DIRECTIONS){
            int x=c.x+d.offsetX,y=c.y+d.offsetY,z=c.z+d.offsetZ;
            if(!world.blockExists(x,y,z))continue;
            TileEntity tile=world.getTileEntity(x,y,z);
            GregTechProbe.Reading reading=GregTechProbe.read(tile,player);
            if(reading!=null){if(found!=null)return null;found=reading;}
        }
        return found;
    }
}
