package com.ptmuk.shop;

/** How paying at a self-checkout went (see SelfCheckoutBlockEntity.pay). */
public final class SelfCheckout {
    public enum Result { OK, NOTHING, NO_CARD, DECLINED, SHORT, WAIT }

    public record Reply(Result result, long total, long change) {
    }

    private SelfCheckout() {
    }
}
