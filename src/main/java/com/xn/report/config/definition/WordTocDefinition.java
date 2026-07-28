package com.xn.report.config.definition;

public class WordTocDefinition {

    private boolean enabled;
    private int maxLevel = 3;
    private boolean updateOnOpen;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public boolean isUpdateOnOpen() {
        return updateOnOpen;
    }

    public void setUpdateOnOpen(boolean updateOnOpen) {
        this.updateOnOpen = updateOnOpen;
    }
}
