const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function nowPast() {
  return new Date(Date.now() - 2000);
}

test("Unauthenticated user cannot read or write any VieSimple collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("vault_documents").where("sharedWithUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("deadlines").where("sharedWithUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("subscriptions").where("sharedWithUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("tasks").where("sharedWithUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("user_profiles").doc(ALICE_UID).get());
});

test("UserProfile: owner can create, read, update; other user is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowPast();

  await assertSucceeds(
    aliceDb.collection("user_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Martin",
      email: "alice@example.fr",
      profileType: "family",
      planTier: "free",
      onboardingCompleted: true,
      activeHouseholdId: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("user_profiles").doc(ALICE_UID).update({
      profileType: "couple",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(bobDb.collection("user_profiles").doc(ALICE_UID).get());
  await assertFails(
    aliceDb.collection("user_profiles").doc(ALICE_UID).update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("VaultDocument: private by default, other household member cannot access private doc, shared doc works", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = nowPast();

  // Private document by Alice
  await assertSucceeds(
    aliceDb.collection("vault_documents").doc("doc_private_1").set({
      userId: ALICE_UID,
      ownerName: "Alice",
      householdId: "",
      visibility: "private",
      sharedWithUids: [ALICE_UID],
      title: "Carte d'identité Alice",
      category: "Administratif",
      docType: "Pièce d'identité",
      folder: "Identité",
      docDate: "2024-01-15",
      expirationDate: "2034-01-15",
      issuer: "Préfecture",
      referenceNumber: "ID-9988",
      amount: 0,
      notes: "Strictement privé",
      tags: ["identité"],
      confidentiality: "sensitive",
      status: "active",
      favorite: true,
      ocrText: "République Française",
      ocrConfidence: "high",
      localFileUri: "",
      versionNumber: 1,
      versionHistory: ["v1 · Création"],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot get or list Alice's private doc
  await assertFails(bobDb.collection("vault_documents").doc("doc_private_1").get());
  // Unfiltered list fails
  await assertFails(aliceDb.collection("vault_documents").get());
  // Filtered list succeeds for Alice
  await assertSucceeds(aliceDb.collection("vault_documents").where("sharedWithUids", "array-contains", ALICE_UID).get());

  // Reject private doc trying to sneak Bob into sharedWithUids
  await assertFails(
    aliceDb.collection("vault_documents").doc("doc_bad_scope").set({
      userId: ALICE_UID,
      ownerName: "Alice",
      householdId: "hh_1",
      visibility: "private",
      sharedWithUids: [ALICE_UID, BOB_UID],
      title: "Bad scope",
      category: "Autre",
      docType: "",
      folder: "",
      docDate: "",
      expirationDate: "",
      issuer: "",
      referenceNumber: "",
      amount: 0,
      notes: "",
      tags: [],
      confidentiality: "standard",
      status: "active",
      favorite: false,
      ocrText: "",
      ocrConfidence: "manual",
      localFileUri: "",
      versionNumber: 1,
      versionHistory: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Shared household doc
  await assertSucceeds(
    aliceDb.collection("vault_documents").doc("doc_shared_1").set({
      userId: ALICE_UID,
      ownerName: "Alice",
      householdId: "hh_1",
      visibility: "household",
      sharedWithUids: [ALICE_UID, BOB_UID],
      title: "Contrat Assurance Habitation",
      category: "Assurance",
      docType: "Contrat",
      folder: "Foyer",
      docDate: "2026-01-01",
      expirationDate: "2026-12-31",
      issuer: "AXA",
      referenceNumber: "AX-123",
      amount: 420.5,
      notes: "",
      tags: ["logement"],
      confidentiality: "standard",
      status: "active",
      favorite: false,
      ocrText: "Assurance habitation",
      ocrConfidence: "high",
      localFileUri: "",
      versionNumber: 1,
      versionHistory: ["v1 · Création"],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("vault_documents").doc("doc_shared_1").get());
  await assertFails(bobDb.collection("vault_documents").doc("doc_shared_1").delete());
  await assertFails(charlieDb.collection("vault_documents").doc("doc_shared_1").get());
});

test("Household: owner creates household & invite, Bob joins with inviteCode, Charlie without code is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = nowPast();

  await assertSucceeds(
    aliceDb.collection("households").doc("hh_1").set({
      ownerId: ALICE_UID,
      name: "Foyer Martin",
      inviteCode: "VS4829AB",
      memberUids: [ALICE_UID],
      adminUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("household_invites").doc("VS4829AB").set({
      householdId: "hh_1",
      householdName: "Foyer Martin",
      ownerId: ALICE_UID,
      createdAt: ts,
    })
  );

  // Invite listing is strictly forbidden
  await assertFails(bobDb.collection("household_invites").get());

  // Bob looks up exact invite code
  await assertSucceeds(bobDb.collection("household_invites").doc("VS4829AB").get());

  // Bob joins with valid invite code
  await assertSucceeds(
    bobDb.collection("households").doc("hh_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      lastInviteCode: "VS4829AB",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Charlie cannot join with wrong invite code
  await assertFails(
    charlieDb.collection("households").doc("hh_1").update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      lastInviteCode: "WRONGCODE",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Deadlines, Subscriptions, Tasks, and User Subcollections obey isolation & schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowPast();

  await assertSucceeds(
    aliceDb.collection("deadlines").doc("dl_1").set({
      userId: ALICE_UID,
      householdId: "hh_1",
      visibility: "household",
      sharedWithUids: [ALICE_UID, BOB_UID],
      title: "Renouvellement assurance auto",
      description: "",
      category: "Assurance",
      dueDate: "2026-12-15",
      recurrence: "yearly",
      customRecurrenceDays: 0,
      reminders: [30, 7, 1],
      status: "pending",
      priority: "high",
      linkedDocumentId: "",
      linkedTaskId: "",
      assigneeUid: ALICE_UID,
      assigneeName: "Alice",
      confidentiality: "standard",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Shared member Bob can mark deadline done
  await assertSucceeds(
    bobDb.collection("deadlines").doc("dl_1").update({
      status: "done",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot delete Alice's deadline
  await assertFails(bobDb.collection("deadlines").doc("dl_1").delete());

  // Subscription
  await assertSucceeds(
    aliceDb.collection("subscriptions").doc("sub_1").set({
      userId: ALICE_UID,
      householdId: "",
      visibility: "private",
      sharedWithUids: [ALICE_UID],
      name: "Forfait Fibre",
      category: "Abonnements",
      amount: 34.99,
      currency: "EUR",
      frequency: "monthly",
      nextBillingDate: "2026-12-05",
      paymentMethod: "Prélèvement SEPA",
      status: "active",
      cancellationStatus: "none",
      notes: "",
      linkedDocumentId: "",
      alertDaysBefore: 3,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Task
  await assertSucceeds(
    aliceDb.collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      householdId: "hh_1",
      visibility: "household",
      sharedWithUids: [ALICE_UID, BOB_UID],
      title: "Envoyer justificatif de domicile",
      description: "",
      category: "Logement",
      dueDate: "2026-11-30",
      priority: "medium",
      assigneeUid: BOB_UID,
      assigneeName: "Bob",
      status: "todo",
      subtasks: ["0|Télécharger facture EDF", "0|Envoyer par mail"],
      checklistTemplate: "",
      linkedDocumentId: "",
      linkedDeadlineId: "",
      confidentiality: "standard",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Security logs & AI conversations strictly user-scoped
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("security_logs").doc("log_1").set({
      userId: ALICE_UID,
      action: "EXPORT_DATA",
      detail: "Export complet effectué",
      createdAt: ts,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("security_logs").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("msg_1").set({
      userId: ALICE_UID,
      sender: "user",
      text: "Quand expire mon assurance auto ?",
      proposedAction: "",
      createdAt: ts,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("ai_conversations").get());

  // FCM tokens & privacy-safe reminder metadata strictly owner-scoped
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("fcm_tokens").doc("dev_android_1").set({
      userId: ALICE_UID,
      token: "fcm_token_example_1234567890",
      platform: "android",
      privacyMode: "strict_no_pii",
      notifDeadlines: true,
      notifTasks: true,
      notifQuietMode: false,
      notifHour: 9,
      upcomingDeadlinesCount: 2,
      overdueTasksCount: 1,
      nextDeadlineDate: "2026-11-28",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("fcm_tokens").doc("dev_android_1").update({
      upcomingDeadlinesCount: 3,
      overdueTasksCount: 0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read/write denied & tampered privacyMode rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("fcm_tokens").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("fcm_tokens").doc("dev_bad").set({
      userId: ALICE_UID,
      token: "fcm_token_example_1234567890",
      platform: "android",
      privacyMode: "cleartext",
      notifDeadlines: true,
      notifTasks: true,
      notifQuietMode: false,
      notifHour: 9,
      upcomingDeadlinesCount: 0,
      overdueTasksCount: 0,
      nextDeadlineDate: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
