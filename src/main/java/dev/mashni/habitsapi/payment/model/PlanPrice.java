package dev.mashni.habitsapi.payment.model;

public enum PlanPrice {
    PRO_MONTHLY(990, 30, "Plano PRO Mensal"),
    PRO_QUARTERLY(2590, 90, "Plano PRO Trimestral"),
    PRO_YEARLY(9999, 365, "Plano PRO Anual");

    private final int priceInCents;
    private final int durationInDays;
    private final String description;

    PlanPrice(int priceInCents, int durationInDays, String description) {
        this.priceInCents = priceInCents;
        this.durationInDays = durationInDays;
        this.description = description;
    }

    public int getPriceInCents() {
        return priceInCents;
    }

    public int getDurationInDays() {
        return durationInDays;
    }

    public String getDescription() {
        return description;
    }
}