import {
  MIN_WALLET_BALANCE,
  MIN_SINGLE_TOPUP,
  LINKEDIN_COST_PER_LEAD,
  VEHICLE_CHECK_COST,
  normalizePlate,
  isValidPlate,
  assertMinBalanceForSpend,
  fetchVehicleDetailsByNumberPlate,
  fetchLinkedInProfileViaProxycurl,
} from "./api-services.js";
import {
  SALES_NAVIGATOR_CONTENT_SCRIPT,
  buildChromeBackgroundServiceScript,
  parseSalesNavigatorInput,
  saveScrapedLinkedInLeadsToFirestore,
} from "./linkedin-scraper.js";

const STORAGE_KEY = "leadx_pro_web_workspace_v1";
const API_KEYS_STORAGE_KEY = "leadx_pro_web_api_keys_v1";
const FIRESTORE_DB_ID = "ai-studio-android-leadbrid-ecb9535c-5b52-48e9-8699-b638d1f3604c";
const GITHUB_RELEASE_APK_URL = "https://github.com/s81373170-ship-it/LeadX/releases/latest/download/LeadX-Pro.apk";
const INSTALL_GUIDE_TEXT = [
  "How to Install LeadX Pro (.apk) on Android:",
  `1. Download LeadX-Pro.apk in Chrome: ${GITHUB_RELEASE_APK_URL}`,
  "2. Open LeadX-Pro.apk from Chrome Downloads or your Files app (avoid tapping inside WhatsApp on Realme/Oppo/Xiaomi).",
  "3. Tap Settings when prompted and turn ON 'Allow from this source' ('Install from unknown sources') for Chrome or Files.",
  "   Manual path: Settings -> Apps -> Special app access -> Install unknown apps -> Chrome / Files -> Allow.",
  "4. Return and tap Install (if Play Protect prompts, tap More details -> Install anyway).",
].join("\n");

function getApkDownloadUrl() {
  if (typeof window !== "undefined" && window.location?.hostname?.endsWith("github.io")) {
    return "./LeadX-Pro.apk";
  }
  return GITHUB_RELEASE_APK_URL;
}

function getShareableApkUrl() {
  if (typeof window !== "undefined" && window.location?.hostname?.endsWith("github.io")) {
    return new URL("./LeadX-Pro.apk", window.location.href).href;
  }
  return GITHUB_RELEASE_APK_URL;
}

async function copyOrShareApkLink() {
  const url = getShareableApkUrl();
  if (typeof navigator !== "undefined" && navigator.share) {
    try {
      await navigator.share({
        title: "LeadX Pro Android App (.apk)",
        text: "Download and install LeadX Pro Android APK in Chrome:",
        url,
      });
      setBanner("Opened share sheet with permanent LeadX-Pro.apk link.", "success");
      return;
    } catch (err) {
      if (err?.name === "AbortError") return;
    }
  }
  await navigator.clipboard.writeText(url);
  setBanner("Permanent LeadX-Pro.apk link copied to clipboard. Share this link instead of Chrome's temporary download URL.", "success");
}

function esc(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatPts(num) {
  return Number(num || 0).toLocaleString("en-IN");
}

function normalizeLoginId(raw) {
  return String(raw || "").trim().toUpperCase();
}

function isValidLoginId(id) {
  return /^[A-Z0-9._-]{3,24}$/.test(id);
}

async function sha256Hex(text) {
  const encoded = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest("SHA-256", encoded);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function loadApiKeys() {
  try {
    return JSON.parse(localStorage.getItem(API_KEYS_STORAGE_KEY) || "{}") || {};
  } catch {
    return {};
  }
}

function saveApiKeys(keys) {
  localStorage.setItem(API_KEYS_STORAGE_KEY, JSON.stringify(keys));
}

async function createSeedWorkspace() {
  const now = Date.now();
  const ownerHash = await sha256Hex("Owner@123");
  const empHash = await sha256Hex("Emp@123");
  return {
    accounts: [
      {
        loginId: "OWN1001",
        fullName: "Workspace Owner",
        role: "owner",
        ownerAccountId: "OWN1001",
        passwordHash: ownerHash,
        balance: 12500,
        createdAtMillis: now - 20000,
      },
      {
        loginId: "EMP001",
        fullName: "Field Executive",
        role: "employee",
        ownerAccountId: "OWN1001",
        passwordHash: empHash,
        balance: 5000,
        createdAtMillis: now - 10000,
      },
    ],
    leads: [],
    transactions: [
      {
        id: "tx_init_owner",
        type: "topup",
        ownerAccountId: "OWN1001",
        loginId: "OWN1001",
        counterpartyId: "",
        amount: 12500,
        balanceAfter: 12500,
        note: "Owner Opening Reserve (₹12,500)",
        createdAtMillis: now - 20000,
      },
      {
        id: "tx_init_emp",
        type: "topup",
        ownerAccountId: "OWN1001",
        loginId: "EMP001",
        counterpartyId: "",
        amount: 5000,
        balanceAfter: 5000,
        note: "Employee Minimum Reserve (₹5,000)",
        createdAtMillis: now - 10000,
      },
    ],
  };
}

class CloudAdapter {
  constructor() {
    this.enabled = false;
    this.user = null;
    this.auth = null;
    this.db = null;
    this.fs = null;
  }

  async init() {
    const cfg = window.LEADX_FIREBASE_CONFIG;
    if (!cfg || !cfg.apiKey || !cfg.appId || !cfg.projectId) {
      return false;
    }
    try {
      const appMod = await import("https://www.gstatic.com/firebasejs/11.0.2/firebase-app.js");
      const authMod = await import("https://www.gstatic.com/firebasejs/11.0.2/firebase-auth.js");
      const fsMod = await import("https://www.gstatic.com/firebasejs/11.0.2/firebase-firestore.js");

      const app = appMod.initializeApp(cfg);
      this.auth = authMod;
      this.fs = fsMod;
      this.authInstance = authMod.getAuth(app);
      this.db = fsMod.getFirestore(app, FIRESTORE_DB_ID);
      this.enabled = true;
      return true;
    } catch (err) {
      console.warn("Firebase Web SDK unavailable; using browser local workspace:", err);
      return false;
    }
  }

  onAuthChange(cb) {
    if (!this.enabled) {
      cb(null);
      return;
    }
    this.auth.onAuthStateChanged(this.authInstance, (user) => {
      this.user = user;
      cb(user);
    });
  }

  async signInGoogle() {
    const provider = new this.auth.GoogleAuthProvider();
    const res = await this.auth.signInWithPopup(this.authInstance, provider);
    this.user = res.user;
    return res.user;
  }

  async signOutGoogle() {
    if (!this.enabled) return;
    await this.auth.signOut(this.authInstance);
    this.user = null;
  }

  async syncDown(workspace) {
    if (!this.enabled || !this.user) return workspace;
    const uid = this.user.uid;
    const { collection, getDocs, doc, setDoc, query, orderBy, serverTimestamp } = this.fs;

    const accSnap = await getDocs(collection(this.db, "users", uid, "team_accounts"));
    if (accSnap.empty) {
      for (const acc of workspace.accounts) {
        await setDoc(doc(this.db, "users", uid, "team_accounts", acc.loginId), {
          userId: uid,
          loginId: acc.loginId,
          passwordHash: acc.passwordHash,
          fullName: acc.fullName,
          role: acc.role,
          ownerAccountId: acc.ownerAccountId,
          balance: acc.balance,
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
      }
    } else {
      workspace.accounts = accSnap.docs.map((d) => {
        const data = d.data();
        return {
          loginId: data.loginId || d.id,
          fullName: data.fullName || d.id,
          role: data.role || "employee",
          ownerAccountId: data.ownerAccountId || "OWN1001",
          passwordHash: data.passwordHash || "",
          balance: Number(data.balance ?? MIN_WALLET_BALANCE),
          createdAtMillis: data.createdAt?.toMillis?.() || Date.now(),
        };
      });
    }

    const leadsSnap = await getDocs(
      query(collection(this.db, "users", uid, "leads"), orderBy("createdAt", "desc"))
    );
    workspace.leads = leadsSnap.docs.map((d) => {
      const data = d.data();
      return {
        id: d.id,
        ...data,
        pointsCost: Number(data.pointsCost ?? 0),
        createdAtMillis: data.createdAt?.toMillis?.() || Date.now(),
      };
    });

    const txSnap = await getDocs(
      query(collection(this.db, "users", uid, "transactions"), orderBy("createdAt", "desc"))
    );
    if (!txSnap.empty) {
      workspace.transactions = txSnap.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          ...data,
          amount: Number(data.amount ?? 0),
          balanceAfter: Number(data.balanceAfter ?? 0),
          createdAtMillis: data.createdAt?.toMillis?.() || Date.now(),
        };
      });
    }
    return workspace;
  }

  async saveRoleProfile(account) {
    if (!this.enabled || !this.user) return;
    const { doc, setDoc, serverTimestamp } = this.fs;
    await setDoc(doc(this.db, "users", this.user.uid), {
      userId: this.user.uid,
      role: account.role,
      activeLoginId: account.loginId,
      fullName: account.fullName,
      updatedAt: serverTimestamp(),
    });
  }

  async saveNewAccount(account) {
    if (!this.enabled || !this.user) return;
    const { doc, setDoc, serverTimestamp } = this.fs;
    await setDoc(doc(this.db, "users", this.user.uid, "team_accounts", account.loginId), {
      userId: this.user.uid,
      loginId: account.loginId,
      passwordHash: account.passwordHash,
      fullName: account.fullName,
      role: account.role,
      ownerAccountId: account.ownerAccountId,
      balance: account.balance,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  }

  async updateBalance(loginId, balance) {
    if (!this.enabled || !this.user) return;
    const { doc, updateDoc, serverTimestamp } = this.fs;
    await updateDoc(doc(this.db, "users", this.user.uid, "team_accounts", loginId), {
      balance,
      updatedAt: serverTimestamp(),
    });
  }

  async writeLead(lead) {
    if (!this.enabled || !this.user) return;
    const { doc, setDoc, serverTimestamp } = this.fs;
    const { id, createdAtMillis, ...rest } = lead;
    await setDoc(doc(this.db, "users", this.user.uid, "leads", id), {
      ...rest,
      userId: this.user.uid,
      createdAt: serverTimestamp(),
    });
  }

  async updateLeadConsent(leadId, fields) {
    if (!this.enabled || !this.user) return;
    const { doc, updateDoc } = this.fs;
    await updateDoc(doc(this.db, "users", this.user.uid, "leads", leadId), fields);
  }

  async writeTransaction(tx) {
    if (!this.enabled || !this.user) return;
    const { doc, setDoc, serverTimestamp } = this.fs;
    const { id, createdAtMillis, ...rest } = tx;
    await setDoc(doc(this.db, "users", this.user.uid, "transactions", id), {
      ...rest,
      userId: this.user.uid,
      createdAt: serverTimestamp(),
    });
  }

  async queryFirestoreLeads(ownerAccountId) {
    if (!this.enabled || !this.user) return null;
    const { collection, getDocs, query, orderBy } = this.fs;
    const leadsSnap = await getDocs(
      query(collection(this.db, "users", this.user.uid, "leads"), orderBy("createdAt", "desc"))
    );
    return leadsSnap.docs
      .map((d) => ({ id: d.id, ...d.data() }))
      .filter((l) => l.ownerAccountId === ownerAccountId);
  }
}

const cloud = new CloudAdapter();

const state = {
  workspace: null,
  loggedInId: null,
  authTab: "signin",
  route: "dashboard", // dashboard | transfer | fetch | team | settings
  fetchTab: "linkedin", // linkedin | plate
  teamTab: "leads", // leads | ledger | directory
  searchQuery: "",
  candidates: [],
  vehicleResult: null,
  topUpModalOpen: false,
  howToInstallModalOpen: false,
  topUpAmount: "5000",
  banner: null,
  busy: false,
  firebaseReady: false,
  googleUser: null,
  apiKeys: loadApiKeys(),
};

async function loadWorkspace() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed?.accounts?.length) return parsed;
    }
  } catch { /* fallback to seed */ }
  const seed = await createSeedWorkspace();
  saveWorkspace(seed);
  return seed;
}

