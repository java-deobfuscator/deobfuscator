package com.javadeobfuscator.deobfuscator.analyzer.frame;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MathFrame extends Frame {

    private List<Frame> targets;

    public MathFrame(int opcode, Frame... targets) {
        super(opcode);
        this.targets = Arrays.asList(targets);
        for (Frame target : this.targets) {
            target.children.add(this);
        }
    }

    public List<Frame> getTargets() {
        return Collections.unmodifiableList(targets);
    }
}
