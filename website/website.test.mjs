import test from "node:test";
import assert from "node:assert/strict";
import {
  MIN_WALLET_BALANCE,
  VEHICLE_CHECK_COST,
  LINKEDIN_COST_PER_LEAD,
  assertMinBalanceForSpend,
  fetchVehicleDetailsByNumberPlate,
} from "./api-services.js";
import {
  SALES_NAVIGATOR_CONTENT_SCRIPT,
  buildChromeBackgroundServiceScript,
  parseSalesNavigatorInput,
  saveScrapedLinkedInLeadsToFirestore,
} from "./linkedin-scraper.js";
import { encodeQrMatrix, renderQrSvg } from "./qr-code.js";

test("fetchVehicleDetailsByNumberPlate returns real RTO with 0 pts deducted when SUREPASS_API_TOKEN is empty", async () => {
  const account = { loginId: "OWN1001", ownerAccountId: "OWN1001", balance: 6000 };
  const result = await fetchVehicleDetailsByNumberPlate({
    rawPlate: "mh-02 ab 1234",
    account,
    surepassToken: "",
  });

  assert.equal(result.plateNumber, "MH02AB1234");
  assert.equal(result.isLiveRcData, false);
  assert.equal(result.rtoOffice, "RTO Andheri West (Mumbai West)");
  assert.equal(result.pointsDeducted, 0);
  assert.equal(account.balance, 6000);
});

test("fetchVehicleDetailsByNumberPlate calls Surepass RC API, deducts 15 pts, and saves to Firestore adapter", async () => {
  const account = { loginId: "EMP001", ownerAccountId: "OWN1001", balance: 5050 };
  const cloudCalls = { balances: [], leads: [], txs: [] };
  const cloudAdapter = {
    async updateBalance(loginId, balance) {
      cloudCalls.balances.push({ loginId, balance });
    },
    async writeLead(lead) {
      cloudCalls.leads.push(lead);
    },
    async writeTransaction(tx) {
      cloudCalls.txs.push(tx);
    },
  };

  let fetchedUrl = "";
  let fetchedOptions = null;
  const mockFetch = async (url, options) => {
    fetchedUrl = url;
    fetchedOptions = options;
    return {
      ok: true,
      status: 200,
      async json() {
        return {
          success: true,
          data: {
            maker_description: "MARUTI SUZUKI INDIA LTD",
            maker_model: "SWIFT VXI",
            vehicle_category: "LMV",
            fuel_type: "PETROL",
            registration_date: "2021-06-15",
            registered_at: "RTO Andheri West (Mumbai West)",
            insurance_upto: "2027-06-14",
            pucc_upto: "2026-12-01",
            fit_up_to: "2036-06-14",
            owner_name: "RAJESH SHARMA",
            mobile_number: "+919820000001",
          },
        };
      },
    };
  };

  let persistedLead = null;
  let persistedTx = null;
  const result = await fetchVehicleDetailsByNumberPlate({
    rawPlate: "MH02AB1234",
    account,
    surepassToken: "test-surepass-token-123",
    fetchImpl: mockFetch,
    cloudAdapter,
    onPersist: (lead, tx) => {
      persistedLead = lead;
      persistedTx = tx;
    },
  });

  assert.equal(fetchedUrl, "https://kyc-api.surepass.io/api/v1/rc/rc-full");
  assert.equal(fetchedOptions.method, "POST");
  assert.equal(fetchedOptions.headers.Authorization, "Bearer test-surepass-token-123");
  assert.equal(JSON.parse(fetchedOptions.body).id_number, "MH02AB1234");

  assert.equal(result.isLiveRcData, true);
  assert.equal(result.pointsDeducted, VEHICLE_CHECK_COST);
  assert.equal(account.balance, 5050 - VEHICLE_CHECK_COST);
  assert.equal(result.consentGranted, false);

  assert.equal(persistedLead.source, "numberplate");
  assert.equal(persistedLead.consentGranted, false);
  assert.equal(persistedLead.phone, "");
  assert.equal(persistedTx.type, "lead_vehicle");
  assert.equal(persistedTx.amount, -15);

  assert.equal(cloudCalls.balances[0].balance, 5035);
  assert.equal(cloudCalls.leads.length, 1);
  assert.equal(cloudCalls.txs.length, 1);
});