function saveWorkspace(ws = state.workspace) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(ws));
}

function activeAccount() {
  if (!state.workspace || !state.loggedInId) return null;
  return state.workspace.accounts.find((a) => a.loginId === state.loggedInId) || null;
}

function setBanner(message, type = "success") {
  state.banner = { message, type };
  render();
}

async function handleSignIn(loginIdRaw, password) {
  const loginId = normalizeLoginId(loginIdRaw);
  if (!isValidLoginId(loginId)) throw new Error("Enter a valid Login ID (3-24 chars: A-Z, 0-9).");
  if (!password) throw new Error("Enter your password.");
  const acc = state.workspace.accounts.find((a) => a.loginId === loginId);
  if (!acc) throw new Error(`Account ID ${loginId} not found.`);
  const hash = await sha256Hex(password);
  if (acc.passwordHash !== hash) throw new Error("Incorrect Login ID or Password.");

  state.loggedInId = acc.loginId;
  state.route = "dashboard";
  state.candidates = [];
  state.vehicleResult = null;
  await cloud.saveRoleProfile(acc);
  setBanner(`Signed in as ${acc.loginId} (${acc.role === "owner" ? "Owner Dashboard" : "Employee Dashboard"}).`, "success");
}

async function handleCreateOwner(rawOwnerId, fullName, password, autoLogin = true) {
  const ownerId = normalizeLoginId(rawOwnerId);
  if (!isValidLoginId(ownerId)) throw new Error("Owner ID must be 3-24 chars (A-Z, 0-9, ., _, -).");
  if ((password || "").trim().length < 6) throw new Error("Owner password must be at least 6 characters.");
  if (state.workspace.accounts.some((a) => a.loginId === ownerId)) throw new Error(`ID ${ownerId} already exists.`);

  const now = Date.now();
  const owner = {
    loginId: ownerId,
    fullName: (fullName || ownerId).trim().slice(0, 60),
    role: "owner",
    ownerAccountId: ownerId,
    passwordHash: await sha256Hex(password),
    balance: MIN_WALLET_BALANCE,
    createdAtMillis: now,
  };
  const tx = {
    id: `tx_${crypto.randomUUID()}`,
    type: "topup",
    ownerAccountId: ownerId,
    loginId: ownerId,
    counterpartyId: "",
    amount: MIN_WALLET_BALANCE,
    balanceAfter: MIN_WALLET_BALANCE,
    note: "Owner Minimum Reserve Initialized",
    createdAtMillis: now,
  };
  state.workspace.accounts.push(owner);
  state.workspace.transactions.unshift(tx);
  saveWorkspace();
  await cloud.saveNewAccount(owner);
  await cloud.writeTransaction(tx);

  if (autoLogin) {
    state.loggedInId = owner.loginId;
    state.route = "dashboard";
    await cloud.saveRoleProfile(owner);
  }
  setBanner(`Owner ID ${owner.loginId} created with 5,000 pts reserve.`, "success");
}

async function handleCreateEmployee(rawEmpId, fullName, password) {
  const owner = activeAccount();
  if (!owner || owner.role !== "owner") throw new Error("Only Owner IDs can add Employee IDs.");
  const empId = normalizeLoginId(rawEmpId);
  if (!isValidLoginId(empId)) throw new Error("Employee ID must be 3-24 chars (A-Z, 0-9, ., _, -).");
  if ((password || "").trim().length < 6) throw new Error("Employee password must be at least 6 characters.");
  if (state.workspace.accounts.some((a) => a.loginId === empId)) throw new Error(`ID ${empId} already exists.`);

  const now = Date.now();
  const emp = {
    loginId: empId,
    fullName: (fullName || empId).trim().slice(0, 60),
    role: "employee",
    ownerAccountId: owner.loginId,
    passwordHash: await sha256Hex(password),
    balance: MIN_WALLET_BALANCE,
    createdAtMillis: now,
  };
  const tx = {
    id: `tx_${crypto.randomUUID()}`,
    type: "topup",
    ownerAccountId: owner.loginId,
    loginId: empId,
    counterpartyId: "",
    amount: MIN_WALLET_BALANCE,
    balanceAfter: MIN_WALLET_BALANCE,
    note: "Employee Minimum Reserve Initialized",
    createdAtMillis: now,
  };
  state.workspace.accounts.push(emp);
  state.workspace.transactions.unshift(tx);
  saveWorkspace();
  await cloud.saveNewAccount(emp);
  await cloud.writeTransaction(tx);
  setBanner(`Employee ${empId} created with password & 5,000 pts reserve.`, "success");
}

