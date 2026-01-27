package myau.property;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import myau.module.Module;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

@SuppressWarnings("unchecked")
public abstract class Property<T> {
    @Getter
    private final String name;
    private final Predicate<T> validator;
    private final BooleanSupplier visibleChecker;
    @Getter
    private T value;
    @Setter
    private Module owner;

    protected Property(String name, Object value, BooleanSupplier visibleChecker) {
        this(name, value, null, visibleChecker);
    }

    protected Property(String name, Object value, Predicate<T> predicate, BooleanSupplier visibleChecker) {
        this.name = name;
        this.validator = predicate;
        this.visibleChecker = visibleChecker;
        this.value = (T) value;
        this.owner = null;
    }

    public abstract String getValuePrompt();

    public boolean isVisible() {
        return this.visibleChecker == null || this.visibleChecker.getAsBoolean();
    }

    public abstract String formatValue();

    public boolean setValue(Object object) {
        if (this.validator != null && !this.validator.test((T) object)) {
            return false;
        } else {
            this.value = (T) object;
            if (this.owner != null) {
                this.owner.verifyValue(this.name);
            }
            return true;
        }
    }

    public abstract boolean parseString(String string);

    public abstract boolean read(JsonObject jsonObject);

    public abstract void write(JsonObject jsonObject);
}
