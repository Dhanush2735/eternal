package com.eternalengine.money;

import java.util.UUID;

/**
 * Step 23b: what a player pays from and is paid into. In the game it is the pieces of money in the player's inventory
 * ({@link CoinPurse}), with a written tab for barter and money counted in days of work; the checks with no world use
 * the old purse of numbers ({@link PurseWallet}).
 */
public interface Wallet {

    /** What is held, as a purse: money id to amount (smallest units), and the start-money flags. */
    PlayerPurses.Purse view();

    /** How much of one money is held. */
    default long amount(String moneyId) {
        return view().amount(moneyId);
    }

    /**
     * Pay exactly {@code amount} of one money; change comes back in the same money.
     *
     * @return false, and nothing changes, when there is too little
     */
    boolean pay(String moneyId, long amount, int year);

    /**
     * Receive {@code amount} of one money.
     *
     * @return what really came (pieces round down to the smallest piece), or -1 when it cannot be held
     */
    long receive(String moneyId, long amount, int year);

    /** Hand over all of one money (the money-changer); returns how much it was. */
    long takeAll(String moneyId);

    /** The purse of numbers of one player (step 19b), for checks with no world. */
    final class PurseWallet implements Wallet {
        private final PlayerPurses purses;
        private final UUID player;

        public PurseWallet(PlayerPurses purses, UUID player) {
            this.purses = purses;
            this.player = player;
        }

        @Override
        public PlayerPurses.Purse view() {
            return purses.view(player);
        }

        @Override
        public boolean pay(String moneyId, long amount, int year) {
            return amount <= 0 || purses.add(player, moneyId, -amount) >= 0;
        }

        @Override
        public long receive(String moneyId, long amount, int year) {
            if (amount <= 0) {
                return 0L;
            }
            return purses.add(player, moneyId, amount) < 0 ? -1L : amount;
        }

        @Override
        public long takeAll(String moneyId) {
            long have = purses.view(player).amount(moneyId);
            if (have > 0) {
                purses.add(player, moneyId, -have);
            }
            return Math.max(0L, have);
        }
    }
}
