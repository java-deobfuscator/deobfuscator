package com.javadeobfuscator.deobfuscator.transformers.special;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;

public class RadonV2Config extends TransformerConfig {

    private boolean antiTamper = true;
    private boolean ejector = true;
    private boolean antiDebug = true;
    private boolean tryCatch = true;
    private boolean flowObf = true;
    private boolean stringPool = true;
    private boolean number = true;
    private boolean numberContextObf = true;
    private boolean indy = true;
    private boolean string = true;

    public RadonV2Config() {
        super(RadonTransformerV2.class);
    }

    public boolean isAntiTamper() {
        return antiTamper;
    }

    public void setAntiTamper(boolean antiTamper) {
        this.antiTamper = antiTamper;
    }

    public boolean isEjector() {
        return ejector;
    }

    public void setEjector(boolean ejector) {
        this.ejector = ejector;
    }

    public boolean isAntiDebug() {
        return antiDebug;
    }

    public void setAntiDebug(boolean antiDebug) {
        this.antiDebug = antiDebug;
    }
    
    public boolean isTryCatch() {
        return tryCatch;
    }

    public void setTryCatch(boolean tryCatch) {
        this.tryCatch = tryCatch;
    }

    public boolean isFlowObf() {
        return flowObf;
    }

    public void setFlowObf(boolean flowObf) {
        this.flowObf = flowObf;
    }
    
    public boolean isStringPool() {
        return stringPool;
    }

    public void setStringPool(boolean stringPool) {
        this.stringPool = stringPool;
    }

    public boolean isNumber() {
        return number;
    }

    public void setNumber(boolean number) {
        this.number = number;
    }

    public boolean isNumberContextObf() {
        return numberContextObf;
    }

    public void setNumberContextObf(boolean numberContextObf) {
        this.numberContextObf = numberContextObf;
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
}
