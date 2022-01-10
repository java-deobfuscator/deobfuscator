package com.javadeobfuscator.deobfuscator.transformers.general.peephole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.iterablematcher.IterableInsnMatcher;
import com.javadeobfuscator.deobfuscator.iterablematcher.NoSideEffectLoad1SlotStep;
import com.javadeobfuscator.deobfuscator.iterablematcher.NoSideEffectLoad2SlotStep;
import com.javadeobfuscator.deobfuscator.iterablematcher.SimpleStep;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.jooq.lambda.tuple.Tuple3;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;

public class StackOperationSimplifier extends Transformer<TransformerConfig> {

    private final List<Tuple3<String, IterableInsnMatcher, Predicate<IterableInsnMatcher>>> operations = new ArrayList<>();

    {
        // POP - POP => POP2
        {
            SimpleStep step1 = new SimpleStep(POP);
            SimpleStep step2 = new SimpleStep(POP);
            operations.add(new Tuple3<>("Simplified %1$s POP-POP to POP2", new IterableInsnMatcher(m -> {
                m.addStep(step1);
                m.addStep(step2);
            }), m -> {
                m.addRemoval(step1);
                m.addReplacement(step2, new InsnNode(POP2));
                return true;
            }));
        }
        // LDC1 - DUP => LDC LDC
        {
            NoSideEffectLoad1SlotStep step1 = new NoSideEffectLoad1SlotStep(false);
            SimpleStep step2 = new SimpleStep(DUP);
            operations.add(new Tuple3<>("Simplified %1$s LDC1-DUP to LDC1-LDC1", new IterableInsnMatcher(m -> {
                m.addStep(step1);
                m.addStep(step2);
            }), m -> {
                m.addReplacement(step2, step1.getCaptured().clone(null));
                return true;
            }));
        }
        // LDC2 - DUP2 => LDC2 LDC2
        {
            NoSideEffectLoad2SlotStep step1 = new NoSideEffectLoad2SlotStep(false);
            SimpleStep step2 = new SimpleStep(DUP2);
            operations.add(new Tuple3<>("Simplified %1$s LDC2-DUP2 to LDC2-LDC2", new IterableInsnMatcher(m -> {
                m.addStep(step1);
                m.addStep(step2);
            }), m -> {
                m.addReplacement(step2, step1.getCaptured().clone(null));
                return true;
            }));
        }
        // INEG - INEG => nothing
        {
            operations.add(new Tuple3<>("Removed %1$s double INEG", new IterableInsnMatcher(m -> {
                m.addStep(new SimpleStep(INEG));
                m.addStep(new SimpleStep(INEG));
            }), m -> {
                m.setRemoveAll();
                return true;
            }));
        }
        // LNEG - LNEG => nothing
        {
            operations.add(new Tuple3<>("Removed %1$s double LNEG", new IterableInsnMatcher(m -> {
                m.addStep(new SimpleStep(LNEG));
                m.addStep(new SimpleStep(LNEG));
            }), m -> {
                m.setRemoveAll();
                return true;
            }));
        }
        // DUP - POP2 => POP
        {
            operations.add(new Tuple3<>("Simplified %1$s DUP-POP2 to POP", new IterableInsnMatcher(m -> {
                m.addStep(new SimpleStep(DUP));
                m.addStep(new SimpleStep(POP2));
            }), m -> {
                m.setRemoval(0);
                m.setReplacement(1, new InsnNode(POP));
                return true;
            }));
        }
        // LDC1 - POP => nothing
        {
            operations.add(new Tuple3<>("Removed %1$s LDC1-POP", new IterableInsnMatcher(m -> {
                m.addStep(new NoSideEffectLoad1SlotStep(true));
                m.addStep(new SimpleStep(POP));
            }), m -> {
                m.setRemoveAll();
                return true;
            }));
        }
        // LDC1 - LDC1 - POP2 => nothing
        {
            operations.add(new Tuple3<>("Removed %1$s LDC1-LDC1-POP2", new IterableInsnMatcher(m -> {
                m.addStep(new NoSideEffectLoad1SlotStep(true));
                m.addStep(new NoSideEffectLoad1SlotStep(true));
                m.addStep(new SimpleStep(POP2));
            }), m -> {
                m.setRemoveAll();
                return true;
            }));
        }
        // LDC2 - POP2 => nothing
        {
            operations.add(new Tuple3<>("Removed %1$s LDC2-POP2", new IterableInsnMatcher(m -> {
                m.addStep(new NoSideEffectLoad2SlotStep(true));
                m.addStep(new SimpleStep(POP2));
            }), m -> {
                m.setRemoveAll();
                return true;
            }));
        }
        // LDC1 - POP2 => POP
        {
            NoSideEffectLoad1SlotStep step1 = new NoSideEffectLoad1SlotStep(true);
            SimpleStep step2 = new SimpleStep(POP2);
            operations.add(new Tuple3<>("Simplified %1$s LDC1-POP2 to POP", new IterableInsnMatcher(m -> {
                m.addStep(step1);
                m.addStep(step2);
            }), m -> {
                m.addRemoval(step1);
                m.addReplacement(step2, new InsnNode(POP));
                return true;
            }));
        }
        // LDC2 - LDC2 - DUP2_X2 - POP2 - POP2 => LDC2 (the second ldc2)
        {
            NoSideEffectLoad2SlotStep step1 = new NoSideEffectLoad2SlotStep(true);
            NoSideEffectLoad2SlotStep step2 = new NoSideEffectLoad2SlotStep(true);
            SimpleStep step3 = new SimpleStep(DUP2_X2);
            SimpleStep step4 = new SimpleStep(POP2);
            SimpleStep step5 = new SimpleStep(POP2);
            operations.add(new Tuple3<>("Simplified %1$s LDC2-LDC2-DUP2_X2-POP2-POP2 to LDC2 (2nd LDC)", new IterableInsnMatcher(m -> {
                m.addStep(step1);
                m.addStep(step2);
                m.addStep(step3);
                m.addStep(step4);
                m.addStep(step5);
            }), m -> {
                m.addRemoval(step1);
                // the second ldc (step2) stays
                m.addRemoval(step3);
                m.addRemoval(step4);
                m.addRemoval(step5);
                return true;
            }));
        }
        // LDC1 - LDC1 - DUP_X1 - POP => LDC1 - LDC1 (LDC's swapped)
        {
            NoSideEffectLoad1SlotStep step1 = new NoSideEffectLoad1SlotStep(true);
            NoSideEffectLoad1SlotStep step2 = new NoSideEffectLoad1SlotStep(true);
            SimpleStep step3 = new SimpleStep(DUP_X1);
            SimpleStep step4 = new SimpleStep(POP);
            operations.add(new Tuple3<>("Simplified %1$s LDC1-LDC1-DUP_X1-POP to LDC1-LDC1 (swapped)", new IterableInsnMatcher(m -> {
                m.addStep(step1);
                m.addStep(step2);
                m.addStep(step3);
                m.addStep(step4);
            }), m -> {
                m.addRemoval(step1);
                m.addRemoval(step2);
                m.addReplacement(step3, step2.getCaptured());
                m.addReplacement(step4, step1.getCaptured());
                return true;
            }));
        }
        // LDC1 - LDC1 - SWAP - DUP_X1 - POP2 - LDC1 - POP2 => nothing
        {
            NoSideEffectLoad1SlotStep step1 = new NoSideEffectLoad1SlotStep(true);
            NoSideEffectLoad1SlotStep step2 = new NoSideEffectLoad1SlotStep(true);
            SimpleStep step3 = new SimpleStep(SWAP);
            SimpleStep step4 = new SimpleStep(DUP_X1);
            SimpleStep step5 = new SimpleStep(POP2);
            NoSideEffectLoad1SlotStep step6 = new NoSideEffectLoad1SlotStep(true);
            SimpleStep step7 = new SimpleStep(POP2);
            operations.add(new Tuple3<>("Removed %1$s LDC1-LDC1-SWAP-DUP_X1-POP2-LDC1-POP2", new IterableInsnMatcher(m -> {
                m.addStep(step1);
                m.addStep(step2);
                m.addStep(step3);
                m.addStep(step4);
                m.addStep(step5);
                m.addStep(step6);
                m.addStep(step7);
            }), m -> {
                m.setRemoveAll();
                return true;
            }));
        }
        // LDC1 - LDC1 - SWAP => LDC1 - LDC1 (LDC's swapped)
        {
            NoSideEffectLoad1SlotStep step1 = new NoSideEffectLoad1SlotStep(true);
            NoSideEffectLoad1SlotStep step2 = new NoSideEffectLoad1SlotStep(true);
            SimpleStep step3 = new SimpleStep(SWAP);
            operations.add(new Tuple3<>("Simplified %1$s LDC1-LDC1-SWAP to LDC1-LDC1 (swapped)", new IterableInsnMatcher(m -> {
                m.addStep(step1);
                m.addStep(step2);
                m.addStep(step3);
            }), m -> {
                m.addReplacement(step1, step2.getCaptured().clone(null));
                m.addReplacement(step2, step1.getCaptured().clone(null));
                m.addRemoval(step3);
                return true;
            }));
        }
        // LDC1 - SWAP - POP => POP - LDC1
        {
            NoSideEffectLoad1SlotStep step1 = new NoSideEffectLoad1SlotStep(true);
            SimpleStep step2 = new SimpleStep(SWAP);
            SimpleStep step3 = new SimpleStep(POP);
            operations.add(new Tuple3<>("Simplified %1$s LDC1-SWAP-POP to POP-LDC1", new IterableInsnMatcher(m -> {
                m.addStep(step1);
                m.addStep(step2);
                m.addStep(step3);
            }), m -> {
                m.addReplacement(step1, new InsnNode(POP));
                m.addReplacement(step2, step1.getCaptured().clone(null));
                m.addRemoval(step3);
                return true;
            }));
        }
    }

