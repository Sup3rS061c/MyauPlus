package myau.events;

import lombok.Getter;
import myau.event.events.Event;
import myau.event.types.EventType;
import net.minecraft.entity.EntityLivingBase;

@Getter
public class RenderLivingEvent implements Event {
    private final EventType type;
    private final EntityLivingBase entity;

    public RenderLivingEvent(EventType type, EntityLivingBase entityLivingBase) {
        this.type = type;
        this.entity = entityLivingBase;
    }

}
