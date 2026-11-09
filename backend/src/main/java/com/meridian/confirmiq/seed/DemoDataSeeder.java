package com.meridian.confirmiq.seed;

import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.confirmation.AmendRequest;
import com.meridian.confirmiq.confirmation.Confirmation;
import com.meridian.confirmiq.confirmation.ConfirmationAction;
import com.meridian.confirmiq.confirmation.ConfirmationRepository;
import com.meridian.confirmiq.confirmation.ConfirmationWorkflowService;
import com.meridian.confirmiq.counterparty.*;
import com.meridian.confirmiq.counterparty.Counterparty.ClientType;
import com.meridian.confirmiq.counterparty.Counterparty.DeliveryChannel;
import com.meridian.confirmiq.document.Document.Source;
import com.meridian.confirmiq.inbound.CounterpartyPaperSimulator;
import com.meridian.confirmiq.inbound.CounterpartyPaperSimulator.Break;
import com.meridian.confirmiq.inbound.InboundService;
import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.security.Role;
import com.meridian.confirmiq.template.ConfirmationTemplate;
import com.meridian.confirmiq.template.TemplateRepository;
import com.meridian.confirmiq.template.TemplateVersion;
import com.meridian.confirmiq.trade.*;
import com.meridian.confirmiq.trade.Trade.IngestionChannel;
import com.meridian.confirmiq.trade.TradeIngestionService.IngestionResult;
import com.meridian.confirmiq.trade.TradeIngestionService.Outcome;
import com.meridian.confirmiq.user.AppUser;
import com.meridian.confirmiq.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Populates an empty database with a realistic FX confirmation book. Every trade is driven through
 * the real workflow with back-dated actors and timestamps, so documents, matching results, chasers
 * and the audit trail are all genuine outputs of the application.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Demo@2026";

    private final CounterpartyRepository counterparties;
    private final SettlementInstructionRepository ssis;
    private final AppUserRepository users;
    private final TemplateRepository templates;
    private final ConfirmationRepository confirmations;
    private final TradeIngestionService ingestion;
    private final ConfirmationWorkflowService workflow;
    private final InboundService inbound;
    private final CounterpartyPaperSimulator simulator;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final AppProperties props;

    private final Random random = new Random(20261027L);
    private final Map<String, CurrentUser> actors = new HashMap<>();
    private final Map<Long, CurrentUser> clientActors = new HashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        if (counterparties.count() > 0) {
            return;
        }
        long started = System.currentTimeMillis();
        log.info("Seeding demo data…");
        tx.executeWithoutResult(s -> {
            seedHouseSsis();
            seedCounterparties();
            seedUsers();
            seedTemplates();
        });
        seedTrades();
        log.info("Demo data ready in {} ms. Sign in with maker / checker / supervisor / client, password {}",
                System.currentTimeMillis() - started, DEMO_PASSWORD);
    }

    // ---------------------------------------------------------------- static data

    private void seedHouseSsis() {
        String[][] house = {
                {"USD", "Meridian Bank N.A., New York", "MRDNUS33XXX", "8800-114592-01"},
                {"EUR", "Meridian Capital Europe SE, Frankfurt", "MRDNDEFFXXX", "DE89 5001 0517 5407 3249 31"},
                {"GBP", "Meridian Capital Markets Ltd., London", "MRDNGB2LXXX", "GB29 MRDN 6016 1331 9268 19"},
                {"JPY", "Meridian Trust Bank, Tokyo", "MRDNJPJTXXX", "0912-3345-881"},
                {"CHF", "Meridian (Suisse) AG, Zurich", "MRDNCHZZXXX", "CH93 0076 2011 6238 5295 7"},
                {"AUD", "Meridian Australia Ltd., Sydney", "MRDNAU2SXXX", "062-000 1122 3344"},
                {"CAD", "Meridian Bank Canada, Toronto", "MRDNCATTXXX", "00012-004-1029384"},
                {"SGD", "Meridian Asia Pte Ltd., Singapore", "MRDNSGSGXXX", "7110-229384-001"},
        };
        for (String[] h : house) {
            SettlementInstruction s = new SettlementInstruction();
            s.setCurrency(h[0]);
            s.setBeneficiaryName(props.bank().name());
            s.setBankName(h[1]);
            s.setBankBic(h[2]);
            s.setAccountNumber(h[3]);
            s.setEffectiveFrom(LocalDate.of(2024, 1, 2));
            ssis.save(s);
        }
    }

    private record CptySpec(String code, String name, String lei, String bic, String country, ClientType type,
                            DeliveryChannel channel, int sla, String[] ssiCcys, String bankBic, String bankName,
                            String contactName, String domain) {}

    private static final List<CptySpec> CPTYS = List.of(
            new CptySpec("NBAM", "Northbridge Asset Management LLP", "213800NBAM7Q2X4KDV11", "NBAMGB2L",
                    "GB", ClientType.ASSET_MANAGER, DeliveryChannel.PORTAL, 24,
                    new String[]{"USD", "EUR", "GBP", "JPY", "CHF", "AUD"}, "CITRGB2LXXX", "Citadel Trust Bank London",
                    "Hannah Clarke", "northbridge-am.example"),
            new CptySpec("AURORA", "Aurora Pension Fund", "5493005AURORA8PF2K40", null,
                    "NL", ClientType.PENSION_FUND, DeliveryChannel.PORTAL, 24,
                    new String[]{"USD", "EUR", "GBP"}, "CUSTNL2AXXX", "Custodia Bank Amsterdam",
                    "Pieter de Vries", "aurora-pensions.example"),
            new CptySpec("HELIX", "Helix Global Macro Fund Ltd.", "549300HELIXGM7FUND88", null,
                    "KY", ClientType.HEDGE_FUND, DeliveryChannel.EMAIL, 24,
                    new String[]{"USD", "EUR", "GBP", "JPY", "AUD", "CAD"}, "PRIMUS33XXX", "Primus Prime Services NY",
                    "Marcus Lee", "helixmacro.example"),
            new CptySpec("KESTREL", "Kestrel Industries plc – Group Treasury", "213800KESTREL5TRSY09", "KSTRGB21",
                    "GB", ClientType.CORPORATE, DeliveryChannel.EMAIL, 48,
                    new String[]{"USD", "EUR", "GBP", "CAD"}, "BARNGB22XXX", "Barnaby & Co Bank",
                    "Olivia Grant", "kestrel-industries.example"),
            new CptySpec("STERLING", "Sterling Point Insurance Group", "549300STRLPT0INS3K12", null,
                    "IE", ClientType.INSURER, DeliveryChannel.EMAIL, 24,
                    new String[]{"USD", "EUR", "GBP", "CHF"}, "CUSTIE2DXXX", "Custodia Bank Dublin",
                    "Aoife Byrne", "sterlingpoint.example"),
            new CptySpec("ORION", "Orion Sovereign Investment Authority", "254900ORIONSIA4X8Q77", "ORSIAEAD",
                    "AE", ClientType.SOVEREIGN, DeliveryChannel.EMAIL, 48,
                    new String[]{"USD", "EUR", "GBP", "JPY", "SGD"}, "GULFAEAAXXX", "Gulf Custody Bank",
                    "Rashid Al Mansoori", "orion-sia.example"),
            new CptySpec("BLUEWATER", "Bluewater Commodities AG", "506700BLUEWTR2CMD551", null,
                    "CH", ClientType.CORPORATE, DeliveryChannel.EMAIL, 24,
                    // No JPY SSI on file – trades in JPY fall into the enrichment exception queue.
                    new String[]{"USD", "EUR", "CHF"}, "ALPSCHZZXXX", "Alpine Commercial Bank",
                    "Lukas Brunner", "bluewater-commodities.example"),
            new CptySpec("TIDEWATER", "Tidewater Capital Partners LP", "549300TIDEWTRCP11A23", null,
                    "US", ClientType.HEDGE_FUND, DeliveryChannel.EMAIL, 24,
                    new String[]{"USD", "EUR", "GBP", "JPY", "CAD", "SGD"}, "PRIMUS33XXX", "Primus Prime Services NY",
                    "Grace Thompson", "tidewatercp.example"));

    private void seedCounterparties() {
        for (CptySpec s : CPTYS) {
            Counterparty c = new Counterparty();
            c.setShortCode(s.code());
            c.setLegalName(s.name());
            c.setLei(s.lei());
            c.setBic(s.bic());
            c.setCountry(s.country());
            c.setClientType(s.type());
            c.setDeliveryChannel(s.channel());
            c.setConfirmationSlaHours(s.sla());
            c.setMasterAgreement("the ISDA 2002 Master Agreement dated " + (10 + random.nextInt(18)) + " "
                    + List.of("March", "June", "September", "November").get(random.nextInt(4)) + " " + (2016 + random.nextInt(7)));
            c.setCreatedAt(Instant.now().minus(Duration.ofDays(400)));
            c.setUpdatedAt(c.getCreatedAt());

            c.addContact(contact(s.contactName(), "fx.confirms@" + s.domain(), "CONFIRMATIONS", true));
            c.addContact(contact("Operations Desk", "operations@" + s.domain(), "OPERATIONS", false));
            c.addContact(contact("Authorised Signatory", "signatories@" + s.domain(), "AUTHORISED_SIGNATORY", false));

            int acct = 100000 + random.nextInt(900000);
            for (String ccy : s.ssiCcys()) {
                SettlementInstruction ssi = new SettlementInstruction();
                ssi.setCurrency(ccy);
                ssi.setBeneficiaryName(s.name());
                ssi.setBankName(s.bankName());
                ssi.setBankBic(s.bankBic());
                ssi.setAccountNumber(ccy + "-" + acct + "-" + (10 + random.nextInt(89)));
                ssi.setIntermediaryBic(ccy.equals("USD") ? null : "MRDNUS33XXX".equals(s.bankBic()) ? null
                        : ccy.equals("JPY") ? "TKYOJPJTXXX" : null);
                ssi.setEffectiveFrom(LocalDate.of(2023, 1 + random.nextInt(12), 1 + random.nextInt(27)));
                c.addSettlementInstruction(ssi);
            }
            counterparties.save(c);
        }
    }

    private static CounterpartyContact contact(String name, String email, String role, boolean primary) {
        CounterpartyContact c = new CounterpartyContact();
        c.setName(name);
        c.setEmail(email);
        c.setPhone("+44 20 7946 0" + (100 + Math.abs(email.hashCode() % 899)));
        c.setRole(role);
        c.setPrimary(primary);
        return c;
    }

    private void seedUsers() {
        user("maker", "Priya Raman", Role.MAKER, null);
        user("maker2", "Ethan Brooks", Role.MAKER, null);
        user("checker", "Daniel Okafor", Role.CHECKER, null);
        user("supervisor", "Sophie Laurent", Role.SUPERVISOR, null);
        user("client", "James Whitfield", Role.CLIENT, "NBAM");
        user("client.aurora", "Pieter de Vries", Role.CLIENT, "AURORA");
    }

    private void user(String username, String name, Role role, String cptyCode) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(name);
        Counterparty c = cptyCode == null ? null : counterparties.findByShortCodeIgnoreCase(cptyCode).orElseThrow();
        u.setEmail(c == null ? username + "@meridian-capital.example"
                : name.toLowerCase().replace(' ', '.') + "@" + CPTYS.stream()
                .filter(s -> s.code().equals(cptyCode)).findFirst().orElseThrow().domain());
        u.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        u.setRole(role);
        u.setCounterparty(c);
        users.save(u);
        CurrentUser actor = new CurrentUser(username, name, role, c == null ? null : c.getId());
        actors.put(username, actor);
        if (c != null) {
            clientActors.put(c.getId(), actor);
        }
    }

    // ---------------------------------------------------------------- templates

    private static final String PREAMBLE = """
            The purpose of this communication is to confirm the terms and conditions of the {{productName}} \
            transaction entered into between {{bankName}} ("Party A") and {{counterpartyName}} ("Party B") on the \
            Trade Date specified below (the "Transaction"). This communication constitutes a "Confirmation" as \
            referred to in, and supplements, forms part of and is subject to, {{masterAgreement}} between Party A \
            and Party B.""";

    private static final String BASE_CLAUSES = """
            The definitions and provisions contained in the 1998 FX and Currency Option Definitions, as published \
            by ISDA, EMTA and the Foreign Exchange Committee, are incorporated into this Confirmation. In the event \
            of any inconsistency, this Confirmation will govern for the purposes of the Transaction.

            Each party represents that it is acting for its own account, has made its own independent decision to \
            enter into the Transaction and is not relying on any communication of the other party as investment advice.

            Payments shall be made in immediately available funds to the accounts specified under Settlement \
            Instructions on the relevant Value Date. Any change to standing settlement instructions must be \
            notified in writing and authenticated by an authorised signatory.

            Please examine this Confirmation carefully and notify us of any discrepancy within one Business Day of \
            receipt. Absent such notice, the terms set out herein shall be deemed correct and accepted.""";

    private static final String CLOSING = """
            Please confirm that the foregoing correctly sets forth the terms of our agreement by signing and \
            returning this Confirmation, or by affirming it electronically in the Meridian Client Portal. \
            Queries: {{operationsEmail}}.""";

    private void seedTemplates() {
        template("FX-SPOT-STD", "FX Spot – Standard", ProductType.FX_SPOT, null, BASE_CLAUSES, "Initial version");
        template("FX-FWD-STD", "FX Outright Forward – Standard", ProductType.FX_FORWARD, null, BASE_CLAUSES
                + "\n\nThe Transaction is a forward foreign exchange transaction for settlement on the Value Date "
                + "specified above. The Exchange Rate is the all-in forward rate inclusive of forward points.",
                "Initial version");
        template("FX-SWAP-STD", "FX Swap – Standard", ProductType.FX_SWAP, null, BASE_CLAUSES
                + "\n\nThe Transaction comprises a near leg and a far leg settling on the Near Value Date and Far "
                + "Value Date respectively. Each leg constitutes a separate FX Transaction for the purposes of the "
                + "Definitions; the far leg reverses the currency flows of the near leg.", "Initial version");
        template("FX-NDF-STD", "Non-Deliverable Forward – Standard", ProductType.FX_NDF, null, BASE_CLAUSES
                + "\n\nOn the Settlement Date, a net amount in the Settlement Currency will be paid by one party to "
                + "the other, determined by comparing the Exchange Rate with the rate published by the Settlement "
                + "Rate Option on the Fixing Date. No physical delivery of the non-deliverable currency will occur. "
                + "Disruption events and fallbacks apply as set out in the applicable EMTA template terms.",
                "Initial version");

        ConfirmationTemplate orion = template("FX-SPOT-ORION", "FX Spot – Orion SIA bespoke", ProductType.FX_SPOT,
                counterparties.findByShortCodeIgnoreCase("ORION").orElseThrow(), BASE_CLAUSES, "Initial bespoke version");
        TemplateVersion v2 = version(2, BASE_CLAUSES + "\n\nNothing in this Confirmation shall constitute a waiver "
                + "of any immunity of Party B other than as expressly agreed in the Master Agreement.",
                "Added sovereign immunity clause requested by Orion legal (ref. OSIA-LGL-2291)");
        v2.setActive(true);
        orion.getVersions().forEach(v -> v.setActive(false));
        orion.addVersion(v2);
        templates.save(orion);
    }

    private ConfirmationTemplate template(String code, String name, ProductType product, Counterparty cpty,
                                          String clauses, String note) {
        ConfirmationTemplate t = new ConfirmationTemplate();
        t.setCode(code);
        t.setName(name);
        t.setProductType(product);
        t.setCounterparty(cpty);
        t.setCreatedAt(Instant.now().minus(Duration.ofDays(300)));
        TemplateVersion v = version(1, clauses, note);
        v.setActive(true);
        t.addVersion(v);
        return templates.save(t);
    }

    private static TemplateVersion version(int n, String clauses, String note) {
        TemplateVersion v = new TemplateVersion();
        v.setVersion(n);
        v.setTitle("Confirmation of {{productName}} Transaction");
        v.setPreamble(PREAMBLE);
        v.setClauses(clauses);
        v.setClosing(CLOSING);
        v.setChangeNote(note);
        v.setCreatedBy("supervisor");
        v.setCreatedAt(Instant.now().minus(Duration.ofDays(n == 1 ? 300 : 40)));
        return v;
    }

    // ---------------------------------------------------------------- trades

    private record Market(String base, String quote, BigDecimal mid, BigDecimal fwdPointsPerMonth) {}

    private static final List<Market> G10 = List.of(
            new Market("EUR", "USD", new BigDecimal("1.0852"), new BigDecimal("0.0024")),
            new Market("GBP", "USD", new BigDecimal("1.2718"), new BigDecimal("0.0011")),
            new Market("USD", "JPY", new BigDecimal("151.24"), new BigDecimal("-0.42")),
            new Market("AUD", "USD", new BigDecimal("0.6581"), new BigDecimal("0.0002")),
            new Market("USD", "CHF", new BigDecimal("0.8823"), new BigDecimal("-0.0021")),
            new Market("USD", "CAD", new BigDecimal("1.3779"), new BigDecimal("-0.0008")),
            new Market("EUR", "GBP", new BigDecimal("0.8531"), new BigDecimal("0.0006")),
            new Market("USD", "SGD", new BigDecimal("1.3452"), new BigDecimal("-0.0019")));

    private static final List<Market> NDF = List.of(
            new Market("USD", "INR", new BigDecimal("83.36"), new BigDecimal("0.11")),
            new Market("USD", "KRW", new BigDecimal("1385.50"), new BigDecimal("-2.10")),
            new Market("USD", "BRL", new BigDecimal("5.4520"), new BigDecimal("0.0280")));

    private static final Map<String, String> NDF_FIXING = Map.of(
            "INR", "INR RBIB (INR01)", "KRW", "KRW KFTC18 (KRW02)", "BRL", "BRL PTAX (BRL09)");

    private enum Scenario {
        NEW_EXCEPTION, ENRICHED, DRAFTED, PENDING_APPROVAL, REJECTED_REDRAFT, APPROVED, AWAITING, AWAITING_CHASED,
        MATCHED, MISMATCHED, DISPUTED, CONFIRMED, CONFIRMED_AFTER_AMEND, CONFIRMED_AFTER_RESUBMIT, CANCELLED
    }

    private void seedTrades() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = today; days.size() < 15; d = d.minusDays(1)) {
            if (d.getDayOfWeek().getValue() < 6) {
                days.add(d);
            }
        }
        List<Counterparty> cptys = counterparties.findAll();
        Map<Long, Set<String>> ssiCcys = tx.execute(s -> {
            Map<Long, Set<String>> m = new HashMap<>();
            counterparties.findAll().forEach(c -> m.put(c.getId(), new HashSet<>(
                    c.getSettlementInstructions().stream().map(SettlementInstruction::getCurrency).toList())));
            return m;
        });
        int count = 0;
        for (int ageIdx = days.size() - 1; ageIdx >= 0; ageIdx--) {
            LocalDate tradeDate = days.get(ageIdx);
            int perDay = ageIdx == 0 ? 7 : 2 + random.nextInt(3);
            for (int k = 0; k < perDay; k++) {
                Scenario scenario = pickScenario(ageIdx, k);
                Counterparty cpty = scenario == Scenario.NEW_EXCEPTION
                        ? byCode(cptys, "BLUEWATER")
                        : cptys.get(random.nextInt(cptys.size()));
                // Guarantee the demo client portal (Northbridge) has items to affirm and a live dispute.
                if (k == 0 && ageIdx >= 1 && ageIdx <= 3) {
                    cpty = byCode(cptys, "NBAM");
                    scenario = ageIdx == 3 ? Scenario.DISPUTED : Scenario.AWAITING;
                }
                TradeRequest req = buildTrade(tradeDate, cpty, ssiCcys.get(cpty.getId()), scenario, count);
                Instant booked = req.executionTime().plus(Duration.ofMinutes(2 + random.nextInt(6)));
                if (booked.isAfter(Instant.now())) {
                    continue;
                }
                IngestionResult result = tx.execute(s ->
                        ingestion.ingest(req, IngestionChannel.SEED, CurrentUser.SYSTEM, booked));
                if (result == null || result.outcome() != Outcome.ACCEPTED) {
                    log.warn("Seed trade rejected: {}", result == null ? "null" : result.message());
                    continue;
                }
                try {
                    play(result.tradeId(), scenario, booked);
                } catch (RuntimeException e) {
                    log.warn("Seed scenario {} for {} stopped: {}", scenario, result.tradeRef(), e.getMessage());
                }
                count++;
            }
        }
        log.info("Seeded {} trades", count);
    }

    private static Counterparty byCode(List<Counterparty> cptys, String code) {
        return cptys.stream().filter(c -> c.getShortCode().equals(code)).findFirst().orElseThrow();
    }

    private Scenario pickScenario(int ageIdx, int k) {
        if (ageIdx == 0) {
            return List.of(Scenario.NEW_EXCEPTION, Scenario.ENRICHED, Scenario.DRAFTED, Scenario.PENDING_APPROVAL,
                    Scenario.PENDING_APPROVAL, Scenario.APPROVED, Scenario.AWAITING).get(k % 7);
        }
        int r = random.nextInt(100);
        if (ageIdx <= 2) {
            if (r < 30) return Scenario.CONFIRMED;
            if (r < 52) return Scenario.AWAITING;
            if (r < 62) return Scenario.MISMATCHED;
            if (r < 70) return Scenario.MATCHED;
            if (r < 76) return Scenario.DISPUTED;
            if (r < 84) return Scenario.REJECTED_REDRAFT;
            if (r < 92) return Scenario.PENDING_APPROVAL;
            return Scenario.APPROVED;
        }
        if (r < 58) return Scenario.CONFIRMED;
        if (r < 66) return Scenario.CONFIRMED_AFTER_AMEND;
        if (r < 74) return Scenario.CONFIRMED_AFTER_RESUBMIT;
        if (r < 82) return Scenario.AWAITING_CHASED;
        if (r < 88) return Scenario.MISMATCHED;
        if (r < 93) return Scenario.DISPUTED;
        if (r < 96) return Scenario.NEW_EXCEPTION;
        return Scenario.CANCELLED;
    }

    private TradeRequest buildTrade(LocalDate tradeDate, Counterparty cpty, Set<String> ssiCcys, Scenario scenario,
                                    int seq) {
        int p = random.nextInt(100);
        ProductType product = p < 40 ? ProductType.FX_SPOT : p < 68 ? ProductType.FX_FORWARD
                : p < 88 ? ProductType.FX_SWAP : ProductType.FX_NDF;

        Market m;
        if (product == ProductType.FX_NDF) {
            m = NDF.get(random.nextInt(NDF.size()));
        } else {
            List<Market> eligible = G10.stream()
                    .filter(x -> scenario == Scenario.NEW_EXCEPTION
                            ? x.base().equals("USD") && x.quote().equals("JPY")
                            : ssiCcys.contains(x.base()) && ssiCcys.contains(x.quote()))
                    .toList();
            m = eligible.isEmpty() ? G10.getFirst() : eligible.get(random.nextInt(eligible.size()));
        }
        if (product == ProductType.FX_NDF && !ssiCcys.contains("USD")) {
            product = ProductType.FX_SPOT;
            m = G10.getFirst();
        }

        LocalDate spot = TradeValidator.addBusinessDays(tradeDate, 2);
        int months = 1 + random.nextInt(6);
        LocalDate valueDate = switch (product) {
            case FX_SPOT, FX_SWAP -> spot;
            case FX_FORWARD, FX_NDF -> weekday(spot.plusMonths(months));
        };
        BigDecimal spread = m.mid().multiply(new BigDecimal(random.nextInt(9) - 4)).movePointLeft(4);
        BigDecimal rate = m.mid().add(spread);
        if (product == ProductType.FX_FORWARD || product == ProductType.FX_NDF) {
            rate = rate.add(m.fwdPointsPerMonth().multiply(BigDecimal.valueOf(months)));
        }
        rate = rate.setScale(scale(m.quote()) == 0 ? 2 : 4, RoundingMode.HALF_UP);

        BigDecimal notional = BigDecimal.valueOf(List.of(1, 2, 3, 5, 5, 10, 10, 15, 20, 25, 50).get(random.nextInt(11)))
                .multiply(BigDecimal.valueOf(1_000_000));
        boolean houseBuysBase = random.nextBoolean();
        BigDecimal quoteAmt = notional.multiply(rate).setScale(scale(m.quote()), RoundingMode.HALF_UP);
        String buyCcy = houseBuysBase ? m.base() : m.quote();
        String sellCcy = houseBuysBase ? m.quote() : m.base();
        BigDecimal buyAmt = houseBuysBase ? notional : quoteAmt;
        BigDecimal sellAmt = houseBuysBase ? quoteAmt : notional;

        LocalDate farDate = null;
        BigDecimal farRate = null, farBuy = null, farSell = null;
        if (product == ProductType.FX_SWAP) {
            farDate = weekday(spot.plusMonths(months));
            farRate = rate.add(m.fwdPointsPerMonth().multiply(BigDecimal.valueOf(months)))
                    .setScale(scale(m.quote()) == 0 ? 2 : 4, RoundingMode.HALF_UP);
            BigDecimal farQuote = notional.multiply(farRate).setScale(scale(m.quote()), RoundingMode.HALF_UP);
            // Far leg reverses the near leg: we buy back what we sold.
            farBuy = houseBuysBase ? farQuote : notional;
            farSell = houseBuysBase ? notional : farQuote;
        }
        String settleCcy = null, fixingSource = null;
        LocalDate fixingDate = null;
        if (product == ProductType.FX_NDF) {
            settleCcy = "USD";
            fixingDate = minusBusinessDays(valueDate, 2);
            fixingSource = NDF_FIXING.get(m.quote());
        }

        Instant exec = tradeDate.atTime(7 + random.nextInt(9), random.nextInt(60), random.nextInt(60))
                .toInstant(ZoneOffset.UTC);
        String[] systems = {"MUREX-FX", "CALYPSO", "360T-ECN", "FXGO-RFQ"};
        String[] books = {"FXG10-LDN", "FXEM-LDN", "FXSWAP-LDN", "FXNDF-SGP", "FXCORP-LDN"};
        String[] traders = {"a.kowalski", "r.mehta", "j.santos", "l.chen", "m.fischer"};
        return new TradeRequest("FO-%d-%05d".formatted(tradeDate.getYear(), 30000 + seq * 7 + random.nextInt(7)),
                systems[random.nextInt(systems.length)], product, cpty.getShortCode(), tradeDate, exec, valueDate,
                buyCcy, buyAmt, sellCcy, sellAmt, rate, farDate, farBuy, farSell, farRate, settleCcy, fixingDate,
                fixingSource, product == ProductType.FX_NDF ? books[3] : product == ProductType.FX_SWAP ? books[2]
                : books[random.nextInt(2)], traders[random.nextInt(traders.length)]);
    }

    /** Drives a trade through the lifecycle up to the scenario's end state, never past "now". */
    private void play(Long tradeId, Scenario scenario, Instant start) {
        Clock clock = new Clock(start);
        CurrentUser maker = random.nextInt(3) == 0 ? actors.get("maker2") : actors.get("maker");
        CurrentUser checker = random.nextInt(4) == 0 ? actors.get("supervisor") : actors.get("checker");
        CurrentUser supervisor = actors.get("supervisor");

        if (scenario == Scenario.NEW_EXCEPTION || scenario == Scenario.ENRICHED) return;
        if (!act(tradeId, ConfirmationAction.GENERATE, maker, clock.plusMinutes(15, 50), null)) return;
        if (scenario == Scenario.DRAFTED) return;
        if (!act(tradeId, ConfirmationAction.SUBMIT, maker, clock.plusMinutes(2, 8), null)) return;
        if (scenario == Scenario.PENDING_APPROVAL) return;
        if (scenario == Scenario.REJECTED_REDRAFT) {
            act(tradeId, ConfirmationAction.REJECT, checker, clock.plusMinutes(20, 60),
                    "Settlement instruction for counterparty needs re-verification against latest SSI update");
            return;
        }
        if (!act(tradeId, ConfirmationAction.APPROVE, checker, clock.plusMinutes(10, 70), null)) return;
        if (scenario == Scenario.APPROVED) return;
        if (scenario == Scenario.CANCELLED) {
            act(tradeId, ConfirmationAction.SEND, maker, clock.plusMinutes(2, 10), null);
            act(tradeId, ConfirmationAction.CANCEL, supervisor, clock.plusHours(3, 20),
                    "Trade cancelled by front office – booked in error (duplicate of RFQ execution)");
            return;
        }
        if (!act(tradeId, ConfirmationAction.SEND, maker, clock.plusMinutes(2, 10), null)) return;
        Instant sentAt = clock.now;
        Confirmation conf = tx.execute(s -> confirmations.findByTradeId(tradeId).orElseThrow());
        boolean portal = conf.getTrade().getCounterparty().getDeliveryChannel() == DeliveryChannel.PORTAL;
        CurrentUser client = clientActors.get(conf.getTrade().getCounterparty().getId());

        switch (scenario) {
            case AWAITING -> { }
            case AWAITING_CHASED -> chaseUntilNow(tradeId, sentAt);
            case MATCHED -> respond(tradeId, Break.NONE, clock.plusHours(1, 8));
            case MISMATCHED -> respond(tradeId, randomBreak(), clock.plusHours(2, 20));
            case DISPUTED -> {
                if (portal && client != null) {
                    act(tradeId, ConfirmationAction.DISPUTE, client, clock.plusHours(1, 10),
                            "Our booking shows a different all-in rate – please review the forward points applied.");
                } else {
                    respond(tradeId, Break.RATE, clock.plusHours(1, 10));
                    act(tradeId, ConfirmationAction.DISPUTE, maker, clock.plusMinutes(20, 90),
                            "Client insists on rate per their paper; front office reviewing execution log.");
                }
            }
            case CONFIRMED -> {
                if (portal && client != null) {
                    act(tradeId, ConfirmationAction.AFFIRM, client, clock.plusHours(1, 12), null);
                } else if (respond(tradeId, Break.NONE, clock.plusHours(1, 14))) {
                    act(tradeId, ConfirmationAction.CONFIRM, maker, clock.plusMinutes(10, 60), null);
                }
            }
            case CONFIRMED_AFTER_RESUBMIT -> {
                // Counterparty paper was wrong; they re-issue corrected paper.
                if (!respond(tradeId, randomBreak(), clock.plusHours(2, 10))) return;
                if (!act(tradeId, ConfirmationAction.CHASE, maker, clock.plusMinutes(20, 60), null)) return; // request corrected paper
                if (respond(tradeId, Break.NONE, clock.plusHours(2, 12))) {
                    act(tradeId, ConfirmationAction.CONFIRM, maker, clock.plusMinutes(10, 40), null);
                }
            }
            case CONFIRMED_AFTER_AMEND -> {
                // Our booking was wrong: client paper shows the agreed rate; ops amends and reissues.
                if (!respond(tradeId, Break.RATE, clock.plusHours(2, 10))) return;
                Confirmation c = tx.execute(s -> confirmations.findByTradeId(tradeId).orElseThrow());
                BigDecimal agreed = c.getTrade().getRate().add(new BigDecimal("0.0015"));
                Trade t = c.getTrade();
                CurrencyPair pair = CurrencyPair.of(t.getBuyCurrency(), t.getSellCurrency());
                boolean buyIsBase = pair.base().equals(t.getBuyCurrency());
                BigDecimal base = buyIsBase ? t.getBuyAmount() : t.getSellAmount();
                BigDecimal quote = base.multiply(agreed).setScale(scale(pair.quote()), RoundingMode.HALF_UP);
                AmendRequest amend = new AmendRequest(null, buyIsBase ? null : quote, buyIsBase ? quote : null,
                        agreed, null, null, null, null, null, null);
                if (!act(tradeId, ConfirmationAction.AMEND, maker, clock.plusMinutes(30, 90),
                        "Front office confirmed booking error on rate (FO ticket FXO-" + (4000 + random.nextInt(999)) + ")",
                        amend)) return;
                if (!act(tradeId, ConfirmationAction.SUBMIT, maker, clock.plusMinutes(2, 6), null)) return;
                if (!act(tradeId, ConfirmationAction.APPROVE, checker, clock.plusMinutes(10, 40), null)) return;
                if (!act(tradeId, ConfirmationAction.SEND, maker, clock.plusMinutes(2, 6), null)) return;
                if (respond(tradeId, Break.NONE, clock.plusHours(1, 8))) {
                    act(tradeId, ConfirmationAction.CONFIRM, maker, clock.plusMinutes(10, 40), null);
                }
            }
            default -> { }
        }
    }

    private void chaseUntilNow(Long tradeId, Instant sentAt) {
        for (int i = 1; i <= props.chaser().escalateAfter(); i++) {
            Instant at = sentAt.plus(Duration.ofHours(24L * i));
            if (at.isAfter(Instant.now())) return;
            tx.executeWithoutResult(s -> {
                Confirmation c = confirmations.findByTradeId(tradeId).orElseThrow();
                workflow.chase(c, CurrentUser.SYSTEM, at, true);
            });
        }
    }

    private Break randomBreak() {
        return List.of(Break.RATE, Break.VALUE_DATE, Break.AMOUNT).get(random.nextInt(3));
    }

    private boolean respond(Long tradeId, Break b, Instant at) {
        if (at.isAfter(Instant.now())) return false;
        tx.executeWithoutResult(s -> {
            Confirmation c = confirmations.findByTradeId(tradeId).orElseThrow();
            byte[] pdf = simulator.render(c.getTrade(), b);
            String name = c.getTrade().getCounterparty().getShortCode() + "_confirmation_" + c.getTrade().getTradeRef() + ".pdf";
            inbound.process(pdf, name, Source.INBOX, CurrentUser.SYSTEM, null, at);
        });
        return true;
    }

    private boolean act(Long tradeId, ConfirmationAction action, CurrentUser actor, Instant at, String comment) {
        return act(tradeId, action, actor, at, comment, null);
    }

    private boolean act(Long tradeId, ConfirmationAction action, CurrentUser actor, Instant at, String comment,
                        AmendRequest amend) {
        if (at.isAfter(Instant.now())) return false;
        tx.executeWithoutResult(s -> workflow.perform(confirmations.findByTradeId(tradeId).orElseThrow(), action,
                actor, at, comment, amend));
        return true;
    }

    private static int scale(String ccy) {
        return ccy.equals("JPY") || ccy.equals("KRW") ? 0 : 2;
    }

    private static LocalDate weekday(LocalDate d) {
        while (d.getDayOfWeek().getValue() >= 6) d = d.plusDays(1);
        return d;
    }

    private static LocalDate minusBusinessDays(LocalDate d, int n) {
        LocalDate r = d;
        int left = n;
        while (left > 0) {
            r = r.minusDays(1);
            if (r.getDayOfWeek().getValue() < 6) left--;
        }
        return r;
    }

    /** Monotonic simulated clock for replaying a trade's history. */
    private final class Clock {
        private Instant now;

        Clock(Instant start) {
            this.now = start;
        }

        Instant plusMinutes(int min, int max) {
            now = now.plus(Duration.ofMinutes(min + random.nextInt(max - min + 1)));
            return now;
        }

        Instant plusHours(int min, int max) {
            now = now.plus(Duration.ofMinutes(60L * min + random.nextInt(60 * (max - min) + 1)));
            return now;
        }
    }
}
