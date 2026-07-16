package com.eternalengine.vip;

public enum VipTakeoverMethod {
    DUEL,
    BATTLE,
    ASSASSINATION,
    MURDER,
    UNKNOWN;

    public boolean isLegitimateCombat() {
        return this == DUEL || this == BATTLE;
    }
}
