import {
  LINKEDIN_COST_PER_LEAD,
  assertMinBalanceForSpend,
} from "./api-services.js";

/**
 * Self-contained DOM extraction script designed for Chrome Extension content/background execution
 * or DevTools execution on an authenticated LinkedIn Sales Navigator search results page.
 * Only reads visible DOM elements already rendered in the user's browser session.
 */
export const SALES_NAVIGATOR_CONTENT_SCRIPT = `
(function extractVisibleSalesNavigatorLeads() {
  var rows = document.querySelectorAll(
    'ol.artdeco-list > li.artdeco-list__item, div[data-x--lead-search-result], li.reusable-search__result-container, div.entity-result'
  );
  var extracted = [];
  rows.forEach(function (row) {
    var nameEl = row.querySelector(
      'span[data-anonymize="person-name"], .artdeco-entity-lockup__title a span, .entity-result__title-text a span[aria-hidden="true"], a[href*="/sales/lead/"], a[href*="/in/"]'
    );
    var linkEl = row.querySelector(
      'a[href*="/sales/lead/"], .artdeco-entity-lockup__title a, a[href*="/in/"]'
    );
    var titleEl = row.querySelector(
      'span[data-anonymize="title"], .artdeco-entity-lockup__subtitle span[data-anonymize="title"], .artdeco-entity-lockup__subtitle, .entity-result__primary-subtitle'
    );
    var companyEl = row.querySelector(
      'a[data-anonymize="company-name"], span[data-anonymize="company-name"], .artdeco-entity-lockup__subtitle a'
    );
    var locEl = row.querySelector(
      'span[data-anonymize="location"], .artdeco-entity-lockup__caption span, .entity-result__secondary-subtitle'
    );

    var fullName = nameEl ? nameEl.innerText.trim() : "";
    var profileUrl = linkEl && linkEl.href ? linkEl.href.split("?")[0] : "";
    var designation = titleEl ? titleEl.innerText.trim() : "";
    var company = companyEl ? companyEl.innerText.trim() : "";
    var location = locEl ? locEl.innerText.trim() : "";

    if (!company && designation.indexOf(" at ") !== -1) {
      var parts = designation.split(" at ");
      designation = parts[0].trim();
      company = parts.slice(1).join(" at ").trim();
    }

    if (fullName && fullName.toLowerCase() !== "linkedin member" && profileUrl) {
      extracted.push({
        fullName: fullName,
        company: company,
        designation: designation,
        location: location,
        profileUrl: profileUrl
      });
    }
  });
  return extracted;
})();
`.trim();

/**
 * Generates Chrome Manifest V3 background service worker code that receives visible
 * Sales Navigator leads from the content script and saves them into Firestore `leads`.
 */
export function buildChromeBackgroundServiceScript(databaseId = "(default)") {
  return `
// Chrome Extension background.js for LeadX Pro LinkedIn Sales Navigator Capture
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type !== "LEADX_CAPTURE_VISIBLE_DOM") return false;

  chrome.scripting.executeScript({
    target: { tabId: sender.tab?.id || message.tabId },
    func: () => ${SALES_NAVIGATOR_CONTENT_SCRIPT.replace(/^[\s\S]*?\(\s*function/, "function")}
  }).then(async (injectionResults) => {
    const leads = injectionResults?.[0]?.result || [];
    if (!leads.length) {
      sendResponse({ ok: false, error: "No visible Sales Navigator results found on active tab." });
      return;
    }
    const { uid, idToken, ownerAccountId, fetchedByLoginId } = message;
    const projectId = message.projectId;
    const baseUrl =
      "https://firestore.googleapis.com/v1/projects/" +
      encodeURIComponent(projectId) +
      "/databases/" +
      encodeURIComponent("${databaseId}") +
      "/documents/users/" +
      encodeURIComponent(uid) +
      "/leads";

    for (const item of leads) {
      await fetch(baseUrl, {
        method: "POST",
        headers: {
          "Authorization": "Bearer " + idToken,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          fields: {
            userId: { stringValue: uid },
            source: { stringValue: "linkedin" },
            ownerAccountId: { stringValue: ownerAccountId },
            fetchedByLoginId: { stringValue: fetchedByLoginId },
            name: { stringValue: item.fullName },
            title: { stringValue: item.designation || "LinkedIn Lead" },
            company: { stringValue: item.company || "" },
            email: { stringValue: "" },
            phone: { stringValue: "" },
            industry: { stringValue: "" },
            location: { stringValue: item.location || "" },
            plateNumber: { stringValue: "" },
            vehicleDetails: { stringValue: "Chrome Background SalesNav Capture" },
            profileUrl: { stringValue: item.profileUrl },
            consentGranted: { booleanValue: true },
            pointsCost: { integerValue: "10" },
            createdAt: { timestampValue: new Date().toISOString() }
          }
        })
      });
    }
    sendResponse({ ok: true, count: leads.length, leads });
  }).catch((err) => {
    sendResponse({ ok: false, error: err?.message || String(err) });
  });

  return true; // keep async message channel open
});
`.trim();
}