    @Override
    public boolean transform() throws Throwable {
        Map<Tuple3<String, IterableInsnMatcher, Predicate<IterableInsnMatcher>>, LongAdder> counters = new HashMap<>();
        AtomicBoolean changed = new AtomicBoolean(false);
        classNodes().forEach(classNode -> classNode.methods.stream().filter(Utils::notAbstractOrNative).forEach(methodNode -> {
            boolean edit = false;
            do {
                if (edit) {
                    changed.set(true);
                }
                edit = false;
                ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();
                for (Tuple3<String, IterableInsnMatcher, Predicate<IterableInsnMatcher>> key : operations) {
                    IterableInsnMatcher matcher = key.v2;
                    if (!matcher.match(iterator)) {
                        continue;
                    }
                    // match successful, apply matcher-specific logic
                    // matcher-specific logic can stop replacement in case additional constraints were not met
                    if (!key.v3.test(matcher)) {
                        matcher.reset();
                        continue;
                    }
                    matcher.replace(iterator);
                    matcher.reset();
                    edit = true;
                    counters.computeIfAbsent(key, (key_) -> new LongAdder()).increment();
                }
            } while (edit);
        }));
        counters.forEach((entry, counter) -> {
            System.out.printf(entry.v1, counter.sum());
            System.out.println();
        });
        //TODO output
        return changed.get();
    }
}
