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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 10000) {
  return new Date(Date.now() - msOffset);
}

function futureDate(msOffset = 3600000) {
  return new Date(Date.now() + msOffset);
}

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").where("accountStatus", "==", "active").get());
  await assertFails(unauthDb.collection("dating_profiles").where("datingEnabled", "==", true).get());
  await assertFails(unauthDb.collection("battles").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      bio: "",
      interests: [],
      languages: ["English"],
      accountStatus: "active",
      onlineVisible: true,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Users and Private PII split isolation + ghost field & status tamper rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  // Valid public user creation (no PII, no @DocumentId `id`)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_x",
      displayName: "Alice",
      bio: "Design & nightlife",
      interests: ["Music", "Art"],
      languages: ["English"],
      accountStatus: "active",
      onlineVisible: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid owner-only private PII creation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      birthYear: 1998,
      ageVerified: true,
      showOnlineStatus: true,
      allowFriendRequests: true,
      notifyMessages: true,
      notifyMatches: true,
      notifyGroups: true,
      notifyBattles: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob CANNOT read Alice's private PII
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get()
  );

  // Alice CANNOT inject ghost field `isVerified` or self-promote accountStatus
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      accountStatus: "suspended",
      updatedAt: pastDate(1000),
    })
  );

  // Bob CANNOT update Alice's public profile
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      bio: "Hacked",
      updatedAt: pastDate(1000),
    })
  );

  // Filtered active user query succeeds
  await assertSucceeds(
    bobDb.collection("users").where("accountStatus", "==", "active").get()
  );
});

