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
            JobTrack.Finished done=JobTrack.finished(cpu);
            if(done!=null)return new Snapshot(window,nonce,cpu.getName(),done.output,done.status,
                done.elapsed,done.total,done.remaining,false,Collections.emptyList());
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
        MachineResolver.Budget budget=new MachineResolver.Budget();
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
            Snapshot.Machine machine=null;
            long dispatched=0,returned=0;int destinations=0;String route="";
            if(out!=null){
                stored=cpu.getStackAmount(out,CraftingItemList.STORAGE);
                active=cpu.getStackAmount(out,CraftingItemList.ACTIVE);
                pending=cpu.getStackAmount(out,CraftingItemList.PENDING);
                if(pending>0) reason=cpu.getScheduledReason(out).name();
                ItemStack icon=out.getItemStackForNEI();
                if(icon!=null){id=Item.getIdFromItem(icon.getItem());damage=icon.getItemDamage();}
                returned=JobTrack.returnedCount(cpu,out);
                JobTrack.Dispatch dispatch=JobTrack.latest(cpu,pattern);
                if(dispatch!=null){
                    dispatched=dispatch.count;destinations=dispatch.destinations.size();
                    provider="Accepted "+dispatch.count+"x";
                    if(destinations>0)route="Last push: "+destinations+(dispatch.partial?"+":"")+" receiving tiles";
                    if(dispatch.provider!=null) provider+=" at "+dispatch.provider.getCustomName();
                }
                if((active>0 || pending>0) && probes<PROBE_LIMIT){
                    probes++;
                    // Prefer the last accepting provider. The adjacent machine remains a candidate.
                    NamedDimensionalCoord coord=dispatch==null?null:dispatch.provider;
                    if(coord==null){
                        List<NamedDimensionalCoord> candidates=cpu.getProviders(out);
                        if(candidates!=null && !candidates.isEmpty()){
                            if(!provider.isEmpty())provider+=" / ";
                            provider+="Candidate: "+candidates.get(0).getCustomName()+
                                (candidates.size()>1?" (+"+(candidates.size()-1)+")":"");
                            if(candidates.size()==1)coord=candidates.get(0);
                        }
                    }
                    if(Loader.isModLoaded("gregtech")){
                        MachineResolver.Result resolved=MachineResolver.resolve(dispatch,coord,cpu.getGrid(),player,budget);
                        if(resolved!=null){
                            GregTechProbe.Reading reading=resolved.reading;
                            route=resolved.route;
                            provider+=" / "+reading.state;progress=reading.progress;max=reading.max;machine=reading.machine;
                        }
                    }
                }
            }
            nodes.add(new Snapshot.Node(label,reason,provider,runs,stored,active,pending,progress,max,id,damage,edges,machine,dispatched,returned,destinations,route));
        }
        IAEStack<?> output=cpu.getFinalMultiOutput();
        JobTrack.observeOutput(cpu,output);
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
}