async function handleTopUp(amountRaw) {
  const acc = activeAccount();
  if (!acc) return;
  const amount = Number(String(amountRaw).trim());
  if (!Number.isFinite(amount) || amount < MIN_SINGLE_TOPUP) {
    throw new Error(`Minimum single top-up is ₹${MIN_SINGLE_TOPUP} (${MIN_SINGLE_TOPUP} pts).`);
  }
  const updated = acc.balance + amount;
  if (updated < MIN_WALLET_BALANCE) {
    throw new Error(`Minimum 5000 balance required, please recharge at least ${MIN_WALLET_BALANCE - acc.balance} pts.`);
  }
  acc.balance = updated;
  const tx = {
    id: `tx_${crypto.randomUUID()}`,
    type: "topup",
    ownerAccountId: acc.ownerAccountId,
    loginId: acc.loginId,
    counterpartyId: "",
    amount,
    balanceAfter: updated,
    note: `Razorpay Top-Up (₹${amount} · ${acc.role})`,
    createdAtMillis: Date.now(),
  };
  state.workspace.transactions.unshift(tx);
  saveWorkspace();
  await cloud.updateBalance(acc.loginId, updated);
  await cloud.writeTransaction(tx);
  state.topUpModalOpen = false;
  setBanner(`Top-Up complete: +${formatPts(amount)} pts added to ${acc.loginId} · Bal: ${formatPts(updated)} pts.`, "success");
}

async function handleTransferPoints(recipientRaw, amountRaw) {
  const sender = activeAccount();
  if (!sender) return;
  const recipientId = normalizeLoginId(recipientRaw);
  const amount = Number(String(amountRaw).trim());
  if (!isValidLoginId(recipientId)) throw new Error("Enter a valid Employee ID.");
  if (recipientId === sender.loginId) throw new Error("Cannot transfer points to yourself.");
  if (!Number.isFinite(amount) || amount <= 0) throw new Error("Enter a positive transfer amount.");

  const recipient = state.workspace.accounts.find((a) => a.loginId === recipientId);
  if (!recipient) throw new Error(`Target ID ${recipientId} not found.`);
  if (recipient.ownerAccountId !== sender.ownerAccountId) {
    throw new Error(`Points can only be shared within team ${sender.ownerAccountId}.`);
  }
  const senderAfter = sender.balance - amount;
  if (senderAfter < MIN_WALLET_BALANCE) {
    const maxShare = Math.max(0, sender.balance - MIN_WALLET_BALANCE);
    throw new Error(`Minimum 5000 balance required, please recharge. Max shareable: ${maxShare} pts.`);
  }

  sender.balance = senderAfter;
  recipient.balance += amount;
  const now = Date.now();
  const outTx = {
    id: `tx_${crypto.randomUUID()}`,
    type: "transfer_out",
    ownerAccountId: sender.ownerAccountId,
    loginId: sender.loginId,
    counterpartyId: recipientId,
    amount: -amount,
    balanceAfter: sender.balance,
    note: `ID Share to ${recipientId}`,
    createdAtMillis: now,
  };
  const inTx = {
    id: `tx_${crypto.randomUUID()}`,
    type: "transfer_in",
    ownerAccountId: recipient.ownerAccountId,
    loginId: recipientId,
    counterpartyId: sender.loginId,
    amount,
    balanceAfter: recipient.balance,
    note: `ID Share from ${sender.loginId}`,
    createdAtMillis: now + 1,
  };
  state.workspace.transactions.unshift(inTx, outTx);
  saveWorkspace();
  await cloud.updateBalance(sender.loginId, sender.balance);
  await cloud.updateBalance(recipient.loginId, recipient.balance);
  await cloud.writeTransaction(outTx);
  await cloud.writeTransaction(inTx);
  setBanner(`Transferred ${formatPts(amount)} pts from ${sender.loginId} to ${recipientId} & logged in 'transactions'.`, "success");
}

async function handleExtractLinkedInUrl(rawUrl) {
  const cand = await fetchLinkedInProfileViaProxycurl({
    rawUrl,
    proxycurlKey: state.apiKeys.proxycurlKey || window.LEADX_API_CONFIG?.PROXYCURL_API_KEY || "",
  });
  state.candidates = [cand, ...state.candidates.filter((c) => c.profileUrl !== cand.profileUrl)];
  setBanner(`Live LinkedIn profile extracted: ${cand.name}.`, "success");
}

function handleImportDomJson(rawInput) {
  const parsed = parseSalesNavigatorInput(rawInput);
  state.candidates = [...parsed, ...state.candidates];
  setBanner(
    `Extracted ${parsed.length} real LinkedIn lead${parsed.length > 1 ? "s" : ""} (Name, Company, Designation, Location, Profile URL).`,
    "success"
  );
}

async function handleSaveLinkedInLeads() {
  const acc = activeAccount();
  if (!acc) return;
  const result = await saveScrapedLinkedInLeadsToFirestore({
    candidates: state.candidates,
    account: acc,
    cloudAdapter: cloud,
    onPersist: (newLeads, tx) => {
      state.workspace.leads.unshift(...newLeads);
      state.workspace.transactions.unshift(tx);
      state.candidates = [];
      saveWorkspace();
    },
  });
  setBanner(
    `Saved ${result.savedCount} LinkedIn lead${result.savedCount > 1 ? "s" : ""} to Firestore 'leads' (-${result.totalCost} pts).`,
    "success"
  );
}

async function handleLookupVehicle(rawPlate) {
  const acc = activeAccount();
  if (!acc) return;
  const result = await fetchVehicleDetailsByNumberPlate({
    rawPlate,
    account: acc,
    surepassToken: state.apiKeys.surepassToken || window.LEADX_API_CONFIG?.SUREPASS_API_TOKEN || "",
    cloudAdapter: cloud,
    onPersist: (lead, tx) => {
      state.workspace.leads.unshift(lead);
      state.workspace.transactions.unshift(tx);
      saveWorkspace();
    },
  });
  state.vehicleResult = result;
  if (!result.isLiveRcData) {
    setBanner(result.statusMessage, "warning");
  } else {
    setBanner(
      `Live Vahan RC verified & saved for ${result.plateNumber} (-${result.pointsDeducted} pts). Tap Request Consent to reveal owner details.`,
      "success"
    );
  }
}

async function handleGrantVehicleConsent() {
  const v = state.vehicleResult;
  if (!v || !v.isLiveRcData || !v.savedLeadId) return;
  v.consentGranted = true;
  const lead = state.workspace.leads.find((l) => l.id === v.savedLeadId);
  const displayName = `${v.ownerName} (${v.manufacturer} ${v.vehicleModel})`.trim();
  const details = `Reg Date: ${v.registrationDate} · Ins: ${v.insuranceValidTill} · Consent Verified`;
  if (lead) {
    lead.name = displayName;
    lead.phone = v.ownerContact;
    lead.vehicleDetails = details;
    lead.consentGranted = true;
  }
  saveWorkspace();
  await cloud.updateLeadConsent(v.savedLeadId, {
    name: displayName,
    phone: v.ownerContact,
    email: "",
    vehicleDetails: details,
    consentGranted: true,
  });
  setBanner(`Owner consent verified for ${v.plateNumber}. Full owner details saved.`, "success");
}

