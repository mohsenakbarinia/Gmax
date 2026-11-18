const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_hunter_01";
const BOB_UID = "bob_hunter_02";

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

function validStatsPayload(uid, created, updated = created) {
  return {
    userId: uid,
    totalHarvests: 1,
    goldTrophies: 1,
    longestVitalShotMeters: 174,
    completedContractsCount: 1,
    activeContractIndex: 1,
    createdAt: created,
    updatedAt: updated,
  };
}

function validTrophyPayload(uid, created, updated = created) {
  return {
    userId: uid,
    species: "ROYAL_STAG",
    weapon: "RIFLE_308",
    hitZone: "HEART",
    rating: "GOLD",
    distanceMeters: 174,
    scorePoints: 950,
    weatherPreset: "GOLDEN_HOUR",
    createdAt: created,
    updatedAt: updated,
  };
}

test("Unauthenticated user: denied all reads and writes on stats and trophies", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("profile").doc("stats").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("profile")
      .doc("stats")
      .set(validStatsPayload(ALICE_UID, now))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("trophies").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trophies")
      .doc("trophy_01")
      .set(validTrophyPayload(ALICE_UID, now))
  );
});

test("Owner: valid UserProfileStats create, read, and partial update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const statsRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("profile")
    .doc("stats");

  await assertSucceeds(statsRef.set(validStatsPayload(ALICE_UID, created)));
  await assertSucceeds(statsRef.get());

  // Mirror TrophyRepository partial update preserving immutable createdAt & userId
  await assertSucceeds(
    statsRef.update({
      totalHarvests: 2,
      goldTrophies: 2,
      longestVitalShotMeters: 215,
      completedContractsCount: 2,
      activeContractIndex: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    statsRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject impossible goldTrophies > totalHarvests
  await assertFails(
    statsRef.update({
      goldTrophies: 99,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field shadow update
  await assertFails(
    statsRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Owner: valid TrophyRecord create, list query, delete succeed; invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const trophiesCol = aliceDb.collection("users").doc(ALICE_UID).collection("trophies");

  // Empty subcollection list must succeed
  await assertSucceeds(trophiesCol.orderBy("createdAt", "desc").get());

  // Valid create (NO @DocumentId `id` field)
  await assertSucceeds(
    trophiesCol.doc("trophy_01").set(validTrophyPayload(ALICE_UID, created))
  );

  // Mirror Kotlin repository query
  await assertSucceeds(trophiesCol.orderBy("createdAt", "desc").limit(50).get());

  // Reject @DocumentId `id` field in payload
  await assertFails(
    trophiesCol.doc("trophy_02").set({
      id: "trophy_02",
      ...validTrophyPayload(ALICE_UID, created),
    })
  );

  // Reject invalid species enum
  await assertFails(
    trophiesCol.doc("trophy_03").set({
      ...validTrophyPayload(ALICE_UID, created),
      species: "DRAGON",
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    trophiesCol.doc("trophy_04").set(
      validTrophyPayload(ALICE_UID, new Date(Date.now() + 60000))
    )
  );

  // Owner can delete own trophy
  await assertSucceeds(trophiesCol.doc("trophy_01").delete());
});

test("Cross-user isolation: Alice cannot read, write, or delete Bob's trophies or stats", async () => {
  const created = new Date(Date.now() - 8000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("profile")
      .doc("stats")
      .set(validStatsPayload(BOB_UID, created));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("trophies")
      .doc("bob_stag")
      .set(validTrophyPayload(BOB_UID, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("profile").doc("stats").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("trophies").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("trophies").doc("bob_stag").delete()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("trophies")
      .doc("injected")
      .set(validTrophyPayload(ALICE_UID, created))
  );
});
