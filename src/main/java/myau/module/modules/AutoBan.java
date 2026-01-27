package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.util.PacketUtil;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

public class AutoBan extends Module {

    public AutoBan() {
        super("AutoBan", "Allows you to ban yourself", Category.MISC, 0, false, false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        sendMultiplePackets();
    }

    private void sendMultiplePackets() {
        for (int i = 0; i < 100; i++) {
            PacketUtil.sendPacket(new C0FPacketConfirmTransaction());
            PacketUtil.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw + 180, mc.thePlayer.rotationPitch + 180, false));
        }
    }
}