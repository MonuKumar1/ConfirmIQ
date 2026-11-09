/**
 * Knowledge base content. Bodies use a small markdown subset rendered by Markdown.tsx:
 * "## " / "### " headings, paragraphs, "- " bullets, "1. " numbered lists, "> " callouts,
 * "| a | b |" tables (first row = header), and **bold** / `code` inline.
 */
export interface Article {
  slug: string
  title: string
  category: string
  summary: string
  body: string
}

export const CATEGORIES = [
  'Getting started',
  'FX products',
  'Settlement & static data',
  'Identifiers & messaging',
  'Controls & operations',
  'Legal & regulatory',
]

export const ARTICLES: Article[] = [
  {
    slug: 'post-trade-lifecycle',
    title: 'The post-trade lifecycle',
    category: 'Getting started',
    summary: 'Where trade confirmation sits between execution and settlement, and who does what.',
    body: `
A trade is agreed in seconds, but it takes a chain of processes – owned by different teams – before money actually moves. Understanding this chain is the key to understanding why confirmations exist.

## Front, middle and back office

| Area | Typical teams | Responsibility |
|---|---|---|
| Front office | Sales, traders | Agree the trade with the client and book it in the trading system (e.g. Murex, Calypso). |
| Middle office | Risk, product control, trade support | Validate bookings, P&L, limits, market risk. |
| Back office / Operations | Confirmations, settlements, reconciliations | Confirm terms with the client, settle cash, reconcile accounts. |

ConfirmIQ is a **back-office (operations)** application.

## The lifecycle step by step

1. **Execution** – the trader agrees price and size with the client (phone, chat, electronic platform).
2. **Booking (trade capture)** – the trade is entered in the front-office system, which publishes a booking message downstream. In ConfirmIQ this arrives as XML or through the REST API.
3. **Enrichment** – operations add static data the trader never types: legal entity, LEI, confirmation contacts and **standard settlement instructions (SSIs)**.
4. **Confirmation** – both parties formally agree the economic terms in writing. This is the legal record of the trade.
5. **Matching / affirmation** – each side's version of the trade is compared. Differences are **breaks** that must be investigated.
6. **Settlement** – on the value date, each party pays the currency it sold to the other party's account.
7. **Reconciliation** – cash and positions are reconciled against bank statements (nostro reconciliation).

> A trade that settles without a confirmation is an uncontrolled risk: if the client later claims different terms, the bank has no agreed written record.

## Why confirmations matter

- **Legal certainty** – the confirmation, together with the master agreement, is the contract.
- **Error detection** – mis-bookings (wrong rate, wrong date, wrong direction) are caught before cash moves.
- **Fraud prevention** – unauthorised or fictitious trades are exposed when the client does not recognise them.
- **Regulation** – regulators require timely confirmation of OTC derivatives (see *Regulation*).
`,
  },
  {
    slug: 'confirmation-affirmation-matching',
    title: 'Confirmation, affirmation and matching',
    category: 'Getting started',
    summary: 'Three related terms that are often confused – and how each works in practice.',
    body: `
## Confirmation

A **confirmation** is a document (paper/PDF or electronic message) that one party sends to the other, setting out the full terms of a trade. It typically includes trade date, value date, currencies, amounts, rate, settlement instructions and a reference to the governing master agreement.

Two models exist:

- **One-way (issuer) confirmation** – one party (usually the dealer) issues the confirmation and the client signs and returns it, or confirms acceptance.
- **Two-way (bilateral) confirmation** – both parties send their own confirmation ("their paper") and each side compares the other's against its own records.

## Affirmation

**Affirmation** is the counterparty's positive acknowledgement that the terms are correct – often a click on a portal or platform rather than a signed document. It is common with buy-side clients (asset managers, pension funds) who do not produce their own paper.

## Matching

**Matching** is the field-by-field comparison of two independent versions of the trade. If every economic field agrees (within tolerance), the trade is **matched**; any difference is a **break** (or mismatch). Electronic matching platforms and SWIFT MT300 matching services do this automatically.

| Term | Who acts | Output |
|---|---|---|
| Confirmation | Issuer sends terms | Document / message |
| Affirmation | Recipient agrees | Acknowledgement |
| Matching | Both versions compared | Matched or break |

## How ConfirmIQ applies these

- Meridian issues a confirmation PDF for every trade (one-way model).
- Clients on the **portal** channel can **affirm** with one click, or **dispute** with a reason.
- Clients on the **email** channel return a signed copy or their own paper; ConfirmIQ **matches** it automatically against the booking and records every field-level difference.
`,
  },
  {
    slug: 'confirmiq-workflow',
    title: 'How ConfirmIQ works',
    category: 'Getting started',
    summary: 'Statuses, roles, four-eyes approval, chasers, amendments and the QR fingerprint.',
    body: `
## Statuses

| Status | Meaning | Next step |
|---|---|---|
| New | Trade booked; enrichment failed or not run | Fix static data (e.g. missing SSI), then Enrich |
| Enriched | Counterparty data and SSIs validated | Maker generates a draft |
| Drafted | DRAFT PDF produced from the active template | Maker submits for approval |
| Pending approval | Waiting for four-eyes check | Checker approves or rejects |
| Approved | FINAL e-stamped PDF issued | Maker sends to client |
| Awaiting client | Sent; response SLA running | Client affirms, returns paper or disputes |
| Matched | Client document agrees with booking | Mark confirmed |
| Mismatch | Client document differs | Amend booking or chase for corrected paper |
| Disputed | Client disagrees | Investigate with front office |
| Confirmed | Terms agreed – complete | – |
| Cancelled | Trade cancelled; notice sent if already released | – |

## Roles

- **Maker** – prepares, sends, chases and amends confirmations.
- **Checker** – independently reviews and approves drafts (cannot approve their own work).
- **Supervisor** – maintains static data and templates, accepts breaks, cancels trades, handles escalations.
- **Client** – uses the portal to affirm, dispute or upload signed copies.

## Straight-through processing (STP)

When a trade arrives with complete static data, ConfirmIQ enriches it automatically. Trades with missing SSIs or contacts drop into an **exception queue** – the "STP rate" on the dashboard measures how many did not need manual repair.

## Document integrity

Every generated PDF carries a **QR code** containing the trade reference, the economic version and a fingerprint (SHA-256 hash) of the economic terms. When the client returns the document:

1. The QR identifies the trade without OCR.
2. If the fingerprint does not match the current terms, the client has returned **superseded paper** (e.g. from before an amendment).
3. Text on the page is still extracted and compared, so a hand-edited PDF is caught as a break.

Every stored document also has a SHA-256 hash of the file itself, visible in the repository.

## Chasers and escalation

If the client has not responded within the counterparty's SLA, an automatic **chaser** (reminder) is sent every 24 hours. After three chasers the confirmation is **escalated** to the supervisor.
`,
  },
  {
    slug: 'fx-fundamentals',
    title: 'FX fundamentals',
    category: 'FX products',
    summary: 'Currency pairs, quotation conventions, pips, value dates and the T+2 spot date.',
    body: `
## Currency pairs and quotation

An exchange rate is quoted as a **currency pair** \`BASE/QUOTE\`, meaning *how many units of the quote currency buy one unit of the base currency*.

- \`EUR/USD 1.0850\` → 1 EUR = 1.0850 USD
- \`USD/JPY 151.24\` → 1 USD = 151.24 JPY

The base currency follows a market hierarchy: **EUR > GBP > AUD > NZD > USD > CAD > CHF > JPY**. That is why we say EUR/USD (not USD/EUR) and USD/JPY (not JPY/USD). ConfirmIQ applies this convention when it checks that amounts are consistent with the rate.

## Pips and points

A **pip** is the smallest conventional price increment – 0.0001 for most pairs, 0.01 for JPY pairs. A move from 1.0850 to 1.0865 is 15 pips.

## Notional, dealt currency, buy and sell

- **Notional** – the amount of the trade in the dealt currency (e.g. EUR 10m).
- **Counter amount** – notional × rate in the other currency.
- Every FX trade is two flows: one party buys currency A and sells currency B. A confirmation always states it from the issuer's perspective, e.g. "Meridian Capital Buys EUR 10,000,000.00 / Sells USD 10,850,000.00".

## Trade date and value date

- **Trade date (T)** – the date the trade was agreed.
- **Value date / settlement date** – the date on which the currencies are actually exchanged.
- **Spot date** – the standard value date, **T+2 business days** for most pairs (T+1 for USD/CAD, USD/TRY and a few others).
- **Business days** exclude weekends and the holidays of both currencies' financial centres. A value date must be a good business day in both centres.

## Worked example

On Tuesday 27 Oct a client sells EUR 5m to Meridian at 1.0853.

| Field | Value |
|---|---|
| Trade date | 27 Oct 2026 |
| Value (spot) date | 29 Oct 2026 (T+2) |
| Meridian buys | EUR 5,000,000.00 |
| Meridian sells | USD 5,426,500.00 (5m × 1.0853) |
`,
  },
  {
    slug: 'fx-spot',
    title: 'FX Spot',
    category: 'FX products',
    summary: 'The simplest FX trade – exchange of two currencies on the spot date.',
    body: `
An **FX spot** trade is an agreement to exchange two currencies at an agreed rate for settlement on the **spot date** (normally T+2).

## Key confirmation fields

- Trade date, value date
- Currency pair, buy amount, sell amount, exchange rate
- Settlement instructions for both currencies

## Typical uses

- Corporates paying foreign suppliers
- Asset managers funding purchases of foreign securities
- Speculative and hedging activity by funds

## Regulatory note

Spot FX is generally **not** a derivative (it is a payment), so derivatives confirmation rules such as EMIR's timely confirmation requirement do not apply to it. Banks nevertheless confirm spot trades under the FX Global Code and internal control standards.

> ConfirmIQ validates that a spot value date is no later than T+2 business days and that the rate agrees with the two amounts.
`,
  },
  {
    slug: 'fx-forward',
    title: 'FX Outright Forward',
    category: 'FX products',
    summary: 'Exchange of currencies on a future date at a rate fixed today; forward points explained.',
    body: `
An **outright forward** is an agreement to exchange currencies on a date **later than spot**, at a rate fixed on the trade date. It locks in an exchange rate for a future payment – the classic corporate hedge.

## Forward points and interest rate parity

The forward rate differs from spot because the two currencies earn different interest rates. The difference is expressed in **forward points**:

\`Forward = Spot × (1 + r_quote × t) / (1 + r_base × t)\`

If the quote currency has the higher interest rate, the forward is above spot (**premium**); otherwise below (**discount**).

## Worked example

A UK exporter expects USD 12.7m in three months and wants certainty in GBP.

| Field | Value |
|---|---|
| Pair | GBP/USD |
| Spot | 1.2718 |
| 3-month forward points | +5 pips |
| All-in forward rate | 1.2723 |
| Meridian buys | USD 12,723,000.00 |
| Meridian sells | GBP 10,000,000.00 |
| Value date | 27 Jan 2027 |

## Why forwards matter for confirmations

Forwards are **derivatives**: there is credit exposure between trade date and value date, they are covered by the ISDA master agreement, and they are within scope of regulatory confirmation timeliness rules.
`,
  },
  {
    slug: 'fx-swap',
    title: 'FX Swap',
    category: 'FX products',
    summary: 'Two offsetting exchanges – a near leg and a far leg – used for funding and rolling positions.',
    body: `
An **FX swap** combines two exchanges of the same notional in opposite directions:

- **Near leg** – usually spot: Meridian buys currency A and sells currency B.
- **Far leg** – a later date: Meridian sells currency A back and buys currency B.

The difference between near and far rates is the **swap points**, reflecting the interest differential. Economically, an FX swap is a collateralised loan of one currency against another.

## Typical uses

- **Funding** – a bank with surplus EUR and a USD need swaps EUR into USD for a period.
- **Rolling a hedge** – extending a maturing forward to a new date.
- **Liquidity management** in treasury.

## Worked example

| Field | Near leg | Far leg |
|---|---|---|
| Value date | 29 Oct 2026 | 29 Jan 2027 |
| Meridian buys | USD 20,000,000.00 | JPY 3,000,200,000 |
| Meridian sells | JPY 3,024,800,000 | USD 20,000,000.00 |
| Rate | 151.24 | 150.01 |

## Confirmation specifics

Both legs must be confirmed together. Breaks often occur on the **far rate** or **far date**. ConfirmIQ confirms near and far legs in one document and matches each field independently.
`,
  },
  {
    slug: 'ndf',
    title: 'Non-Deliverable Forward (NDF)',
    category: 'FX products',
    summary: 'Cash-settled forwards for restricted currencies such as INR, KRW and BRL.',
    body: `
Some currencies cannot be freely delivered offshore because of capital controls (e.g. INR, KRW, BRL, TWD, CNY onshore). An **NDF** lets investors hedge or take exposure to them **without delivering the restricted currency**.

## How it works

1. On trade date the parties agree a notional and an **NDF rate** (the contract rate).
2. On the **fixing date** (usually two business days before settlement) an official reference rate is observed from the **settlement rate option** – e.g. \`INR RBIB (INR01)\`, \`KRW KFTC18 (KRW02)\`, \`BRL PTAX (BRL09)\`.
3. On the **settlement date**, only the difference is paid in a convertible **settlement currency** (usually USD).

\`Settlement amount (USD) = Notional (USD) × (Contract rate − Fixing rate) / Fixing rate\`

## Worked example

Meridian buys INR 835.8m / sells USD 10m at 83.58 for settlement on 29 Dec. On the fixing date RBI publishes 84.00.

- Value of INR at fixing: 835,800,000 / 84.00 = USD 9,950,000
- Meridian receives INR worth USD 9.95m but paid USD 10m → **Meridian pays the client USD 50,000** net.

## Confirmation specifics

NDF confirmations must state the **settlement currency, fixing date and settlement rate option**; these are matched like any other term. Disruption fallbacks are governed by EMTA template terms.
`,
  },
  {
    slug: 'other-products',
    title: 'Beyond FX: other confirmable products',
    category: 'FX products',
    summary: 'How rates, equities and fixed income confirmations differ – and how ConfirmIQ would extend.',
    body: `
ConfirmIQ v1 covers FX, but the product model is pluggable (one \`ProductTermsProvider\` per asset class). Typical confirmable fields for other products:

| Product | Key confirmation fields |
|---|---|
| FX option | Call/put currencies, strike, expiry date/time and cut, premium amount and date, exercise style |
| Interest rate swap (IRS) | Notional, effective and termination dates, fixed rate, floating index (e.g. SOFR, €STR), day count, payment frequency, business day convention |
| Cross-currency swap | Two notionals, initial/final exchange of principal, two interest legs |
| Equity cash trade | ISIN, quantity, price, gross/net consideration, commission, venue, settlement date (T+1 in the US) |
| Bond | ISIN, nominal, clean price, accrued interest, dirty price, settlement amount |

Cash equities and bonds are usually confirmed through post-trade matching utilities and custodians rather than bilateral PDFs, while OTC derivatives use ISDA-based confirmations or electronic platforms.
`,
  },
  {
    slug: 'ssi',
    title: 'Standard Settlement Instructions (SSIs)',
    category: 'Settlement & static data',
    summary: 'Where the money goes – beneficiary, correspondent, intermediary – and why SSI changes are high-risk.',
    body: `
A **standard settlement instruction (SSI)** tells the paying party where to send a currency. Each counterparty maintains one SSI per currency (sometimes per product).

## Anatomy of an SSI

| Element | Meaning |
|---|---|
| Beneficiary | The legal entity that owns the account |
| Account with institution | The bank holding the account (identified by BIC) |
| Account number / IBAN | The account itself |
| Intermediary (correspondent) | A bank in the currency's home country used to route the payment, when the account bank is not a direct participant in that currency's clearing system |

## Nostro and vostro

- **Nostro** – "our account with you": a bank's account in a foreign currency held at a correspondent bank.
- **Vostro** – "your account with us".

## Why SSIs are on the confirmation

Printing both parties' SSIs lets the client verify that payments will go to the right place **before** value date. Wrong SSIs cause failed settlements, compensation claims and overdraft costs.

## Fraud risk and controls

Changing an SSI is the classic vector for payment fraud (a fake email asks to "update" bank details). Good practice:

- Verify SSI changes by **call-back** to a known contact from static data – never to a number in the request.
- Apply **four-eyes** approval to static data changes.
- Keep a history: new SSIs **supersede** old ones rather than overwriting them (ConfirmIQ retains the previous instruction inactive).
`,
  },
  {
    slug: 'settlement-risk',
    title: 'Settlement risk, CLS and netting',
    category: 'Settlement & static data',
    summary: 'Herstatt risk and the mechanisms that reduce it.',
    body: `
## Herstatt (settlement) risk

In an FX trade each party pays a different currency, often in different time zones. If one party pays and the other defaults before paying, the first loses the full principal. This is named after Bankhaus Herstatt, which was closed in 1974 after receiving DEM payments but before paying out the USD leg.

## Mitigants

- **CLS (Continuous Linked Settlement)** – a specialist settlement system that settles both legs simultaneously on a **payment-versus-payment (PvP)** basis for its eligible currencies, removing principal risk.
- **Bilateral netting** – instead of exchanging every gross flow, parties pay only the net amount per currency per value date.
- **Confirmations** – catching errors before value date avoids failed or erroneous payments.
- **Credit limits** on settlement exposure per counterparty.
`,
  },
  {
    slug: 'identifiers-messaging',
    title: 'Identifiers and industry messaging',
    category: 'Identifiers & messaging',
    summary: 'LEI, BIC, UTI, ISIN, SWIFT MT300 family, FpML and ISO 20022.',
    body: `
## Identifiers

| Identifier | What it identifies | Format |
|---|---|---|
| LEI – Legal Entity Identifier | A legal entity (ISO 17442); mandatory for regulatory reporting | 20 alphanumeric characters |
| BIC – Business Identifier Code | A financial institution / branch on the SWIFT network (ISO 9362) | 8 or 11 characters, e.g. MRDNGB2LXXX |
| IBAN | A bank account (ISO 13616) | Country code + check digits + account |
| UTI – Unique Transaction Identifier | A reportable transaction, shared by both parties | Up to 52 characters |
| ISIN | A security (ISO 6166) | 12 characters |

## SWIFT MT confirmation messages

| Message | Use |
|---|---|
| MT300 | Foreign exchange confirmation (spot, forward, swap legs) |
| MT304 | Advice/instruction of a third-party FX deal (fund managers via custodians) |
| MT305 / MT306 | FX option confirmations |
| MT320 | Fixed loan/deposit confirmation |
| MT360 / MT361 | Interest rate / cross-currency swap confirmations |

Electronic MT300 matching is the norm between banks; PDF/paper confirmations are more common with corporate and buy-side clients.

## FpML and ISO 20022

- **FpML (Financial products Markup Language)** – an XML standard describing OTC derivatives, widely used between trading and post-trade systems. ConfirmIQ's booking XML is FpML-inspired.
- **ISO 20022** – the XML-based messaging standard progressively replacing MT messages for payments (e.g. pacs.008) and, over time, other areas.
`,
  },
  {
    slug: 'controls-operations',
    title: 'Operational controls: four-eyes, SLAs, chasers and breaks',
    category: 'Controls & operations',
    summary: 'The control framework every confirmations team runs – and the KPIs used to measure it.',
    body: `
## Four-eyes (maker–checker)

No single person should both prepare and release a client document. A **maker** prepares; an independent **checker** verifies and approves. ConfirmIQ enforces this technically: the preparer of a draft cannot approve it, regardless of role.

## Segregation of duties

Front-office staff must not be able to confirm their own trades or change settlement instructions. Confirmations are therefore an operations function independent of trading.

## SLAs and ageing

- **Issuance SLA** – how quickly the bank sends its confirmation (often same day / T+0).
- **Response SLA** – how quickly the client must respond (per counterparty, e.g. 24 or 48 hours).
- **Ageing** – unconfirmed trades are bucketed by age (T+0, T+1, T+2, T+3–5, >T+5). Old unconfirmed trades are a key operational-risk indicator reported to management.

## Chasers and escalation

A **chaser** is a reminder sent to a client who has not responded. Chasing follows a schedule (e.g. daily) and, after a threshold, the item is **escalated** – to a supervisor, the relationship manager or the client's senior operations contact.

## Breaks

A **break** is any difference between our records and the client's. Typical root causes:

| Break | Common cause |
|---|---|
| Rate | Trader mis-keyed rate; forward points applied differently |
| Value date | Holiday calendar differences; wrong tenor |
| Amount | Wrong notional or direction; rounding |
| SSI | Client changed bank details; stale static data |

Resolution is either an **amendment** (our booking was wrong – re-issue an amended confirmation that requires fresh approval) or a **chaser for corrected paper** (the client's record was wrong). Small differences within agreed tolerance may be **accepted** by a supervisor.

## KPIs on the ConfirmIQ dashboard

- **First-time match rate** – share of trades whose first client document matched.
- **STP (enrichment) rate** – share of trades enriched without manual repair.
- **SLA compliance** – share of confirmed trades confirmed within the response SLA.
- **Average time to confirm** – from dispatch to confirmed.
`,
  },
  {
    slug: 'legal-documentation',
    title: 'Legal documentation: ISDA, definitions and master agreements',
    category: 'Legal & regulatory',
    summary: 'Why the confirmation refers to a master agreement and to the 1998 FX Definitions.',
    body: `
## ISDA Master Agreement

The **ISDA Master Agreement** (1992 or 2002 versions) is the standard bilateral contract for OTC derivatives published by the International Swaps and Derivatives Association. It is negotiated once per counterparty and covers events of default, termination, close-out netting and governing law. Each trade is then documented by a short **confirmation** that "supplements, forms part of and is subject to" the master agreement.

A **Schedule** customises the master agreement, and a **Credit Support Annex (CSA)** governs collateral.

## Other FX master agreements

- **IFEMA** (International Foreign Exchange Master Agreement) and **FEOMA** (options) – FX-specific alternatives.

## Definitions

Confirmations incorporate standard definitions so they can be short. For FX the relevant booklet is the **1998 FX and Currency Option Definitions** (ISDA, EMTA and the Foreign Exchange Committee). For NDFs, **EMTA template terms** define fixing sources and disruption fallbacks.

## Precedence

If the confirmation conflicts with the definitions or the master agreement, the confirmation usually prevails for that transaction – which is why its accuracy matters so much.
`,
  },
  {
    slug: 'regulation',
    title: 'Regulatory drivers for timely confirmation',
    category: 'Legal & regulatory',
    summary: 'EMIR, Dodd-Frank, MiFID II and the FX Global Code – what they expect from confirmations.',
    body: `
> This is a high-level orientation, not legal advice. Scope and deadlines depend on entity classification and product.

## EMIR (EU / UK)

EMIR's risk-mitigation rules require **timely confirmation** of OTC derivative contracts that are not centrally cleared. For financial counterparties the deadline is generally the **end of the business day following execution (T+1)**, with slightly longer deadlines for some non-financial counterparties. Firms must also have procedures to report unconfirmed trades outstanding for more than five business days to their regulator on request, and to resolve disputes. FX forwards and swaps are derivatives in scope; FX spot is not.

## Dodd-Frank (US)

CFTC rules require swap dealers to confirm swap transactions promptly. Physically settled FX forwards and FX swaps were exempted from the definition of "swap" for many purposes by the US Treasury, but remain subject to reporting and business-conduct standards; NDFs are treated as swaps.

## MiFID II

MiFID II defines which FX contracts are financial instruments (relevant for reporting and conduct). Forwards entered into for commercial payment purposes may fall under the "means of payment" exclusion.

## FX Global Code

The **FX Global Code** is a voluntary set of principles for the wholesale FX market. It expects market participants to confirm trades as soon as practicable, use secure and automated confirmation methods where possible, monitor unconfirmed trades and have independent escalation of discrepancies.

## Why this matters for ConfirmIQ

- SLAs, ageing reports and escalation exist to evidence timely confirmation.
- The audit trail evidences who did what and when.
- Disputes and breaks are recorded with reasons, supporting dispute-resolution obligations.
`,
  },
]

