# Payments: Stripe Payment Links

Status: built 2026-11-27. Owner chose Stripe Payment Links. No live link is set yet, so every offer shows
"Payments open soon".

## How it works

- `GET /api/v1/pricing?language=pl|da|de` returns the offers for that language, with the owner's prices
  (confirmed 2026-11-25) and a buy link when one is set.
- The exam intro screen in Echo shows these offers under "Get the full course".
- Paying happens on Stripe's own page. The backend holds **no Stripe keys** and never sees card details.
  A payment link is a public URL, not a secret.
- The "first 50 buyers per language keep the launch price" rule is set **on the link in Stripe**
  (limit the number of payments to 50). Subscribers keep the price they signed up at.

| Offer | Languages | Launch price / month | Later / month | Railway variable |
|---|---|---|---|---|
| Exam-readiness course | Polish | 100 PLN | 720 PLN | `PAYMENT_LINK_EXAM_PL` |
| Exam-readiness course | Danish | 175 DKK | 1,249 DKK | `PAYMENT_LINK_EXAM_DA` |
| Exam-readiness course | German | €25 | €169 | `PAYMENT_LINK_EXAM_DE` |
| Language course | Danish | 175 DKK | 1,249 DKK | `PAYMENT_LINK_COURSE_DA` |
| Language course | German | €25 | €169 | `PAYMENT_LINK_COURSE_DE` |

Polish has no regular course yet (Echo offers Polish as B1 exam practice only), so it has one offer.

## Owner steps (about 20 minutes)

1. Create a Stripe account at stripe.com and finish the business details (needed before live payments).
2. In Stripe: Product catalogue, add a product per row above, with a **recurring monthly** price in that
   currency (for example "Exam-readiness course, Polish", 100 PLN monthly).
3. For each product: Payment Links, Create, choose the price, and under "Advanced" turn on
   **Limit the number of payments** = 50. Optionally turn on BLIK / Przelewy24 for PLN and MobilePay for DKK.
4. Copy each link (it starts with `https://buy.stripe.com/`).
5. Railway, backend service, Variables: add the variables above with those links. The backend restarts and the
   buttons switch to "Join for ...". Only `https` links are accepted; anything else counts as not set.
6. After 50 sales, Stripe stops that link. Create a new link at the full price and replace the variable.

## Not done yet (deliberately small)

- **Access is not unlocked automatically.** Everyone still sees all practice tests. Unlocking after payment
  needs a Stripe webhook and a secret signing key; that is the next step once real buyers exist.
- AI marking of writing and speaking (part of what the offers promise) runs only when `ANTHROPIC_API_KEY`
  is set on the backend; it is not set yet. Turn it on before opening payments.
- VAT, invoices, refunds and terms of sale: open owner decisions. Stripe Tax can add VAT.
