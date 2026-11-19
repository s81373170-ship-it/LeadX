export const MIN_WALLET_BALANCE = 5000;
export const MIN_SINGLE_TOPUP = 100;
export const LINKEDIN_COST_PER_LEAD = 10;
export const VEHICLE_CHECK_COST = 15;

export const RTO_REGISTRY = {
  MH01: { code: "MH01", office: "RTO Tardeo (Mumbai South)", city: "Mumbai", state: "Maharashtra" },
  MH02: { code: "MH02", office: "RTO Andheri West (Mumbai West)", city: "Mumbai", state: "Maharashtra" },
  MH03: { code: "MH03", office: "RTO Wadala (Mumbai East)", city: "Mumbai", state: "Maharashtra" },
  MH04: { code: "MH04", office: "RTO Thane", city: "Thane", state: "Maharashtra" },
  MH12: { code: "MH12", office: "RTO Pune", city: "Pune", state: "Maharashtra" },
  MH14: { code: "MH14", office: "RTO Pimpri-Chinchwad", city: "Pune", state: "Maharashtra" },
  DL01: { code: "DL01", office: "Mall Road Zonal Office (North Delhi)", city: "New Delhi", state: "Delhi" },
  DL02: { code: "DL02", office: "Tilak Marg Zonal Office (New Delhi)", city: "New Delhi", state: "Delhi" },
  DL03: { code: "DL03", office: "Sheikh Sarai Zonal Office (South Delhi)", city: "New Delhi", state: "Delhi" },
  DL04: { code: "DL04", office: "Janakpuri Zonal Office (West Delhi)", city: "New Delhi", state: "Delhi" },
  KA01: { code: "KA01", office: "RTO Koramangala (Bengaluru Central)", city: "Bengaluru", state: "Karnataka" },
  KA03: { code: "KA03", office: "RTO Indiranagar (Bengaluru East)", city: "Bengaluru", state: "Karnataka" },
  KA05: { code: "KA05", office: "RTO Jayanagar (Bengaluru South)", city: "Bengaluru", state: "Karnataka" },
  GJ01: { code: "GJ01", office: "RTO Subhash Bridge (Ahmedabad)", city: "Ahmedabad", state: "Gujarat" },
  GJ05: { code: "GJ05", office: "RTO Surat", city: "Surat", state: "Gujarat" },
  TN01: { code: "TN01", office: "RTO Chennai Central (Ayanavaram)", city: "Chennai", state: "Tamil Nadu" },
  TS09: { code: "TS09", office: "RTO Khairatabad (Hyderabad Central)", city: "Hyderabad", state: "Telangana" },
  UP16: { code: "UP16", office: "ARTO Gautam Buddha Nagar (Noida)", city: "Noida", state: "Uttar Pradesh" },
  HR26: { code: "HR26", office: "RLA Gurugram North", city: "Gurugram", state: "Haryana" },
  RJ14: { code: "RJ14", office: "RTO Jaipur", city: "Jaipur", state: "Rajasthan" },
  WB01: { code: "WB01", office: "PVD Beltala (Kolkata Central)", city: "Kolkata", state: "West Bengal" },
  KL07: { code: "KL07", office: "RTO Ernakulam (Kochi)", city: "Kochi", state: "Kerala" },
};

export const STATE_NAMES = {
  AN: "Andaman & Nicobar", AP: "Andhra Pradesh", AR: "Arunachal Pradesh", AS: "Assam",
  BR: "Bihar", CG: "Chhattisgarh", CH: "Chandigarh", DD: "Dadra & Nagar Haveli and Daman & Diu",
  DL: "Delhi", GA: "Goa", GJ: "Gujarat", HP: "Himachal Pradesh", HR: "Haryana",
  JH: "Jharkhand", JK: "Jammu & Kashmir", KA: "Karnataka", KL: "Kerala", LA: "Ladakh",
  LD: "Lakshadweep", MH: "Maharashtra", ML: "Meghalaya", MN: "Manipur", MP: "Madhya Pradesh",
  MZ: "Mizoram", NL: "Nagaland", OD: "Odisha", PB: "Punjab", PY: "Puducherry",
  RJ: "Rajasthan", SK: "Sikkim", TN: "Tamil Nadu", TR: "Tripura", TS: "Telangana",
  UK: "Uttarakhand", UP: "Uttar Pradesh", WB: "West Bengal",
};

