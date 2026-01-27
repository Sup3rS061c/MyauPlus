package myau.events;

import lombok.Getter;
import myau.event.events.Event;

@Getter
public class Shader2DEvent implements Event {
    public enum ShaderType {
        GLOW,
        OUTLINE
    }

    private final ShaderType shaderType;

    public Shader2DEvent(ShaderType shaderType) {
        this.shaderType = shaderType;
    }

}