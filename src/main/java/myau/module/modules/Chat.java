package myau.module.modules;

import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;

public class Chat extends Module {

    public static Chat INSTANCE;

    public final BooleanProperty enableShadow = new BooleanProperty("Enable Shadow", true);
    public final FloatProperty shadowRadius = new FloatProperty("Shadow Radius", 4.0F, 0.0F, 20.0F);
    public final FloatProperty shadowOpacity = new FloatProperty("Shadow Opacity", 0.6F, 0.0F, 1.0F);
    public final BooleanProperty enableRoundCorners = new BooleanProperty("Round Corners", true);
    public final FloatProperty cornerRadius = new FloatProperty("Corner Radius", 4.0F, 0.0F, 20.0F);
    public final FloatProperty backgroundOpacity = new FloatProperty("Background Opacity", 0.6F, 0.0F, 1.0F);

    public Chat() {
        super("Chat", "Customize chat rendering with shadows and rounded corners", Category.RENDER, 0, false, false);
        INSTANCE = this;


    }
}