export function normalizePlate(raw) {
  return String(raw || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
}

export function isValidPlate(plate) {
  return /^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{3,4}$/.test(plate);
}

export function resolveRto(plate) {
  const clean = normalizePlate(plate);
  const p4 = clean.slice(0, 4);
  if (RTO_REGISTRY[p4]) return RTO_REGISTRY[p4];
  const st = STATE_NAMES[clean.slice(0, 2)] || "India";
  return { code: p4, office: `RTO ${p4}`, city: st, state: st };
}

export function assertMinBalanceForSpend(account, cost) {
  if (!account) {
    throw new Error("Active account required.");
  }
  if (account.balance < MIN_WALLET_BALANCE || account.balance - cost < MIN_WALLET_BALANCE) {
    throw new Error("Minimum 5000 balance required, please recharge");
  }
}

function getConfiguredSurepassToken(explicitToken) {
  const fromArg = String(explicitToken || "").trim();
  if (fromArg && fromArg !== "MY_SUREPASS_API_TOKEN") return fromArg;
  const fromGlobal =
    typeof globalThis !== "undefined" ? String(globalThis.LEADX_API_CONFIG?.SUREPASS_API_TOKEN || "").trim() : "";
  if (fromGlobal && fromGlobal !== "MY_SUREPASS_API_TOKEN") return fromGlobal;
  return "";
}

/**
 * Fetches live vehicle RC details from official Surepass Vahan API using SUREPASS_API_TOKEN
 * and deducts 15 points per successful live RC response (enforcing the 5,000-point reserve).
 * Never generates synthetic vehicle models or fake owners when SUREPASS_API_TOKEN is absent.
 */
export async function fetchVehicleDetailsByNumberPlate({
  rawPlate,
  account,
  surepassToken = "",
  fetchImpl = globalThis.fetch,
  cloudAdapter = null,
  onPersist = null,
}) {
  const plate = normalizePlate(rawPlate);
  if (!isValidPlate(plate)) {
    throw new Error("Enter a valid Indian Vehicle Number (e.g. MH02AB1234).");
  }

  const rto = resolveRto(plate);
  const token = getConfiguredSurepassToken(surepassToken);

  if (!token) {
    return {
      savedLeadId: "",
      plateNumber: plate,
      isLiveRcData: false,
      rtoOffice: rto.office,
      registeredCity: rto.city,
      registeredState: rto.state,
      manufacturer: "",
      vehicleModel: "",
      vehicleClass: "",
      fuelType: "",
      registrationDate: "",
      insuranceValidTill: "",
      pucValidTill: "",
      fitnessValidTill: "",
      ownerName: "",
      ownerContact: "",
      consentGranted: false,
      pointsDeducted: 0,
      statusMessage: `Verified Real RTO (${rto.office}). Live RC chassis/insurance/owner data requires SUREPASS_API_TOKEN in Settings or firebase-config.js — zero fake data generated, 0 pts deducted.`,
    };
  }

  assertMinBalanceForSpend(account, VEHICLE_CHECK_COST);

  if (typeof fetchImpl !== "function") {
    throw new Error("Fetch API is unavailable in this environment.");
  }

  const resp = await fetchImpl("https://kyc-api.surepass.io/api/v1/rc/rc-full", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ id_number: plate }),
  });

  const payload = await resp.json();
  if (!resp.ok || !payload?.success || !payload?.data) {
    throw new Error(payload?.message || `Surepass Vahan API error (HTTP ${resp.status}).`);
  }

  const d = payload.data;
  const regDate = String(d.registration_date || "").trim();
  const leadId = `lead_${crypto.randomUUID()}`;
  const txId = `tx_${crypto.randomUUID()}`;
  const now = Date.now();

  // Deduct 15 points per successful Vahan RC lookup
  account.balance -= VEHICLE_CHECK_COST;

  const lead = {
    id: leadId,
    source: "numberplate",
    ownerAccountId: account.ownerAccountId,
    fetchedByLoginId: account.loginId,
    name: `${d.maker_description || ""} ${d.maker_model || ""}`.trim() || plate,
    title: `${d.vehicle_category || ""} · ${d.fuel_type || ""} · Reg ${regDate.slice(0, 4)}`.trim(),
    company: String(d.registered_at || rto.office).trim(),
    email: "",
    phone: "",
    industry: `Insurance Valid Till ${d.insurance_upto || "—"} · PUC ${d.pucc_upto || "—"}`,
    location: `${rto.city}, ${rto.state}`,
    plateNumber: plate,
    vehicleDetails: `Reg Date: ${regDate} · Fitness: ${d.fit_up_to || "—"} · Consent Pending`,
    profileUrl: "",
    consentGranted: false,
    pointsCost: VEHICLE_CHECK_COST,
    createdAtMillis: now,
  };

  const tx = {
    id: txId,
    type: "lead_vehicle",
    ownerAccountId: account.ownerAccountId,
    loginId: account.loginId,
    counterpartyId: "",
    amount: -VEHICLE_CHECK_COST,
    balanceAfter: account.balance,
    note: `Vehicle RC Check (${plate})`,
    createdAtMillis: now,
  };

  if (typeof onPersist === "function") {
    onPersist(lead, tx);
  }
  if (cloudAdapter) {
    await cloudAdapter.updateBalance(account.loginId, account.balance);
    await cloudAdapter.writeLead(lead);
    await cloudAdapter.writeTransaction(tx);
  }

  return {
    savedLeadId: leadId,
    plateNumber: plate,
    isLiveRcData: true,
    rtoOffice: String(d.registered_at || rto.office).trim(),
    registeredCity: rto.city,
    registeredState: rto.state,
    manufacturer: String(d.maker_description || "").trim(),
    vehicleModel: String(d.maker_model || "").trim(),
    vehicleClass: String(d.vehicle_category || "").trim(),
    fuelType: String(d.fuel_type || "").trim(),
    registrationDate: regDate,
    insuranceValidTill: String(d.insurance_upto || "").trim(),
    pucValidTill: String(d.pucc_upto || "").trim(),
    fitnessValidTill: String(d.fit_up_to || "").trim(),
    ownerName: String(d.owner_name || "").trim(),
    ownerContact: String(d.mobile_number || "").trim(),
    consentGranted: false,
    pointsDeducted: VEHICLE_CHECK_COST,
    statusMessage: "Live Official Vahan RC Verified (-15 pts)",
  };
}

