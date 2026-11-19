const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any LeadX collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("team_accounts").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("leads").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("team_accounts")
      .doc("OWN1001")
      .set({
        userId: ALICE_UID,
        loginId: "OWN1001",
        passwordHash: "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92",
        fullName: "Owner Admin",
        role: "owner",
        ownerAccountId: "OWN1001",
        balance: 12500,
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Workspace owner can write role profile, team accounts, and update balances", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      role: "owner",
      activeLoginId: "OWN1001",
      fullName: "Owner Admin",
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("team_accounts").get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("team_accounts")
      .doc("OWN1001")
      .set({
        userId: ALICE_UID,
        loginId: "OWN1001",
        passwordHash: "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92",
        fullName: "Owner Admin",
        role: "owner",
        ownerAccountId: "OWN1001",
        balance: 12500,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("team_accounts")
      .doc("OWN1001")
      .update({
        balance: 17500,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("team_accounts")
      .doc("OWN1001")
      .update({
        role: "employee",
        updatedAt: new Date(Date.now() - 500),
      })
  );
});

test("Leads, consent updates, transactions, and cross-user isolation obey parity rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("leads").doc("lead_rc_1").set({
      userId: ALICE_UID,
      source: "numberplate",
      ownerAccountId: "OWN1001",
      fetchedByLoginId: "EMP001",
      name: "HYUNDAI CRETA",
      title: "LMV · PETROL · 2022",
      company: "RTO MH02 · Mumbai West",
      email: "",
      phone: "",
      industry: "Ins Valid Till 15 Nov 2027",
      location: "Mumbai, Maharashtra",
      plateNumber: "MH02AB1234",
      vehicleDetails: "Consent Pending",
      profileUrl: "",
      consentGranted: false,
      pointsCost: 15,
      createdAt: now,
    })
  );

  // Numberplate consent unlock allowed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("leads").doc("lead_rc_1").update({
      consentGranted: true,
      vehicleDetails: "Consent Verified",
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_1")
      .set({
        userId: ALICE_UID,
        type: "lead_vehicle",
        ownerAccountId: "OWN1001",
        loginId: "EMP001",
        counterpartyId: "",
        amount: -15,
        balanceAfter: 6175,
        note: "Vehicle Plate Check (MH02AB1234)",
        createdAt: now,
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_1")
      .delete()
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("team_accounts").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("leads").doc("lead_rc_1").get()
  );
});