/**
 * Normalizes a single scraped LinkedIn Sales Navigator entry into a LeadX candidate object.
 */
function toCandidate(item, captureMethod = "Sales Navigator DOM") {
  const fullName = String(item.fullName || item.name || "").trim();
  if (!fullName || fullName.toLowerCase() === "linkedin member") return null;
  let designation = String(item.designation || item.title || "").trim();
  let company = String(item.company || "").trim();
  if (!company && designation.includes(" at ")) {
    const idx = designation.indexOf(" at ");
    company = designation.slice(idx + 4).trim();
    designation = designation.slice(0, idx).trim();
  }
  return {
    id: `li_dom_${crypto.randomUUID()}`,
    name: fullName,
    title: designation || "LinkedIn Profile",
    company,
    email: String(item.email || "").trim(),
    phone: String(item.phone || "").trim(),
    industry: String(item.industry || "").trim(),
    location: String(item.location || "").trim(),
    profileUrl: String(item.profileUrl || item.url || "").trim(),
    captureMethod,
    selected: true,
  };
}

/**
 * Extracts visible Sales Navigator search results directly from a DOM Document (or parsed HTML string).
 */
export function extractVisibleSalesNavFromHtml(htmlString, DomParserImpl = globalThis.DOMParser) {
  if (typeof DomParserImpl !== "function") {
    throw new Error("DOMParser is unavailable for HTML extraction; paste JSON array instead.");
  }
  const doc = new DomParserImpl().parseFromString(htmlString, "text/html");
  const rows = doc.querySelectorAll(
    'ol.artdeco-list > li.artdeco-list__item, div[data-x--lead-search-result], li.reusable-search__result-container, div.entity-result'
  );
  const results = [];
  rows.forEach((row) => {
    const nameEl = row.querySelector(
      'span[data-anonymize="person-name"], .artdeco-entity-lockup__title a span, .entity-result__title-text a span[aria-hidden="true"], a[href*="/sales/lead/"], a[href*="/in/"]'
    );
    const linkEl = row.querySelector('a[href*="/sales/lead/"], .artdeco-entity-lockup__title a, a[href*="/in/"]');
    const titleEl = row.querySelector(
      'span[data-anonymize="title"], .artdeco-entity-lockup__subtitle span[data-anonymize="title"], .artdeco-entity-lockup__subtitle, .entity-result__primary-subtitle'
    );
    const companyEl = row.querySelector(
      'a[data-anonymize="company-name"], span[data-anonymize="company-name"], .artdeco-entity-lockup__subtitle a'
    );
    const locEl = row.querySelector(
      'span[data-anonymize="location"], .artdeco-entity-lockup__caption span, .entity-result__secondary-subtitle'
    );

    const cand = toCandidate(
      {
        fullName: nameEl?.textContent || "",
        profileUrl: linkEl?.getAttribute("href")?.split("?")[0] || "",
        designation: titleEl?.textContent || "",
        company: companyEl?.textContent || "",
        location: locEl?.textContent || "",
      },
      "Sales Navigator DOM"
    );
    if (cand && cand.profileUrl) results.push(cand);
  });
  return results;
}

