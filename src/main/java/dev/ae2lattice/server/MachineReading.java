package dev.ae2lattice.server;
import dev.ae2lattice.net.Snapshot;
final class MachineReading {
    final String state;
    final int progress,max;
    final Snapshot.Machine machine;
    MachineReading(String state,int progress,int max,Snapshot.Machine machine){
        this.state=state;this.progress=progress;this.max=max;this.machine=machine;
    }
}