test("assertMinBalanceForSpend blocks requests that breach the 5,000-point reserve", () => {
  assert.throws(
    () => assertMinBalanceForSpend({ balance: MIN_WALLET_BALANCE + 10 }, VEHICLE_CHECK_COST),
    /Minimum 5000 balance required, please recharge/
  );
  assert.throws(
    () => assertMinBalanceForSpend({ balance: MIN_WALLET_BALANCE - 1 }, LINKEDIN_COST_PER_LEAD),
    /Minimum 5000 balance required, please recharge/
  );
});

test("parseSalesNavigatorInput and saveScrapedLinkedInLeadsToFirestore extract 5 fields & persist to Firestore leads", async () => {
  const jsonPayload = JSON.stringify([
    {
      fullName: "Aarav Mehta",
      company: "FinScale Technologies",
      designation: "VP of Engineering",
      location: "Bengaluru, Karnataka",
      profileUrl: "https://www.linkedin.com/sales/lead/ACwAAA12345",
    },
    {
      fullName: "LinkedIn Member",
      company: "Hidden",
      designation: "Hidden",
      location: "",
      profileUrl: "https://www.linkedin.com/in/hidden",
    },
  ]);

  const candidates = parseSalesNavigatorInput(jsonPayload);
  assert.equal(candidates.length, 1);
  assert.equal(candidates[0].name, "Aarav Mehta");
  assert.equal(candidates[0].company, "FinScale Technologies");
  assert.equal(candidates[0].title, "VP of Engineering");
  assert.equal(candidates[0].location, "Bengaluru, Karnataka");
  assert.equal(candidates[0].profileUrl, "https://www.linkedin.com/sales/lead/ACwAAA12345");

  const account = { loginId: "OWN1001", ownerAccountId: "OWN1001", balance: 5100 };
  const cloudLeads = [];
  const cloudTxs = [];
  const cloudAdapter = {
    async updateBalance() {},
    async writeLead(l) {
      cloudLeads.push(l);
    },
    async writeTransaction(t) {
      cloudTxs.push(t);
    },
  };

  const saved = await saveScrapedLinkedInLeadsToFirestore({
    candidates,
    account,
    cloudAdapter,
  });

  assert.equal(saved.savedCount, 1);
  assert.equal(saved.totalCost, LINKEDIN_COST_PER_LEAD);
  assert.equal(account.balance, 5090);
  assert.equal(cloudLeads.length, 1);
  assert.equal(cloudLeads[0].source, "linkedin");
  assert.equal(cloudLeads[0].company, "FinScale Technologies");
  assert.equal(cloudTxs[0].amount, -10);
});

test("chrome background & content scripts include Sales Navigator selectors and Firestore leads endpoint", () => {
  assert.ok(SALES_NAVIGATOR_CONTENT_SCRIPT.includes("person-name"));
  assert.ok(SALES_NAVIGATOR_CONTENT_SCRIPT.includes("company-name"));
  const bg = buildChromeBackgroundServiceScript("ai-studio-android-leadbrid-ecb9535c-5b52-48e9-8699-b638d1f3604c");
  assert.ok(bg.includes("chrome.runtime.onMessage.addListener"));
  assert.ok(bg.includes("chrome.scripting.executeScript"));
  assert.ok(bg.includes("/leads"));
});

test("QR code encoder builds valid Version 5 matrix and crisp SVG for the LeadX-Pro.apk download URL", () => {
  const url = "https://github.com/s81373170-ship-it/LeadX/releases/latest/download/LeadX-Pro.apk";
  const encoded = encodeQrMatrix(url);
  assert.equal(encoded.version, 5);
  assert.equal(encoded.size, 37);
  // Top-left 7x7 finder outer corners + center dot must be dark
  assert.equal(encoded.matrix[0][0], 1);
  assert.equal(encoded.matrix[0][6], 1);
  assert.equal(encoded.matrix[6][0], 1);
  assert.equal(encoded.matrix[3][3], 1);
  assert.equal(encoded.matrix[1][1], 0);
  const svg = renderQrSvg(url);
  assert.ok(svg.startsWith("<svg "));
  assert.ok(svg.includes("viewBox=\"0 0 41 41\""));
  assert.ok(svg.includes("<rect "));
});
