package dev.ae2lattice.server;

import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.util.NamedDimensionalCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.Loader;
import dev.ae2lattice.mixin.CpuAccessor;
import dev.ae2lattice.mixin.TaskAccessor;
import dev.ae2lattice.net.Snapshot;
import dev.ae2lattice.net.ItemNbt;
import net.minecraft.nbt.NBTTagCompound;
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
    static Snapshot build(CraftingCPUCluster cpu,EntityPlayerMP player,int window,int nonce,int requestedPage){
        if(!cpu.isBusy()) {
            JobTrack.clear(cpu);
            JobTrack.Finished done=JobTrack.finished(cpu);
            if(done!=null)return new Snapshot(window,nonce,cpu.getName(),done.output,done.status,
                done.elapsed,done.total,done.remaining,false,Collections.emptyList());
            return new Snapshot(window,nonce,cpu.getName(),"","Idle",0,0,0,false,Collections.emptyList());
        }
        JobTrack.Topology topology=JobTrack.topology(cpu);
        List<ICraftingPatternDetails> patterns=topology.patterns;
        Map<ICraftingPatternDetails,CraftingCPUCluster.TaskProgress> tasks=((CpuAccessor)(Object)cpu).lattice$getTasks();
        boolean truncated=patterns.size()>=Snapshot.MAX_TRACKED || tasks.size()>patterns.size();
        int count=patterns.size(),page=Snapshot.clampPage(requestedPage,count);
        int start=page*Snapshot.PAGE_SIZE,end=Math.min(count,start+Snapshot.PAGE_SIZE);
        Map<IAEStack<?>,List<Integer>> producers=topology.producers;
        List<Snapshot.Node> nodes=new ArrayList<>();
        int probes=0;
        MachineResolver.Budget budget=new MachineResolver.Budget();
        for(int i=start;i<end;i++){
            ICraftingPatternDetails pattern=patterns.get(i);
            Set<Integer> deps=new LinkedHashSet<>();
            for(IAEStack<?> input:pattern.getCondensedAEInputs()) if(input!=null){
                List<Integer> candidates=producers.get(key(input));
                if(candidates!=null) for(int idx:candidates) if(idx!=i) deps.add(idx);
            }
            if(deps.size()>Snapshot.MAX_EDGES) truncated=true;
            int[] edges=deps.stream().filter(d->d>=start && d<end).limit(Snapshot.MAX_EDGES).mapToInt(d->d-start).toArray();
            int[] external=deps.stream().filter(d->d<start || d>=end).limit(Snapshot.MAX_EDGES).mapToInt(Integer::intValue).toArray();
            IAEStack<?> out=first(pattern.getCondensedAEOutputs());
            CraftingCPUCluster.TaskProgress task=tasks.get(pattern);
            long runs=task==null?0:((TaskAccessor)(Object)task).lattice$getRemaining();
            String label=out==null?"Input-only pattern":out.getDisplayName();
            long stored=0,active=0,pending=0;
            String reason="",provider="";
            int progress=-1,max=-1,id=-1,damage=0;
            Snapshot.Machine machine=null;
            long dispatched=0,returned=0;int destinations=0;String route="",fluidName="";boolean recipeSafe=false;byte[] tag=new byte[0];
            if(out!=null){
                stored=cpu.getStackAmount(out,CraftingItemList.STORAGE);
                active=cpu.getStackAmount(out,CraftingItemList.ACTIVE);
                pending=cpu.getStackAmount(out,CraftingItemList.PENDING);
                if(pending>0) reason=cpu.getScheduledReason(out).name();
                ItemStack icon=out.getItemStackForNEI();
                if(icon!=null){id=Item.getIdFromItem(icon.getItem());damage=icon.getItemDamage();
                    if(out.isItem()){
                        byte[] encoded=ItemNbt.encode(icon.getTagCompound());
                        if(encoded!=null && (!out.hasTagCompound() || icon.hasTagCompound())){tag=encoded;recipeSafe=true;}
                    }
                }
                if(out instanceof IAEFluidStack){
                    IAEFluidStack fluid=(IAEFluidStack)out;
                    if(fluid.getFluid()!=null){
                        fluidName=fluid.getFluid().getName();
                        byte[] encoded=ItemNbt.encode(fluid.getFluidStack().tag);
                        recipeSafe=encoded!=null && fluidName.length()<=64 && (!out.hasTagCompound() || fluid.getFluidStack().tag!=null);
                        if(recipeSafe)tag=encoded;
                    }
                }
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
                    {
                        MachineResolver.Result resolved=MachineResolver.resolve(dispatch,coord,cpu.getGrid(),player,budget);
                        if(resolved!=null){
                            MachineReading reading=resolved.reading;
                            route=resolved.route;
                            provider+=" / "+reading.state;progress=reading.progress;max=reading.max;machine=reading.machine;
                            if(JobTrack.shared(cpu,pattern,dispatch,machine)){
                                route="Shared receiver: progress not attributed";progress=-1;max=-1;
                            }
                        }
                    }
                }
            }
            nodes.add(new Snapshot.Node(label,reason,provider,runs,stored,active,pending,progress,max,id,damage,edges,machine,dispatched,returned,destinations,route,external,
                describe(pattern.getCondensedAEInputs()),describe(pattern.getCondensedAEOutputs()),fluidName,recipeSafe,new Snapshot.Details(tag,preview(pattern.getCondensedAEInputs()),preview(pattern.getCondensedAEOutputs()))));
        }
        IAEStack<?> output=cpu.getFinalMultiOutput();
        JobTrack.observeOutput(cpu,output);
        return new Snapshot(window,nonce,cpu.getName(),output==null?"":output.getDisplayName(),
            cpu.isSuspended()?"Suspended":"Crafting",cpu.getElapsedTime(),cpu.getStartItemCount(),
            cpu.getRemainingItemCount(),truncated,nodes,page,count);
    }
    private static List<Snapshot.Slot> preview(IAEStack<?>[] stacks){
        List<Snapshot.Slot> result=new ArrayList<>();
        for(IAEStack<?> stack:stacks){
            if(stack==null || stack.getStackSize()<=0)continue;
            if(result.size()>=Snapshot.MAX_SLOTS)break;
            if(stack instanceof IAEFluidStack){
                IAEFluidStack fluid=(IAEFluidStack)stack;
                if(fluid.getFluid()!=null)result.add(new Snapshot.Slot(-1,0,stack.getStackSize(),0,fluid.getFluid().getName()));
            }else{
                ItemStack item=stack.getItemStackForNEI();
                if(item!=null)result.add(new Snapshot.Slot(Item.getIdFromItem(item.getItem()),item.getItemDamage(),stack.getStackSize(),0,""));
            }
        }
        return result;
    }
    private static String describe(IAEStack<?>[] stacks){
        StringBuilder result=new StringBuilder();
        for(IAEStack<?> s:stacks){
            if(s==null)continue;
            String entry=s.getStackSize()+"x "+s.getDisplayName();
            if(result.length()+entry.length()+2>60){
                if(result.length()==0)result.append(entry.substring(0,Math.min(60,entry.length())));
                result.append(" ...");break;
            }
            if(result.length()>0)result.append("; ");
            result.append(entry);
        }
        return result.toString();
    }
    private static IAEStack<?> key(IAEStack<?> stack){
        IAEStack<?> copy=stack.copy();copy.setStackSize(1);return copy;
    }
    private static IAEStack<?> first(IAEStack<?>[] stacks){
        for(IAEStack<?> s:stacks) if(s!=null)return s;return null;
    }
}
