//This class is full of shit by gemini!
package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.RenderUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final IntProperty minLatency = new IntProperty("Min Latency", 50, 10, 1000);
    private final IntProperty maxLatency = new IntProperty("Max Latency", 100, 10, 1000);
    private final FloatProperty minDistance = new FloatProperty("Min Distance", 0.0f, 0.0f, 3.0f);
    private final FloatProperty maxDistance = new FloatProperty("Max Distance", 6.0f, 0.0f, 10.0f);
    private final IntProperty stopOnTargetHurtTime = new IntProperty("Stop Target HurtTime", 10, -1, 10);
    private final IntProperty stopOnSelfHurtTime = new IntProperty("Stop Self HurtTime", 10, -1, 10);
    private final BooleanProperty drawRealPosition = new BooleanProperty("Draw Real Position", true);

    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final List<Packet<?>> skipPackets = new ArrayList<>();

    private EntityLivingBase target;
    private Vec3 realTargetPos;
    private int currentLatency = 0;

    private double renderX, renderY, renderZ;

    public BackTrack() {
        super("BackTrack", "Allows you to hit past opponents", Category.COMBAT, 0, false, false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{(currentLatency == 0 ? maxLatency.getValue() : currentLatency) + "ms"};
    }

    @Override
    public void onEnabled() {
        resetStates();
    }

    @Override
    public void onDisabled() {
        releaseAll();
        resetStates();
    }

    private void resetStates() {
        packetQueue.clear();
        skipPackets.clear();
        realTargetPos = null;
        target = null;
        renderX = 0;
        renderY = 0;
        renderZ = 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        if (event.getType() == EventType.PRE) {
            // 距离检查：如果目标太远，强制停止 Backtrack
            if (target != null && realTargetPos != null) {
                double distance = realTargetPos.distanceTo(mc.thePlayer.getPositionVector());
                if (distance > maxDistance.getValue() || distance < minDistance.getValue()) {
                    releaseAll(); // 距离不满足时释放所有包
                }
            }

            // 处理队列中的数据包
            while (!packetQueue.isEmpty()) {
                TimedPacket timedPacket = packetQueue.peek();
                if (timedPacket != null && timedPacket.timer.hasTimeElapsed(currentLatency)) {
                    packetQueue.poll();
                    Packet<?> packet = timedPacket.packet;
                    skipPackets.add(packet);
                    handlePacket(packet);
                } else {
                    break;
                }
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        if (target == null || realTargetPos == null || target.isDead || !drawRealPosition.getValue())
            return;

        Vec3 pos = realTargetPos;

        if (renderX == 0 && renderY == 0 && renderZ == 0) {
            renderX = pos.xCoord;
            renderY = pos.yCoord;
            renderZ = pos.zCoord;
        }

        renderX = RenderUtil.lerpDouble(pos.xCoord, renderX, 0.5);
        renderY = RenderUtil.lerpDouble(pos.yCoord, renderY, 0.5);
        renderZ = RenderUtil.lerpDouble(pos.zCoord, renderZ, 0.5);

        double viewerX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double viewerY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double viewerZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        double x = renderX - viewerX;
        double y = renderY - viewerY;
        double z = renderZ - viewerZ;

        double width = target.width / 2.0;
        double height = target.height;

        AxisAlignedBB box = new AxisAlignedBB(
                x - width, y, z - width,
                x + width, y + height, z + width
        );

        Color color = new Color(72, 125, 227, 100);

        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(box, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        Packet<?> packet = event.getPacket();

        if (event.getType() == EventType.SEND) {
            if (packet instanceof C02PacketUseEntity) {
                C02PacketUseEntity wrapper = (C02PacketUseEntity) packet;
                if (wrapper.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    Entity entity = wrapper.getEntityFromWorld(mc.theWorld);
                    if (entity instanceof EntityLivingBase) {
                        EntityLivingBase newTarget = (EntityLivingBase) entity;

                        // 核心修复：只有当目标改变，或者还没有初始化位置时，才重置 realTargetPos
                        // 这样连续攻击同一个目标时，realTargetPos 会保持由 Receive 包更新的状态，不会跳回
                        if (this.target != newTarget || this.realTargetPos == null) {
                            this.target = newTarget;
                            this.realTargetPos = new Vec3(target.serverPosX / 32.0D, target.serverPosY / 32.0D, target.serverPosZ / 32.0D);

                            this.renderX = this.realTargetPos.xCoord;
                            this.renderY = this.realTargetPos.yCoord;
                            this.renderZ = this.realTargetPos.zCoord;

                            // 只有切换目标时才重新随机延迟，或者你想每次攻击都随机也可以放在外面
                            double distance = realTargetPos.distanceTo(mc.thePlayer.getPositionVector());
                            if (distance <= maxDistance.getValue() && distance >= minDistance.getValue()) {
                                int min = minLatency.getValue();
                                int max = maxLatency.getValue();
                                this.currentLatency = min + (int) (Math.random() * ((max - min) + 1));
                            }
                        }
                    }
                }
            }
        } else if (event.getType() == EventType.RECEIVE) {
            if (skipPackets.contains(packet)) {
                skipPackets.remove(packet);
                return;
            }

            // 伤害时间检查
            if (target != null && stopOnTargetHurtTime.getValue() != -1 && target.hurtTime > stopOnTargetHurtTime.getValue()) {
                releaseAll();
                return;
            }
            if (stopOnSelfHurtTime.getValue() != -1 && mc.thePlayer.hurtTime > stopOnSelfHurtTime.getValue()) {
                releaseAll();
                return;
            }

            if (mc.thePlayer.ticksExisted < 20 || target == null || target.isDead) {
                releaseAll();
                return;
            }

            if (packet instanceof S19PacketEntityStatus
                    || packet instanceof S02PacketChat
                    || packet instanceof S0BPacketAnimation
                    || packet instanceof S06PacketUpdateHealth
                    || packet instanceof S08PacketPlayerPosLook
                    || packet instanceof S40PacketDisconnect) {

                if (packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect) {
                    releaseAll();
                }
                return;
            }

            if (packet instanceof S13PacketDestroyEntities) {
                S13PacketDestroyEntities wrapper = (S13PacketDestroyEntities) packet;
                for (int id : wrapper.getEntityIDs()) {
                    if (target != null && id == target.getEntityId()) {
                        releaseAll();
                        return;
                    }
                }
            }

            boolean shouldCancel = false;

            if (packet instanceof S14PacketEntity) {
                S14PacketEntity wrapper = (S14PacketEntity) packet;
                Entity entity = wrapper.getEntity(mc.theWorld);
                if (entity != null && target != null && entity.getEntityId() == target.getEntityId()) {
                    if (realTargetPos != null) {
                        // S14 累加逻辑
                        realTargetPos = realTargetPos.addVector(
                                wrapper.func_149062_c() / 32.0D,
                                wrapper.func_149061_d() / 32.0D,
                                wrapper.func_149064_e() / 32.0D
                        );
                    }
                    shouldCancel = true;
                }
            } else if (packet instanceof S18PacketEntityTeleport) {
                S18PacketEntityTeleport wrapper = (S18PacketEntityTeleport) packet;
                if (wrapper.getEntityId() == target.getEntityId()) {
                    // S18 绝对定位
                    realTargetPos = new Vec3(wrapper.getX() / 32.0D, wrapper.getY() / 32.0D, wrapper.getZ() / 32.0D);
                    shouldCancel = true;
                }
            }

            if (shouldCancel) {
                packetQueue.add(new TimedPacket(packet));
                event.setCancelled(true);
            }
        }
    }

    private void releaseAll() {
        if (!packetQueue.isEmpty()) {
            for (TimedPacket timedPacket : packetQueue) {
                Packet<?> packet = timedPacket.packet;
                skipPackets.add(packet);
                handlePacket(packet);
            }
            packetQueue.clear();
        }
    }

    private void handlePacket(Packet<?> packet) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        if (mc.getNetHandler() != null) {
            try {
                ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class TimedPacket {
        private final Packet<?> packet;
        private final TimerUtil timer;

        public TimedPacket(Packet<?> packet) {
            this.packet = packet;
            this.timer = new TimerUtil();
            this.timer.reset();
        }
    }
}