export interface GlossaryTerm {
  term: string
  definition: string
  see?: string
}

export const GLOSSARY: GlossaryTerm[] = [
  { term: 'Affirmation', definition: 'The counterparty\'s positive acknowledgement that the trade terms are correct, typically via a portal or platform.', see: 'confirmation-affirmation-matching' },
  { term: 'Ageing', definition: 'Grouping of unconfirmed trades by how long they have been outstanding (T+0, T+1 …) to monitor operational risk.', see: 'controls-operations' },
  { term: 'Amendment', definition: 'A change to the economic terms of a booked trade. Requires a new (amended) confirmation and fresh approval.' },
  { term: 'Base currency', definition: 'The first currency of a pair; the rate states how many units of the quote currency buy one unit of it.', see: 'fx-fundamentals' },
  { term: 'BIC', definition: 'Business Identifier Code (ISO 9362), the 8- or 11-character identifier of a bank on the SWIFT network.', see: 'identifiers-messaging' },
  { term: 'Book', definition: 'An internal portfolio in the trading system to which a trade is booked for risk and P&L.' },
  { term: 'Break', definition: 'A difference between the bank\'s record of a trade and the counterparty\'s record. Must be investigated and resolved.', see: 'controls-operations' },
  { term: 'Business day', definition: 'A day on which banks are open in the relevant financial centres. Value dates must be business days for both currencies.' },
  { term: 'Cancellation', definition: 'Termination of a trade booked in error or agreed to be unwound. A cancellation notice supersedes earlier confirmations.' },
  { term: 'Chaser', definition: 'A reminder sent to a counterparty that has not responded to a confirmation within the agreed time.', see: 'controls-operations' },
  { term: 'Checker', definition: 'The person who independently reviews and approves work prepared by a maker (four-eyes principle).' },
  { term: 'CLS', definition: 'Continuous Linked Settlement – a system settling both legs of FX trades simultaneously (PvP) to eliminate settlement risk.', see: 'settlement-risk' },
  { term: 'Confirmation', definition: 'A document or message setting out the full terms of a trade, exchanged between the parties as the legal record.', see: 'confirmation-affirmation-matching' },
  { term: 'Correspondent bank', definition: 'A bank that holds accounts for other banks in its domestic currency and processes payments on their behalf.' },
  { term: 'Counterparty', definition: 'The other party to a trade – the bank\'s client or another dealer.' },
  { term: 'Currency pair', definition: 'Two currencies quoted against each other, written BASE/QUOTE (e.g. EUR/USD).', see: 'fx-fundamentals' },
  { term: 'CSA', definition: 'Credit Support Annex – part of the ISDA documentation governing collateral exchange.', see: 'legal-documentation' },
  { term: 'Dealt currency', definition: 'The currency in which the notional amount of a trade was agreed.' },
  { term: 'Dispute', definition: 'A formal disagreement by the counterparty with the confirmed terms.' },
  { term: 'EMIR', definition: 'European Market Infrastructure Regulation – includes timely confirmation requirements for uncleared OTC derivatives.', see: 'regulation' },
  { term: 'EMTA', definition: 'Trade association for emerging markets; publishes template terms and fixing sources for NDFs.', see: 'ndf' },
  { term: 'Enrichment', definition: 'Adding static data (legal entity, contacts, SSIs) to a booked trade so it can be confirmed and settled.' },
  { term: 'Escalation', definition: 'Raising an unresolved item to a more senior person or the client\'s management after chasers fail.' },
  { term: 'Execution time', definition: 'The exact time the trade was agreed; important for regulatory reporting and timeliness.' },
  { term: 'Far leg', definition: 'The later of the two exchanges in an FX swap, reversing the near leg.', see: 'fx-swap' },
  { term: 'Fixing date', definition: 'The date on which the reference rate for an NDF is observed.', see: 'ndf' },
  { term: 'Forward points', definition: 'The difference between the forward and spot rate, reflecting the interest-rate differential.', see: 'fx-forward' },
  { term: 'Four-eyes principle', definition: 'Control requiring two independent people – maker and checker – for sensitive actions.', see: 'controls-operations' },
  { term: 'FpML', definition: 'Financial products Markup Language – an XML standard for OTC derivative trade data.', see: 'identifiers-messaging' },
  { term: 'FX Global Code', definition: 'Voluntary global principles of good practice for the wholesale FX market.', see: 'regulation' },
  { term: 'FX swap', definition: 'Simultaneous purchase and sale of the same amount of currency for two different value dates.', see: 'fx-swap' },
  { term: 'Herstatt risk', definition: 'Settlement risk that one party pays its leg of an FX trade but the other fails to pay.', see: 'settlement-risk' },
  { term: 'House', definition: 'The bank itself (as opposed to its clients), e.g. "house SSIs".' },
  { term: 'IBAN', definition: 'International Bank Account Number (ISO 13616).' },
  { term: 'Intermediary bank', definition: 'A bank in the payment chain between the payer\'s bank and the beneficiary\'s bank.', see: 'ssi' },
  { term: 'ISDA Master Agreement', definition: 'Standard bilateral contract for OTC derivatives; individual confirmations supplement it.', see: 'legal-documentation' },
  { term: 'LEI', definition: 'Legal Entity Identifier – a 20-character global ID for legal entities (ISO 17442).', see: 'identifiers-messaging' },
  { term: 'Maker', definition: 'The person who prepares a piece of work (e.g. a confirmation) for independent review.' },
  { term: 'Matching', definition: 'Field-by-field comparison of two versions of a trade to confirm they agree.', see: 'confirmation-affirmation-matching' },
  { term: 'MT300', definition: 'SWIFT message type for FX confirmations.', see: 'identifiers-messaging' },
  { term: 'NDF', definition: 'Non-Deliverable Forward – a cash-settled forward on a restricted currency.', see: 'ndf' },
  { term: 'Near leg', definition: 'The first exchange in an FX swap, usually on the spot date.', see: 'fx-swap' },
  { term: 'Netting', definition: 'Offsetting obligations so only a net amount per currency is paid.', see: 'settlement-risk' },
  { term: 'Nostro', definition: '"Our account with you" – a bank\'s foreign-currency account held at a correspondent.', see: 'ssi' },
  { term: 'Notional', definition: 'The principal amount of a trade on which payments are based.' },
  { term: 'OTC', definition: 'Over-the-counter – traded bilaterally rather than on an exchange.' },
  { term: 'Outright forward', definition: 'An FX trade for a value date beyond spot at a rate fixed today.', see: 'fx-forward' },
  { term: 'Pip', definition: 'Smallest conventional price increment – 0.0001 for most pairs, 0.01 for JPY pairs.', see: 'fx-fundamentals' },
  { term: 'PvP', definition: 'Payment versus payment – both currency legs settle simultaneously or not at all.' },
  { term: 'Quote currency', definition: 'The second currency of a pair, in which the price is expressed.' },
  { term: 'Reconciliation', definition: 'Comparing internal records with external statements (e.g. nostro statements) to find differences.' },
  { term: 'Settlement', definition: 'The actual exchange of cash (and/or securities) that discharges the trade obligations.' },
  { term: 'Settlement rate option', definition: 'The named source of the fixing rate for an NDF, e.g. INR RBIB (INR01).', see: 'ndf' },
  { term: 'SLA', definition: 'Service level agreement – here, the time allowed for issuing or responding to a confirmation.', see: 'controls-operations' },
  { term: 'Spot date', definition: 'Standard settlement date for FX – normally two business days after trade date (T+2).', see: 'fx-fundamentals' },
  { term: 'SSI', definition: 'Standard Settlement Instruction – the account details a party uses to receive a currency.', see: 'ssi' },
  { term: 'Static data', definition: 'Reference data that changes rarely: counterparties, LEIs, contacts, SSIs, calendars.' },
  { term: 'STP', definition: 'Straight-through processing – handling a trade end to end without manual intervention.' },
  { term: 'SWIFT', definition: 'The global messaging network used by banks for payments and confirmations.' },
  { term: 'Swap points', definition: 'The difference between far and near rates in an FX swap.', see: 'fx-swap' },
  { term: 'T+n', definition: 'Notation for n business days after trade date (T).' },
  { term: 'Tenor', definition: 'The length of time until the value date, e.g. 1M, 3M.' },
  { term: 'Trade date', definition: 'The date on which the trade was agreed.' },
  { term: 'UTI', definition: 'Unique Transaction Identifier used in regulatory trade reporting.', see: 'identifiers-messaging' },
  { term: 'Value date', definition: 'The date on which the currencies are exchanged (settlement date).', see: 'fx-fundamentals' },
  { term: 'Vostro', definition: '"Your account with us" – an account a bank holds on behalf of another bank.' },
]