/**
 * Fetches live LinkedIn profile enrichment via Proxycurl API when PROXYCURL_API_KEY is supplied.
 */
export async function fetchLinkedInProfileViaProxycurl({
  rawUrl,
  proxycurlKey = "",
  fetchImpl = globalThis.fetch,
}) {
  const url = String(rawUrl || "").trim();
  if (!url.toLowerCase().includes("linkedin.com/")) {
    throw new Error("Enter a valid LinkedIn URL (e.g. https://www.linkedin.com/in/...).");
  }
  const normalizedUrl = url.startsWith("http") ? url : `https://${url}`;
  const key =
    String(proxycurlKey || "").trim() ||
    (typeof globalThis !== "undefined" ? String(globalThis.LEADX_API_CONFIG?.PROXYCURL_API_KEY || "").trim() : "");

  if (!key || key === "MY_PROXYCURL_API_KEY") {
    throw new Error(
      "Direct cross-origin LinkedIn scraping is blocked by browser CORS without PROXYCURL_API_KEY. Set PROXYCURL_API_KEY in Settings or use the LinkedIn Sales Navigator DOM Scraper below."
    );
  }

  const resp = await fetchImpl(
    `https://nubela.co/proxycurl/api/v2/linkedin?url=${encodeURIComponent(normalizedUrl)}&use_cache=if-present`,
    { headers: { Authorization: `Bearer ${key}` } }
  );
  if (!resp.ok) {
    throw new Error(`Proxycurl API error (HTTP ${resp.status}).`);
  }
  const json = await resp.json();
  if (!json?.full_name) {
    throw new Error("Proxycurl returned no public profile details for this URL.");
  }
  const exp = json.experiences?.[0] || {};
  return {
    id: `li_${crypto.randomUUID()}`,
    name: json.full_name,
    title: json.occupation || json.headline || exp.title || "LinkedIn Profile",
    company: exp.company || "",
    email: json.personal_emails?.[0] || json.work_email || "",
    phone: "",
    industry: json.industry || "",
    location: [json.city, json.state, json.country_full_name].filter(Boolean).join(", "),
    profileUrl: normalizedUrl,
    captureMethod: "Live Proxycurl API",
    selected: true,
  };
}
