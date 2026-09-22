/**
 * Quotes a final price for an order.
 *
 * Steps (in order):
 *   1. Sum line items (qty * unit price) to a subtotal.
 *   2. Add an expedited-shipping surcharge if flagged.
 *   3. Apply a promo-code discount if the code is one of the known codes.
 *   4. Apply a loyalty-tier discount based on customer.loyaltyYears.
 *   5. Add regional tax.
 *
 */
public class PriceEngine {

    public static Money quote(Order order, Customer customer) {
        if (order == null || order.lines() == null || order.lines().isEmpty()) {
            throw new IllegalArgumentException("order has no lines");
        }
        if (customer == null) {
            throw new IllegalArgumentException("customer is null");
        }

        // 1. Subtotal
        String currency = order.lines().get(0).unit().currency();
        Money subtotal = Money.of(currency, 0.0);
        for (Order.Line l : order.lines()) {
            if (l.qty() <= 0) {
                continue;
            }
            subtotal = subtotal.add(l.unit().times(l.qty()));
        }

        Money running = subtotal;

        // 2. Expedited surcharge: flat 15 if the order is expedited.
        if (order.expedited()) {
            running = running.add(Money.of(currency, 15.0));
        }

        // 3. Promo code
        String code = order.promoCode();
        if (code != null) {
            if ("WELCOME10".equals(code)) {
                running = running.subtract(subtotal.times(0.10));
            } else if ("SUMMER15".equals(code)) {
                running = running.subtract(subtotal.times(0.15));
            } else if ("VIP20".equals(code) && customer.loyaltyYears() >= 3) {
                running = running.subtract(subtotal.times(0.20));
            }
        }

        // 4. Loyalty-tier discount
        running = applyLoyaltyDiscount(
            running, subtotal, customer.loyaltyYears());

        // 5. Regional tax
        double taxRate;
        String region = customer.region();
        if ("EU".equals(region)) {
            taxRate = 0.20;
        } else if ("US".equals(region)) {
            taxRate = 0.07;
        } else if ("IN".equals(region)) {
            taxRate = 0.18;
        } else {
            taxRate = 0.0;
        }
        if (taxRate > 0.0) {
            running = running.add(running.times(taxRate));
        }

        return running;
    }

    private static Money applyLoyaltyDiscount(Money running,
                                               Money subtotal,
                                               int years) {
        if (years >= 5) {
            return running.subtract(subtotal.times(0.10));
        }
        if (years >= 3) {
            return running.subtract(subtotal.times(0.05));
        }
        if (years >= 1) {
            return running.subtract(subtotal.times(0.02));
        }
        return running;
    }
}
