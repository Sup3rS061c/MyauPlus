package myau.mixin;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin(KeyBinding.class)
public interface IAccessorKeyBinding {
    @Accessor("pressed")
    boolean getPressed();

    @Accessor("pressed")
    void setPressed(boolean pressed);
}