async function handleExportOwnerCsv() {
  const acc = activeAccount();
  if (!acc || acc.role !== "owner") throw new Error("Only Owner accounts can export all team leads.");

  let leads = null;
  if (cloud.enabled && cloud.user) {
    try {
      leads = await cloud.queryFirestoreLeads(acc.loginId);
    } catch (err) {
      console.warn("Firestore direct export query failed, using synced local state:", err);
    }
  }
  if (!leads) {
    leads = state.workspace.leads.filter((l) => l.ownerAccountId === acc.loginId);
  }
  if (leads.length === 0) {
    throw new Error("No collected leads found to export yet.");
  }

  const csvCell = (val) => `"${String(val ?? "").replaceAll('"', '""')}"`;
  const header = "Source,Name,Title,Company,Email,Phone,Industry,Location,PlateNumber,VehicleDetails,ProfileUrl,ConsentGranted,FetchedBy,OwnerId,PointsCost";
  const rows = leads.map((l) =>
    [
      l.source, l.name, l.title, l.company, l.email, l.phone, l.industry,
      l.location, l.plateNumber, l.vehicleDetails, l.profileUrl,
      l.consentGranted, l.fetchedByLoginId, l.ownerAccountId, l.pointsCost,
    ].map(csvCell).join(",")
  );
  const csv = [header, ...rows].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `leadx_leads_${acc.loginId}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
  setBanner(`Downloaded CSV with ${leads.length} collected lead${leads.length > 1 ? "s" : ""}.`, "success");
}

function renderBanner() {
  if (!state.banner) return "";
  return `
    <div class="banner ${esc(state.banner.type)}">
      <span>${esc(state.banner.message)}</span>
      <button class="btn btn-subtle btn-sm" id="dismiss-banner-btn">Dismiss</button>
    </div>
  `;
}

function renderLoginView() {
  const isSignIn = state.authTab === "signin";
  return `
    <div class="auth-center">
      <div class="auth-box">
        <div class="wallet-card">
          <div class="section-label">LeadX Pro · Web Portal</div>
          <h1 style="margin:8px 0 6px;font-size:26px;">Owner &amp; Employee Access</h1>
          <div style="color:#a1a1aa;font-size:13px;">
            Separate Owner &amp; Employee IDs · ₹1 = 1 Pt · 5,000 Pts Minimum Reserve
            · Mode: ${cloud.enabled ? (state.googleUser ? `Firestore (${esc(state.googleUser.email)})` : "Firebase Google Sign-In Ready") : "Browser Persistent Workspace"}
          </div>
        </div>

        ${renderBanner()}

        <div class="card strong stack">
          <div class="segmented">
            <button class="seg-btn ${isSignIn ? "active" : ""}" id="auth-tab-signin">Sign In (ID &amp; Pass)</button>
            <button class="seg-btn ${!isSignIn ? "active" : ""}" id="auth-tab-register">Create Owner ID</button>
          </div>
          <form id="auth-form" class="stack">
            <input class="input mono" id="login-id-input" placeholder="${isSignIn ? "Login ID (e.g. OWN1001 or EMP001)" : "New Owner ID (e.g. OWN2001)"}" required />
            ${!isSignIn ? `<input class="input" id="owner-name-input" placeholder="Owner Full Name" />` : ""}
            <input class="input" type="password" id="login-pass-input" placeholder="Password" required />
            <button class="btn btn-primary" type="submit">${isSignIn ? "Sign In with ID & Password" : "Create Owner ID (5,000 Pts Min)"}</button>
          </form>
          ${isSignIn ? `
            <div>
              <div class="section-label" style="margin-bottom:8px;">Default Workspace IDs</div>
              <div class="grid-2">
                <button type="button" class="btn btn-subtle" id="fill-owner-btn">Owner: OWN1001 / Owner@123</button>
                <button type="button" class="btn btn-subtle" id="fill-emp-btn">Employee: EMP001 / Emp@123</button>
              </div>
            </div>
          ` : ""}
          ${cloud.enabled ? (
            state.googleUser
              ? `<button type="button" class="btn btn-outline" id="google-signout-btn">Disconnect Google Account (${esc(state.googleUser.email)})</button>`
              : `<button type="button" class="btn btn-outline" id="google-signin-btn">Connect Google Cloud Sync (Optional)</button>`
          ) : ""}
        </div>

        <div class="card stack">
          <div class="row-between">
            <div class="section-label">Android App (.apk) · Direct Chrome Download</div>
            <span class="pill ok">UNIVERSAL APK</span>
          </div>
          <p style="margin:0;font-size:13px;color:var(--graphite);">
            Download <strong>LeadX-Pro.apk</strong> directly in Chrome. To share with teammates, use <strong>Share Permanent Link</strong> below (never share Chrome's temporary Downloads URL or a Private repo link).
          </p>
          <div class="grid-2">
            <a class="btn btn-primary" href="${esc(getApkDownloadUrl())}" download="LeadX-Pro.apk" style="text-decoration:none;">Download LeadX-Pro.apk</a>
            <button type="button" class="btn btn-outline" id="share-apk-url-btn">Share Permanent Link</button>
          </div>
          <button type="button" class="btn btn-subtle" id="how-to-install-btn">How to Install (.apk) · Unknown Sources Guide</button>
        </div>
      </div>
    </div>
    ${renderHowToInstallModal()}
  `;
}

function renderDashboard(acc) {
  const isOwner = acc.role === "owner";
  const leads = state.workspace.leads.filter((l) => isOwner ? l.ownerAccountId === acc.loginId : l.fetchedByLoginId === acc.loginId);
  const employees = state.workspace.accounts.filter((a) => a.ownerAccountId === acc.ownerAccountId && a.role !== "owner");
  const txs = state.workspace.transactions.filter((t) => isOwner ? t.ownerAccountId === acc.loginId : t.loginId === acc.loginId).slice(0, 8);
  const usable = Math.max(0, acc.balance - MIN_WALLET_BALANCE);
  const meterPct = Math.min(100, Math.round((acc.balance / Math.max(MIN_WALLET_BALANCE * 2, 1)) * 100));

  return `
    <div class="stack">
      <div class="row-between">
        <div>
          <div class="section-label">${isOwner ? "Owner Dashboard · Role: owner" : "Employee Dashboard · Role: employee"}</div>
          <h1 style="margin:4px 0 6px;font-size:26px;">${esc(acc.fullName)}</h1>
          <div class="row-gap">
            <span class="pill dark mono">${esc(acc.loginId)}</span>
            <span class="pill">${isOwner ? "OWNER" : `EMP · OWNER ${esc(acc.ownerAccountId)}`}</span>
          </div>
        </div>
        <div class="row-gap">
          <button class="btn btn-outline btn-sm" id="switch-id-btn">Switch Login ID</button>
        </div>
      </div>

      ${renderBanner()}

      <div class="wallet-card">
        <div class="row-between">
          <div class="section-label">Wallet Balance · ₹1 = 1 Pt</div>
          <span class="pill ${acc.balance >= MIN_WALLET_BALANCE ? "ok" : "warn"}">${acc.balance >= MIN_WALLET_BALANCE ? "5,000+ RESERVE ACTIVE" : "BELOW 5,000 RESERVE"}</span>
        </div>
        <div class="wallet-amount mono">${formatPts(acc.balance)} pts</div>
        <div class="meter-track"><div class="meter-fill" style="width:${meterPct}%"></div></div>
        <div class="row-between" style="font-size:13px;color:#d4d4d8;margin-bottom:16px;">
          <span>Min Reserve: ${formatPts(MIN_WALLET_BALANCE)} pts</span>
          <span class="mono" style="font-weight:700;color:#fff;">Usable: ${formatPts(usable)} pts</span>
        </div>
        <button class="btn btn-white" style="width:100%;" id="open-topup-btn">
          Top-Up Wallet (${isOwner ? "Owner" : "Employee"} · ₹1 = 1 Pt)
        </button>
      </div>

      ${acc.balance < MIN_WALLET_BALANCE ? `
        <div class="banner warning">Minimum 5000 balance required, please recharge before fetching leads or transferring points.</div>
      ` : ""}

      <div class="grid-2">
        <div class="card">
          <div class="section-label">${isOwner ? "Team Collected Leads" : "My Collected Leads"}</div>
          <div class="mono" style="font-size:28px;font-weight:700;margin-top:6px;">${leads.length}</div>
        </div>
        <div class="card">
          <div class="section-label">${isOwner ? "Team Employees" : "Assigned Owner ID"}</div>
          <div class="mono" style="font-size:28px;font-weight:700;margin-top:6px;">${isOwner ? employees.length : esc(acc.ownerAccountId)}</div>
        </div>
      </div>

      <div class="grid-2">
        <button class="btn btn-outline" style="justify-content:space-between;padding:16px;height:auto;" id="dash-linkedin-btn">
          <span><strong>LinkedIn URL / DOM</strong><br/><small style="font-weight:500;color:var(--graphite);">10 pts / lead</small></span>
          <span>→</span>
        </button>
        <button class="btn btn-outline" style="justify-content:space-between;padding:16px;height:auto;" id="dash-plate-btn">
          <span><strong>Number Plate RC</strong><br/><small style="font-weight:500;color:var(--graphite);">15 pts / live RC check</small></span>
          <span>→</span>
        </button>
      </div>

      ${isOwner ? `
        <div class="grid-2">
          <button class="btn btn-primary" id="dash-transfer-btn">Transfer Points to Employee ID</button>
          <button class="btn btn-outline" id="dash-export-csv-btn">Export Firestore Leads CSV</button>
        </div>
      ` : ""}

      <div class="card">
        <div class="row-between" style="margin-bottom:8px;">
          <div class="section-label">Recent Transactions</div>
          <button class="btn btn-subtle btn-sm" id="dash-view-ledger-btn">Full Ledger &amp; CSV</button>
        </div>
        ${txs.length === 0 ? `<p style="color:var(--graphite);margin:0;">No transactions yet.</p>` : txs.map((tx) => `
          <div class="list-row">
            <div>
              <div style="font-weight:600;font-size:14px;">${esc(tx.note)}</div>
              <div class="mono" style="font-size:12px;color:var(--muted);">${esc(tx.loginId)}${tx.counterpartyId ? ` · ${esc(tx.counterpartyId)}` : ""}</div>
            </div>
            <div style="text-align:right;" class="mono">
              <div class="${tx.amount >= 0 ? "credit" : "debit"}">${tx.amount >= 0 ? "+" : ""}${formatPts(tx.amount)}</div>
              <div style="font-size:12px;color:var(--graphite);">Bal ${formatPts(tx.balanceAfter)}</div>
            </div>
          </div>
        `).join("")}
      </div>
    </div>
  `;
}

function renderTransferScreen(acc) {
  const employees = state.workspace.accounts.filter((a) => a.ownerAccountId === acc.ownerAccountId && a.role !== "owner");
  const transferLogs = state.workspace.transactions.filter(
    (t) => t.ownerAccountId === acc.loginId && (t.type === "transfer_out" || t.type === "transfer_in")
  );
  const usable = Math.max(0, acc.balance - MIN_WALLET_BALANCE);

  return `
    <div class="stack">
      <div class="row-between">
        <div>
          <div class="section-label">Owner Point Transfer</div>
          <h1 style="margin:4px 0 0;font-size:26px;">Transfer Points by Employee ID</h1>
        </div>
        <button class="btn btn-outline btn-sm" id="transfer-back-btn">← Dashboard</button>
      </div>

      ${renderBanner()}

      <div class="wallet-card">
        <div class="section-label">Available Owner Balance</div>
        <div class="wallet-amount mono">${formatPts(acc.balance)} pts</div>
        <div style="color:#d4d4d8;font-size:13px;">Shareable now: <strong class="mono">${formatPts(usable)} pts</strong> (5,000 pts reserve locked)</div>
      </div>

      <div class="card strong stack">
        <div class="section-label">Select Registered Employee ID</div>
        ${employees.map((e) => `
          <button type="button" class="btn btn-subtle transfer-emp-pick" data-id="${esc(e.loginId)}" style="justify-content:space-between;width:100%;">
            <span class="mono"><strong>${esc(e.loginId)}</strong> · ${esc(e.fullName)}</span>
            <span class="mono">${formatPts(e.balance)} pts</span>
          </button>
        `).join("")}
        <form id="transfer-form" class="stack">
          <input class="input mono" id="transfer-recipient-input" value="${esc(employees[0]?.loginId || "")}" placeholder="Employee Login ID (e.g. EMP001)" required />
          <div class="grid-4">
            ${["500", "1000", "2000", "5000"].map((p) => `<button type="button" class="btn btn-subtle btn-sm transfer-amt-preset" data-amt="${p}">${formatPts(p)}</button>`).join("")}
          </div>
          <input class="input mono" type="number" id="transfer-amount-input" value="2000" placeholder="Points to transfer" required />
          <button class="btn btn-primary" type="submit">Transfer Points &amp; Update 'transactions'</button>
        </form>
      </div>

      <div class="card">
        <div class="section-label" style="margin-bottom:8px;">'transactions' Transfer Log (${transferLogs.length})</div>
        ${transferLogs.length === 0 ? `<p style="color:var(--graphite);margin:0;">No point transfers yet.</p>` : transferLogs.map((tx) => `
          <div class="list-row">
            <div>
              <div style="font-weight:600;">${esc(tx.note)}</div>
              <div class="mono" style="font-size:12px;color:var(--muted);">${esc(tx.loginId)} ↔ ${esc(tx.counterpartyId)}</div>
            </div>
            <div class="mono" style="text-align:right;">
              <div class="${tx.amount >= 0 ? "credit" : "debit"}">${tx.amount >= 0 ? "+" : ""}${formatPts(tx.amount)}</div>
              <div style="font-size:12px;">Bal ${formatPts(tx.balanceAfter)}</div>
            </div>
          </div>
        `).join("")}
      </div>
    </div>
  `;
}

function renderFetchScreen(acc) {
  const isLi = state.fetchTab === "linkedin";
  const selectedCount = state.candidates.filter((c) => c.selected).length;
  const hasSurepass = Boolean(
    (state.apiKeys.surepassToken || window.LEADX_API_CONFIG?.SUREPASS_API_TOKEN || "").trim()
  );
  const hasProxycurl = Boolean(
    (state.apiKeys.proxycurlKey || window.LEADX_API_CONFIG?.PROXYCURL_API_KEY || "").trim()
  );
  const bgWorkerCode = buildChromeBackgroundServiceScript(FIRESTORE_DB_ID);

  return `
    <div class="stack">
      <div class="row-between">
        <div>
          <div class="section-label">Real Lead Intelligence · Zero Fake Data</div>
          <h1 style="margin:4px 0 0;font-size:26px;">Fetch Leads</h1>
        </div>
        <span class="pill dark mono">Usable: ${formatPts(Math.max(0, acc.balance - MIN_WALLET_BALANCE))} pts</span>
      </div>

      ${acc.balance < MIN_WALLET_BALANCE ? `<div class="banner warning">Minimum 5000 balance required, please recharge</div>` : ""}
      ${renderBanner()}

      <div class="segmented">
        <button class="seg-btn ${isLi ? "active" : ""}" id="fetch-tab-li">LinkedIn URL &amp; DOM (10 pts)</button>
        <button class="seg-btn ${!isLi ? "active" : ""}" id="fetch-tab-plate">Number Plate RC (15 pts)</button>
      </div>

      ${isLi ? `
        <div class="card strong stack">
          <div class="row-between">
            <div class="section-label">1. Proxycurl Live Profile URL</div>
            <span class="pill">${hasProxycurl ? "KEY CONFIGURED" : "SET KEY IN SETTINGS"}</span>
          </div>
          <form id="li-url-form" class="stack">
            <input class="input" id="li-url-input" placeholder="https://www.linkedin.com/in/username" required />
            <button class="btn btn-primary" type="submit">Extract Real LinkedIn Profile</button>
          </form>
        </div>

        <div class="card stack">
          <div class="row-between" style="flex-wrap:wrap;gap:8px;">
            <div class="section-label">2. LinkedIn Sales Navigator DOM &amp; Chrome Background Scraper</div>
            <div class="row-gap">
              <button type="button" class="btn btn-outline btn-sm" id="copy-extractor-btn">Copy Content Extractor JS</button>
              <button type="button" class="btn btn-subtle btn-sm" id="copy-bg-script-btn">Copy Chrome Background Worker</button>
            </div>
          </div>
          <p style="margin:0;color:var(--graphite);font-size:13px;">Paste either the JSON output from <code>linkedin-scraper.js</code> or raw visible Sales Navigator search result HTML below. Extracts Full Name, Company, Designation, Location &amp; Profile URL and saves directly to Firestore <code>leads</code>.</p>
          <textarea class="textarea mono" rows="4" id="li-dom-json-input" placeholder='[{"fullName":"Satya Nadella","company":"Microsoft","designation":"CEO","location":"Redmond, WA","profileUrl":"https://www.linkedin.com/in/satyanadella"}]'></textarea>
          <button type="button" class="btn btn-outline" id="import-dom-btn">Parse Visible Sales Navigator DOM / JSON</button>
        </div>

        ${state.candidates.length > 0 ? `
          <div class="card strong stack">
            <div class="row-between">
              <div><strong>${state.candidates.length} Extracted Leads</strong> · ${selectedCount} selected (-${selectedCount * LINKEDIN_COST_PER_LEAD} pts)</div>
              <button type="button" class="btn btn-subtle btn-sm" id="clear-candidates-btn">Clear</button>
            </div>
            ${state.candidates.map((c) => `
              <label class="card" style="display:flex;gap:12px;align-items:flex-start;cursor:pointer;padding:12px;">
                <input type="checkbox" class="cand-check" data-id="${esc(c.id)}" ${c.selected ? "checked" : ""} />
                <div style="flex:1;">
                  <div class="row-between"><strong>${esc(c.name)}</strong><span class="pill">${esc(c.captureMethod)}</span></div>
                  <div style="font-size:13px;">${esc(c.title)} ${c.company ? `· ${esc(c.company)}` : ""}</div>
                  ${c.location ? `<div style="font-size:12px;color:var(--graphite);">${esc(c.location)}</div>` : ""}
                  <div class="mono" style="font-size:11px;color:var(--muted);">${esc(c.profileUrl)}</div>
                </div>
              </label>
            `).join("")}
            <button type="button" class="btn btn-primary" id="save-li-leads-btn">Save ${selectedCount} Lead${selectedCount > 1 ? "s" : ""} to Firestore (-${selectedCount * LINKEDIN_COST_PER_LEAD} pts)</button>
          </div>
        ` : ""}
        <div class="card">
          <div class="section-label">Chrome Extension Background Script (linkedin-scraper.js)</div>
          <code class="mono" style="display:block;font-size:11px;white-space:pre-wrap;word-break:break-word;color:var(--graphite);margin-top:6px;max-height:140px;overflow:auto;">${esc(bgWorkerCode)}</code>
        </div>
      ` : `
        <div class="card strong stack">
          <div class="row-between">
            <div class="section-label">Indian Vehicle Number Plate Verification (api-services.js)</div>
            <span class="pill">${hasSurepass ? "SUREPASS LIVE (-15 PTS)" : "RTO REGISTRY · SET SUREPASS_API_TOKEN"}</span>
          </div>
          <div class="plate-preview">
            <span class="plate-ind mono">IND</span>
            <span class="plate-digits mono" id="plate-preview-digits">MH02AB1234</span>
          </div>
          <form id="plate-form" class="stack">
            <input class="input mono" id="plate-input" placeholder="MH02AB1234" required />
            <div class="row-gap">
              ${["MH02AB1234", "KA05MJ4321", "DL01CA9999"].map((p) => `<button type="button" class="btn btn-subtle btn-sm plate-sample-btn" data-plate="${p}">${p}</button>`).join("")}
            </div>
            <button class="btn btn-primary" type="submit">${hasSurepass ? "Verify Live Vahan RC (-15 pts)" : "Verify Real RTO Jurisdiction (0 pts)"}</button>
          </form>
        </div>

        ${state.vehicleResult ? `
          <div class="card strong stack">
            <div class="row-between">
              <div class="section-label">Vahan Record · ${esc(state.vehicleResult.plateNumber)}</div>
              <span class="pill ${state.vehicleResult.isLiveRcData ? "ok" : ""}">${state.vehicleResult.isLiveRcData ? "LIVE RC (-15 PTS)" : "RTO VERIFIED (0 PTS)"}</span>
            </div>
            <div class="list-row"><span>RTO Office</span><strong>${esc(state.vehicleResult.rtoOffice)}</strong></div>
            <div class="list-row"><span>Registered City</span><strong>${esc(state.vehicleResult.registeredCity)}</strong></div>
            <div class="list-row"><span>State / UT</span><strong>${esc(state.vehicleResult.registeredState)}</strong></div>
            ${!state.vehicleResult.isLiveRcData ? `
              <div class="banner warning">${esc(state.vehicleResult.statusMessage)}</div>
            ` : `
              <div class="list-row"><span>Manufacturer / Model</span><strong>${esc(state.vehicleResult.manufacturer)} ${esc(state.vehicleResult.vehicleModel)}</strong></div>
              <div class="list-row"><span>Fuel / Class</span><strong>${esc(state.vehicleResult.fuelType)} · ${esc(state.vehicleResult.vehicleClass)}</strong></div>
              <div class="list-row"><span>Insurance Valid Till</span><strong>${esc(state.vehicleResult.insuranceValidTill)}</strong></div>
              <div class="list-row"><span>PUC / Fitness</span><strong>${esc(state.vehicleResult.pucValidTill)} / ${esc(state.vehicleResult.fitnessValidTill)}</strong></div>
              ${!state.vehicleResult.consentGranted ? `
                <button type="button" class="btn btn-outline" id="grant-consent-btn">Request Consent &amp; Unlock Owner Details</button>
              ` : `
                <div class="banner success">OWNER CONSENT VERIFIED · ${esc(state.vehicleResult.ownerName)} ${state.vehicleResult.ownerContact ? `· ${esc(state.vehicleResult.ownerContact)}` : ""}</div>
              `}
            `}
          </div>
        ` : ""}
      `}
    </div>
  `;
}

function renderTeamScreen(acc) {
  const isOwner = acc.role === "owner";
  const allScopedLeads = state.workspace.leads.filter((l) => isOwner ? l.ownerAccountId === acc.loginId : l.fetchedByLoginId === acc.loginId);
  const q = state.searchQuery.trim().toLowerCase();
  const leads = q
    ? allScopedLeads.filter((l) =>
        [l.name, l.company, l.email, l.phone, l.plateNumber, l.fetchedByLoginId].some((f) => String(f || "").toLowerCase().includes(q))
      )
    : allScopedLeads;
  const txs = state.workspace.transactions.filter((t) => isOwner ? t.ownerAccountId === acc.loginId : t.loginId === acc.loginId);
  const employees = state.workspace.accounts.filter((a) => a.ownerAccountId === acc.loginId && a.role !== "owner");

  return `
    <div class="stack">
      <div class="row-between">
        <div>
          <div class="section-label">Records, Team &amp; Ledger</div>
          <h1 style="margin:4px 0 0;font-size:26px;">Team &amp; Ledger</h1>
        </div>
        ${isOwner ? `<button class="btn btn-primary btn-sm" id="team-export-csv-btn">Export Leads CSV</button>` : ""}
      </div>

      ${renderBanner()}

      <div class="segmented">
        <button class="seg-btn ${state.teamTab === "leads" ? "active" : ""}" id="team-tab-leads">Leads (${allScopedLeads.length})</button>
        <button class="seg-btn ${state.teamTab === "ledger" ? "active" : ""}" id="team-tab-ledger">Ledger (${txs.length})</button>
        ${isOwner ? `<button class="seg-btn ${state.teamTab === "directory" ? "active" : ""}" id="team-tab-dir">Team Directory (${employees.length})</button>` : ""}
      </div>

      ${state.teamTab === "leads" ? `
        <input class="input" id="leads-search-input" value="${esc(state.searchQuery)}" placeholder="Search leads by name, company, plate, or ID..." />
        ${leads.length === 0 ? `<div class="card"><p style="margin:0;color:var(--graphite);">No collected leads yet. Fetch real LinkedIn or Vahan RC leads from the Fetch Leads tab.</p></div>` : leads.map((l) => `
          <div class="card stack" style="gap:6px;">
            <div class="row-between">
              <strong>${esc(l.name)}</strong>
              <span class="pill dark">${esc(l.source).toUpperCase()}</span>
            </div>
            <div style="font-size:13px;">${esc(l.company)} ${l.title ? `· ${esc(l.title)}` : ""}</div>
            ${l.location || l.plateNumber ? `<div style="font-size:13px;color:var(--graphite);">${esc(l.location)} ${l.plateNumber ? `· ${esc(l.plateNumber)}` : ""}</div>` : ""}
            ${l.email || l.phone ? `<div class="mono" style="font-size:12px;">${esc(l.email)} ${esc(l.phone)}</div>` : ""}
            <div class="row-between" style="font-size:12px;">
              <span class="mono" style="color:var(--muted);">BY ${esc(l.fetchedByLoginId)}</span>
              <span class="debit mono">-${l.pointsCost} pts</span>
            </div>
          </div>
        `).join("")}
      ` : state.teamTab === "ledger" ? `
        <div class="card">
          ${txs.map((tx) => `
            <div class="list-row">
              <div>
                <div style="font-weight:600;">${esc(tx.note)}</div>
                <div class="mono" style="font-size:12px;color:var(--muted);">ID: ${esc(tx.loginId)}${tx.counterpartyId ? ` · Partner: ${esc(tx.counterpartyId)}` : ""}</div>
              </div>
              <div class="mono" style="text-align:right;">
                <div class="${tx.amount >= 0 ? "credit" : "debit"}">${tx.amount >= 0 ? "+" : ""}${formatPts(tx.amount)}</div>
                <div style="font-size:12px;">Bal ${formatPts(tx.balanceAfter)}</div>
              </div>
            </div>
          `).join("")}
        </div>
      ` : `
        <div class="card strong stack">
          <div class="section-label">Create Registered Employee ID &amp; Password</div>
          <form id="create-emp-form" class="stack">
            <input class="input mono" id="new-emp-id" placeholder="Employee ID (e.g. EMP002)" required />
            <input class="input" id="new-emp-name" placeholder="Full Name" required />
            <input class="input" type="password" id="new-emp-pass" placeholder="Password (min 6 chars)" required />
            <button class="btn btn-primary" type="submit">Add Employee (Starts with 5,000 pts)</button>
          </form>
        </div>
        <div class="card">
          <div class="section-label" style="margin-bottom:8px;">Registered Employees (${employees.length})</div>
          ${employees.map((e) => `
            <div class="list-row">
              <div><strong class="mono">${esc(e.loginId)}</strong> · ${esc(e.fullName)}</div>
              <div class="mono"><strong>${formatPts(e.balance)} pts</strong></div>
            </div>
          `).join("")}
        </div>
      `}
    </div>
  `;
}

function renderSettingsScreen() {
  return `
    <div class="stack">
      <div>
        <div class="section-label">Live API Integrations</div>
        <h1 style="margin:4px 0 0;font-size:26px;">API Keys &amp; Cloud Sync</h1>
      </div>
      ${renderBanner()}
      <div class="card strong stack">
        <div class="section-label">Third-Party Provider Keys (Stored in your browser only)</div>
        <form id="apikeys-form" class="stack">
          <label style="font-size:13px;font-weight:600;">Surepass Vahan RC API Token (for Live Number Plate RC checks)
            <input class="input mono" id="surepass-key-input" value="${esc(state.apiKeys.surepassToken || "")}" placeholder="Paste Surepass Bearer Token" style="margin-top:6px;" />
          </label>
          <label style="font-size:13px;font-weight:600;">Proxycurl API Key (for headless LinkedIn URL lookup)
            <input class="input mono" id="proxycurl-key-input" value="${esc(state.apiKeys.proxycurlKey || "")}" placeholder="Paste Proxycurl Key" style="margin-top:6px;" />
          </label>
          <button class="btn btn-primary" type="submit">Save API Keys</button>
        </form>
      </div>
      <div class="card stack">
        <div class="section-label">Reset Browser Workspace</div>
        <p style="margin:0;font-size:13px;color:var(--graphite);">Resets local browser accounts back to OWN1001 &amp; EMP001 with 0 fake leads.</p>
        <button type="button" class="btn btn-outline" id="reset-local-btn">Reset Browser Workspace</button>
      </div>
    </div>
  `;
}

function renderHowToInstallModal() {
  if (!state.howToInstallModalOpen) return "";
  return `
    <div class="modal-backdrop" id="install-guide-backdrop">
      <div class="modal-card stack" style="max-width:540px;max-height:90vh;overflow:auto;">
        <div class="row-between">
          <div>
            <div class="section-label">Android Sideload Guide</div>
            <h2 style="margin:4px 0 0;">How to Install LeadX Pro (.apk)</h2>
          </div>
          <button type="button" class="btn btn-subtle btn-sm" id="close-install-guide-btn">✕</button>
        </div>
        <div class="banner warning">
          <span>Why shared APKs look blocked: Android disables <strong>Install from unknown sources</strong> by default for Chrome, Files, and WhatsApp until enabled once.</span>
        </div>
        <div class="card" style="padding:14px;">
          <div class="section-label">Step 01 · Open in Chrome Downloads or Files</div>
          <p style="margin:6px 0 0;font-size:13px;color:var(--graphite);">Open <strong>LeadX-Pro.apk</strong> from Chrome Downloads or your Files app. On Realme, Oppo (ColorOS), or Xiaomi devices, avoid tapping inside WhatsApp directly.</p>
        </div>
        <div class="card" style="padding:14px;">
          <div class="section-label">Step 02 · Enable 'Install from unknown sources'</div>
          <p style="margin:6px 0 0;font-size:13px;color:var(--graphite);">When Android prompts <em>"For your security, your phone isn't allowed to install unknown apps"</em>, tap <strong>Settings</strong> and toggle ON <strong>Allow from this source</strong> for Chrome or Files (<code>Settings → Apps → Special app access → Install unknown apps</code>).</p>
        </div>
        <div class="card" style="padding:14px;">
          <div class="section-label">Step 03 · Tap Install Universal APK</div>
          <p style="margin:6px 0 0;font-size:13px;color:var(--graphite);">Return to the installer and tap <strong>Install</strong>. LeadX Pro is distributed as a standard universal <code>.apk</code> (not a split <code>.aab</code> bundle). If Play Protect prompts, tap <em>More details → Install anyway</em>.</p>
        </div>
        <div class="grid-2">
          <button type="button" class="btn btn-primary" id="copy-install-guide-btn">Copy Install Guide</button>
          <button type="button" class="btn btn-outline" id="dismiss-install-guide-btn">Done</button>
        </div>
      </div>
    </div>
  `;
}

function renderTopUpModal(acc) {
  if (!state.topUpModalOpen) return "";
  return `
    <div class="modal-backdrop" id="topup-backdrop">
      <div class="modal-card stack">
        <div class="row-between">
          <div>
            <div class="section-label">Razorpay Instant Top-Up · ₹1 = 1 Pt</div>
            <h2 style="margin:4px 0 0;">Recharge ${esc(acc.loginId)}</h2>
          </div>
          <button type="button" class="btn btn-subtle btn-sm" id="close-topup-btn">✕</button>
        </div>
        <div class="grid-4">
          ${["1000", "2500", "5000", "10000"].map((p) => `<button type="button" class="btn ${state.topUpAmount === p ? "btn-primary" : "btn-subtle"} btn-sm topup-preset-btn" data-amt="${p}">₹${formatPts(p)}</button>`).join("")}
        </div>
        <form id="topup-form" class="stack">
          <input class="input mono" type="number" id="topup-amount-input" value="${esc(state.topUpAmount)}" min="100" required />
          <button class="btn btn-primary" type="submit">Pay ₹${esc(state.topUpAmount)} &amp; Add Points</button>
        </form>
      </div>
    </div>
  `;
}

function render() {
  const root = document.getElementById("app");
  const acc = activeAccount();

  if (!acc) {
    root.innerHTML = renderLoginView();
    bindLoginEvents();
    return;
  }

  const isOwner = acc.role === "owner";
  let content = "";
  if (state.route === "dashboard") content = renderDashboard(acc);
  else if (state.route === "transfer" && isOwner) content = renderTransferScreen(acc);
  else if (state.route === "fetch") content = renderFetchScreen(acc);
  else if (state.route === "team") content = renderTeamScreen(acc);
  else content = renderSettingsScreen();

  root.innerHTML = `
    <div class="layout-frame">
      <aside class="sidebar">
        <div class="brand-mark">
          <div class="brand-kicker">LeadX Pro · Web</div>
          <div class="brand-title">${isOwner ? "Owner Portal" : "Employee Portal"}</div>
          <div class="mono" style="font-size:12px;color:#d4d4d8;">${esc(acc.loginId)} · ${formatPts(acc.balance)} pts</div>
        </div>
        <button class="nav-btn ${state.route === "dashboard" ? "active" : ""}" data-nav="dashboard">Dashboard</button>
        <button class="nav-btn ${state.route === "fetch" ? "active" : ""}" data-nav="fetch">Fetch Real Leads</button>
        ${isOwner ? `<button class="nav-btn ${state.route === "transfer" ? "active" : ""}" data-nav="transfer">Transfer Points (ID)</button>` : ""}
        <button class="nav-btn ${state.route === "team" ? "active" : ""}" data-nav="team">Team &amp; Ledger</button>
        <button class="nav-btn ${state.route === "settings" ? "active" : ""}" data-nav="settings">API Keys &amp; Sync</button>
        <div style="margin-top:auto;padding-top:16px;" class="stack">
          <a class="btn btn-primary" href="${esc(getApkDownloadUrl())}" download="LeadX-Pro.apk" style="width:100%;text-decoration:none;">Download Android APK</a>
          <button class="btn btn-subtle" style="width:100%;" id="sidebar-how-to-install-btn">How to Install (.apk)</button>
          <button class="btn btn-outline" style="width:100%;" id="sidebar-logout-btn">Switch Login ID</button>
        </div>
      </aside>
      <main class="main-stage">${content}</main>
    </div>
    ${renderTopUpModal(acc)}
    ${renderHowToInstallModal()}
  `;

  bindAppEvents();
}

function runSafe(fn) {
  Promise.resolve().then(fn).catch((err) => setBanner(err.message || "Action failed.", "error"));
}

function bindHowToInstallModalEvents() {
  const closeModal = () => {
    state.howToInstallModalOpen = false;
    render();
  };
  document.getElementById("close-install-guide-btn")?.addEventListener("click", closeModal);
  document.getElementById("dismiss-install-guide-btn")?.addEventListener("click", closeModal);
  document.getElementById("install-guide-backdrop")?.addEventListener("click", (e) => {
    if (e.target.id === "install-guide-backdrop") closeModal();
  });
  document.getElementById("copy-install-guide-btn")?.addEventListener("click", () =>
    runSafe(async () => {
      await navigator.clipboard.writeText(INSTALL_GUIDE_TEXT);
      state.howToInstallModalOpen = false;
      setBanner("Install guide & Unknown Sources steps copied to clipboard.", "success");
    })
  );
}

function bindLoginEvents() {
  document.getElementById("dismiss-banner-btn")?.addEventListener("click", () => {
    state.banner = null;
    render();
  });
  document.getElementById("share-apk-url-btn")?.addEventListener("click", () =>
    runSafe(copyOrShareApkLink)
  );
  document.getElementById("how-to-install-btn")?.addEventListener("click", () => {
    state.howToInstallModalOpen = true;
    render();
  });
  bindHowToInstallModalEvents();
  document.getElementById("google-signin-btn")?.addEventListener("click", () =>
    runSafe(async () => {
      await cloud.signInGoogle();
    })
  );
  document.getElementById("google-signout-btn")?.addEventListener("click", () =>
    runSafe(() => cloud.signOutGoogle())
  );
  document.getElementById("auth-tab-signin")?.addEventListener("click", () => {
    state.authTab = "signin";
    state.banner = null;
    render();
  });
  document.getElementById("auth-tab-register")?.addEventListener("click", () => {
    state.authTab = "register";
    state.banner = null;
    render();
  });
  document.getElementById("fill-owner-btn")?.addEventListener("click", () => {
    document.getElementById("login-id-input").value = "OWN1001";
    document.getElementById("login-pass-input").value = "Owner@123";
  });
  document.getElementById("fill-emp-btn")?.addEventListener("click", () => {
    document.getElementById("login-id-input").value = "EMP001";
    document.getElementById("login-pass-input").value = "Emp@123";
  });
  document.getElementById("auth-form")?.addEventListener("submit", (e) => {
    e.preventDefault();
    const id = document.getElementById("login-id-input").value;
    const pass = document.getElementById("login-pass-input").value;
    if (state.authTab === "signin") {
      runSafe(() => handleSignIn(id, pass));
    } else {
      const name = document.getElementById("owner-name-input")?.value || id;
      runSafe(() => handleCreateOwner(id, name, pass, true));
    }
  });
}

function bindAppEvents() {
  document.getElementById("dismiss-banner-btn")?.addEventListener("click", () => {
    state.banner = null;
    render();
  });
  document.querySelectorAll("[data-nav]").forEach((btn) =>
    btn.addEventListener("click", () => {
      state.route = btn.dataset.nav;
      state.banner = null;
      render();
    })
  );
  const signOutTeam = () => {
    state.loggedInId = null;
    state.banner = null;
    render();
  };
  document.getElementById("sidebar-logout-btn")?.addEventListener("click", signOutTeam);
  document.getElementById("sidebar-how-to-install-btn")?.addEventListener("click", () => {
    state.howToInstallModalOpen = true;
    render();
  });
  bindHowToInstallModalEvents();
  document.getElementById("switch-id-btn")?.addEventListener("click", signOutTeam);

  // Dashboard actions
  document.getElementById("open-topup-btn")?.addEventListener("click", () => {
    state.topUpModalOpen = true;
    render();
  });
  document.getElementById("dash-linkedin-btn")?.addEventListener("click", () => {
    state.fetchTab = "linkedin";
    state.route = "fetch";
    render();
  });
  document.getElementById("dash-plate-btn")?.addEventListener("click", () => {
    state.fetchTab = "plate";
    state.route = "fetch";
    render();
  });
  document.getElementById("dash-transfer-btn")?.addEventListener("click", () => {
    state.route = "transfer";
    render();
  });
  document.getElementById("dash-view-ledger-btn")?.addEventListener("click", () => {
    state.teamTab = "ledger";
    state.route = "team";
    render();
  });
  document.getElementById("dash-export-csv-btn")?.addEventListener("click", () => runSafe(handleExportOwnerCsv));
  document.getElementById("team-export-csv-btn")?.addEventListener("click", () => runSafe(handleExportOwnerCsv));

  // Top-Up Modal
  document.getElementById("close-topup-btn")?.addEventListener("click", () => {
    state.topUpModalOpen = false;
    render();
  });
  document.querySelectorAll(".topup-preset-btn").forEach((b) =>
    b.addEventListener("click", () => {
      state.topUpAmount = b.dataset.amt;
      render();
    })
  );
  document.getElementById("topup-form")?.addEventListener("submit", (e) => {
    e.preventDefault();
    const val = document.getElementById("topup-amount-input").value;
    runSafe(() => handleTopUp(val));
  });

  // Transfer screen
  document.getElementById("transfer-back-btn")?.addEventListener("click", () => {
    state.route = "dashboard";
    render();
  });
  document.querySelectorAll(".transfer-emp-pick").forEach((b) =>
    b.addEventListener("click", () => {
      document.getElementById("transfer-recipient-input").value = b.dataset.id;
    })
  );
  document.querySelectorAll(".transfer-amt-preset").forEach((b) =>
    b.addEventListener("click", () => {
      document.getElementById("transfer-amount-input").value = b.dataset.amt;
    })
  );
  document.getElementById("transfer-form")?.addEventListener("submit", (e) => {
    e.preventDefault();
    const rec = document.getElementById("transfer-recipient-input").value;
    const amt = document.getElementById("transfer-amount-input").value;
    runSafe(() => handleTransferPoints(rec, amt));
  });

  // Fetch screen
  document.getElementById("fetch-tab-li")?.addEventListener("click", () => {
    state.fetchTab = "linkedin";
    render();
  });
  document.getElementById("fetch-tab-plate")?.addEventListener("click", () => {
    state.fetchTab = "plate";
    render();
  });
  document.getElementById("li-url-form")?.addEventListener("submit", (e) => {
    e.preventDefault();
    const url = document.getElementById("li-url-input").value;
    runSafe(() => handleExtractLinkedInUrl(url));
  });
  document.getElementById("copy-extractor-btn")?.addEventListener("click", () =>
    runSafe(async () => {
      await navigator.clipboard.writeText(SALES_NAVIGATOR_CONTENT_SCRIPT);
      setBanner("Copied Sales Navigator DOM content script to clipboard.", "success");
    })
  );
  document.getElementById("copy-bg-script-btn")?.addEventListener("click", () =>
    runSafe(async () => {
      await navigator.clipboard.writeText(buildChromeBackgroundServiceScript(FIRESTORE_DB_ID));
      setBanner("Copied Chrome background service worker script to clipboard.", "success");
    })
  );
  document.getElementById("import-dom-btn")?.addEventListener("click", () =>
    runSafe(() => handleImportDomJson(document.getElementById("li-dom-json-input").value))
  );
  document.getElementById("clear-candidates-btn")?.addEventListener("click", () => {
    state.candidates = [];
    render();
  });
  document.querySelectorAll(".cand-check").forEach((chk) =>
    chk.addEventListener("change", () => {
      const target = state.candidates.find((c) => c.id === chk.dataset.id);
      if (target) target.selected = chk.checked;
      render();
    })
  );
  document.getElementById("save-li-leads-btn")?.addEventListener("click", () => runSafe(handleSaveLinkedInLeads));

  const plateInput = document.getElementById("plate-input");
  const platePreview = document.getElementById("plate-preview-digits");
  plateInput?.addEventListener("input", () => {
    const clean = normalizePlate(plateInput.value).slice(0, 12);
    plateInput.value = clean;
    if (platePreview) platePreview.textContent = clean || "MH02AB1234";
  });
  document.querySelectorAll(".plate-sample-btn").forEach((b) =>
    b.addEventListener("click", () => {
      if (plateInput) plateInput.value = b.dataset.plate;
      if (platePreview) platePreview.textContent = b.dataset.plate;
    })
  );
  document.getElementById("plate-form")?.addEventListener("submit", (e) => {
    e.preventDefault();
    runSafe(() => handleLookupVehicle(plateInput.value));
  });
  document.getElementById("grant-consent-btn")?.addEventListener("click", () => runSafe(handleGrantVehicleConsent));

  // Team screen
  document.getElementById("team-tab-leads")?.addEventListener("click", () => {
    state.teamTab = "leads";
    render();
  });
  document.getElementById("team-tab-ledger")?.addEventListener("click", () => {
    state.teamTab = "ledger";
    render();
  });
  document.getElementById("team-tab-dir")?.addEventListener("click", () => {
    state.teamTab = "directory";
    render();
  });
  document.getElementById("leads-search-input")?.addEventListener("input", (e) => {
    state.searchQuery = e.target.value;
    render();
    const el = document.getElementById("leads-search-input");
    if (el) {
      el.focus();
      el.setSelectionRange(el.value.length, el.value.length);
    }
  });
  document.getElementById("create-emp-form")?.addEventListener("submit", (e) => {
    e.preventDefault();
    const id = document.getElementById("new-emp-id").value;
    const name = document.getElementById("new-emp-name").value;
    const pass = document.getElementById("new-emp-pass").value;
    runSafe(() => handleCreateEmployee(id, name, pass));
  });

  // Settings
  document.getElementById("apikeys-form")?.addEventListener("submit", (e) => {
    e.preventDefault();
    state.apiKeys = {
      surepassToken: document.getElementById("surepass-key-input").value.trim(),
      proxycurlKey: document.getElementById("proxycurl-key-input").value.trim(),
    };
    saveApiKeys(state.apiKeys);
    setBanner("API keys saved to browser local storage.", "success");
  });
  document.getElementById("reset-local-btn")?.addEventListener("click", () =>
    runSafe(async () => {
      state.workspace = await createSeedWorkspace();
      saveWorkspace();
      state.loggedInId = null;
      setBanner("Browser workspace reset to initial OWN1001 & EMP001 accounts.", "success");
    })
  );
}

(async function bootstrap() {
  state.workspace = await loadWorkspace();
  state.firebaseReady = await cloud.init();
  cloud.onAuthChange(async (user) => {
    state.googleUser = user;
    if (user) {
      state.workspace = await cloud.syncDown(state.workspace);
      saveWorkspace();
    }
    render();
  });
  if (!cloud.enabled) {
    render();
  }
})();