/**
 * Parses either a JSON array extracted by the Sales Navigator background/console script
 * or raw copied LinkedIn Sales Navigator HTML markup.
 */
export function parseSalesNavigatorInput(rawInput, DomParserImpl = globalThis.DOMParser) {
  const trimmed = String(rawInput || "").trim();
  if (!trimmed) {
    throw new Error("Paste extracted Sales Navigator JSON or visible DOM HTML first.");
  }
  if (trimmed.startsWith("[")) {
    let arr;
    try {
      arr = JSON.parse(trimmed);
    } catch {
      throw new Error("Invalid JSON array from Sales Navigator DOM script.");
    }
    if (!Array.isArray(arr)) throw new Error("Expected a JSON array of LinkedIn leads.");
    const parsed = arr.map((item) => toCandidate(item, "Sales Navigator DOM")).filter(Boolean);
    if (parsed.length === 0) throw new Error("No valid visible LinkedIn leads found in JSON.");
    return parsed;
  }
  if (trimmed.includes("<")) {
    const htmlResults = extractVisibleSalesNavFromHtml(trimmed, DomParserImpl);
    if (htmlResults.length === 0) {
      throw new Error("No Sales Navigator search result cards detected in pasted HTML.");
    }
    return htmlResults;
  }
  throw new Error("Unrecognized format. Paste either JSON array [...] or Sales Navigator DOM HTML.");
}

/**
 * Deducts 10 points per lead (enforcing the 5,000-point reserve) and persists scraped
 * Sales Navigator leads + point transaction into Firestore `leads` & `transactions` collections.
 */
export async function saveScrapedLinkedInLeadsToFirestore({
  candidates,
  account,
  cloudAdapter = null,
  onPersist = null,
}) {
  const selected = (candidates || []).filter((c) => c.selected);
  if (selected.length === 0) {
    throw new Error("Select at least 1 LinkedIn lead to save.");
  }

  const totalCost = selected.length * LINKEDIN_COST_PER_LEAD;
  assertMinBalanceForSpend(account, totalCost);

  account.balance -= totalCost;
  const now = Date.now();
  const newLeads = selected.map((c, idx) => ({
    id: `lead_${crypto.randomUUID()}`,
    source: "linkedin",
    ownerAccountId: account.ownerAccountId,
    fetchedByLoginId: account.loginId,
    name: c.name,
    title: c.title,
    company: c.company,
    email: c.email || "",
    phone: c.phone || "",
    industry: c.industry || "",
    location: c.location || "",
    plateNumber: "",
    vehicleDetails: c.captureMethod || "Sales Navigator DOM",
    profileUrl: c.profileUrl || "",
    consentGranted: true,
    pointsCost: LINKEDIN_COST_PER_LEAD,
    createdAtMillis: now + idx,
  }));

  const tx = {
    id: `tx_${crypto.randomUUID()}`,
    type: "lead_linkedin",
    ownerAccountId: account.ownerAccountId,
    loginId: account.loginId,
    counterpartyId: "",
    amount: -totalCost,
    balanceAfter: account.balance,
    note: `LinkedIn Capture (${newLeads.length} lead${newLeads.length > 1 ? "s" : ""})`,
    createdAtMillis: now,
  };

  if (typeof onPersist === "function") {
    onPersist(newLeads, tx);
  }
  if (cloudAdapter) {
    await cloudAdapter.updateBalance(account.loginId, account.balance);
    for (const lead of newLeads) {
      await cloudAdapter.writeLead(lead);
    }
    await cloudAdapter.writeTransaction(tx);
  }

  return { savedCount: newLeads.length, totalCost, leads: newLeads, transaction: tx };
}
