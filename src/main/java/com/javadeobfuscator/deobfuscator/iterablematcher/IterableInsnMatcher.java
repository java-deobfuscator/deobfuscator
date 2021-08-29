package com.javadeobfuscator.deobfuscator.iterablematcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

public class IterableInsnMatcher {

    private final List<IterableStep<?>> steps = new ArrayList<>();
    private final Map<IterableStep<?>, Optional<AbstractInsnNode>> replacements = new HashMap<>();
    private final List<AbstractInsnNode> endInserts = new ArrayList<>(4);
    private boolean hasMatched;
    private boolean hasReplaced;

    public IterableInsnMatcher() {
    }

    public IterableInsnMatcher(Consumer<IterableInsnMatcher> initializer) {
        initializer.accept(this);
    }

    public <N extends AbstractInsnNode, T extends IterableStep<N>> T addStep(T step) {
        Preconditions.checkState(!hasReplaced, "Reset before adding steps needed, already matched & replaced");
        Preconditions.checkState(!hasMatched, "Reset before adding steps needed, already matched");
        for (IterableStep<?> s : steps) {
            if (s == step) {
                throw new IllegalArgumentException("step " + step + " is already added");
            }
        }
        steps.add(step);
        return step;
    }

    /**
     * leaves the given iterator just after the first matching instruction
     */
    public boolean match(ListIterator<AbstractInsnNode> iterator) {
        if (steps.isEmpty()) {
            throw new IllegalStateException("At least 1 step is required");
        }
        while (iterator.hasNext()) {
            if (match0(iterator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * leaves the given iterator just after the first matching instruction
     */
    public boolean matchImmediately(ListIterator<AbstractInsnNode> iterator) {
        if (steps.isEmpty()) {
            throw new IllegalStateException("At least 1 step is required");
        }
        if (!iterator.hasNext()) {
            return false;
        }
        return match0(iterator);
    }

    private boolean match0(ListIterator<AbstractInsnNode> iterator) {
        int size = steps.size();
        int stepCount = 0;
        AbstractInsnNode next = iterator.next();
        while (next != null) {
            IterableStep<?> step = steps.get(stepCount);
            if (!step.match(next)) {
                break;
            }
            stepCount++;
            if (stepCount == size) {
                hasMatched = true;
                return true;
            }
            next = next.getNext();
        }
        return false;
    }

    public void addReplacement(IterableStep<?> step, AbstractInsnNode replacement) {
        Preconditions.checkArgument(steps.contains(step), "Step was not added - cannot add replacement for it. Step: " + step);
        Preconditions.checkState(hasMatched, "Cannot add replacements before matching");
        replacements.put(step, Optional.ofNullable(replacement));
    }

    public void addRemoval(IterableStep<?> step) {
        addReplacement(step, null);
    }

    public void addEndInsert(AbstractInsnNode step) {
        endInserts.add(step);
    }

    public void setRemoveAll() {
        steps.forEach(this::addRemoval);
    }

    public void setRemoval(int stepNumber) {
        addRemoval(steps.get(stepNumber));
    }

    public void setReplacement(int stepNumber, AbstractInsnNode replacement) {
        addReplacement(steps.get(stepNumber), replacement);
    }

    /**
     * perform previously set up replacement / removal
     * 
     * iterator afterwards will be just after the last matched step
     *
     * @param iterator must be in same state as it was after match was called
     */
    @SuppressWarnings("OptionalAssignedToNull")
    public boolean replace(ListIterator<AbstractInsnNode> iterator) {
        Preconditions.checkState(iterator.hasPrevious(), "iterator not genuine, no previous");
        iterator.previous();
        boolean change = false;
        for (IterableStep<?> step : steps) {
            AbstractInsnNode next = iterator.next();
            Preconditions.checkState(step.getCaptured() == next,
                    "iterator not genuine, expected " + Utils.prettyprint(step.getCaptured()) + " but got " + Utils.prettyprint(next));
            Optional<AbstractInsnNode> replacement = replacements.get(step);
            if (replacement != null) {
                if (replacement.isPresent()) {
                    iterator.set(replacement.get());
                } else {
                    iterator.remove();
                }
                change = true;
            }
        }
        if (!endInserts.isEmpty()) {
            endInserts.forEach(iterator::add);
            return true;
        }
        return change;
    }

    /**
     * resets internals and replacements for next match
     */
    public void reset() {
        for (IterableStep<?> step : steps) {
            step.reset();
        }
        hasMatched = false;
        hasReplaced = false;
        endInserts.clear();
    }
    
    public static int removeAll(IterableInsnMatcher matcher, InsnList insns) {
        int count = 0;
        ListIterator<AbstractInsnNode> it = insns.iterator();
        while (it.hasNext()) {
            if (matcher.match(it)) {
                matcher.setRemoveAll();
                if (!matcher.replace(it)) {
                    throw new IllegalStateException("Could not find anything to replace!");
                }
                matcher.reset();
                ++count;
            }
        }
        return count;
    }
}
