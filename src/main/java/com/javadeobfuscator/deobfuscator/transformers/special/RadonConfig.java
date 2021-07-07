package com.javadeobfuscator.deobfuscator.transformers.special;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;

public class RadonConfig extends TransformerConfig {

    private FlowMode flowMode = FlowMode.COMBINED;
    private NumberMode numberMode = NumberMode.NEW;
    private StringPoolMode stringPoolMode = StringPoolMode.NEW;
    private boolean indy = true;
    private boolean string = true;
    private boolean trashClasses = false;
    private boolean fastIndy = true;

    public RadonConfig() {
        super(RadonTransformer.class);
    }

    public enum FlowMode {
        NONE,
        LIGHT,
        NORMAL_OR_HEAVY,
        COMBINED
    }

    public enum NumberMode {
        NONE,
        LEGACY,
        NEW
    }

    public enum StringPoolMode {
        NONE,
        LEGACY,
        NEW
    }

    public FlowMode getFlowMode() {
        return flowMode;
    }

    public void setFlowMode(FlowMode flowMode) {
        this.flowMode = flowMode;
    }

    public NumberMode getNumberMode() {
        return numberMode;
    }

    public void setNumberMode(NumberMode numberMode) {
        this.numberMode = numberMode;
    }

    public StringPoolMode getStringPoolMode() {
        return stringPoolMode;
    }

    public void setStringPoolMode(StringPoolMode stringPoolMode) {
        this.stringPoolMode = stringPoolMode;
    }

    public boolean isIndy() {
        return indy;
    }

    public void setIndy(boolean indy) {
        this.indy = indy;
    }

    public boolean isString() {
        return string;
    }

    public void setString(boolean string) {
        this.string = string;
    }

    public boolean isTrashClasses() {
        return trashClasses;
    }

    public void setTrashClasses(boolean trashClasses) {
        this.trashClasses = trashClasses;
    }

    public boolean isFastIndy() {
        return fastIndy;
    }

    public void setFastIndy(boolean fastIndy) {
        this.fastIndy = fastIndy;
    }
}
