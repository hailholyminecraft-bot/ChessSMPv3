package com.chesssmp.abilities;

@FunctionalInterface
public interface AbilityAction {
    /**
     * Executes the ability. Return false (usually via {@link AbilityContext#fail(String)}) when the ability could not
     * be used - no cooldown is consumed in that case.
     */
    boolean execute(AbilityContext ctx);
}