test("Dating profile enforces 18+ age gate and owner-only writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  // Under-18 profile MUST fail
  await assertFails(
    aliceDb.collection("dating_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      datingEnabled: true,
      datingBio: "Hi",
      age: 17,
      city: "Seattle",
      relationshipIntent: "dating",
      conversationStyle: "playful",
      availabilityStatus: "tonight",
      interests: ["Coffee"],
      languages: ["English"],
      preferredAgeMin: 18,
      preferredAgeMax: 30,
      preferredCity: "Seattle",
      preferredIntents: ["dating"],
      preferredLanguages: ["English"],
      photos: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 18+ profile succeeds
  await assertSucceeds(
    aliceDb.collection("dating_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      datingEnabled: true,
      datingBio: "Coffee & late-night synth",
      age: 25,
      city: "Seattle",
      relationshipIntent: "dating",
      conversationStyle: "playful",
      availabilityStatus: "tonight",
      interests: ["Coffee", "Synth"],
      languages: ["English"],
      preferredAgeMin: 21,
      preferredAgeMax: 34,
      preferredCity: "Seattle",
      preferredIntents: ["dating", "casual"],
      preferredLanguages: ["English"],
      photos: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Query aligned with datingEnabled == true succeeds
  await assertSucceeds(
    bobDb.collection("dating_profiles").where("datingEnabled", "==", true).get()
  );
});

test("Private groups isolate members and allow revocable invites", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  // Alice creates private group + owner member record
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_secret").set({
      name: "Midnight Founders",
      description: "Private circle",
      visibility: "private",
      ownerId: ALICE_UID,
      memberCount: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_secret").collection("members").doc(ALICE_UID).set({
      groupId: "grp_secret",
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice_x",
      role: "owner",
      status: "active",
      joinedAt: ts,
      updatedAt: ts,
    })
  );

  // Charlie CANNOT read private group or its member list
  await assertFails(charlieDb.collection("groups").doc("grp_secret").get());
  await assertFails(charlieDb.collection("groups").doc("grp_secret").collection("members").get());

  // Alice creates and revokes invite
  await assertSucceeds(
    aliceDb.collection("group_invites").doc("inv_1").set({
      groupId: "grp_secret",
      inviterId: ALICE_UID,
      inviteCode: "XCHAT99",
      revoked: false,
      expiresAt: futureDate(),
      createdAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_invites").doc("inv_1").update({ revoked: true })
  );
});

test("Conversations and messages enforce memberUids isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_ab").set({
      type: "direct",
      memberUids: [ALICE_UID, BOB_UID],
      lastMessagePreview: "Hey Bob",
      lastSenderId: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_ab").collection("messages").doc("msg_1").set({
      conversationId: "conv_ab",
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "text",
      content: "Hey Bob!",
      readBy: [ALICE_UID],
      deleted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can query messages
  await assertSucceeds(
    bobDb.collection("conversations").doc("conv_ab").collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Charlie CANNOT read conversation, single message doc, unfiltered messages, or Alice's messages
  await assertFails(charlieDb.collection("conversations").doc("conv_ab").get());
  await assertFails(
    charlieDb.collection("conversations").doc("conv_ab").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc("conv_ab").collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc("conv_ab").collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    bobDb.collection("conversations").doc("conv_ab").collection("messages").doc("msg_spoof").set({
      conversationId: "conv_ab",
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "text",
      content: "Spoofed!",
      readBy: [BOB_UID],
      deleted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Matches require mutual likes and cannot be forged", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  // Alice likes Bob
  await assertSucceeds(
    aliceDb.collection("likes").doc(`${ALICE_UID}_${BOB_UID}`).set({
      fromUserId: ALICE_UID,
      toUserId: BOB_UID,
      interactionType: "like",
      intentContext: "dating",
      createdAt: ts,
    })
  );

  // Alice CANNOT forge a match before Bob likes back
  await assertFails(
    aliceDb.collection("matches").doc(`${ALICE_UID}_${BOB_UID}`).set({
      userAId: ALICE_UID,
      userBId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      status: "active",
      conversationId: `dm_${ALICE_UID}_${BOB_UID}`,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob likes Alice back + creates match
  await assertSucceeds(
    bobDb.collection("likes").doc(`${BOB_UID}_${ALICE_UID}`).set({
      fromUserId: BOB_UID,
      toUserId: ALICE_UID,
      interactionType: "like",
      intentContext: "dating",
      createdAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("matches").doc(`${ALICE_UID}_${BOB_UID}`).set({
      userAId: ALICE_UID,
      userBId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      status: "active",
      conversationId: `dm_${ALICE_UID}_${BOB_UID}`,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Unmatching works and locks terminal state
  await assertSucceeds(
    aliceDb.collection("matches").doc(`${ALICE_UID}_${BOB_UID}`).update({
      status: "unmatched",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("matches").doc(`${ALICE_UID}_${BOB_UID}`).update({
      status: "active",
      updatedAt: pastDate(500),
    })
  );
});

test("Battles block arbitrary score tampering and lock terminal completed state", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("battles").doc("battle_1").set({
      title: "Midnight Roast",
      topic: "Freestyle",
      creatorId: ALICE_UID,
      challengerId: ALICE_UID,
      challengerName: "Alice",
      opponentId: "",
      opponentName: "",
      challengerScore: 0,
      opponentScore: 0,
      status: "waiting",
      durationSeconds: 180,
      winnerId: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob joins battle
  await assertSucceeds(
    bobDb.collection("battles").doc("battle_1").update({
      opponentId: BOB_UID,
      opponentName: "Bob",
      status: "live",
      startedAt: pastDate(5000),
      endsAt: futureDate(180000),
      updatedAt: pastDate(5000),
    })
  );

  // Charlie sends valid Crown (+10) gift
  await assertSucceeds(
    charlieDb.collection("battles").doc("battle_1").update({
      challengerScore: 10,
      updatedAt: pastDate(2000),
    })
  );

  // Charlie CANNOT inject +500 cheat score
  await assertFails(
    charlieDb.collection("battles").doc("battle_1").update({
      challengerScore: 510,
      updatedAt: pastDate(1000),
    })
  );

  // Complete battle locks terminal state
  await assertSucceeds(
    aliceDb.collection("battles").doc("battle_1").update({
      status: "completed",
      winnerId: ALICE_UID,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("battles").doc("battle_1").update({
      opponentScore: 20,
      updatedAt: pastDate(200),
    })
  );
});

test("Reports are write-only for regular users and readable by verified admins only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext("admin_1", {
    email: "webtheblack101@gmail.com",
    email_verified: true,
  }).firestore();
  const spoofAdminDb = testEnv.authenticatedContext("spoof_1", {
    email: "webtheblack101@gmail.com",
    email_verified: false,
  }).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      reportedUserId: BOB_UID,
      targetType: "user",
      targetId: BOB_UID,
      reason: "Harassment",
      description: "Abusive DMs",
      status: "open",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Regular user & spoofed unverified email CANNOT list reports
  await assertFails(bobDb.collection("reports").get());
  await assertFails(spoofAdminDb.collection("reports").get());

  // Verified admin CAN list reports
  await assertSucceeds(adminDb.collection("reports").get());
});
