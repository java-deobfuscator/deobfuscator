package com.javadeobfuscator.deobfuscator.analyzer.frame;

import java.util.Arrays;
import java.util.List;

public class DupFrame extends Frame {
    private List<Frame> targets;

    public DupFrame(int opcode, Frame... targets) {
        super(opcode);
        this.targets = Arrays.asList(targets);
        for (Frame target : this.targets) {
            target.getChildren().add(this);
        }
        this.parents.addAll(this.targets);
    }

    public List<Frame> getTargets() {
        return targets;
    }
}
